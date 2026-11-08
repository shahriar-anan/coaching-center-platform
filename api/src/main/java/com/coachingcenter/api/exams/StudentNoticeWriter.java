package com.coachingcenter.api.exams;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the student notices. Each row is inserted once: the unique key makes a repeat a silent no-op instead of an error
 * that would abort the caller's transaction. A row stores no text and no marks; the inbox reads those at view time.
 */
@Service
class StudentNoticeWriter {

	static final String EXAM_REMINDER = "EXAM_REMINDER";

	static final String EXAM_STARTING_SOON = "EXAM_STARTING_SOON";

	static final String EXAM_OPEN = "EXAM_OPEN";

	static final String PURCHASE_GRANTED = "PURCHASE_GRANTED";

	static final String EXAM_PUBLISHED = "EXAM_PUBLISHED";

	static final String MCQ_SCORE = "MCQ_SCORE";

	static final String WRITTEN_RESULT = "WRITTEN_RESULT";

	/** An exam's live time, end time or duration changed on publish. In-app only. */
	static final String EXAM_TIME_CHANGED = "EXAM_TIME_CHANGED";

	/** The centre added the student to an exam or package by hand. It is not a purchase, so it offers no payment. */
	static final String ACCESS_GRANTED = "ACCESS_GRANTED";

	/** A written retake was granted on the student's result (NR-01, R14). */
	static final String RETAKE_AVAILABLE = "RETAKE_AVAILABLE";

	/** The centre declined a student's retake request. The note is shown on the exam's result page. */
	static final String RETAKE_DECLINED = "RETAKE_DECLINED";

	/** A payment on a direct enrolment was recorded, the first one included. Keyed by the payment (plan section 7). */
	static final String INSTALLMENT_PAYMENT_RECEIVED = "INSTALLMENT_PAYMENT_RECEIVED";

	/** An unpaid installment falls due within the reminder window. Keyed by the student, the installment and its date. */
	static final String INSTALLMENT_DUE_SOON = "INSTALLMENT_DUE_SOON";

	/** An installment is past its date and not paid. Keyed also by the ISO week of the reminder (plan section 6.2). */
	static final String INSTALLMENT_OVERDUE = "INSTALLMENT_OVERDUE";

	/** A payment was voided. The notice links to the voided payment, so its receipt can be shown as void. */
	static final String INSTALLMENT_PAYMENT_VOIDED = "INSTALLMENT_PAYMENT_VOIDED";

	/** The unpaid schedule was changed. The notice links to the plan through its latest payment. */
	static final String INSTALLMENT_SCHEDULE_CHANGED = "INSTALLMENT_SCHEDULE_CHANGED";

	/**
	 * A money notice. It names the installment or the payment, never an exam or package, so a later change to those cannot hide
	 * it (plan section 7). The student check is in the insert, so a suspended student gets no row.
	 */
	private static final String INSERT_INSTALLMENT = """
			insert into student_notices (id, user_id, kind, message_key, exam_id, module_id, participant_id, installment_id,
			  direct_payment_id, created_at)
			select ?, u.id, ?, ?, null, null, null, ?, ?, ?
			from users u
			where u.id = ? and u.role = 'STUDENT' and u.status = 'ACTIVE' and u.deleted_at is null
			on conflict (kind, message_key) do nothing
			""";

	/** A schedule change links to the plan's latest payment. Payments are never deleted, so the link always holds. */
	private static final String INSERT_SCHEDULE_CHANGED = """
			insert into student_notices (id, user_id, kind, message_key, exam_id, module_id, participant_id, installment_id,
			  direct_payment_id, created_at)
			select ?, u.id, ?, ?, null, null, null, null,
			  (select d.id from live_exam_direct_payments d where d.plan_id = ? order by d.created_at desc, d.receipt_no desc
			    limit 1), ?
			from users u
			where u.id = ? and u.role = 'STUDENT' and u.status = 'ACTIVE' and u.deleted_at is null
			on conflict (kind, message_key) do nothing
			""";

	private static final String INSERT = """
			insert into student_notices (id, user_id, kind, message_key, exam_id, participant_id, created_at)
			select ?, u.id, ?, ?, ?, ?, ?
			from users u
			where u.id = ? and u.role = 'STUDENT' and u.status = 'ACTIVE' and u.deleted_at is null
			on conflict (kind, message_key) do nothing
			""";

	private static final String INSERT_MODULE = """
			insert into student_notices (id, user_id, kind, message_key, exam_id, module_id, participant_id, created_at)
			select ?, u.id, ?, ?, null, ?, null, ?
			from users u
			where u.id = ? and u.role = 'STUDENT' and u.status = 'ACTIVE' and u.deleted_at is null
			on conflict (kind, message_key) do nothing
			""";

	private final JdbcTemplate jdbc;

	private final Clock clock;

	StudentNoticeWriter(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** Joins the caller's transaction. Called when an MCQ score posts. */
	void mcqScore(UUID studentId, UUID examId, UUID participantId) {
		insert(MCQ_SCORE, participantId.toString(), studentId, examId, participantId);
	}

	/** Joins the caller's transaction. Called when the Master Admin publishes a written result. */
	void writtenResult(UUID studentId, UUID examId, UUID participantId, Instant publishedAt) {
		insert(WRITTEN_RESULT, participantId + ":" + publishedAt.toEpochMilli(), studentId, examId, participantId);
	}

	/** One reminder per student, exam, and start time. Returns how many rows were new. */
	@Transactional
	int remind(UUID examId, Instant liveAt, Collection<UUID> studentIds) {
		return startKeyed(EXAM_REMINDER, examId, liveAt, studentIds);
	}

	/** One in-app notice per student, exam, and start time, sent within the hour before it. Returns how many rows were new. */
	@Transactional
	int startingSoon(UUID examId, Instant liveAt, Collection<UUID> studentIds) {
		return startKeyed(EXAM_STARTING_SOON, examId, liveAt, studentIds);
	}

	/** One notice per student, exam, and start time, once the exam's window opens. Returns how many rows were new. */
	@Transactional
	int opened(UUID examId, Instant liveAt, Collection<UUID> studentIds) {
		return startKeyed(EXAM_OPEN, examId, liveAt, studentIds);
	}

	/**
	 * Joins the caller's transaction. Called when an exam is published: one notice per student with access, once per exam,
	 * so publishing it again adds nothing.
	 */
	void published(UUID examId, Collection<UUID> studentIds) {
		for (UUID studentId : studentIds) {
			insert(EXAM_PUBLISHED, studentId + ":" + examId, studentId, examId, null);
		}
	}

	/** Joins the caller's transaction. Called when a publish changes an exam's live time, end time or duration. */
	void examTimeChanged(UUID examId, Collection<UUID> studentIds) {
		long publishedAt = clock.instant().toEpochMilli();
		for (UUID studentId : studentIds) {
			insert(EXAM_TIME_CHANGED, studentId + ":" + examId + ":" + publishedAt, studentId, examId, null);
		}
	}

	/** Joins the caller's transaction. Called when a written retake is granted on a student's result (R14). */
	void retakeAvailable(UUID examId, UUID studentId) {
		insert(RETAKE_AVAILABLE, studentId + ":" + examId + ":" + clock.instant().toEpochMilli(), studentId, examId, null);
	}

	/** Joins the caller's transaction. Called when a retake request is declined. */
	void retakeDeclined(UUID examId, UUID studentId) {
		insert(RETAKE_DECLINED, studentId + ":" + examId + ":" + clock.instant().toEpochMilli(), studentId, examId, null);
	}

	/**
	 * Joins the caller's transaction. Called once a grant is created, by a paid order or by a manual grant (API-ADMIN calls
	 * this with the same three values). Keyed by student, target and scope, so a grant repeated for the same target adds
	 * nothing. An exam grant names its exam; a module grant names its module (V40 allows a module-level notice).
	 */
	void purchaseGranted(UUID studentId, UUID moduleOrExamId, String scope) {
		if (LiveExamOrder.EXAM.equals(scope)) {
			insert(PURCHASE_GRANTED, studentId + ":" + scope + ":" + moduleOrExamId, studentId, moduleOrExamId, null);
		} else if (LiveExamOrder.MODULE.equals(scope)) {
			insertForModule(PURCHASE_GRANTED, studentId + ":" + scope + ":" + moduleOrExamId, studentId, moduleOrExamId);
		}
	}

	/** Joins the caller's transaction. Called when the centre grants access by hand (a roster or a manual grant). */
	void accessGranted(UUID studentId, UUID moduleOrExamId, String scope) {
		if (LiveExamOrder.EXAM.equals(scope)) {
			insert(ACCESS_GRANTED, studentId + ":" + scope + ":" + moduleOrExamId, studentId, moduleOrExamId, null);
		} else if (LiveExamOrder.MODULE.equals(scope)) {
			insertForModule(ACCESS_GRANTED, studentId + ":" + scope + ":" + moduleOrExamId, studentId, moduleOrExamId);
		}
	}

	/** Joins the caller's transaction. Called when a payment is recorded, the first one of a plan included (plan section 7). */
	void installmentPaymentReceived(UUID studentId, UUID paymentId) {
		insertInstallment(INSTALLMENT_PAYMENT_RECEIVED, paymentId.toString(), studentId, null, paymentId);
	}

	/** Joins the caller's transaction. Called when the latest payment is voided (C6). */
	void installmentPaymentVoided(UUID studentId, UUID paymentId) {
		insertInstallment(INSTALLMENT_PAYMENT_VOIDED, paymentId.toString(), studentId, null, paymentId);
	}

	/** Joins the caller's transaction. Called when the unpaid rows of a plan are replaced (C6). */
	void installmentScheduleChanged(UUID studentId, UUID planId, Instant changedAt) {
		jdbc.update(INSERT_SCHEDULE_CHANGED, UUID.randomUUID(), INSTALLMENT_SCHEDULE_CHANGED,
				studentId + ":" + planId + ":" + changedAt.toEpochMilli(), planId, Timestamp.from(clock.instant()), studentId);
	}

	/** One reminder before an unpaid installment falls due. Returns true when the row was new. */
	boolean installmentDueSoon(UUID studentId, UUID installmentId, LocalDate dueOn) {
		return insertInstallment(INSTALLMENT_DUE_SOON, studentId + ":" + installmentId + ":" + dueOn, studentId, installmentId,
				null);
	}

	/** One weekly reminder once an unpaid installment is past its date. Returns true when the row was new. */
	boolean installmentOverdue(UUID studentId, UUID installmentId, LocalDate dueOn, String week) {
		return insertInstallment(INSTALLMENT_OVERDUE, studentId + ":" + installmentId + ":" + dueOn + ":" + week, studentId,
				installmentId, null);
	}

	private boolean insertInstallment(String kind, String key, UUID studentId, UUID installmentId, UUID paymentId) {
		return jdbc.update(INSERT_INSTALLMENT, UUID.randomUUID(), kind, key, installmentId, paymentId,
				Timestamp.from(clock.instant()), studentId) == 1;
	}

	private int startKeyed(String kind, UUID examId, Instant liveAt, Collection<UUID> studentIds) {
		int written = 0;
		for (UUID studentId : studentIds) {
			if (insert(kind, studentId + ":" + examId + ":" + liveAt.toEpochMilli(), studentId, examId, null)) {
				written++;
			}
		}
		return written;
	}

	private boolean insert(String kind, String key, UUID studentId, UUID examId, UUID participantId) {
		return jdbc.update(INSERT, UUID.randomUUID(), kind, key, examId, participantId,
				Timestamp.from(clock.instant()), studentId) == 1;
	}

	/** A module-level notice: no exam, the module named. Same student check and key rule as the exam insert. */
	private boolean insertForModule(String kind, String key, UUID studentId, UUID moduleId) {
		return jdbc.update(INSERT_MODULE, UUID.randomUUID(), kind, key, moduleId, Timestamp.from(clock.instant()), studentId) == 1;
	}

}
