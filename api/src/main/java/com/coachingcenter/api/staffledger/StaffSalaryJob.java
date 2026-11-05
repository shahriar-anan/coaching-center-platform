package com.coachingcenter.api.staffledger;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.coachingcenter.api.staffledger.SalaryReminders.Kind;
import com.coachingcenter.api.staffledger.SalaryReminders.Reminder;
import com.coachingcenter.api.staffledger.StaffPayCalculator.Line;
import com.coachingcenter.api.staffledger.StaffPayCalculator.Uncovered;
import com.coachingcenter.api.staffnotifications.StaffNotificationKind;
import com.coachingcenter.api.staffnotifications.StaffNotificationWriter;

/**
 * The System Admin salary job (staff pay plan, phase 5). Every day at 06:00 Dhaka it:
 *
 * <ol>
 * <li>writes one SALARY line for each finished month that a System Admin with salary settings has no line for yet (unique per
 * person and month, so a repeat run adds nothing). The amount is the monthly salary and the due date is the pay day of the
 * following month. A missed or late run catches up on its next pass;</li>
 * <li>tells the Master Admin when a salary line is due soon and not yet covered by a payment, and when it is overdue (the day
 * after the due date, then weekly while it stays unpaid). Each notice has a dedupe key per recipient, person and month, and
 * the overdue one also per week.</li>
 * </ol>
 *
 * <p>
 * It reads the pay tables and writes only SALARY lines and staff notifications. Marking and publishing are not touched. Each
 * person is one transaction, so one failure leaves the others to the next run.
 */
@Component
public class StaffSalaryJob {

	static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

	private static final Logger log = LoggerFactory.getLogger(StaffSalaryJob.class);

	private static final String INSERT_SALARY = """
			insert into staff_ledger (id, user_id, kind, amount, due_on, period, created_at)
			values (:id, :user, 'SALARY', :amount, :due, :period, :now)
			on conflict (user_id, period) do nothing
			""";

	private final NamedParameterJdbcTemplate jdbc;

	private final StaffSalaryService salaries;

	private final StaffNotificationWriter notifications;

	private final TransactionTemplate transactions;

	private final Clock clock;

	private final AtomicBoolean running = new AtomicBoolean();

	@Value("${app.staff-pay.jobs-enabled:true}")
	private boolean enabled = true;

	public StaffSalaryJob(JdbcTemplate jdbc, StaffSalaryService salaries, StaffNotificationWriter notifications,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.jdbc = new NamedParameterJdbcTemplate(jdbc);
		this.salaries = salaries;
		this.notifications = notifications;
		this.transactions = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/** The scheduled run: 06:00 every day in Dhaka. Does nothing when {@code app.staff-pay.jobs-enabled} is false. */
	@Scheduled(cron = "0 0 6 * * *", zone = "Asia/Dhaka")
	public void run() {
		if (enabled) {
			runOn(LocalDate.now(clock.withZone(DHAKA)));
		}
	}

	/**
	 * One pass for {@code today} (a Dhaka date). Returns the number of SALARY lines written. A pass that starts while another is
	 * running does nothing.
	 */
	public int runOn(LocalDate today) {
		if (!running.compareAndSet(false, true)) {
			log.info("staff salary job already running");
			return 0;
		}
		try {
			List<UUID> masters = masterIds();
			int written = 0;
			for (UUID userId : payableUsers()) {
				try {
					Integer count = transactions.execute(status -> process(userId, today, masters));
					written += count == null ? 0 : count;
				}
				catch (RuntimeException exception) {
					log.warn("staff salary job failed userId={} error={}", userId, exception.getClass().getSimpleName());
				}
			}
			if (written > 0) {
				log.info("staff salary job wrote {} salary line(s)", written);
			}
			return written;
		}
		catch (RuntimeException exception) {
			log.warn("staff salary job failed error={}", exception.getClass().getSimpleName());
			return 0;
		}
		finally {
			running.set(false);
		}
	}

	/** One person: the missing salary lines first, then the reminders for whatever is still uncovered. */
	private int process(UUID userId, LocalDate today, List<UUID> masters) {
		int written = writeMissing(userId, today);
		Map<UUID, String> periods = salaries.salaryPeriods(userId);
		int dueSoonDays = dueSoonDays();
		for (Uncovered item : StaffPayCalculator.uncovered(salaries.lines(userId))) {
			Line line = item.line();
			String period = periods.get(line.id());
			if (period == null) {
				continue;
			}
			Reminder reminder = SalaryReminders.reminderFor(line.dueOn(), item.amount().signum() > 0, today, dueSoonDays);
			if (reminder.kind() != Kind.NONE) {
				remind(userId, period, line.dueOn(), item.amount(), reminder, masters);
			}
		}
		return written;
	}

	/** Writes a SALARY line for each month that is finished and has none. Only a live System Admin with salary settings. */
	private int writeMissing(UUID userId, LocalDate today) {
		StaffSalaryService.Terms terms = salaries.terms(userId);
		if (terms == null || !terms.payable() || terms.monthlySalary() == null || terms.payDay() == null
				|| terms.salaryStart() == null) {
			return 0;
		}
		int written = 0;
		for (YearMonth period : SalarySchedule.periodsToWrite(terms.salaryStart(), terms.salaryEnd(), today,
				salaries.writtenPeriods(userId))) {
			written += jdbc.update(INSERT_SALARY, new MapSqlParameterSource().addValue("id", UUID.randomUUID())
				.addValue("user", userId)
				.addValue("amount", terms.monthlySalary())
				.addValue("due", SalarySchedule.dueOn(period, terms.payDay()))
				.addValue("period", period.toString())
				.addValue("now", Timestamp.from(clock.instant())));
		}
		return written;
	}

	/** Tells each live Master Admin of one reminder. The dedupe key holds the recipient, so a repeat run adds nothing. */
	private void remind(UUID userId, String period, LocalDate dueOn, BigDecimal amount, Reminder reminder, List<UUID> masters) {
		String name = StaffNotificationWriter.fitText(personName(userId));
		String link = "/staff/pay/" + userId;
		for (UUID master : masters) {
			StaffNotificationKind kind;
			String key;
			if (reminder.kind() == Kind.DUE_SOON) {
				kind = StaffNotificationKind.SALARY_DUE_SOON;
				key = "SALARY_DUE_SOON:" + master + ":" + userId + ":" + period;
			}
			else {
				kind = StaffNotificationKind.SALARY_OVERDUE;
				key = "SALARY_OVERDUE:" + master + ":" + userId + ":" + period + ":" + SalaryReminders.isoWeek(reminder.weekStart());
			}
			notifications.notify(master, kind,
					Map.of("personName", name, "period", period, "amount", amount, "dueOn", dueOn.toString()), link, key);
		}
	}

	private int dueSoonDays() {
		Integer days = jdbc.queryForObject("select salary_due_soon_days from staff_pay_defaults where id = 1",
				new MapSqlParameterSource(), Integer.class);
		return days == null ? 0 : days;
	}

	private String personName(UUID userId) {
		return jdbc.query("select coalesce(full_name, '') as full_name from admin_profiles where user_id = :user",
				new MapSqlParameterSource("user", userId), (rs, number) -> rs.getString("full_name")).stream()
			.findFirst()
			.orElse("");
	}

	/** The live Master Admins. The notification writer skips any that is not active. */
	private List<UUID> masterIds() {
		return jdbc.query("select id from users where role = 'MASTER_ADMIN' and deleted_at is null",
				new MapSqlParameterSource(), (rs, number) -> rs.getObject("id", UUID.class));
	}

	/**
	 * Everyone a pass looks at: a live System Admin with salary settings (to write lines for), and anyone who already has a
	 * SALARY line (so a reminder still goes out for a line written before the person was removed).
	 */
	private List<UUID> payableUsers() {
		return jdbc.query("""
				select u.id as id from users u
				where u.role = 'SYSTEM_ADMIN' and u.deleted_at is null
				  and exists (select 1 from staff_pay_settings s where s.user_id = u.id and s.monthly_salary is not null)
				union
				select l.user_id as id from staff_ledger l where l.kind = 'SALARY'
				""", new MapSqlParameterSource(), (rs, number) -> rs.getObject("id", UUID.class));
	}

}
