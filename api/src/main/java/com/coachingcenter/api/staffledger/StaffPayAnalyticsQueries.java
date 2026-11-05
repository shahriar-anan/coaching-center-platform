package com.coachingcenter.api.staffledger;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The SQL behind the Master Admin's overview and analytics (staff pay plan, phase 6), in one place so it is easy to review.
 *
 * <p>
 * These queries read {@code staff_ledger} generically, by the {@code kind} column and the stored dates. They do not depend on
 * the classes that write MARKING or SALARY lines, so phases 4 and 5 can ship in any order. Rules applied everywhere:
 * <ul>
 * <li>voided lines ({@code voided_at} set) are left out;</li>
 * <li>only System Admins and instructors count (the Master Admin is not paid through the ledger);</li>
 * <li>a line's month is its SALARY {@code period}, its PAYMENT {@code paid_on} month, and otherwise its {@code due_on} month.
 * Both date columns already hold Dhaka dates, so the months need no time-zone conversion;</li>
 * <li>months are text {@code YYYY-MM}, which compare correctly as text.</li>
 * </ul>
 */
@Repository
public class StaffPayAnalyticsQueries {

	/** The live lines of every System Admin and instructor, with the person's name. */
	private static final String STAFF_LINES = """
			select u.id as user_id, u.role, coalesce(p.full_name, '') as full_name, l.id, l.kind, l.amount, l.due_on,
				l.period, l.paid_on, l.created_at
			from staff_ledger l
			join users u on u.id = l.user_id
			left join admin_profiles p on p.user_id = u.id
			where u.role in ('SYSTEM_ADMIN', 'INSTRUCTOR') and l.voided_at is null
			""";

	/** The same lines, each with its month. A MARKING line's month is its publish date's month (its due_on). */
	private static final String BUCKETED = """
			with lines as (%s),
			bucketed as (
				select lines.*,
					case
						when kind = 'SALARY' then period
						when kind = 'PAYMENT' then to_char(paid_on, 'YYYY-MM')
						else to_char(due_on, 'YYYY-MM')
					end as bucket_month
				from lines
			)
			""".formatted(STAFF_LINES);

	private final NamedParameterJdbcTemplate jdbc;

	public StaffPayAnalyticsQueries(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Every live line of every System Admin and instructor, oldest due first. Used by the overview's open-dues rule. */
	public List<StaffLine> staffLines() {
		return jdbc.query(STAFF_LINES + " order by l.due_on, l.created_at, l.id", new MapSqlParameterSource(),
				(rs, number) -> staffLine(rs));
	}

	/** The sum of live payments dated in {@code [from, to)}, the Dhaka month window for "paid this month". */
	public BigDecimal paidBetween(LocalDate from, LocalDate to) {
		BigDecimal total = jdbc.queryForObject("""
				select coalesce(sum(l.amount), 0)
				from staff_ledger l
				join users u on u.id = l.user_id
				where u.role in ('SYSTEM_ADMIN', 'INSTRUCTOR') and l.voided_at is null and l.kind = 'PAYMENT'
					and l.paid_on >= :from and l.paid_on < :to
				""", new MapSqlParameterSource().addValue("from", java.sql.Date.valueOf(from))
			.addValue("to", java.sql.Date.valueOf(to)), BigDecimal.class);
		return total == null ? BigDecimal.ZERO : total;
	}

	/** Totals by month and kind, for the months {@code from} to {@code to} (both YYYY-MM, both included). */
	public List<MonthKindTotal> payrollTotals(String from, String to) {
		return jdbc.query(BUCKETED + """
				select bucket_month, kind, coalesce(sum(amount), 0) as total
				from bucketed
				where bucket_month between :from and :to
				group by bucket_month, kind
				""", new MapSqlParameterSource("from", from).addValue("to", to),
				(rs, number) -> new MonthKindTotal(rs.getString("bucket_month"), rs.getString("kind"),
						rs.getBigDecimal("total")));
	}

	/** Sheets published (MARKING lines) per instructor per month, for the months {@code from} to {@code to}. */
	public List<InstructorMonth> sheets(String from, String to) {
		return jdbc.query(BUCKETED + """
				select user_id, full_name, bucket_month, count(*) as sheets, coalesce(sum(amount), 0) as amount
				from bucketed
				where kind = 'MARKING' and bucket_month between :from and :to
				group by user_id, full_name, bucket_month
				order by full_name, user_id, bucket_month
				""", new MapSqlParameterSource("from", from).addValue("to", to),
				(rs, number) -> new InstructorMonth(rs.getObject("user_id", UUID.class), rs.getString("full_name"),
						rs.getString("bucket_month"), rs.getLong("sheets"), rs.getBigDecimal("amount")));
	}

	/**
	 * Each person's earned or due, paid, and balance for the range. Earned and paid are summed over the months {@code from} to
	 * {@code to}. The balance is at the end of {@code to}: everything charged and paid up to and including that month.
	 */
	public List<PersonRange> personTotals(String from, String to) {
		return jdbc.query(BUCKETED + """
				select user_id, full_name, role,
					coalesce(sum(amount) filter (
						where kind in ('SALARY', 'MARKING', 'ADJUSTMENT') and bucket_month between :from and :to), 0) as earned,
					coalesce(sum(amount) filter (
						where kind = 'PAYMENT' and bucket_month between :from and :to), 0) as paid,
					coalesce(sum(case when kind = 'PAYMENT' then -amount else amount end) filter (
						where bucket_month <= :to), 0) as balance
				from bucketed
				group by user_id, full_name, role
				""", new MapSqlParameterSource("from", from).addValue("to", to),
				(rs, number) -> new PersonRange(rs.getObject("user_id", UUID.class), rs.getString("full_name"),
						rs.getString("role"), rs.getBigDecimal("earned"), rs.getBigDecimal("paid"),
						rs.getBigDecimal("balance")));
	}

	private static StaffLine staffLine(ResultSet rs) throws SQLException {
		return new StaffLine(rs.getObject("user_id", UUID.class), rs.getString("role"), rs.getString("full_name"),
				rs.getObject("id", UUID.class), rs.getString("kind"), rs.getBigDecimal("amount"),
				rs.getObject("due_on", LocalDate.class), rs.getTimestamp("created_at").toInstant());
	}

	/** One live line of a staff member, as the overview reads it. */
	public record StaffLine(UUID userId, String role, String fullName, UUID id, String kind, BigDecimal amount,
			LocalDate dueOn, Instant createdAt) {
	}

	/** A month's total for one kind. {@code kind} is one of MARKING, SALARY, ADJUSTMENT or PAYMENT. */
	public record MonthKindTotal(String month, String kind, BigDecimal total) {
	}

	/** One instructor's published sheets in one month. */
	public record InstructorMonth(UUID userId, String fullName, String month, long sheets, BigDecimal amount) {
	}

	/** One person's earned, paid and balance over a range. */
	public record PersonRange(UUID userId, String fullName, String role, BigDecimal earned, BigDecimal paid,
			BigDecimal balance) {
	}

}
