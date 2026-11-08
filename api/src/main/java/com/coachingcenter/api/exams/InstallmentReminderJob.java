package com.coachingcenter.api.exams;

import java.time.Clock;
import java.time.LocalDate;
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

import com.coachingcenter.api.staffledger.SalaryReminders;
import com.coachingcenter.api.staffnotifications.StaffNotificationKind;

/**
 * The reminder job for direct-enrolment payment plans (plan section 6.2). Every day at 06:30 Dhaka it looks at each plan that
 * can still owe money, and at the oldest installment that is not fully paid (C8). When that installment is due within
 * {@code app.installments.due-soon-days} days, the student and the Master Admin are told once. From the day after it falls due,
 * they are told once a week until it is paid. The timing is {@link SalaryReminders}, the same as staff salaries.
 *
 * <p>
 * A plan whose grant is revoked, or with no live payment, gets nothing. A suspended student gets no notice and no email, but
 * the Master Admin is still told (E10). Every reminder's key holds the recipient, so a repeat run adds nothing, and a missed run
 * is caught up by the next one. Each plan is one transaction, so one failure leaves the others to the next run.
 */
@Component
class InstallmentReminderJob {

	private static final Logger log = LoggerFactory.getLogger(InstallmentReminderJob.class);

	/** The plans that can owe a reminder. Each one is read again inside its own transaction. */
	private static final String PLANS = """
			select p.id from live_exam_payment_plans p
			join live_exam_grants g on g.id = p.grant_id
			where g.revoked_at is null
			  and exists (select 1 from live_exam_direct_payments d where d.plan_id = p.id and d.voided_at is null)
			order by p.created_at, p.id
			""";

	private final JdbcTemplate jdbc;

	private final InstallmentPlanReader reader;

	private final StudentNoticeWriter notices;

	private final ExamMailQueue mail;

	private final InstallmentStaffNotices staff;

	private final TransactionTemplate transactions;

	private final Clock clock;

	private final AtomicBoolean running = new AtomicBoolean();

	@Value("${app.exam-jobs.scheduled:true}")
	private boolean scheduled = true;

	/** How many days before a due date the due-soon reminder starts (plan section 6.2). */
	@Value("${app.installments.due-soon-days:3}")
	private int dueSoonDays = 3;

	InstallmentReminderJob(JdbcTemplate jdbc, InstallmentPlanReader reader, StudentNoticeWriter notices, ExamMailQueue mail,
			InstallmentStaffNotices staff, PlatformTransactionManager transactionManager, Clock clock) {
		this.jdbc = jdbc;
		this.reader = reader;
		this.notices = notices;
		this.mail = mail;
		this.staff = staff;
		this.transactions = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/** The scheduled run: 06:30 every day in Dhaka. Does nothing when {@code app.exam-jobs.scheduled} is false. */
	@Scheduled(cron = "0 30 6 * * *", zone = "Asia/Dhaka")
	public void run() {
		if (scheduled) {
			runOn(LocalDate.now(clock.withZone(LiveExamReceipts.DHAKA)));
		}
	}

	/**
	 * One pass for {@code today}, a Dhaka date. Returns how many plans had a reminder due on this pass (a repeat adds no rows, so
	 * the count can include plans already told). A pass that starts while another is running does nothing.
	 */
	int runOn(LocalDate today) {
		if (!running.compareAndSet(false, true)) {
			log.info("installment reminder job already running");
			return 0;
		}
		try {
			int due = 0;
			for (UUID planId : jdbc.queryForList(PLANS, UUID.class)) {
				try {
					Boolean reminded = transactions.execute(status -> remind(planId, today));
					if (Boolean.TRUE.equals(reminded)) {
						due++;
					}
				}
				catch (RuntimeException exception) {
					log.warn("installment reminder failed planId={} error={}", planId, exception.getClass().getSimpleName());
				}
			}
			if (due > 0) {
				log.info("installment reminder job had {} plan(s) due a reminder", due);
			}
			return due;
		}
		catch (RuntimeException exception) {
			log.warn("installment reminder job failed error={}", exception.getClass().getSimpleName());
			return 0;
		}
		finally {
			running.set(false);
		}
	}

	/** One plan: the oldest installment not fully paid, and the reminder its date calls for. Returns true when one is due. */
	private boolean remind(UUID planId, LocalDate today) {
		// The same row lock the payment writes take, so a payment recorded now is seen by this read, and no reminder follows it.
		jdbc.queryForList("select id from live_exam_payment_plans where id = ? for update", UUID.class, planId);
		InstallmentPlanReader.PlanDetail detail = reader.detail(planId, null).orElse(null);
		if (detail == null || detail.plan().accessRevoked()
				|| detail.payments().stream().noneMatch(payment -> payment.voidedAt() == null)) {
			return false;
		}
		InstallmentCalculator.RowState owed = detail.schedule()
			.stream()
			.filter(row -> row.uncovered().signum() > 0)
			.findFirst()
			.orElse(null);
		if (owed == null) {
			return false;
		}
		UUID installmentId = reader.installmentId(planId, owed.seq()).orElse(null);
		if (installmentId == null) {
			return false;
		}
		SalaryReminders.Reminder reminder = SalaryReminders.reminderFor(owed.dueOn(), true, today, dueSoonDays);
		if (reminder.kind() == SalaryReminders.Kind.NONE) {
			return false;
		}
		boolean overdue = reminder.kind() == SalaryReminders.Kind.OVERDUE;
		String week = overdue ? SalaryReminders.isoWeek(reminder.weekStart()) : null;
		String kind = overdue ? ExamOutboundMail.INSTALLMENT_OVERDUE : ExamOutboundMail.INSTALLMENT_DUE_SOON;
		UUID studentId = detail.plan().studentId();
		// The student, in-app and by email. The writers add nothing for a suspended student or an unverified address.
		if (overdue) {
			notices.installmentOverdue(studentId, installmentId, owed.dueOn(), week);
		}
		else {
			notices.installmentDueSoon(studentId, installmentId, owed.dueOn());
		}
		mail.enqueueInstallmentReminderForStudent(kind, studentId, installmentId, owed.dueOn(), week);
		// The Master Admin is told whatever the student's status is (E10).
		staff.reminder(overdue ? StaffNotificationKind.INSTALLMENT_OVERDUE : StaffNotificationKind.INSTALLMENT_DUE_SOON,
				detail.plan(), installmentId, owed.dueOn(), week, owed.uncovered());
		mail.enqueueInstallmentReminderForMaster(kind, installmentId, owed.dueOn(), week);
		return true;
	}

}
