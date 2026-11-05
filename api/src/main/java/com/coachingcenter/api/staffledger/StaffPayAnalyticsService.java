package com.coachingcenter.api.staffledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;
import com.coachingcenter.api.staffledger.StaffPayAnalyticsQueries.InstructorMonth;
import com.coachingcenter.api.staffledger.StaffPayAnalyticsQueries.MonthKindTotal;
import com.coachingcenter.api.staffledger.StaffPayAnalyticsQueries.PersonRange;
import com.coachingcenter.api.staffledger.StaffPayAnalyticsQueries.StaffLine;
import com.coachingcenter.api.staffledger.StaffPayCalculator.Line;
import com.coachingcenter.api.staffledger.StaffPayOpenDues.OpenDue;

/**
 * The Master Admin's overview and analytics (staff pay plan, phase 6). Read only. Every route checks STAFF_PAY_MANAGE.
 *
 * <p>
 * Figures are money as {@link BigDecimal} to two places. Voided lines are left out everywhere. "Today" and every month
 * boundary are Dhaka dates. The owed split and the overdue part come from {@link StaffPayOpenDues}, which applies the same
 * oldest-first rule as the person balances in {@link StaffPayCalculator}.
 */
@Service
public class StaffPayAnalyticsService {

	private static final String DENIED = "Access is denied.";

	private static final String SALARY = "SALARY";

	private final StaffPayAnalyticsQueries queries;

	private final PermissionChecker permissions;

	private final Clock clock;

	public StaffPayAnalyticsService(StaffPayAnalyticsQueries queries, PermissionChecker permissions, Clock clock) {
		this.queries = queries;
		this.permissions = permissions;
		this.clock = clock;
	}

	/* ---------- overview ---------- */

	/**
	 * What is owed now, split into salaries, marking and adjustments, with the overdue part; the paid this month; the salary
	 * dues in the next thirty days; and who is owed, most overdue first.
	 */
	@Transactional(readOnly = true)
	public Overview overview() {
		requireManage();
		LocalDate today = today();
		Map<UUID, List<StaffLine>> byPerson = new LinkedHashMap<>();
		Map<UUID, String> names = new HashMap<>();
		for (StaffLine line : queries.staffLines()) {
			byPerson.computeIfAbsent(line.userId(), key -> new ArrayList<>()).add(line);
			names.put(line.userId(), line.fullName());
		}

		List<OwingRow> owing = new ArrayList<>();
		List<DueItem> dueItems = new ArrayList<>();
		List<OpenDue> allOpen = new ArrayList<>();
		LocalDate windowEnd = StaffPayMonths.windowEnd(today);
		for (Map.Entry<UUID, List<StaffLine>> person : byPerson.entrySet()) {
			List<OpenDue> open = StaffPayOpenDues.of(toLines(person.getValue()));
			String fullName = names.get(person.getKey());
			allOpen.addAll(open);
			for (OpenDue due : open) {
				if (due.kind() == LedgerKind.SALARY && !due.dueOn().isBefore(today)
						&& !due.dueOn().isAfter(windowEnd)) {
					dueItems.add(new DueItem(person.getKey(), fullName, due.dueOn().toString(), money(due.uncovered())));
				}
			}
			if (!open.isEmpty()) {
				LocalDate next = open.stream().map(OpenDue::dueOn).min(Comparator.naturalOrder()).orElse(null);
				owing.add(new OwingRow(person.getKey(), fullName, roleOf(person.getValue()), split(open, null),
						split(open, today), next == null ? null : next.toString()));
			}
		}

		owing.sort(Comparator.comparing((OwingRow row) -> row.overdue().total()).reversed()
			.thenComparing((OwingRow row) -> row.owedNow().total(), Comparator.reverseOrder())
			.thenComparing(OwingRow::fullName, Comparator.nullsLast(Comparator.naturalOrder()))
			.thenComparing(OwingRow::userId));
		dueItems.sort(Comparator.comparing(DueItem::dueOn).thenComparing(DueItem::fullName,
				Comparator.nullsLast(Comparator.naturalOrder())));

		BigDecimal dueTotal = dueItems.stream().map(DueItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
		LocalDate monthStart = StaffPayMonths.monthStart(today);
		BigDecimal paidThisMonth = queries.paidBetween(monthStart, StaffPayMonths.nextMonthStart(today));
		return new Overview(today.toString(), split(allOpen, null), split(allOpen, today), money(paidThisMonth),
				new DueWindow(today.toString(), windowEnd.toString(), money(dueTotal), dueItems), owing);
	}

	/* ---------- analytics ---------- */

	/**
	 * Payroll by month, sheets published per instructor by month, and each person's earned, paid and balance, for the months
	 * {@code from} to {@code to} (YYYY-MM, both included). A missing end defaults to this month, and a missing start to twelve
	 * months before the end. Empty months show zero.
	 */
	@Transactional(readOnly = true)
	public Analytics analytics(String fromText, String toText) {
		requireManage();
		LocalDate today = today();
		List<FieldErrorDetail> errors = new ArrayList<>();
		YearMonth givenFrom = null;
		YearMonth givenTo = null;
		if (fromText != null && !fromText.isBlank()) {
			givenFrom = StaffPayMonths.parse(fromText);
			if (givenFrom == null) {
				errors.add(new FieldErrorDetail("from", "Use the month as YYYY-MM."));
			}
		}
		if (toText != null && !toText.isBlank()) {
			givenTo = StaffPayMonths.parse(toText);
			if (givenTo == null) {
				errors.add(new FieldErrorDetail("to", "Use the month as YYYY-MM."));
			}
		}
		if (!errors.isEmpty()) {
			throw validation(errors);
		}
		YearMonth to = givenTo != null ? givenTo : StaffPayMonths.defaultTo(today);
		YearMonth from = givenFrom != null ? givenFrom : StaffPayMonths.defaultFrom(to);
		if (from.isAfter(to)) {
			errors.add(new FieldErrorDetail("to", "The end month cannot be before the start month."));
		}
		else if (StaffPayMonths.spanMonths(from, to) > StaffPayMonths.MAX_SPAN_MONTHS) {
			errors.add(new FieldErrorDetail("from", "Choose at most " + StaffPayMonths.MAX_SPAN_MONTHS + " months."));
		}
		if (!errors.isEmpty()) {
			throw validation(errors);
		}

		List<String> months = StaffPayMonths.range(from, to).stream().map(StaffPayMonths::key).toList();
		String fromKey = StaffPayMonths.key(from);
		String toKey = StaffPayMonths.key(to);

		Map<String, Map<String, BigDecimal>> byMonthKind = new HashMap<>();
		for (MonthKindTotal row : queries.payrollTotals(fromKey, toKey)) {
			byMonthKind.computeIfAbsent(row.month(), key -> new HashMap<>()).put(row.kind(), row.total());
		}
		List<PayrollMonth> payroll = new ArrayList<>();
		for (String month : months) {
			Map<String, BigDecimal> kinds = byMonthKind.getOrDefault(month, Map.of());
			BigDecimal salary = kinds.getOrDefault("SALARY", BigDecimal.ZERO);
			BigDecimal marking = kinds.getOrDefault("MARKING", BigDecimal.ZERO);
			BigDecimal adjustments = kinds.getOrDefault("ADJUSTMENT", BigDecimal.ZERO);
			payroll.add(new PayrollMonth(month, money(salary), money(marking), money(adjustments),
					money(salary.add(marking).add(adjustments)), money(kinds.getOrDefault("PAYMENT", BigDecimal.ZERO))));
		}

		Map<UUID, InstructorBuild> instructors = new LinkedHashMap<>();
		for (InstructorMonth row : queries.sheets(fromKey, toKey)) {
			instructors.computeIfAbsent(row.userId(), key -> new InstructorBuild(row.fullName())).sheets.put(row.month(), row);
		}
		List<InstructorSheets> sheets = new ArrayList<>();
		for (Map.Entry<UUID, InstructorBuild> entry : instructors.entrySet()) {
			Map<String, InstructorMonth> byMonth = entry.getValue().sheets;
			List<MonthSheets> perMonth = new ArrayList<>();
			long total = 0;
			for (String month : months) {
				InstructorMonth cell = byMonth.get(month);
				long count = cell == null ? 0 : cell.sheets();
				total += count;
				perMonth.add(new MonthSheets(month, count, money(cell == null ? BigDecimal.ZERO : cell.amount())));
			}
			sheets.add(new InstructorSheets(entry.getKey(), entry.getValue().fullName, total, perMonth));
		}
		sheets.sort(Comparator.comparing(InstructorSheets::fullName, Comparator.nullsLast(Comparator.naturalOrder()))
			.thenComparing(InstructorSheets::userId));

		List<PersonRangeRow> people = new ArrayList<>();
		for (PersonRange row : queries.personTotals(fromKey, toKey)) {
			boolean active = row.earned().signum() != 0 || row.paid().signum() != 0 || row.balance().signum() != 0;
			if (active) {
				people.add(new PersonRangeRow(row.userId(), row.fullName(), row.role(), money(row.earned()),
						money(row.paid()), money(row.balance())));
			}
		}
		people.sort(Comparator.comparing(PersonRangeRow::fullName, Comparator.nullsLast(Comparator.naturalOrder()))
			.thenComparing(PersonRangeRow::userId));

		return new Analytics(fromKey, toKey, months, payroll, sheets, people);
	}

	/* ---------- helpers ---------- */

	/** The split of the open dues by kind. With a date, only the dues whose date has passed (the overdue part). */
	private static Split split(List<OpenDue> open, LocalDate overdueBefore) {
		List<OpenDue> counted = overdueBefore == null ? open
				: open.stream().filter(due -> due.dueOn().isBefore(overdueBefore)).toList();
		BigDecimal salaries = BigDecimal.ZERO;
		BigDecimal marking = BigDecimal.ZERO;
		BigDecimal adjustments = BigDecimal.ZERO;
		for (OpenDue due : counted) {
			switch (due.kind()) {
				case SALARY -> salaries = salaries.add(due.uncovered());
				case MARKING -> marking = marking.add(due.uncovered());
				case ADJUSTMENT -> adjustments = adjustments.add(due.uncovered());
				case PAYMENT -> {
					// A payment is never a due, so it is never open.
				}
			}
		}
		return new Split(money(salaries.add(marking).add(adjustments)), money(salaries), money(marking), money(adjustments));
	}

	private static List<Line> toLines(List<StaffLine> rows) {
		return rows.stream()
			.map(row -> new Line(row.id(), LedgerKind.valueOf(row.kind()), row.amount(), row.dueOn(), row.createdAt(), false))
			.toList();
	}

	private static String roleOf(List<StaffLine> rows) {
		return rows.isEmpty() ? null : rows.get(0).role();
	}

	private static BigDecimal money(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP);
	}

	private LocalDate today() {
		return StaffPayMonths.dhakaDate(clock.instant());
	}

	private void requireManage() {
		if (!permissions.has(Permissions.STAFF_PAY_MANAGE)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, DENIED);
		}
	}

	private static ApiException validation(List<FieldErrorDetail> errors) {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.", errors);
	}

	/** The instructor's name and the month cells read so far, while the sheets are grouped. */
	private static final class InstructorBuild {

		private final String fullName;

		private final Map<String, InstructorMonth> sheets = new HashMap<>();

		private InstructorBuild(String fullName) {
			this.fullName = fullName;
		}

	}

	/* ---------- views ---------- */

	/**
	 * The overview. Money is what is owed now (never below zero per person), split by kind; {@code overdue} is the part whose due
	 * date has passed; {@code paidThisMonth} the payments dated in this Dhaka month. {@code owing} lists everyone owed, most
	 * overdue first.
	 */
	public record Overview(String asOf, Split owedNow, Split overdue, BigDecimal paidThisMonth, DueWindow dueNext30Days,
			List<OwingRow> owing) {
	}

	/** An amount split by kind. {@code total} is salaries plus marking plus adjustments. */
	public record Split(BigDecimal total, BigDecimal salaries, BigDecimal marking, BigDecimal adjustments) {
	}

	/** One person who is owed money. {@code nextDueOn} is the earliest due date that is still unpaid. */
	public record OwingRow(UUID userId, String fullName, String role, Split owedNow, Split overdue, String nextDueOn) {
	}

	/** The unpaid salary due dates in [from, to]. */
	public record DueWindow(String from, String to, BigDecimal amount, List<DueItem> items) {
	}

	/** One unpaid salary due in the window. */
	public record DueItem(UUID userId, String fullName, String dueOn, BigDecimal amount) {
	}

	/** The analytics for a range. {@code months} lists every month of the range; each per-month list has one row per month. */
	public record Analytics(String from, String to, List<String> months, List<PayrollMonth> payroll,
			List<InstructorSheets> sheets, List<PersonRangeRow> people) {
	}

	/** Earned or due by kind, and paid, in one month. {@code earned} is salary plus marking plus adjustments. */
	public record PayrollMonth(String month, BigDecimal salary, BigDecimal marking, BigDecimal adjustments, BigDecimal earned,
			BigDecimal paid) {
	}

	/** One instructor's sheets in the range: a count per month, zero where none. */
	public record InstructorSheets(UUID userId, String fullName, long total, List<MonthSheets> byMonth) {
	}

	/** One instructor's published sheets and their value in one month. */
	public record MonthSheets(String month, long sheets, BigDecimal amount) {
	}

	/** One person's earned or due, paid and balance. The balance is at the end of the range. */
	public record PersonRangeRow(UUID userId, String fullName, String role, BigDecimal earned, BigDecimal paid,
			BigDecimal balance) {
	}

}
