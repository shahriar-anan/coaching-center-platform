package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.coachingcenter.api.PostgresIntegrationTest;

/**
 * The NR-01 migrations against real PostgreSQL. The constraint tests run on a schema migrated to the latest version; the
 * backfill test starts a schema at V44, fills it in the pre-NR-01 shape, then migrates it to the latest version.
 * Every write runs in its own transaction on the named schema, so a rejected row rolls back alone.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Nr01MigrationIT extends PostgresIntegrationTest {

	private static final String SCHEMA = "nr01_it";

	private static final String BACKFILL = "nr01_backfill";

	private static final Instant WINDOW_START = Instant.parse("2026-10-29T04:00:00Z");

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PlatformTransactionManager transactions;

	private TransactionTemplate tx;

	@BeforeAll
	void migrateSchema() {
		tx = new TransactionTemplate(transactions);
		jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
		migrate(SCHEMA, null);
	}

	@AfterAll
	void dropSchemas() {
		jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
		jdbc.execute("DROP SCHEMA IF EXISTS " + BACKFILL + " CASCADE");
	}

	@Test
	void aMultiAnswerQuestionStoresEveryCorrectOptionAndEveryChoice() {
		Fixture f = fixture(SCHEMA);
		UUID participant = participant(SCHEMA, f.exam(), f.student(), "LIVE", "IN_PROGRESS", 1);
		UUID question = UUID.randomUUID();
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		sql(SCHEMA, j -> {
			j.update("insert into live_exam_questions (id, exam_id, stem, marks, position, created_at, updated_at)"
					+ " values (?, ?, 'Stem', 1, 0, now(), now())", question, f.exam());
			insertOption(j, question, first, true);
			insertOption(j, question, second, true);
			insertAnswer(j, participant, question, first);
			insertAnswer(j, participant, question, second);
		});
		assertThat(count(SCHEMA, "select count(*) from live_exam_options where question_id = ? and correct", question))
			.isEqualTo(2);
		assertThat(count(SCHEMA, "select count(*) from live_exam_answers where participant_id = ? and question_id = ?",
				participant, question)).isEqualTo(2);
		assertThat(scalar(SCHEMA, Boolean.class, "select multiple_answer from live_exam_questions where id = ?", question))
			.isFalse();
	}

	@Test
	void aStudentHasOneOpenAttemptButSeveralSubmittedOnes() {
		Fixture f = fixture(SCHEMA);
		participant(SCHEMA, f.exam(), f.student(), "LIVE", "IN_PROGRESS", 1);
		assertThatThrownBy(() -> participant(SCHEMA, f.exam(), f.student(), "RETAKE", "IN_PROGRESS", 2))
			.isInstanceOf(DataIntegrityViolationException.class);

		sql(SCHEMA, j -> j.update("update live_exam_participants set status = 'SUBMITTED' where exam_id = ?", f.exam()));
		participant(SCHEMA, f.exam(), f.student(), "RETAKE", "SUBMITTED", 2);
		assertThat(count(SCHEMA, "select count(*) from live_exam_participants where exam_id = ? and student_id = ?",
				f.exam(), f.student())).isEqualTo(2);
	}

	@Test
	void theWindowIsAtLeastTheDurationAndPracticeHasNoWindow() {
		Fixture f = fixture(SCHEMA);
		assertThatThrownBy(() -> sql(SCHEMA, j -> insertExam(j, f, "LIVE", WINDOW_START,
				WINDOW_START.plusSeconds(600), 30, false, null)))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> sql(SCHEMA, j -> insertExam(j, f, "PRACTICE", WINDOW_START,
				WINDOW_START.plusSeconds(1800), 30, false, null)))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> sql(SCHEMA, j -> insertExam(j, f, "LIVE", WINDOW_START, null, 30, false, null)))
			.isInstanceOf(DataIntegrityViolationException.class);
		sql(SCHEMA, j -> insertExam(j, f, "PRACTICE", null, null, 30, false, null));
	}

	@Test
	void aFreeExamCannotCarryAPrice() {
		Fixture f = fixture(SCHEMA);
		assertThatThrownBy(() -> sql(SCHEMA, j -> insertExam(j, f, "LIVE", WINDOW_START,
				WINDOW_START.plusSeconds(1800), 30, true, new BigDecimal("100"))))
			.isInstanceOf(DataIntegrityViolationException.class);
		sql(SCHEMA, j -> insertExam(j, f, "LIVE", WINDOW_START, WINDOW_START.plusSeconds(1800), 30, true, null));
	}

	@Test
	void aFavouriteNamesExactlyOneTarget() {
		Fixture f = fixture(SCHEMA);
		assertThatThrownBy(() -> favourite(f.student(), f.exam(), f.module()))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> favourite(f.student(), null, null))
			.isInstanceOf(DataIntegrityViolationException.class);
		favourite(f.student(), f.exam(), null);
		assertThatThrownBy(() -> favourite(f.student(), f.exam(), null))
			.isInstanceOf(DataIntegrityViolationException.class);
		favourite(f.student(), null, f.module());
	}

	@Test
	void aPackageMembershipCanBeAddedAgainAfterItWasRemoved() {
		Fixture f = fixture(SCHEMA);
		membership(f.module(), f.exam());
		sql(SCHEMA, j -> j.update("update live_exam_package_exams set removed_at = now() where module_id = ?", f.module()));
		membership(f.module(), f.exam());
		assertThatThrownBy(() -> membership(f.module(), f.exam()))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void theBackfillBringsAnExistingDatabaseToTheNewShape() {
		jdbc.execute("DROP SCHEMA IF EXISTS " + BACKFILL + " CASCADE");
		migrate(BACKFILL, MigrationVersion.fromVersion("44"));

		UUID category = UUID.randomUUID();
		UUID module = UUID.randomUUID();
		UUID instructor = UUID.randomUUID();
		UUID studentOne = UUID.randomUUID();
		UUID studentTwo = UUID.randomUUID();
		UUID examA = UUID.randomUUID();
		UUID examB = UUID.randomUUID();
		UUID examC = UUID.randomUUID();
		UUID examD = UUID.randomUUID();
		UUID participantOne = UUID.randomUUID();
		UUID participantTwo = UUID.randomUUID();
		UUID participantThree = UUID.randomUUID();
		Instant created = Instant.parse("2026-10-01T00:00:00Z");
		Instant published = Instant.parse("2026-10-02T00:00:00Z");
		sql(BACKFILL, j -> {
			insertUser(j, instructor, "INSTRUCTOR");
			insertUser(j, studentOne, "STUDENT");
			insertUser(j, studentTwo, "STUDENT");
			j.update("insert into live_exam_categories (id, title_en, title_bn, position, created_at, updated_at)"
					+ " values (?, 'Category', 'বিভাগ', 0, now(), now())", category);
			j.update("insert into live_exam_modules (id, category_id, title_en, title_bn, description_en, description_bn,"
					+ " access_mode, status, finally_saved, position, created_at, updated_at)"
					+ " values (?, ?, 'Package', 'প্যাকেজ', '', '', 'MANUAL', 'PUBLISHED', true, 0, now(), now())",
					module, category);
			j.update("insert into live_exam_instructors (module_id, instructor_id, assigned_at) values (?, ?, now())",
					module, instructor);
			v44Exam(j, examA, module, "MCQ", "PUBLISHED", WINDOW_START, 30, created, published);
			v44Exam(j, examB, module, "WRITTEN", "PUBLISHED", WINDOW_START, 40, created.plusSeconds(86400), published);
			v44Exam(j, examC, module, "MCQ", "DRAFT", null, 30, created.plusSeconds(172800),
					created.plusSeconds(172800));
			j.update("update live_exams set deleted_at = now() where id = ?", examC);
			v44Exam(j, examD, module, "MCQ", "DRAFT", null, 25, created.plusSeconds(259200),
					created.plusSeconds(259200));
			j.update("update live_exams set question_stored_file_name = 'paper-b.pdf', question_original_file_name = 'b.pdf',"
					+ " question_byte_size = 2048, question_content_type = 'application/pdf' where id = ?", examB);
			v44Participant(j, participantOne, examA, studentOne, "LIVE", "SUBMITTED", 12);
			v44Participant(j, participantTwo, examA, studentTwo, "RETAKE", "SUBMITTED", 7);
			v44Participant(j, participantThree, examB, studentOne, "LIVE", "SUBMITTED", null);
		});

		migrate(BACKFILL, null);

		assertThat(count(BACKFILL, "select count(*) from live_exams where category_id is null")).isZero();
		assertThat(scalar(BACKFILL, String.class, "select is_nullable from information_schema.columns"
				+ " where table_name = 'live_exams' and column_name = 'category_id' and table_schema = current_schema()"))
			.isEqualTo("NO");
		assertThat(membershipCount(examA)).isEqualTo(1);
		assertThat(membershipCount(examB)).isEqualTo(1);
		assertThat(membershipCount(examD)).isEqualTo(1);
		assertThat(membershipCount(examC)).isZero();
		assertThat(count(BACKFILL, "select position from live_exam_package_exams where exam_id = ? and removed_at is null",
				examB)).isEqualTo(1);

		assertThat(instant(BACKFILL, "select ends_at from live_exams where id = ?", examA))
			.isEqualTo(WINDOW_START.plusSeconds(30 * 60));
		assertThat(instant(BACKFILL, "select ends_at from live_exams where id = ?", examB))
			.isEqualTo(WINDOW_START.plusSeconds(40 * 60));
		assertThat(instant(BACKFILL, "select ends_at from live_exams where id = ?", examD)).isNull();

		assertThat(count(BACKFILL, "select count(*) from live_exams where instructor_id = ? and deleted_at is null",
				instructor)).isEqualTo(3);
		assertThat(instant(BACKFILL, "select published_at from live_exams where id = ?", examA)).isEqualTo(published);
		assertThat(instant(BACKFILL, "select published_at from live_exams where id = ?", examD)).isNull();

		assertThat(scalar(BACKFILL, String.class, "select kind from live_exam_participants where id = ?", participantOne))
			.isEqualTo("LIVE");
		assertThat(scalar(BACKFILL, String.class, "select kind from live_exam_participants where id = ?", participantTwo))
			.isEqualTo("ARCHIVED");
		assertThat(count(BACKFILL, "select attempt_no from live_exam_participants where id = ?", participantTwo))
			.isEqualTo(1);
		assertThat(scalar(BACKFILL, BigDecimal.class, "select score from live_exam_participants where id = ?",
				participantOne)).isEqualByComparingTo("12.00");
		assertThat(count(BACKFILL, "select count(*) from live_exam_participants where kind = 'RETAKE'")).isZero();

		UUID paper = scalar(BACKFILL, UUID.class,
				"select id from live_exam_question_papers where exam_id = ? and version = 1", examB);
		assertThat(scalar(BACKFILL, String.class, "select stored_file_name from live_exam_question_papers where id = ?",
				paper)).isEqualTo("paper-b.pdf");
		assertThat(scalar(BACKFILL, UUID.class, "select question_paper_id from live_exams where id = ?", examB))
			.isEqualTo(paper);
		assertThat(scalar(BACKFILL, UUID.class, "select question_paper_id from live_exam_participants where id = ?",
				participantThree)).isEqualTo(paper);
	}

	private int membershipCount(UUID exam) {
		return count(BACKFILL, "select count(*) from live_exam_package_exams where exam_id = ? and removed_at is null", exam);
	}

	private void migrate(String schema, MigrationVersion target) {
		FluentConfiguration config = Flyway.configure().dataSource(dataSource).schemas(schema);
		if (target != null) {
			config.target(target);
		}
		config.load().migrate();
	}

	private record Fixture(UUID category, UUID module, UUID student, UUID exam) {
	}

	/** A student, a category, a published package and a dated exam in it. Committed, so each test can build on it. */
	private Fixture fixture(String schema) {
		Fixture f = new Fixture(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
		sql(schema, j -> {
			insertUser(j, f.student(), "STUDENT");
			j.update("insert into live_exam_categories (id, title_en, title_bn, position, created_at, updated_at)"
					+ " values (?, 'Category', 'বিভাগ', 0, now(), now())", f.category());
			j.update("insert into live_exam_modules (id, category_id, title_en, title_bn, description_en, description_bn,"
					+ " access_mode, status, finally_saved, position, created_at, updated_at)"
					+ " values (?, ?, 'Package', 'প্যাকেজ', '', '', 'MANUAL', 'PUBLISHED', true, 0, now(), now())",
					f.module(), f.category());
			insertExam(j, f, "LIVE", WINDOW_START, WINDOW_START.plusSeconds(1800), 30, false, null, f.exam());
		});
		return f;
	}

	private void insertUser(JdbcTemplate j, UUID id, String role) {
		long digits = ThreadLocalRandom.current().nextLong(1_000_000_000L);
		j.update("insert into users (id, phone, email, role, status, created_at, updated_at)"
				+ " values (?, ?, ?, ?, 'ACTIVE', now(), now())", id,
				"+8801" + String.format("%09d", digits), id + "@example.com", role);
	}

	private void insertExam(JdbcTemplate j, Fixture f, String mode, Instant liveAt, Instant endsAt, int duration,
			boolean free, BigDecimal price) {
		insertExam(j, f, mode, liveAt, endsAt, duration, free, price, UUID.randomUUID());
	}

	private void insertExam(JdbcTemplate j, Fixture f, String mode, Instant liveAt, Instant endsAt, int duration,
			boolean free, BigDecimal price, UUID id) {
		j.update("insert into live_exams (id, module_id, category_id, exam_type, title_en, title_bn, description_en,"
				+ " description_bn, rules_en, rules_bn, paper_language, live_at, ends_at, duration_minutes, mode, status,"
				+ " finally_saved, is_free, price, created_at, updated_at)"
				+ " values (?, ?, ?, 'MCQ', 'Exam', 'পরীক্ষা', '', '', '', '', 'BN', ?, ?, ?, ?, 'PUBLISHED', true, ?, ?,"
				+ " now(), now())", id, f.module(), f.category(), timestamp(liveAt), timestamp(endsAt), duration, mode,
				free, price);
	}

	private void v44Exam(JdbcTemplate j, UUID id, UUID module, String type, String status, Instant liveAt, int duration,
			Instant createdAt, Instant updatedAt) {
		j.update("insert into live_exams (id, module_id, exam_type, title_en, title_bn, description_en, description_bn,"
				+ " rules_en, rules_bn, paper_language, live_at, duration_minutes, status, finally_saved, created_at,"
				+ " updated_at) values (?, ?, ?, 'Exam', 'পরীক্ষা', '', '', '', '', 'BN', ?, ?, ?, true, ?, ?)",
				id, module, type, timestamp(liveAt), duration, status, Timestamp.from(createdAt), Timestamp.from(updatedAt));
	}

	private void v44Participant(JdbcTemplate j, UUID id, UUID exam, UUID student, String kind, String status,
			Integer score) {
		j.update("insert into live_exam_participants (id, exam_id, student_id, kind, started_at, deadline_at, submitted_at,"
				+ " score, status, created_at, updated_at) values (?, ?, ?, ?, now(), now(), now(), ?, ?, now(), now())",
				id, exam, student, kind, score, status);
	}

	private UUID participant(String schema, UUID exam, UUID student, String kind, String status, int attemptNo) {
		UUID id = UUID.randomUUID();
		sql(schema, j -> j.update("insert into live_exam_participants (id, exam_id, student_id, kind, started_at,"
				+ " deadline_at, status, attempt_no, created_at, updated_at)"
				+ " values (?, ?, ?, ?, now(), now() + interval '40 minutes', ?, ?, now(), now())", id, exam, student, kind,
				status, attemptNo));
		return id;
	}

	private void insertOption(JdbcTemplate j, UUID question, UUID id, boolean correct) {
		j.update("insert into live_exam_options (id, question_id, option_text, correct, position, created_at, updated_at)"
				+ " values (?, ?, 'Option', ?, 0, now(), now())", id, question, correct);
	}

	private void insertAnswer(JdbcTemplate j, UUID participant, UUID question, UUID option) {
		j.update("insert into live_exam_answers (participant_id, question_id, option_id) values (?, ?, ?)", participant,
				question, option);
	}

	private void favourite(UUID student, UUID exam, UUID module) {
		sql(SCHEMA, j -> j.update("insert into student_favourites (id, student_id, exam_id, module_id, created_at)"
				+ " values (?, ?, ?, ?, now())", UUID.randomUUID(), student, exam, module));
	}

	private void membership(UUID module, UUID exam) {
		sql(SCHEMA, j -> j.update("insert into live_exam_package_exams (id, module_id, exam_id, position, added_at)"
				+ " values (?, ?, ?, 0, now())", UUID.randomUUID(), module, exam));
	}

	private static Timestamp timestamp(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}

	private void sql(String schema, Consumer<JdbcTemplate> work) {
		tx.executeWithoutResult(status -> {
			jdbc.execute("SET LOCAL search_path TO " + schema);
			work.accept(jdbc);
		});
	}

	private <T> T query(String schema, Function<JdbcTemplate, T> work) {
		return tx.execute(status -> {
			jdbc.execute("SET LOCAL search_path TO " + schema);
			return work.apply(jdbc);
		});
	}

	private <T> T scalar(String schema, Class<T> type, String sql, Object... args) {
		return query(schema, j -> j.queryForObject(sql, type, args));
	}

	private int count(String schema, String sql, Object... args) {
		return scalar(schema, Integer.class, sql, args);
	}

	private Instant instant(String schema, String sql, Object... args) {
		Timestamp value = scalar(schema, Timestamp.class, sql, args);
		return value == null ? null : value.toInstant();
	}

}
