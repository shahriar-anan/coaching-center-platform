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
 * Writes the three student notices. Each row is inserted once: the unique key makes a repeat a silent no-op instead of an
 * error that would abort the caller's transaction. A row stores no text and no marks; the inbox reads those at view time.
 */
@Service
class StudentNoticeWriter {

	static final String EXAM_REMINDER = "EXAM_REMINDER";

	static final String MCQ_SCORE = "MCQ_SCORE";

	static final String WRITTEN_RESULT = "WRITTEN_RESULT";

	private static final String INSERT = """
			insert into student_notices (id, user_id, kind, message_key, exam_id, participant_id, created_at)
			select ?, u.id, ?, ?, ?, ?, ?
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
		int written = 0;
		for (UUID studentId : studentIds) {
			if (insert(EXAM_REMINDER, studentId + ":" + examId + ":" + liveAt.toEpochMilli(), studentId, examId, null)) {
				written++;
			}
		}
		return written;
	}

	private boolean insert(String kind, String key, UUID studentId, UUID examId, UUID participantId) {
		return jdbc.update(INSERT, UUID.randomUUID(), kind, key, examId, participantId,
				Timestamp.from(clock.instant()), studentId) == 1;
	}

}
