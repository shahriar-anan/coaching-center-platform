package com.coachingcenter.api.offline;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

import com.coachingcenter.api.staffnotifications.StaffNotificationKind;
import com.coachingcenter.api.staffnotifications.StaffNotificationWriter;

/**
 * The monthly fee reminders (plan sections 4.3 and 7), copied from {@code InstallmentReminderJob}: every day at 06:30 Dhaka, each
 * admission in its own transaction, and each unpaid due in its window.
 *
 * <ul>
 * <li>Three days before the due date: the student is told {@code OFFLINE_FEE_DUE_SOON}.</li>
 * <li>From the due date until the day before the third day after it: the student is told {@code OFFLINE_FEE_DUE_TODAY}.</li>
 * <li>Three days after the due date, if it is still unpaid: the student is told {@code OFFLINE_FEE_OVERDUE}, and every Master Admin
 * and System Admin is told by staff notification. After that the admins are told again each week (every seven days from the
 * overdue mark) until the due is paid.</li>
 * </ul>
 *
 * <p>
 * A window is read on each run, so a missed run is caught up by the next one that falls in it. Each reminder's key holds the due
 * and the kind (and, for the weekly admin notice, the week), so a repeat run adds nothing. A suspended student gets no notice and no
 * mail (the writers check the status), but the admins are still told (E10). A closed admission keeps its past dues in the reminders
 * until they are paid or written off; its future dues were cancelled at close.
 *
 * <p>
 * The course-ending notice (7 days before the course end, to the Master Admins) is not sent: its kind {@code OFFLINE_COURSE_ENDING}
 * is not in the staff notification enum, and the enum is not changed here.
 */
@Component
public class OfflineFeeReminderJob {

	private static final Logger log = LoggerFactory.getLogger(OfflineFeeReminderJob.class);

	/** The admissions that can owe a reminder. Each is read again inside its own transaction. */
	private static final String ADMISSIONS = """
			select id from offline_admissions where status in ('ONBOARDED', 'CLOSED') and deleted_at is null order by created_at, id
			""";

	static final int BEFORE_DAYS = 3;

	static final int AFTER_DAYS = 3;

	static final int WEEK_DAYS = 7;

	private final JdbcTemplate jdbc;

	private final OfflineFeeLedger ledger;

	private final OfflineFeeNotices notices;

	private final TransactionTemplate transactions;

	private final Clock clock;

	private final AtomicBoolean running = new AtomicBoolean();

	@Value("${app.exam-jobs.scheduled:true}")
	private boolean scheduled = true;

	public OfflineFeeReminderJob(JdbcTemplate jdbc, OfflineFeeLedger ledger, OfflineFeeNotices notices,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.jdbc = jdbc;
		this.ledger = ledger;
		this.notices = notices;
		this.transactions = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/** The scheduled run: 06:30 every day in Dhaka. Does nothing when {@code app.exam-jobs.scheduled} is false. */
	@Scheduled(cron = "0 30 6 * * *", zone = "Asia/Dhaka")
	public void run() {
		if (scheduled) {
			runOn(LocalDate.now(clock.withZone(OfflineFeeRules.DHAKA)));
		}
	}

	/**
	 * One pass for {@code today}, a Dhaka date. Returns how many rows were newly written (student notices, mail and staff
	 * notifications). A pass that starts while another is running does nothing.
	 */
	public int runOn(LocalDate today) {
		if (!running.compareAndSet(false, true)) {
			log.info("offline fee reminder job already running");
			return 0;
		}
		try {
			int written = 0;
			for (UUID admissionId : jdbc.queryForList(ADMISSIONS, UUID.class)) {
				try {
					Integer rows = transactions.execute(status -> remind(admissionId, today));
					written += rows == null ? 0 : rows;
				}
				catch (RuntimeException exception) {
					log.warn("offline fee reminder failed admissionId={} error={}", admissionId,
							exception.getClass().getSimpleName());
				}
			}
			return written;
		}
		catch (RuntimeException exception) {
			log.warn("offline fee reminder job failed error={}", exception.getClass().getSimpleName());
			return 0;
		}
		finally {
			running.set(false);
		}
	}

	/** One admission: each unpaid due, judged against its window. Joins the transaction the caller opened. */
	int remind(UUID admissionId, LocalDate today) {
		OfflineFeeLedger.Admission admission = ledger.lock(admissionId);
		if (!OfflineAdmission.ONBOARDED.equals(admission.status()) && !OfflineAdmission.CLOSED.equals(admission.status())) {
			return 0;
		}
		int written = 0;
		for (OfflineFeeLedger.DueRow due : ledger.dues(admissionId)) {
			if (due.owed().signum() <= 0) {
				continue;
			}
			LocalDate dueOn = due.dueOn();
			if (today.isBefore(dueOn.minusDays(BEFORE_DAYS))) {
				continue;
			}
			if (today.isBefore(dueOn)) {
				written += toStudent(OfflineFeeNotices.DUE_SOON, due.id(), admission.studentId());
			}
			else if (today.isBefore(dueOn.plusDays(AFTER_DAYS))) {
				written += toStudent(OfflineFeeNotices.DUE_TODAY, due.id(), admission.studentId());
			}
			else {
				written += overdue(admissionId, admission.studentId(), due, today);
			}
		}
		return written;
	}

	private int overdue(UUID admissionId, UUID studentId, OfflineFeeLedger.DueRow due, LocalDate today) {
		int written = toStudent(OfflineFeeNotices.OVERDUE, due.id(), studentId);
		long days = ChronoUnit.DAYS.between(due.dueOn(), today);
		// The overdue mark is day three; from then on the admins are told once per week, numbered from zero.
		long week = (days - AFTER_DAYS) / WEEK_DAYS;
		Map<String, Object> payload = Map.of(
				"personName", StaffNotificationWriter.fitText(ledger.personName(admissionId)),
				"amount", due.owed(),
				"dueOn", due.dueOn().toString());
		written += notices.staff(StaffNotificationKind.OFFLINE_FEE_OVERDUE, admins(), payload, "/staff/admissions/" + admissionId,
				"OFFLINE_FEE_OVERDUE:" + due.id() + ":" + week);
		return written;
	}

	/** The student's notice and mail for one due and kind. Both are keyed by the due, so each is written once. */
	private int toStudent(String kind, UUID dueId, UUID studentId) {
		int written = 0;
		if (notices.studentNotice(kind, dueId, studentId)) {
			written++;
		}
		if (notices.studentMail(kind, dueId, studentId)) {
			written++;
		}
		return written;
	}

	private List<UUID> admins() {
		return notices.admins();
	}

}
