package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.PostgresIntegrationTest;

/** Re-scoring against real PostgreSQL. The test rolls back, so the shared database keeps nothing it wrote. */
@SpringBootTest
@Transactional
class McqRescorerIT extends PostgresIntegrationTest {

	private static final Instant START = Instant.parse("2026-10-29T04:00:00Z");

	@Autowired
	private McqRescorer rescorer;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private LiveExamParticipantRepository participants;

	@Test
	void aKeyChangeRescoresSubmittedPapersAndLeavesAnOpenAttemptAlone() {
		UUID category = UUID.randomUUID();
		UUID exam = UUID.randomUUID();
		UUID singleQuestion = UUID.randomUUID();
		UUID multiQuestion = UUID.randomUUID();
		UUID a = UUID.randomUUID();
		UUID x = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		UUID c = UUID.randomUUID();
		UUID strongStudent = UUID.randomUUID();
		UUID weakStudent = UUID.randomUUID();
		UUID openStudent = UUID.randomUUID();
		UUID strongAttempt = UUID.randomUUID();
		UUID weakAttempt = UUID.randomUUID();
		UUID openAttempt = UUID.randomUUID();

		jdbc.update("insert into live_exam_categories (id, title_en, title_bn, position, created_at, updated_at)"
				+ " values (?, 'Category', 'বিভাগ', 0, now(), now())", category);
		insertStudent(strongStudent);
		insertStudent(weakStudent);
		insertStudent(openStudent);
		jdbc.update("insert into live_exams (id, module_id, category_id, exam_type, title_en, title_bn, description_en,"
				+ " description_bn, rules_en, rules_bn, paper_language, live_at, ends_at, duration_minutes, mode, status,"
				+ " finally_saved, pass_percent, negative_marks, answers_changeable, created_at, updated_at)"
				+ " values (?, null, ?, 'MCQ', 'Exam', 'পরীক্ষা', '', '', '', '', 'BN', ?, ?, 60, 'LIVE', 'PUBLISHED',"
				+ " true, 50, 0.25, true, now(), now())", exam, category, Timestamp.from(START),
				Timestamp.from(START.plusSeconds(3600)));
		jdbc.update("insert into live_exam_questions (id, exam_id, stem, marks, position, multiple_answer, created_at,"
				+ " updated_at) values (?, ?, 'Single', 1, 0, false, now(), now())", singleQuestion, exam);
		jdbc.update("insert into live_exam_questions (id, exam_id, stem, marks, position, multiple_answer, created_at,"
				+ " updated_at) values (?, ?, 'Multi', 1, 1, true, now(), now())", multiQuestion, exam);
		option(a, singleQuestion, true);
		option(x, singleQuestion, false);
		option(b, multiQuestion, true);
		option(c, multiQuestion, true);

		attempt(strongAttempt, exam, strongStudent, "SUBMITTED", new BigDecimal("2.00"), true);
		answer(strongAttempt, singleQuestion, a);
		answer(strongAttempt, multiQuestion, b);
		answer(strongAttempt, multiQuestion, c);
		attempt(weakAttempt, exam, weakStudent, "SUBMITTED", new BigDecimal("-0.25"), false);
		answer(weakAttempt, singleQuestion, x);
		attempt(openAttempt, exam, openStudent, "IN_PROGRESS", null, null);
		answer(openAttempt, singleQuestion, a);

		// The key changes: A is no longer correct, X is.
		jdbc.update("update live_exam_options set correct = false where id = ?", a);
		jdbc.update("update live_exam_options set correct = true where id = ?", x);

		int changed = rescorer.rescore(exam);

		assertThat(changed).isEqualTo(2);
		// Single: now wrong, -0.25. Multi: exact, +1. Maximum 2, pass at 50 percent, so 0.75 fails.
		LiveExamParticipant strong = participants.findById(strongAttempt).orElseThrow();
		assertThat(strong.getScore()).isEqualByComparingTo("0.75");
		assertThat(strong.getPassed()).isFalse();
		LiveExamParticipant weak = participants.findById(weakAttempt).orElseThrow();
		assertThat(weak.getScore()).isEqualByComparingTo("1.00");
		assertThat(weak.getPassed()).isTrue();
		LiveExamParticipant open = participants.findById(openAttempt).orElseThrow();
		assertThat(open.getScore()).isNull();
		assertThat(open.getPassed()).isNull();

		assertThat(rescorer.rescore(exam)).as("a second run changes nothing").isZero();
	}

	private void insertStudent(UUID id) {
		long digits = java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000L);
		jdbc.update("insert into users (id, phone, email, role, status, created_at, updated_at)"
				+ " values (?, ?, ?, 'STUDENT', 'ACTIVE', now(), now())", id, "+8801" + String.format("%09d", digits),
				id + "@example.com");
	}

	private void option(UUID id, UUID question, boolean correct) {
		jdbc.update("insert into live_exam_options (id, question_id, option_text, correct, position, created_at,"
				+ " updated_at) values (?, ?, 'Option', ?, 0, now(), now())", id, question, correct);
	}

	private void attempt(UUID id, UUID exam, UUID student, String status, BigDecimal score, Boolean passed) {
		jdbc.update("insert into live_exam_participants (id, exam_id, student_id, kind, started_at, deadline_at,"
				+ " submitted_at, status, score, passed, attempt_no, created_at, updated_at)"
				+ " values (?, ?, ?, 'LIVE', ?, ?, ?, ?, ?, ?, 1, now(), now())", id, exam, student,
				Timestamp.from(START), Timestamp.from(START.plusSeconds(3600)),
				"SUBMITTED".equals(status) ? Timestamp.from(START.plusSeconds(60)) : null, status, score, passed);
	}

	private void answer(UUID participant, UUID question, UUID option) {
		jdbc.update("insert into live_exam_answers (participant_id, question_id, option_id) values (?, ?, ?)",
				participant, question, option);
	}

}
