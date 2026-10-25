package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
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

import com.coachingcenter.api.PostgresIntegrationTest;

@SpringBootTest
class DemoContentSeedIT extends PostgresIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-10-25T06:00:00Z");

	private static final String CLEANUP = ".cursor/progress/ui-delivery/demo-cleanup.sql";

	private static final String DEMO_CATEGORY = "a0000000-0000-4000-8000-%";

	private static final String DEMO_MODULE = "b0000000-0000-4000-8000-%";

	private static final String DEMO_EXAM = "e0000000-0000-4000-8000-%";

	private static final String DEMO_QUESTION = "f0000000-0000-4000-8000-%";

	private static final String DEMO_PARTICIPANT = "70000000-0000-4000-8000-%";

	private static final String DEMO_STUDENT = "50000000-0000-4000-8000-%";

	private static final String DEMO_INSTRUCTOR = "60000000-0000-4000-8000-%";

	@Autowired
	private DemoContentSeed seed;

	@Autowired
	private DataSource dataSource;

	private JdbcTemplate jdbc;

	private String rosterEmail;

	private UUID rosterStudent;

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
	void theSeedRunsTwiceWithoutDuplicatesAndLinksTheExistingStudent() throws Exception {
		createRosterStudent();
		seed.seed(new String[] { "dev" }, env(), NOW);
		assertCatalog();
		seed.seed(new String[] { "dev" }, env(), NOW);
		assertCatalog();

		assertThat(jdbc.queryForObject("select count(*) from live_exam_roster where module_id = ? and student_id = ?", Integer.class,
				DemoContentSeed.MODULE_19, rosterStudent)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from live_exam_instructors where module_id = ? and instructor_id = ?",
				Integer.class, DemoContentSeed.MODULE_19, DemoContentSeed.INSTRUCTOR)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from live_exams where id = ? and live_at is null and status = 'PUBLISHED'",
				Integer.class, DemoContentSeed.demo('e', 4))).isEqualTo(1);
		assertThat(jdbc.queryForObject("select question_stored_file_name from live_exams where id = ?", String.class,
				DemoContentSeed.demo('e', 4))).isNotNull();
		assertThat(jdbc.queryForObject("select published_at from live_exam_participants where id = ?", Object.class,
				DemoContentSeed.demo('7', 502L))).isNull();
		assertThat(jdbc.queryForObject("select published_at from live_exam_participants where id = ?", Object.class,
				DemoContentSeed.demo('7', 501L))).isNotNull();
	}

	@Test
	void theCleanupRemovesEveryDemoRowAndNothingElse() throws Exception {
		createRosterStudent();
		seed.seed(new String[] { "dev" }, env(), NOW);
		UUID keep = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO live_exam_categories (id, title_en, title_bn, position, created_at, updated_at)
				VALUES (?, 'Keep me', 'থাকুক', 99, now(), now())
				""", keep);
		UUID realStudent = UUID.randomUUID();
		String realPhone = "+8801" + ThreadLocalRandom.current().nextInt(100000000, 999999999);
		jdbc.update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, 'unused', 'STUDENT', 'ACTIVE', now(), now())
				""", realStudent, realPhone, "real-" + realStudent + "@example.com");

		runCleanup();

		assertThat(count("live_exam_categories", DEMO_CATEGORY)).isZero();
		assertThat(count("live_exam_modules", DEMO_MODULE)).isZero();
		assertThat(count("live_exams", DEMO_EXAM)).isZero();
		assertThat(count("live_exam_questions", DEMO_QUESTION)).isZero();
		assertThat(count("live_exam_participants", DEMO_PARTICIPANT)).isZero();
		assertThat(count("users", DEMO_STUDENT)).isZero();
		assertThat(count("users", DEMO_INSTRUCTOR)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from live_exam_roster where student_id = ?", Integer.class, rosterStudent))
			.isZero();
		assertThat(jdbc.queryForObject("select count(*) from live_exam_categories where id = ?", Integer.class, keep)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from users where id = ?", Integer.class, realStudent)).isEqualTo(1);
		jdbc.update("DELETE FROM users WHERE id = ?", realStudent);
		jdbc.update("DELETE FROM live_exam_categories WHERE id = ?", keep);
	}

	@Test
	void theCleanupScriptIsReadableFromTheApiFolder() throws Exception {
		assertThat(Files.readString(Path.of(CLEANUP))).contains("live_exam_participants", "users");
	}

	private void assertCatalog() {
		assertThat(count("live_exam_categories", DEMO_CATEGORY)).isEqualTo(3);
		assertThat(count("live_exam_modules", DEMO_MODULE)).isEqualTo(3);
		assertThat(count("live_exams", DEMO_EXAM)).isEqualTo(8);
		assertThat(count("live_exam_questions", DEMO_QUESTION)).isEqualTo(40);
		assertThat(count("live_exam_participants", DEMO_PARTICIPANT)).isEqualTo(10);
		assertThat(count("users", DEMO_STUDENT)).isEqualTo(DemoContentSeed.STUDENTS);
		assertThat(count("users", DEMO_INSTRUCTOR)).isEqualTo(1);
	}

	private int count(String table, String idPattern) {
		return jdbc.queryForObject("select count(*) from " + table + " where id::text like ?", Integer.class, idPattern);
	}

	private void createRosterStudent() {
		rosterStudent = UUID.randomUUID();
		rosterEmail = "demo-roster-" + rosterStudent + "@example.com";
		jdbc.update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, 'unused', 'STUDENT', 'ACTIVE', now(), now())
				""", rosterStudent, "+8801" + ThreadLocalRandom.current().nextInt(100000000, 999999999), rosterEmail);
	}

	private Function<String, String> env() {
		Map<String, String> values = Map.of(
				"SEED_INSTRUCTOR_PHONE", "01755555555",
				"SEED_INSTRUCTOR_EMAIL", "demo-instructor@example.com",
				"SEED_INSTRUCTOR_NAME", "ডেমো শিক্ষক",
				"SEED_INSTRUCTOR_PASSWORD", "demo-instructor-1",
				"SEED_STUDENT_EMAIL", rosterEmail == null ? "" : rosterEmail);
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
	void dropRosterStudent() throws Exception {
		runCleanup();
		if (rosterStudent != null) {
			jdbc.update("DELETE FROM users WHERE id = ?", rosterStudent);
			rosterStudent = null;
		}
	}

}
