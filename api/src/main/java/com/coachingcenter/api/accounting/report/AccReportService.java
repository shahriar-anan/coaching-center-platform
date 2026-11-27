package com.coachingcenter.api.accounting.report;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.accounting.AccBalances;
import com.coachingcenter.api.accounting.AccDates;
import com.coachingcenter.api.accounting.AccMoney;
import com.coachingcenter.api.accounting.AccRefusals;
import com.coachingcenter.api.accounting.report.AccEntryRows.EntryView;
import com.coachingcenter.api.accounting.report.AccPayeeRows.PayeeRow;

/**
 * The figures of the accounting pages (contracts.md, section 3.2 and 4): the home page here; the reports and the statement follow.
 * Read only. Every money figure comes from {@link AccBalances}, so the home page, the reports and the payee pages agree.
 */
@Service
public class AccReportService {

	/** How many payees the home page lists as owed the most. */
	static final int TOP_OWED = 10;

	/** How many entries the home page lists as the latest. */
	static final int LATEST = 10;

	/** The longest span the monthly report answers (contracts.md, section 3.2). */
	static final long MAX_MONTHS = 36;

	private final JdbcTemplate jdbc;

	private final AccBalances balances;

	private final Clock clock;

	public AccReportService(JdbcTemplate jdbc, AccBalances balances, Clock clock) {
		this.jdbc = jdbc;
		this.balances = balances;
		this.clock = clock;
	}

	/** The home page: owed, spent and income this month and last, each live group, the top owed, the latest entries, 12 months. */
	@Transactional(readOnly = true)
	public Dashboard dashboard() {
		LocalDate today = AccDates.today(clock);
		YearMonth month = YearMonth.from(today);
		YearMonth last = month.minusMonths(1);
		AccBalances.Owed owed = balances.owedAndAdvance(null);
		AccBalances.Totals thisMonth = balances.totals(month.atDay(1), month.atEndOfMonth(), null);
		AccBalances.Totals lastMonth = balances.totals(last.atDay(1), last.atEndOfMonth(), null);

		List<GroupFigure> groups = new ArrayList<>();
		jdbc.query("select id, name_en, name_bn, mode, direction from acc_groups where archived_at is null order by position",
				(rs) -> {
					UUID group = rs.getObject("id", UUID.class);
					String direction = rs.getString("direction");
					AccBalances.Owed groupOwed = balances.owedAndAdvance(group);
					AccBalances.Totals groupMonth = balances.totals(month.atDay(1), month.atEndOfMonth(), group);
					groups.add(new GroupFigure(group, rs.getString("name_en"), rs.getString("name_bn"), rs.getString("mode"), direction,
							AccMoney.format(groupOwed.owed()),
							AccMoney.format("EXPENSE".equals(direction) ? groupMonth.spent() : groupMonth.income())));
				});

		List<PayeeRow> topOwed = AccPayeeRows.find(jdbc, month.atDay(1), month.atEndOfMonth(), "coalesce(b.balance, 0) > 0",
				new ArrayList<>(), "coalesce(b.balance, 0) desc, p.name", TOP_OWED);
		List<EntryView> latest = AccEntryRows.find(jdbc, "", new ArrayList<>(), "e.recorded_at desc, e.no desc", LATEST);

		return new Dashboard(today.toString(), AccMoney.format(owed.owed()), AccMoney.format(owed.advance()),
				AccMoney.format(thisMonth.spent()), AccMoney.format(lastMonth.spent()), AccMoney.format(thisMonth.income()), groups,
				topOwed, latest, twelveMonths(month));
	}

	/** The 12 months ending this month, oldest first; a month with nothing is 0.00. */
	private List<MonthFigure> twelveMonths(YearMonth end) {
		YearMonth start = end.minusMonths(11);
		Map<String, BigDecimal[]> byMonth = new HashMap<>();
		jdbc.query("""
				select to_char(e.entry_date, 'YYYY-MM') as month,
				  coalesce(sum(case when e.kind in ('PAYMENT', 'SPEND') and g.direction = 'EXPENSE' then e.amount end), 0) as expense,
				  coalesce(sum(case when e.kind = 'INCOME' then e.amount end), 0) as income
				from acc_entries e join acc_groups g on g.id = e.group_id
				where e.status = 'ACTIVE' and e.entry_date between ? and ?
				group by 1
				""", (rs) -> {
					byMonth.put(rs.getString("month").trim(), new BigDecimal[] { rs.getBigDecimal("expense"), rs.getBigDecimal("income") });
				}, start.atDay(1), end.atEndOfMonth());
		List<MonthFigure> months = new ArrayList<>();
		for (YearMonth month = start; !month.isAfter(end); month = month.plusMonths(1)) {
			BigDecimal[] found = byMonth.get(month.toString());
			BigDecimal expense = found == null ? BigDecimal.ZERO : found[0];
			BigDecimal income = found == null ? BigDecimal.ZERO : found[1];
			months.add(new MonthFigure(month.toString(), AccMoney.format(expense), AccMoney.format(income)));
		}
		return months;
	}

	// ---- the reports (R2) ---------------------------------------------------------------------------------------------------

	/**
	 * Owed now: every payee with a balance that is not zero, by group in group order, payees by balance (largest first). Archived payees
	 * and groups with a balance are included, marked as archived.
	 */
	@Transactional(readOnly = true)
	public OwedReport owed() {
		YearMonth month = YearMonth.from(AccDates.today(clock));
		List<PayeeRow> rows = AccPayeeRows.find(jdbc, month.atDay(1), month.atEndOfMonth(),
				"g.mode = 'PAYEES' and coalesce(b.balance, 0) <> 0", new ArrayList<>(), "g.position, coalesce(b.balance, 0) desc, p.name", 0);
		Map<UUID, OwedBuilder> groups = new LinkedHashMap<>();
		for (PayeeRow row : rows) {
			groups.computeIfAbsent(row.groupId(), key -> new OwedBuilder(row.groupId(), row.groupNameEn(), row.groupNameBn())).add(row);
		}
		BigDecimal owed = BigDecimal.ZERO;
		BigDecimal advance = BigDecimal.ZERO;
		List<OwedGroup> found = new ArrayList<>();
		for (OwedBuilder group : groups.values()) {
			owed = owed.add(group.owed);
			advance = advance.add(group.advance);
			found.add(group.build());
		}
		return new OwedReport(AccMoney.format(owed), AccMoney.format(advance), found);
	}

	/**
	 * Expense and income by month. The months run from {@code from} to {@code to} (YYYY-MM, both included; at most 36). Without them, the
	 * last 12 months ending this month. One row per group that has an active entry in the range, in group order.
	 */
	@Transactional(readOnly = true)
	public MonthlyReport monthly(String from, String to) {
		YearMonth thisMonth = YearMonth.from(AccDates.today(clock));
		YearMonth end = blank(to) ? thisMonth : AccDates.parseMonth(to, "to");
		YearMonth start = blank(from) ? end.minusMonths(11) : AccDates.parseMonth(from, "from");
		if (start.isAfter(end)) {
			throw AccRefusals.validation("from", "The first month cannot be after the last month.");
		}
		long count = ChronoUnit.MONTHS.between(start, end) + 1;
		if (count > MAX_MONTHS) {
			throw AccRefusals.validation("from", "Choose at most 36 months.");
		}
		List<String> months = new ArrayList<>();
		for (YearMonth month = start; !month.isAfter(end); month = month.plusMonths(1)) {
			months.add(month.toString());
		}
		Map<UUID, MonthlyRowBuilder> rows = new LinkedHashMap<>();
		jdbc.query("""
				select g.id, g.name_en, g.name_bn, g.direction, to_char(e.entry_date, 'YYYY-MM') as month,
				  coalesce(sum(case when e.kind in ('PAYMENT', 'SPEND') and g.direction = 'EXPENSE' then e.amount
				                    when e.kind = 'INCOME' then e.amount else 0 end), 0) as amount
				from acc_entries e join acc_groups g on g.id = e.group_id
				where e.status = 'ACTIVE' and e.entry_date between ? and ?
				group by g.id, g.name_en, g.name_bn, g.direction, g.position, to_char(e.entry_date, 'YYYY-MM')
				order by g.position
				""", (rs) -> {
			UUID group = rs.getObject("id", UUID.class);
			String nameEn = rs.getString("name_en");
			String nameBn = rs.getString("name_bn");
			String direction = rs.getString("direction");
			MonthlyRowBuilder row = rows.computeIfAbsent(group, key -> new MonthlyRowBuilder(group, nameEn, nameBn, direction, months.size()));
			int index = months.indexOf(rs.getString("month").trim());
			if (index >= 0) {
				row.amounts[index] = row.amounts[index].add(rs.getBigDecimal("amount"));
			}
		}, start.atDay(1), end.atEndOfMonth());
		BigDecimal[] expense = zeros(months.size());
		BigDecimal[] income = zeros(months.size());
		List<MonthlyRow> found = new ArrayList<>();
		for (MonthlyRowBuilder row : rows.values()) {
			BigDecimal total = BigDecimal.ZERO;
			List<String> amounts = new ArrayList<>();
			for (int i = 0; i < months.size(); i++) {
				total = total.add(row.amounts[i]);
				amounts.add(AccMoney.format(row.amounts[i]));
				if ("EXPENSE".equals(row.direction)) {
					expense[i] = expense[i].add(row.amounts[i]);
				}
				else {
					income[i] = income[i].add(row.amounts[i]);
				}
			}
			found.add(new MonthlyRow(row.groupId, row.nameEn, row.nameBn, row.direction, amounts, AccMoney.format(total)));
		}
		return new MonthlyReport(months, found, formatAll(expense), formatAll(income));
	}

	/**
	 * One group over a range (both days included): totals per type (quantity only for counted types) and, for a Payees group, per payee.
	 * The range is required. An unknown group is 404.
	 */
	@Transactional(readOnly = true)
	public GroupSummary summary(UUID groupId, String from, String to) {
		requireGroup(groupId);
		LocalDate start = AccDates.parseDay(from, "from");
		LocalDate end = AccDates.parseDay(to, "to");
		checkRange(start, end);
		List<ByType> byType = jdbc.query("""
				select t.id, t.name_en, t.name_bn, t.kind, t.unit_en, t.unit_bn,
				  case when t.kind = 'COUNTED' then sum(e.quantity) end as quantity,
				  sum(e.amount) as amount, count(*) as entries
				from acc_entries e join acc_types t on t.id = e.type_id
				where e.group_id = ? and e.status = 'ACTIVE' and e.entry_date between ? and ?
				group by t.id, t.name_en, t.name_bn, t.kind, t.unit_en, t.unit_bn, t.position
				order by t.position, t.name_en
				""", (rs, row) -> new ByType(rs.getObject("id", UUID.class), rs.getString("name_en"), rs.getString("name_bn"),
						rs.getString("kind"), rs.getString("unit_en"), rs.getString("unit_bn"),
						AccEntryRows.quantity(rs.getBigDecimal("quantity")), AccMoney.format(rs.getBigDecimal("amount")),
						rs.getLong("entries")), groupId, start, end);
		List<ByPayee> byPayee = jdbc.query("""
				select p.id, p.name,
				  coalesce(sum(case when e.kind = 'CHARGE' then e.amount end), 0) as charged,
				  coalesce(sum(case when e.kind = 'PAYMENT' then e.amount end), 0) as paid
				from acc_payees p join acc_entries e on e.payee_id = p.id
				where p.group_id = ? and e.status = 'ACTIVE' and e.entry_date between ? and ?
				group by p.id, p.name
				order by p.name
				""", (rs, row) -> new ByPayee(rs.getObject("id", UUID.class), rs.getString("name"),
						AccMoney.format(rs.getBigDecimal("charged")), AccMoney.format(rs.getBigDecimal("paid"))), groupId, start, end);
		AccBalances.Totals totals = balances.totals(start, end, groupId);
		return new GroupSummary(groupId, start.toString(), end.toString(), byType, byPayee, AccMoney.format(totals.charged()),
				AccMoney.format(totals.paid()), AccMoney.format(totals.spent()), AccMoney.format(totals.income()));
	}

	/**
	 * One payee's statement: the balance before the range, every active entry in it oldest first with the running balance, and the
	 * balance after. The range defaults to this month (each day defaults to this month's first or last day). An unknown payee is 404.
	 */
	@Transactional(readOnly = true)
	public Statement statement(UUID payeeId, String from, String to) {
		YearMonth month = YearMonth.from(AccDates.today(clock));
		List<PayeeRow> found = AccPayeeRows.find(jdbc, month.atDay(1), month.atEndOfMonth(), "p.id = ?",
				new ArrayList<>(List.of(payeeId)), "p.name", 0);
		if (found.isEmpty()) {
			throw AccRefusals.notFound("payee");
		}
		LocalDate start = blank(from) ? month.atDay(1) : AccDates.parseDay(from, "from");
		LocalDate end = blank(to) ? month.atEndOfMonth() : AccDates.parseDay(to, "to");
		checkRange(start, end);
		BigDecimal opening = jdbc.queryForObject("""
				select coalesce(sum(case e.kind when 'CHARGE' then e.amount when 'PAYMENT' then -e.amount else 0 end), 0)
				from acc_entries e where e.payee_id = ? and e.status = 'ACTIVE' and e.entry_date < ?
				""", BigDecimal.class, payeeId, start);
		BigDecimal charged = BigDecimal.ZERO;
		BigDecimal paid = BigDecimal.ZERO;
		List<Object> args = new ArrayList<>(List.of(payeeId, start, end));
		List<EntryView> entries = AccEntryRows.find(jdbc, " where e.payee_id = ? and e.status = 'ACTIVE' and e.entry_date between ? and ?",
				args, "e.entry_date, e.no", 0);
		List<EntryView> lines = new ArrayList<>();
		BigDecimal running = opening;
		for (EntryView entry : entries) {
			BigDecimal amount = new BigDecimal(entry.amount());
			if ("CHARGE".equals(entry.kind())) {
				charged = charged.add(amount);
				running = running.add(amount);
			}
			else {
				paid = paid.add(amount);
				running = running.subtract(amount);
			}
			lines.add(entry.withBalanceAfter(AccMoney.format(running)));
		}
		return new Statement(found.get(0), start.toString(), end.toString(), AccMoney.format(opening), AccMoney.format(charged),
				AccMoney.format(paid), AccMoney.format(running), lines);
	}

	private void requireGroup(UUID groupId) {
		List<UUID> found = jdbc.queryForList("select id from acc_groups where id = ?", UUID.class, groupId);
		if (found.isEmpty()) {
			throw AccRefusals.notFound("group");
		}
	}

	private static void checkRange(LocalDate start, LocalDate end) {
		if (end.isBefore(start)) {
			throw AccRefusals.validation("to", "The end date cannot be before the start date.");
		}
	}

	private static BigDecimal[] zeros(int size) {
		BigDecimal[] values = new BigDecimal[size];
		Arrays.fill(values, BigDecimal.ZERO);
		return values;
	}

	private static List<String> formatAll(BigDecimal[] values) {
		List<String> formatted = new ArrayList<>();
		for (BigDecimal value : values) {
			formatted.add(AccMoney.format(value));
		}
		return formatted;
	}

	private static boolean blank(String text) {
		return text == null || text.isBlank();
	}

	/** The owed rows of one group while they are collected. */
	static final class OwedBuilder {

		private final UUID groupId;

		private final String nameEn;

		private final String nameBn;

		private final List<PayeeRow> payees = new ArrayList<>();

		private BigDecimal owed = BigDecimal.ZERO;

		private BigDecimal advance = BigDecimal.ZERO;

		OwedBuilder(UUID groupId, String nameEn, String nameBn) {
			this.groupId = groupId;
			this.nameEn = nameEn;
			this.nameBn = nameBn;
		}

		void add(PayeeRow row) {
			BigDecimal balance = new BigDecimal(row.balance());
			if (balance.signum() > 0) {
				owed = owed.add(balance);
			}
			else {
				advance = advance.add(balance.negate());
			}
			payees.add(row);
		}

		OwedGroup build() {
			return new OwedGroup(groupId, nameEn, nameBn, AccMoney.format(owed), AccMoney.format(advance), List.copyOf(payees));
		}

	}

	/** The owed report: the total owed and in advance, and each group with its payees. */
	public record OwedReport(String total, String advance, List<OwedGroup> groups) {
	}

	/** One group of the owed report: what its payees are owed and in advance, and the payees (archived ones marked). */
	public record OwedGroup(UUID groupId, String nameEn, String nameBn, String owed, String advance, List<PayeeRow> payees) {
	}

	/** The monthly report: the months, a row per group, and the expense and income of each month. */
	public record MonthlyReport(List<String> months, List<MonthlyRow> rows, List<String> expense, List<String> income) {
	}

	/** One group's amounts per month (in the order of the months) and their total. */
	public record MonthlyRow(UUID groupId, String nameEn, String nameBn, String direction, List<String> amounts, String total) {
	}

	/** One group's totals over a range: per type and per payee, and the four figures. */
	public record GroupSummary(UUID groupId, String from, String to, List<ByType> byType, List<ByPayee> byPayee, String charged,
			String paid, String spent, String income) {
	}

	/** One type's figures: the quantity (counted types only, else null), the amount and the number of entries. */
	public record ByType(UUID typeId, String nameEn, String nameBn, String kind, String unitEn, String unitBn, String quantity,
			String amount, long count) {
	}

	/** One payee's charges and payments in the range. */
	public record ByPayee(UUID payeeId, String name, String charged, String paid) {
	}

	/** A payee's statement: the balances before, charged, paid and after the range, and the lines with the running balance. */
	public record Statement(PayeeRow payee, String from, String to, String opening, String charged, String paid, String closing,
			List<EntryView> lines) {
	}

	/** The months of one group while the monthly report is read. */
	private static final class MonthlyRowBuilder {

		private final UUID groupId;

		private final String nameEn;

		private final String nameBn;

		private final String direction;

		private final BigDecimal[] amounts;

		MonthlyRowBuilder(UUID groupId, String nameEn, String nameBn, String direction, int months) {
			this.groupId = groupId;
			this.nameEn = nameEn;
			this.nameBn = nameBn;
			this.direction = direction;
			this.amounts = zeros(months);
		}

	}

	/** The home page. Money is a string with two decimals. */
	public record Dashboard(String today, String owed, String advance, String spentThisMonth, String spentLastMonth,
			String incomeThisMonth, List<GroupFigure> groups, List<PayeeRow> topOwed, List<EntryView> latest, List<MonthFigure> months) {
	}

	/** One live group: what its payees are owed (Payees groups) and what it had this month (spent or income). */
	public record GroupFigure(UUID groupId, String nameEn, String nameBn, String mode, String direction, String owed, String thisMonth) {
	}

	/** Expense and income of one month, YYYY-MM. */
	public record MonthFigure(String month, String expense, String income) {
	}

}
