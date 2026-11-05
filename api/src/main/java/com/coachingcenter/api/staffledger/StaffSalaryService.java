package com.coachingcenter.api.staffledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.coachingcenter.api.staffledger.StaffPayCalculator.Line;
import com.coachingcenter.api.staffledger.StaffPayCalculator.Uncovered;

/**
 * The salary view of one person's ledger (staff pay plan, phase 5): the settings, the next pay date and the amount due on it,
 * and one row per salary month with its status. Reads only; the salary job writes the SALARY lines. Money is {@link BigDecimal}.
 */
@Service
public class StaffSalaryService {

	static final String PAID = "PAID";

	static final String DUE = "DUE";

	static final String OVERDUE = "OVERDUE";

	private final NamedParameterJdbcTemplate jdbc;

	public StaffSalaryService(JdbcTemplate jdbc) {
		this.jdbc = new NamedParameterJdbcTemplate(jdbc);
	}

	/**
	 * The salary terms as set by the Master Admin, with whether the person is paid through this at all (a live System Admin).
	 * Null when no pay settings exist.
	 */
	record Terms(BigDecimal monthlySalary, Integer payDay, LocalDate salaryStart, LocalDate salaryEnd, boolean payable) {
	}

	/**
	 * The salary view for one person. {@code nextPayDate} is the next due date on or after today; {@code nextPayAmount} is what
	 * is still uncovered on that date's line, or the monthly salary when that month has no line yet.
	 */
	public record SalaryView(BigDecimal monthlySalary, Integer payDay, String salaryStart, String salaryEnd, String nextPayDate,
			BigDecimal nextPayAmount, List<SalaryMonth> months) {
	}

	/** One salary month: {@code paid} is the part of the amount already covered; {@code status} is PAID, DUE or OVERDUE. */
	public record SalaryMonth(String period, String dueOn, BigDecimal amount, BigDecimal paid, BigDecimal uncovered,
			String status) {
	}

	/** The salary view for {@code userId} as of {@code today} (Dhaka). Months run newest first. */
	public SalaryView view(UUID userId, LocalDate today) {
		Terms terms = terms(userId);
		Map<UUID, String> periods = salaryPeriods(userId);
		List<SalaryMonth> months = new ArrayList<>();
		Map<LocalDate, BigDecimal> uncoveredByDue = new HashMap<>();
		for (Uncovered item : StaffPayCalculator.uncovered(lines(userId))) {
			Line line = item.line();
			String period = periods.get(line.id());
			if (period == null) {
				continue;
			}
			BigDecimal uncovered = item.amount();
			uncoveredByDue.merge(line.dueOn(), uncovered, BigDecimal::add);
			String status = uncovered.signum() == 0 ? PAID : line.dueOn().isBefore(today) ? OVERDUE : DUE;
			months.add(new SalaryMonth(period, line.dueOn().toString(), scale(line.amount()),
					scale(line.amount().subtract(uncovered)), scale(uncovered), status));
		}
		months.sort(Comparator.comparing(SalaryMonth::period).reversed());

		String nextDate = null;
		BigDecimal nextAmount = null;
		if (terms != null && terms.monthlySalary() != null) {
			LocalDate next = SalarySchedule.nextDue(terms.salaryStart(), terms.salaryEnd(), terms.payDay(), today);
			if (next != null) {
				nextDate = next.toString();
				BigDecimal onLine = uncoveredByDue.get(next);
				nextAmount = onLine == null ? scale(terms.monthlySalary()) : scale(onLine);
			}
		}
		return new SalaryView(terms == null ? null : scale(terms.monthlySalary()), terms == null ? null : terms.payDay(),
				terms == null || terms.salaryStart() == null ? null : terms.salaryStart().toString(),
				terms == null || terms.salaryEnd() == null ? null : terms.salaryEnd().toString(), nextDate, nextAmount,
				months);
	}

	/** The terms, or null when the person has no pay settings row. */
	Terms terms(UUID userId) {
		return jdbc.query("""
				select s.monthly_salary, s.pay_day, s.salary_start, s.salary_end,
					(u.role = 'SYSTEM_ADMIN' and u.deleted_at is null) as payable
				from staff_pay_settings s join users u on u.id = s.user_id
				where s.user_id = :user
				""", new MapSqlParameterSource("user", userId), (rs, number) -> new Terms(rs.getBigDecimal("monthly_salary"),
						rs.getObject("pay_day", Integer.class), rs.getObject("salary_start", LocalDate.class),
						rs.getObject("salary_end", LocalDate.class), rs.getBoolean("payable")))
			.stream()
			.findFirst()
			.orElse(null);
	}

	/** Every ledger line of the person, voided ones included (the calculator leaves them out). */
	List<Line> lines(UUID userId) {
		return jdbc.query("""
				select id, kind, amount, due_on, created_at, voided_at from staff_ledger where user_id = :user
				""", new MapSqlParameterSource("user", userId), (rs, number) -> new Line(rs.getObject("id", UUID.class),
					LedgerKind.valueOf(rs.getString("kind")), rs.getBigDecimal("amount"),
					rs.getObject("due_on", LocalDate.class), rs.getTimestamp("created_at").toInstant(),
					rs.getTimestamp("voided_at") != null));
	}

	/** The months already written as SALARY lines. */
	Set<YearMonth> writtenPeriods(UUID userId) {
		return new HashSet<>(salaryPeriods(userId).values().stream().map(text -> YearMonth.parse(text)).toList());
	}

	/** Line id to period (YYYY-MM), for the SALARY lines only. */
	Map<UUID, String> salaryPeriods(UUID userId) {
		Map<UUID, String> periods = new HashMap<>();
		jdbc.query("""
				select id, period from staff_ledger where user_id = :user and kind = 'SALARY'
				""", new MapSqlParameterSource("user", userId), (rs, number) -> {
					periods.put(rs.getObject("id", UUID.class), rs.getString("period"));
					return null;
				});
		return periods;
	}

	/** Money to two places, as the ledger shows it. */
	static BigDecimal scale(BigDecimal value) {
		return StaffPayCalculator.scale(value);
	}

}
