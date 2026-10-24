package com.coachingcenter.api.exams;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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

	private final LiveExamRepository exams;

	private final LiveExamModuleRepository modules;

	private final UserRepository users;

	private final Clock clock;

	private final org.springframework.jdbc.core.JdbcTemplate jdbc;

	ExamMailQueue(ExamOutboundMailRepository mail, LiveExamOrderRepository orders, LiveExamGrantRepository grants,
			LiveExamRepository exams, LiveExamModuleRepository modules, UserRepository users, Clock clock,
			org.springframework.jdbc.core.JdbcTemplate jdbc) {
		this.mail = mail;
		this.orders = orders;
		this.grants = grants;
		this.exams = exams;
		this.modules = modules;
		this.users = users;
		this.clock = clock;
		this.jdbc = jdbc;
	}

	@Transactional
	public List<UUID> enqueueForSucceededOrder(UUID orderId) {
		LiveExamOrder order = orders.findById(orderId).orElse(null);
		if (order == null || !LiveExamOrder.SUCCEEDED.equals(order.getStatus())) {
			return List.of();
		}
		List<UUID> ids = new ArrayList<>();
		if (grants.existsByOrderId(order.getId()) && activeStudent(order.getStudentId()) != null) {
			add(ids, insert(ExamOutboundMail.PAYMENT_RECEIPT, order.getId().toString(), order.getStudentId()));
		}
		if (LiveExamOrder.EXAM.equals(order.getScope())) {
			exams.findByIdAndDeletedAtIsNull(order.getExamId())
				.ifPresent(exam -> ids.addAll(consider(order.getStudentId(), exam, clock.instant())));
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
		for (UUID studentId : exams.findActiveStudentsWithAccess(exam.getModuleId(), exam.getId())) {
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
			if (!modulePublished(exam.getModuleId()) || exam.getLiveAt() == null) {
				continue;
			}
			for (UUID studentId : exams.findActiveStudentsWithAccess(exam.getModuleId(), exam.getId())) {
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
		return exam != null && exam.getDeletedAt() == null && LiveExam.PUBLISHED.equals(exam.getStatus())
				&& exam.getLiveAt() != null && modulePublished(exam.getModuleId());
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
