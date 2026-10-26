package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.coachingcenter.api.PostgresIntegrationTest;

@SpringBootTest
class DemoContentSeedIT extends PostgresIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-10-26T06:00:00Z");

	private static final String CLEANUP = ".cursor/progress/ui-delivery/demo-cleanup.sql";

	private static final String TEST_PASSWORD = "demo-student-pass-1";

	private static final String DEMO_CATEGORY = "a0000000-0000-4000-8000-%";

	private static final String DEMO_MODULE = "b0000000-0000-4000-8000-%";

	private static final String DEMO_EXAM = "e0000000-0000-4000-8000-%";

	private static final String DEMO_QUESTION = "f0000000-0000-4000-8000-%";

	private static final String DEMO_PARTICIPANT = "70000000-0000-4000-8000-%";

	private static final String DEMO_STUDENT = "50000000-0000-4000-8000-%";

	private static final String DEMO_INSTRUCTOR = "60000000-0000-4000-8000-%";

	private static final String DEMO_ADMIN = "40000000-0000-4000-8000-%";

	private static final String DEMO_ORDER = "c0000000-0000-4000-8000-%";

	private static final String DEMO_GRANT = "d0000000-0000-4000-8000-%";

	private static final String DEMO_ANNOUNCEMENT = "80000000-0000-4000-8000-%";

	@Autowired
	private DemoContentSeed seed;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	private JdbcTemplate jdbc;

	private UUID realStudent;

	@BeforeEach
	void cleanFirst() throws Exception {
		jdbc = new JdbcTemplate(dataSource);
		runCleanup();
	}

	@Test
	void theSeedRefusesProdAndAnyProfileThatIsNotStagingOrDev() {
		assertThatThrownBy(() -> seed.seed(new String[] { "prod", "staging" }, env(), NOW))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("prod");
		assertThatThrownBy(() -> seed.seed(new String[] { "test" }, env(), NOW))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("staging or dev");
		assertThat(count("live_exam_categories", DEMO_CATEGORY)).isZero();
	}

	@Test
	void theSeedRunsTwiceWithoutDuplicatesAndWritesNoMail() {
		seed.seed(new String[] { "dev" }, env(), NOW);
		assertCatalog();
		seed.seed(new String[] { "dev" }, env(), NOW.plus(Duration.ofHours(3)));
		assertCatalog();

		assertThat(jdbc.queryForObject("SELECT count(*) FROM exam_outbound_mail WHERE user_id::text LIKE ?", Integer.class,
				DEMO_STUDENT)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM exam_outbound_mail WHERE kind = 'HOUR_BEFORE'", Integer.class))
			.isZero();
	}

	@Test
	void thePackagesHaveTheirShape() {
		seed.seed(new String[] { "dev" }, env(), NOW);
		assertThat(examCount(DemoContentSeed.MODULE_MCQ, "MCQ")).isEqualTo(5);
		assertThat(examCount(DemoContentSeed.MODULE_WRITTEN, "WRITTEN")).isEqualTo(4);
		assertThat(examCount(DemoContentSeed.MODULE_FULL, "MCQ")).isEqualTo(3);
		assertThat(examCount(DemoContentSeed.MODULE_FULL, "WRITTEN")).isEqualTo(2);
		for (UUID module : new UUID[] { DemoContentSeed.MODULE_MCQ, DemoContentSeed.MODULE_WRITTEN, DemoContentSeed.MODULE_FULL }) {
			assertThat(jdbc.queryForObject("""
					SELECT count(*) FROM live_exam_modules m JOIN live_exam_categories c ON c.id = m.category_id
					WHERE m.id = ? AND c.parent_id = ? AND length(m.description_en) > 0 AND length(m.description_bn) > 0
					""", Integer.class, module, DemoContentSeed.CATEGORY_ROOT)).isEqualTo(1);
		}
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_instructors WHERE module_id = ? AND instructor_id = ?",
				Integer.class, DemoContentSeed.MODULE_FULL, DemoContentSeed.INSTRUCTOR)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_instructors WHERE module_id = ? AND instructor_id = ?",
				Integer.class, DemoContentSeed.MODULE_MCQ, DemoContentSeed.INSTRUCTOR_B)).isEqualTo(1);
	}

	@Test
	void e2IsLiveAtEveryRun() {
		seed.seed(new String[] { "dev" }, env(), NOW);
		assertLiveNow(NOW);
		Instant later = NOW.plus(Duration.ofDays(1));
		seed.seed(new String[] { "dev" }, env(), later);
		assertLiveNow(later);
	}

	@Test
	void theOwnersHaveTheirGrantsAndOrders() {
		seed.seed(new String[] { "dev" }, env(), NOW);
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM live_exam_grants WHERE student_id = ? AND scope = 'MODULE' AND module_id = ?
				  AND source = 'ORDER' AND revoked_at IS NULL
				""", Integer.class, DemoContentSeed.OWNER_PACKAGE, DemoContentSeed.MODULE_FULL)).isEqualTo(1);
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM live_exam_orders WHERE tran_id = 'DEMO-PKG-B3-5101' AND status = 'SUCCEEDED'
				""", Integer.class)).isEqualTo(1);
		assertThat(grantCount(DemoContentSeed.OWNER_EXAM, DemoContentSeed.demo('e', 4))).isEqualTo(1);
		assertThat(grantCount(DemoContentSeed.LIVE_STUDENT, DemoContentSeed.demo('e', 2))).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_orders WHERE tran_id = 'DEMO-STUCK-E9-5001' AND status = 'INITIATED'"
				+ " AND created_at = ?", Integer.class, java.sql.Timestamp.from(NOW.minus(Duration.ofDays(2))))).isEqualTo(1);
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM live_exam_grants g JOIN live_exam_orders o ON o.id = g.order_id
				WHERE o.tran_id = 'DEMO-STUCK-E9-5001'
				""", Integer.class)).isZero();
	}

	@Test
	void theLoginStudentsSignInWithTheEnvPasswordAndTheNoPhoneStudentHasNoPhone() {
		seed.seed(new String[] { "dev" }, env(), NOW);
		String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, DemoContentSeed.OWNER_PACKAGE);
		assertThat(passwords.matches(TEST_PASSWORD, hash)).isTrue();
		assertThat(jdbc.queryForObject("SELECT phone FROM users WHERE id = ?", String.class, DemoContentSeed.NO_PHONE)).isNull();
		assertThat(jdbc.queryForObject("SELECT email_verified_at IS NOT NULL FROM users WHERE id = ?", Boolean.class,
				DemoContentSeed.OWNER_PACKAGE)).isTrue();
	}

	@Test
	void theRemindersAndNoticesAreWrittenOnceEach() {
		seed.seed(new String[] { "dev" }, env(), NOW);
		int first = noticeCount();
		seed.seed(new String[] { "dev" }, env(), NOW);
		assertThat(noticeCount()).isEqualTo(first);

		assertThat(noticeOf(DemoContentSeed.OWNER_EXAM, "EXAM_REMINDER", DemoContentSeed.demo('e', 4))).isEqualTo(1);
		assertThat(noticeOf(DemoContentSeed.LIVE_STUDENT, "EXAM_REMINDER", DemoContentSeed.demo('e', 2))).isZero();
		assertThat(noticeOf(DemoContentSeed.LIVE_STUDENT, "EXAM_OPEN", DemoContentSeed.demo('e', 2))).isEqualTo(1);
		assertThat(noticeOf(DemoContentSeed.REMINDER_STUDENT, "EXAM_STARTING_SOON", DemoContentSeed.demo('e', 3))).isEqualTo(1);
		assertThat(noticeOf(DemoContentSeed.REMINDER_STUDENT, "EXAM_REMINDER", DemoContentSeed.demo('e', 3))).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM student_notices WHERE user_id = ? AND kind = 'MCQ_SCORE'", Integer.class,
				DemoContentSeed.OWNER_PACKAGE)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM student_notices WHERE user_id = ? AND kind = 'WRITTEN_RESULT'", Integer.class,
				DemoContentSeed.OWNER_PACKAGE)).isEqualTo(1);
	}

	@Test
	void theAnnouncementReachesEveryLoginStudentOnce() {
		seed.seed(new String[] { "dev" }, env(), NOW);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM announcements WHERE id = ?", Integer.class,
				DemoContentSeed.ANNOUNCEMENT)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM student_notices WHERE announcement_id = ?", Integer.class,
				DemoContentSeed.ANNOUNCEMENT)).isEqualTo(DemoContentSeed.LOGIN_STUDENTS.size());
	}

	@Test
	void theWrittenSittingsCoverTheQueueAndTheReleasedAnswers() {
		seed.seed(new String[] { "dev" }, env(), NOW);
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM live_exam_participants WHERE id = ? AND status = 'SUBMITTED' AND score IS NULL
				  AND answer_stored_file_name IS NOT NULL AND published_at IS NULL
				""", Integer.class, DemoContentSeed.demo('7', 601))).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT published_at IS NULL AND score = 28 FROM live_exam_participants WHERE id = ?",
				Boolean.class, DemoContentSeed.demo('7', 602))).isTrue();
		assertThat(jdbc.queryForObject("SELECT published_at IS NOT NULL AND score = 38 AND evaluated_stored_file_name IS NOT NULL"
				+ " FROM live_exam_participants WHERE id = ?", Boolean.class, DemoContentSeed.demo('7', 501))).isTrue();
		assertThat(jdbc.queryForObject("""
				SELECT status = 'FAILED_TO_SUBMIT' AND score = 0 AND answer_stored_file_name IS NULL
				FROM live_exam_participants WHERE id = ?
				""", Boolean.class, DemoContentSeed.demo('7', 603))).isTrue();
		assertThat(jdbc.queryForObject("SELECT answers_released_at IS NOT NULL FROM live_exams WHERE id = ?", Boolean.class,
				DemoContentSeed.demo('e', 1))).isTrue();
		assertThat(jdbc.queryForObject("SELECT answers_released_at IS NULL FROM live_exams WHERE id = ?", Boolean.class,
				DemoContentSeed.demo('e', 10))).isTrue();
	}

	@Test
	void theCleanupRemovesEveryDemoRowAndKeepsARealOne() throws Exception {
		seed.seed(new String[] { "dev" }, env(), NOW);
		UUID keep = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO live_exam_categories (id, title_en, title_bn, position, created_at, updated_at)
				VALUES (?, 'Keep me', 'থাকুক', 99, now(), now())
				""", keep);
		realStudent = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, 'unused', 'STUDENT', 'ACTIVE', now(), now())
				""", realStudent, "+8801" + ThreadLocalRandom.current().nextInt(100000000, 999999999),
				"real-" + realStudent + "@example.com");

		runCleanup();

		assertThat(count("live_exam_categories", DEMO_CATEGORY)).isZero();
		assertThat(count("live_exam_modules", DEMO_MODULE)).isZero();
		assertThat(count("live_exams", DEMO_EXAM)).isZero();
		assertThat(count("live_exam_questions", DEMO_QUESTION)).isZero();
		assertThat(count("live_exam_participants", DEMO_PARTICIPANT)).isZero();
		assertThat(count("users", DEMO_STUDENT)).isZero();
		assertThat(count("users", DEMO_INSTRUCTOR)).isZero();
		assertThat(count("users", DEMO_ADMIN)).isZero();
		assertThat(count("live_exam_orders", DEMO_ORDER)).isZero();
		assertThat(count("live_exam_grants", DEMO_GRANT)).isZero();
		assertThat(count("announcements", DEMO_ANNOUNCEMENT)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_orders WHERE tran_id LIKE 'DEMO-%'", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_order_exams WHERE exam_id::text LIKE ?", Integer.class, DEMO_EXAM))
			.isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM student_notices WHERE user_id::text LIKE ?", Integer.class, DEMO_STUDENT))
			.isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_instructors WHERE module_id::text LIKE ?", Integer.class, DEMO_MODULE))
			.isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_categories WHERE id = ?", Integer.class, keep)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, realStudent)).isEqualTo(1);
	}

	@Test
	void theCleanupScriptIsReadableFromTheApiFolder() throws Exception {
		assertThat(Files.readString(Path.of(CLEANUP))).contains("live_exam_participants", "users", "announcements", "live_exam_orders");
	}

	private void assertCatalog() {
		assertThat(count("live_exam_categories", DEMO_CATEGORY)).isEqualTo(5);
		assertThat(count("live_exam_modules", DEMO_MODULE)).isEqualTo(3);
		assertThat(count("live_exams", DEMO_EXAM)).isEqualTo(14);
		assertThat(count("live_exam_questions", DEMO_QUESTION)).isEqualTo(80);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_options WHERE question_id::text LIKE ?", Integer.class,
				DEMO_QUESTION)).isEqualTo(320);
		// e1: 8 demo students; e10: 5101; e7: 5003, 5004, 5005; e13: 5101, 5002.
		assertThat(count("live_exam_participants", DEMO_PARTICIPANT)).isEqualTo(14);
		assertThat(count("users", DEMO_STUDENT)).isEqualTo(DemoContentSeed.STUDENTS + DemoContentSeed.LOGIN_STUDENTS.size());
		assertThat(count("users", DEMO_INSTRUCTOR)).isEqualTo(2);
		assertThat(count("users", DEMO_ADMIN)).isEqualTo(1);
		assertThat(count("live_exam_orders", DEMO_ORDER)).isEqualTo(7);
		assertThat(count("live_exam_grants", DEMO_GRANT)).isEqualTo(4);
	}

	private void assertLiveNow(Instant when) {
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM live_exams WHERE id = ? AND live_at <= ? AND ? < live_at + make_interval(mins => duration_minutes)
				""", Integer.class, DemoContentSeed.demo('e', 2), java.sql.Timestamp.from(when), java.sql.Timestamp.from(when)))
			.isEqualTo(1);
	}

	private int examCount(UUID module, String type) {
		return jdbc.queryForObject("SELECT count(*) FROM live_exams WHERE module_id = ? AND exam_type = ?", Integer.class, module, type);
	}

	private int grantCount(UUID student, UUID exam) {
		return jdbc.queryForObject("SELECT count(*) FROM live_exam_grants WHERE student_id = ? AND exam_id = ? AND revoked_at IS NULL",
				Integer.class, student, exam);
	}

	private int noticeOf(UUID student, String kind, UUID exam) {
		return jdbc.queryForObject("SELECT count(*) FROM student_notices WHERE user_id = ? AND kind = ? AND exam_id = ?",
				Integer.class, student, kind, exam);
	}

	private int noticeCount() {
		return jdbc.queryForObject("SELECT count(*) FROM student_notices WHERE user_id::text LIKE ?", Integer.class, DEMO_STUDENT);
	}

	private int count(String table, String idPattern) {
		return jdbc.queryForObject("select count(*) from " + table + " where id::text like ?", Integer.class, idPattern);
	}

	private Function<String, String> env() {
		Map<String, String> values = Map.ofEntries(
				Map.entry("SEED_SYSTEM_ADMIN_PHONE", "01766666666"),
				Map.entry("SEED_SYSTEM_ADMIN_EMAIL", "demo-system-admin@example.com"),
				Map.entry("SEED_SYSTEM_ADMIN_NAME", "ডেমো সিস্টেম অ্যাডমিন"),
				Map.entry("SEED_SYSTEM_ADMIN_PASSWORD", "demo-system-admin-1"),
				Map.entry("SEED_INSTRUCTOR_PHONE", "01755555555"),
				Map.entry("SEED_INSTRUCTOR_EMAIL", "demo-instructor@example.com"),
				Map.entry("SEED_INSTRUCTOR_NAME", "ডেমো শিক্ষক"),
				Map.entry("SEED_INSTRUCTOR_PASSWORD", "demo-instructor-1"),
				Map.entry("SEED_INSTRUCTOR2_PHONE", "01777777777"),
				Map.entry("SEED_INSTRUCTOR2_EMAIL", "demo-instructor-b@example.com"),
				Map.entry("SEED_INSTRUCTOR2_NAME", "ডেমো শিক্ষক বি"),
				Map.entry("SEED_INSTRUCTOR2_PASSWORD", "demo-instructor-2"),
				Map.entry("SEED_TEST_STUDENT_PASSWORD", TEST_PASSWORD));
		return values::get;
	}

	private void runCleanup() throws Exception {
		String script = Files.readString(Path.of(CLEANUP));
		String sql = Arrays.stream(script.split("\n"))
			.filter(line -> !line.trim().startsWith("--"))
			.reduce("", (all, line) -> all + line + "\n");
		for (String statement : sql.split(";")) {
			if (!statement.isBlank()) {
				jdbc.execute(statement);
			}
		}
	}

	@AfterEach
	void dropRealStudent() throws Exception {
		runCleanup();
		if (realStudent != null) {
			jdbc.update("DELETE FROM users WHERE id = ?", realStudent);
			realStudent = null;
		}
	}

}
