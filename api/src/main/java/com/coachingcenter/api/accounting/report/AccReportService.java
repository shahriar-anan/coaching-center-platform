package com.coachingcenter.api.accounting.report;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.accounting.AccBalances;
import com.coachingcenter.api.accounting.AccDates;
import com.coachingcenter.api.accounting.AccMoney;
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
