package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;

/** The two NR-01 notice kinds, against real PostgreSQL. The test rolls back, so the shared database keeps nothing it wrote. */
@SpringBootTest
@Transactional
@Import(StudentNoticeWriterNr01IT.ClockConfig.class)
class StudentNoticeWriterNr01IT extends PostgresIntegrationTest {

	private static final Instant TEN_AM = Instant.parse("2026-10-29T04:00:00Z");

	@Autowired
	private StudentNoticeWriter notices;

	@Autowired
	private MutableClock clock;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void anExamUpdateNoticeGoesToEachStudentOnceAPublishAndNotToAnInstructor() {
		UUID student = insertUser("STUDENT");
		UUID instructor = insertUser("INSTRUCTOR");
		UUID exam = insertExam();
		clock.set(TEN_AM);

		notices.examUpdated(exam, List.of(student, instructor));
		assertThat(countNotices("EXAM_UPDATED", student)).isEqualTo(1);
		assertThat(countNotices("EXAM_UPDATED", instructor)).isZero();

		notices.examUpdated(exam, List.of(student));
		assertThat(countNotices("EXAM_UPDATED", student)).as("the same publish instant adds nothing").isEqualTo(1);

		clock.set(TEN_AM.plusSeconds(60));
		notices.examUpdated(exam, List.of(student));
		assertThat(countNotices("EXAM_UPDATED", student)).as("a later publish adds its own notice").isEqualTo(2);
	}

	@Test
	void aRetakeNoticeGoesToTheStudentWhoWasGrantedTheRetake() {
		UUID student = insertUser("STUDENT");
		UUID exam = insertExam();
		clock.set(TEN_AM);

		notices.retakeAvailable(exam, student);

		assertThat(countNotices("RETAKE_AVAILABLE", student)).isEqualTo(1);
	}

	private long countNotices(String kind, UUID student) {
		return jdbc.queryForObject("select count(*) from student_notices where kind = ? and user_id = ?", Long.class, kind,
				student);
	}

	private UUID insertUser(String role) {
		UUID id = UUID.randomUUID();
		long digits = java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000L);
		jdbc.update("insert into users (id, phone, email, role, status, created_at, updated_at)"
				+ " values (?, ?, ?, ?, 'ACTIVE', now(), now())", id, "+8801" + String.format("%09d", digits),
				id + "@example.com", role);
		return id;
	}

	private UUID insertExam() {
		UUID category = UUID.randomUUID();
		UUID exam = UUID.randomUUID();
		jdbc.update("insert into live_exam_categories (id, title_en, title_bn, position, created_at, updated_at)"
				+ " values (?, 'Category', 'বিভাগ', 0, now(), now())", category);
		jdbc.update("insert into live_exams (id, module_id, category_id, exam_type, title_en, title_bn, description_en,"
				+ " description_bn, rules_en, rules_bn, paper_language, duration_minutes, mode, status, finally_saved,"
				+ " created_at, updated_at) values (?, null, ?, 'MCQ', 'Exam', 'পরীক্ষা', '', '', '', '', 'BN', 30, 'PRACTICE',"
				+ " 'PUBLISHED', true, now(), now())", exam, category);
		return exam;
	}

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(TEN_AM);
		}

	}

}
