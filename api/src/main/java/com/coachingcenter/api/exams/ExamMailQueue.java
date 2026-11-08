package com.coachingcenter.api.exams;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.users.User;
import com.coachingcenter.api.users.UserRepository;

@Service
class ExamMailQueue {

	private final ExamOutboundMailRepository mail;

	private final LiveExamOrderRepository orders;

	private final LiveExamGrantRepository grants;

	private final LiveExamMemberships memberships;

	private final LiveExamRepository exams;

	private final LiveExamModuleRepository modules;

	private final UserRepository users;

	private final Clock clock;

	private final org.springframework.jdbc.core.JdbcTemplate jdbc;

	private final LiveExamOrderBundle bundles;

	private final LiveExamCatalogVisibility visibility;

	ExamMailQueue(ExamOutboundMailRepository mail, LiveExamOrderRepository orders, LiveExamGrantRepository grants,
			LiveExamRepository exams, LiveExamModuleRepository modules, UserRepository users, Clock clock,
			org.springframework.jdbc.core.JdbcTemplate jdbc, LiveExamOrderBundle bundles, LiveExamMemberships memberships,
			LiveExamCatalogVisibility visibility) {
		this.visibility = visibility;
		this.memberships = memberships;
		this.mail = mail;
		this.orders = orders;
		this.grants = grants;
		this.exams = exams;
		this.modules = modules;
		this.users = users;
		this.clock = clock;
		this.jdbc = jdbc;
		this.bundles = bundles;
	}

	@Transactional
	public List<UUID> enqueueForSucceededOrder(UUID orderId) {
		LiveExamOrder order = orders.findById(orderId).orElse(null);
		if (order == null || !LiveExamOrder.SUCCEEDED.equals(order.getStatus())) {
			return List.of();
		}
		List<UUID> ids = new ArrayList<>();
		boolean granted = grants.existsByOrderId(order.getId());
		User student = activeStudent(order.getStudentId());
		// No receipt row when there is nowhere to send it: no typed address, and no verified account email (Phase 4B).
		if (granted && student != null && ExamMailTexts.receiptAddress(order, student) != null) {
			add(ids, insert(ExamOutboundMail.PAYMENT_RECEIPT, order.getId().toString(), order.getStudentId()));
		}
		UUID masterAdmin = granted ? masterAdmin() : null;
		if (masterAdmin != null) {
			add(ids, insert(ExamOutboundMail.PURCHASE_NOTICE, order.getId().toString(), masterAdmin));
		}
		if (LiveExamOrder.EXAM.equals(order.getScope())) {
			exams.findByIdAndDeletedAtIsNull(order.getExamId())
				.ifPresent(exam -> ids.addAll(consider(order.getStudentId(), exam, clock.instant())));
		}
		else if (LiveExamOrderBundle.SCOPE.equals(order.getScope())) {
			// Only the bundle's exams: a row for another exam would take its key and block the mail if bought later.
			for (UUID examId : bundles.examIds(order.getId())) {
				exams.findByIdAndDeletedAtIsNull(examId)
					.ifPresent(exam -> ids.addAll(consider(order.getStudentId(), exam, clock.instant())));
			}
		}
		else {
			for (LiveExam exam : exams.findDatedPublishedByModuleId(order.getModuleId())) {
				ids.addAll(consider(order.getStudentId(), exam, clock.instant()));
			}
		}
		return ids;
	}

	@Transactional
	public List<UUID> enqueueForExam(UUID examId) {
		LiveExam exam = exams.findByIdAndDeletedAtIsNull(examId).orElse(null);
		if (!mailable(exam)) {
			return List.of();
		}
		List<UUID> ids = new ArrayList<>();
		Instant now = clock.instant();
		for (UUID studentId : exams.findActiveStudentsWithAccessToExam(exam.getId())) {
			ids.addAll(consider(studentId, exam, now));
		}
		return ids;
	}

	@Transactional
	public List<UUID> enqueueForModule(UUID moduleId) {
		if (!modulePublished(moduleId)) {
			return List.of();
		}
		List<UUID> ids = new ArrayList<>();
		for (LiveExam exam : exams.findDatedPublishedByModuleId(moduleId)) {
			ids.addAll(enqueueForExam(exam.getId()));
		}
		return ids;
	}

	/**
	 * The receipt of one payment on a direct enrolment's plan (plan section 7). Joins the payment's transaction, so the row is
	 * written only if the payment is. Nothing is queued for a student with no verified email, or who is not active. The scan
	 * sends it after commit.
	 */
	@Transactional
	public void enqueueInstallmentReceipt(UUID paymentId, UUID studentId) {
		User student = activeStudent(studentId);
		if (student == null || ExamMailTexts.receiptAddress(student) == null) {
			return;
		}
		insert(ExamOutboundMail.INSTALLMENT_RECEIPT, paymentId.toString(), studentId);
	}

	/**
	 * One installment reminder to a student, at the verified account email (plan section 7). Nothing is queued for a student who
	 * is not active or has no verified email. Joins the caller's transaction, so a repeat run adds nothing.
	 */
	@Transactional
	public void enqueueInstallmentReminderForStudent(String kind, UUID studentId, UUID installmentId, LocalDate dueOn,
			String week) {
		User student = activeStudent(studentId);
		if (student == null || ExamMailTexts.receiptAddress(student) == null) {
			return;
		}
		insert(kind, ExamMailTexts.installmentKey(studentId, installmentId, dueOn, week), studentId);
	}

	/** The same reminder to the Master Admin (plan section 7, D20). Nothing is queued when there is no Master Admin with an email. */
	@Transactional
	public void enqueueInstallmentReminderForMaster(String kind, UUID installmentId, LocalDate dueOn, String week) {
		UUID master = masterAdmin();
		if (master != null) {
			insert(kind, ExamMailTexts.installmentKey(master, installmentId, dueOn, week), master);
		}
	}

	@Transactional
	public List<UUID> enqueueForRoster(UUID moduleId, UUID studentId) {
		if (!modulePublished(moduleId) || activeStudent(studentId) == null) {
			return List.of();
		}
		List<UUID> ids = new ArrayList<>();
		Instant now = clock.instant();
		for (LiveExam exam : exams.findDatedPublishedByModuleId(moduleId)) {
			ids.addAll(consider(studentId, exam, now));
		}
		return ids;
	}

	@Transactional
	public void enqueueHourBefore(Instant now) {
		Instant after = now.plus(Duration.ofMinutes(15));
		Instant before = now.plus(Duration.ofMinutes(70));
		for (LiveExam exam : exams.findPublishedStartingBetween(after, before)) {
			if (!mailable(exam)) {
				continue;
			}
			for (UUID studentId : exams.findActiveStudentsWithAccessToExam(exam.getId())) {
				insert(ExamOutboundMail.HOUR_BEFORE, ExamMailTexts.examKey(studentId, exam), studentId);
			}
		}
	}

	@Transactional(readOnly = true)
	public List<UUID> pendingIds() {
		return mail.findPendingIds();
	}

	private List<UUID> consider(UUID studentId, LiveExam exam, Instant now) {
		if (!mailable(exam) || activeStudent(studentId) == null) {
			return List.of();
		}
		List<UUID> ids = new ArrayList<>();
		add(ids, insert(ExamOutboundMail.EXAM_TIME, ExamMailTexts.examKey(studentId, exam), studentId));
		if (ExamMailTexts.inHourWindow(exam.getLiveAt(), now)) {
			add(ids, insert(ExamOutboundMail.HOUR_BEFORE, ExamMailTexts.examKey(studentId, exam), studentId));
		}
		return ids;
	}

	private boolean mailable(LiveExam exam) {
		// NR-01 (contracts section 8): published, dated, and its home category branch open. The recipients come from the
		// published packages the exam is in and from exam grants, so a package no longer has to be the exam's only owner.
		return exam != null && exam.getDeletedAt() == null && LiveExam.PUBLISHED.equals(exam.getStatus())
				&& exam.getLiveAt() != null && !visibility.branchHidden(exam.getCategoryId());
	}

	private boolean modulePublished(UUID moduleId) {
		LiveExamModule module = modules.findByIdAndDeletedAtIsNull(moduleId).orElse(null);
		return module != null && LiveExamModule.PUBLISHED.equals(module.getStatus());
	}

	private User activeStudent(UUID userId) {
		User user = users.findByIdAndDeletedAtIsNull(userId).orElse(null);
		if (user == null || !Role.STUDENT.name().equals(user.getRole()) || !"ACTIVE".equals(user.getStatus())) {
			return null;
		}
		return user;
	}

	// There is one Master Admin. The oldest active one with an email is the recipient if that ever differs. Package-private so
	// the installment reminders use the same recipient.
	UUID masterAdmin() {
		List<UUID> found = jdbc.queryForList("""
				select id from users
				where role = 'MASTER_ADMIN' and status = 'ACTIVE' and deleted_at is null
				  and email is not null and email <> ''
				order by created_at, id
				limit 1
				""", UUID.class);
		return found.isEmpty() ? null : found.get(0);
	}

	private UUID insert(String kind, String key, UUID userId) {
		UUID id = UUID.randomUUID();
		java.sql.Timestamp now = java.sql.Timestamp.from(clock.instant());
		int inserted = jdbc.update("""
				insert into exam_outbound_mail (id, kind, message_key, user_id, attempt_count, status, created_at, updated_at)
				values (?, ?, ?, ?, 0, 'PENDING', ?, ?)
				on conflict (kind, message_key) do nothing
				""", id, kind, key, userId, now, now);
		return inserted == 1 ? id : null;
	}

	private static void add(List<UUID> ids, UUID id) {
		if (id != null) {
			ids.add(id);
		}
	}

}
