package com.coachingcenter.api.exams;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
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
