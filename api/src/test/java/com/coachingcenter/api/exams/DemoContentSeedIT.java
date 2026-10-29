package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
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
import com.coachingcenter.api.auth.AuthService;
import com.coachingcenter.api.auth.ClientType;

@SpringBootTest
class DemoContentSeedIT extends PostgresIntegrationTest {

	/** 27 October 2026, 16:00 in Dhaka. */
	private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

	private static final String CLEANUP = ".cursor/progress/ui-delivery/demo-cleanup.sql";

	private static final String TEST_PASSWORD = "demo-pass-1234";

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

	@Autowired
	private AuthService auth;

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
		assertThatThrownBy(() -> seed.seed(new String[] { "dev" }, key -> null, NOW))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("SEED_DEMO_PASSWORD is required.");
		assertThat(count("live_exam_categories", DEMO_CATEGORY)).isZero();
		assertThat(count("users", DEMO_STUDENT)).isZero();
	}

	@Test
	void aSecondRunWritesNothingAndTheSeedWritesNoMail() {
		int hourBefore = jdbc.queryForObject("SELECT count(*) FROM exam_outbound_mail WHERE kind = 'HOUR_BEFORE'", Integer.class);
		seed.seed(new String[] { "dev" }, env(), NOW);
		assertCatalog();
		int notices = noticeCount();
		int participants = count("live_exam_participants", DEMO_PARTICIPANT);

		// A later run, even on another day, adds no row and moves no date: the client's marking is safe.
		Timestamp before = jdbc.queryForObject("SELECT live_at FROM live_exams WHERE id = ?", Timestamp.class, DemoContentSeed.demo('e', 20));
		seed.seed(new String[] { "dev" }, env(), NOW.plusSeconds(3 * 86_400));
		assertCatalog();
		assertThat(noticeCount()).isEqualTo(notices);
		assertThat(count("live_exam_participants", DEMO_PARTICIPANT)).isEqualTo(participants);
		assertThat(jdbc.queryForObject("SELECT live_at FROM live_exams WHERE id = ?", Timestamp.class, DemoContentSeed.demo('e', 20)))
			.isEqualTo(before);

		assertThat(jdbc.queryForObject("SELECT count(*) FROM exam_outbound_mail WHERE user_id::text LIKE ?", Integer.class,
				DEMO_STUDENT)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM exam_outbound_mail WHERE kind = 'HOUR_BEFORE'", Integer.class))
			.isEqualTo(hourBefore);
	}

	@Test
	void theCategoriesAndPackagesHaveTheirShape() {
		seed.seed(new String[] { "dev" }, env(), NOW);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_categories WHERE id::text LIKE ? AND parent_id IS NULL",
				Integer.class, DEMO_CATEGORY)).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_categories WHERE id::text LIKE ? AND parent_id IS NOT NULL",
				Integer.class, DEMO_CATEGORY)).isEqualTo(4);
		for (UUID module : List.of(DemoContentSeed.MODULE_MCQ, DemoContentSeed.MODULE_WRITTEN, DemoContentSeed.MODULE_FULL,
				DemoContentSeed.MODULE_BAR)) {
			assertThat(jdbc.queryForObject("""
					SELECT count(*) FROM live_exam_modules m JOIN live_exam_categories c ON c.id = m.category_id
					WHERE m.id = ? AND c.parent_id IS NOT NULL AND length(m.description_en) > 0 AND length(m.description_bn) > 0
					  AND m.price IS NOT NULL AND m.status = 'PUBLISHED'
					""", Integer.class, module)).isEqualTo(1);
		}
		assertThat(examCount(DemoContentSeed.MODULE_MCQ, "MCQ")).isEqualTo(3);
		assertThat(examCount(DemoContentSeed.MODULE_MCQ, "WRITTEN")).isZero();
		assertThat(examCount(DemoContentSeed.MODULE_WRITTEN, "WRITTEN")).isEqualTo(5);
		assertThat(examCount(DemoContentSeed.MODULE_WRITTEN, "MCQ")).isZero();
		assertThat(examCount(DemoContentSeed.MODULE_FULL, "MCQ")).isEqualTo(5);
		assertThat(examCount(DemoContentSeed.MODULE_FULL, "WRITTEN")).isEqualTo(6);
		assertThat(examCount(DemoContentSeed.MODULE_BAR, "MCQ")).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exams WHERE id::text LIKE ? AND duration_minutes <> 20",
				Integer.class, DEMO_EXAM)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exams WHERE id::text LIKE ? AND live_at IS NULL", Integer.class,
				DEMO_EXAM)).isEqualTo(3);
		// The base data has one exam with no price (Penal MCQ); the review adds a free exam and a two-package exam with none of their own.
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exams WHERE id::text LIKE ? AND price IS NULL", Integer.class,
				DEMO_EXAM)).isEqualTo(3);
	}

	@Test
	void onlyThePreviewExamHasPreviewOnAndItsPaperHasTenQuestions() {
		seed.seed(new String[] { "dev" }, env(), NOW);
		assertThat(jdbc.queryForList("SELECT id FROM live_exams WHERE preview_enabled AND id::text LIKE ?", UUID.class, DEMO_EXAM))
			.containsExactly(DemoContentSeed.demo('e', 66));
		// A standalone MCQ paper whose window has ended, so the preview is open; its answers are not released.
		assertThat(jdbc.queryForObject("""
				SELECT exam_type = 'MCQ' AND module_id IS NULL AND status = 'PUBLISHED' AND title_en = 'Preview MCQ'
				  AND ends_at < ? AND answers_released_at IS NULL
				FROM live_exams WHERE id = ?
				""", Boolean.class, java.sql.Timestamp.from(NOW), DemoContentSeed.demo('e', 66))).isTrue();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_questions WHERE exam_id = ?", Integer.class,
				DemoContentSeed.demo('e', 66))).isEqualTo(10);
	}

	@Test
	void theNextSevenDaysEachHaveAnElevenPmExamInTheMixedPackage() {
		seed.seed(new String[] { "dev" }, env(), NOW);
		for (int day = 1; day <= 7; day++) {
			Map<String, Object> row = jdbc.queryForMap("""
					SELECT exam_type, module_id, (live_at AT TIME ZONE 'Asia/Dhaka')::date AS day,
					       (live_at AT TIME ZONE 'Asia/Dhaka')::time AS at
					FROM live_exams WHERE id = ?
					""", DemoContentSeed.demo('e', 19 + day));
			assertThat(row.get("day").toString()).isEqualTo(java.time.LocalDate.of(2026, 10, 27).plusDays(day).toString());
			assertThat(row.get("at").toString()).isEqualTo("23:00:00");
			assertThat(row.get("exam_type")).isEqualTo(day % 2 == 1 ? "MCQ" : "WRITTEN");
			assertThat(row.get("module_id")).isEqualTo(DemoContentSeed.MODULE_FULL);
		}
	}

	@Test
	void studentAHasTwoPublishedResultsOneWaitingPaperAndTheMixedPackage() {
		seed.seed(new String[] { "dev" }, env(), NOW);
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM live_exam_grants WHERE student_id = ? AND scope = 'MODULE' AND module_id = ?
				  AND source = 'ORDER' AND revoked_at IS NULL
				""", Integer.class, DemoContentSeed.STUDENT_A, DemoContentSeed.MODULE_FULL)).isEqualTo(1);
		// MCQ e10: scored, ten picks behind it, answers released.
		assertThat(jdbc.queryForObject("""
				SELECT score = 35 AND passed AND status = 'SUBMITTED' FROM live_exam_participants WHERE id = ?
				""", Boolean.class, DemoContentSeed.demo('7', 1))).isTrue();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_answers WHERE participant_id = ?", Integer.class,
				DemoContentSeed.demo('7', 1))).isEqualTo(10);
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM live_exam_answers a JOIN live_exam_options o ON o.id = a.option_id
				WHERE a.participant_id = ? AND o.correct
				""", Integer.class, DemoContentSeed.demo('7', 1))).isEqualTo(7);
		assertThat(jdbc.queryForObject("SELECT answers_released_at IS NOT NULL FROM live_exams WHERE id = ?", Boolean.class,
				DemoContentSeed.demo('e', 10))).isTrue();
		// Written e11: marked, released, with a marked file, and no correction waiting.
		assertThat(jdbc.queryForObject("""
				SELECT published_at IS NOT NULL AND score = 32 AND passed AND comment IS NOT NULL
				  AND evaluated_stored_file_name IS NOT NULL AND answer_stored_file_name IS NOT NULL
				  AND pending_evaluated_at IS NULL AND ready_for_release_at IS NULL
				FROM live_exam_participants WHERE id = ?
				""", Boolean.class, DemoContentSeed.demo('7', 2))).isTrue();
		// Written e12: handed in, not marked.
		assertThat(jdbc.queryForObject("""
				SELECT status = 'SUBMITTED' AND score IS NULL AND published_at IS NULL AND evaluated_at IS NULL
				  AND answer_stored_file_name IS NOT NULL
				FROM live_exam_participants WHERE id = ?
				""", Boolean.class, DemoContentSeed.demo('7', 3))).isTrue();
	}

	@Test
	void theInstructorHasTwoPackagesAndFourPapersToMark() {
		seed.seed(new String[] { "dev" }, env(), NOW);
		assertThat(jdbc.queryForList("SELECT module_id FROM live_exam_instructors WHERE instructor_id = ?", UUID.class,
				DemoContentSeed.INSTRUCTOR)).containsExactlyInAnyOrder(DemoContentSeed.MODULE_WRITTEN, DemoContentSeed.MODULE_FULL);
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM live_exam_participants p JOIN live_exams e ON e.id = p.exam_id
				WHERE e.exam_type = 'WRITTEN' AND p.status = 'SUBMITTED' AND p.evaluated_at IS NULL AND p.published_at IS NULL
				  AND p.id::text LIKE ? AND e.module_id IN (?, ?)
				""", Integer.class, DEMO_PARTICIPANT, DemoContentSeed.MODULE_WRITTEN, DemoContentSeed.MODULE_FULL)).isEqualTo(4);
	}

	@Test
	void everyAccountSignsInWithThePasswordByPhoneAndByEmail() {
		seed.seed(new String[] { "dev" }, env(), NOW);
		assertThat(count("users", DEMO_STUDENT)).isEqualTo(4);
		assertThat(count("users", DEMO_INSTRUCTOR)).isEqualTo(1);
		assertThat(count("users", DEMO_ADMIN)).isEqualTo(1);
		for (DemoContentSeed.Account account : DemoContentSeed.ACCOUNTS) {
			String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, account.id());
			assertThat(passwords.matches(TEST_PASSWORD, hash)).isTrue();
			assertThat(jdbc.queryForObject("SELECT email_verified_at IS NOT NULL AND status = 'ACTIVE' FROM users WHERE id = ?",
					Boolean.class, account.id())).isTrue();
			assertThat(auth.login(account.phone(), TEST_PASSWORD, ClientType.WEB, "127.0.0.1", "test").accessToken()).isNotBlank();
			assertThat(auth.login(account.email(), TEST_PASSWORD, ClientType.WEB, "127.0.0.1", "test").accessToken()).isNotBlank();
		}
		assertThat(jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, DemoContentSeed.STAFF))
			.isEqualTo("SYSTEM_ADMIN");
	}

	@Test
	void paymentsAreInEveryStateAndOnlyPaidOnesGrantAccess() {
		seed.seed(new String[] { "dev" }, env(), NOW);
		assertThat(count("live_exam_orders", DEMO_ORDER)).isEqualTo(15);
		assertThat(orderCount("SUCCEEDED")).isEqualTo(5);
		assertThat(orderCount("FAILED")).isEqualTo(3);
		assertThat(orderCount("CANCELLED")).isEqualTo(2);
		assertThat(orderCount("INITIATED")).isEqualTo(5);
		// Only a paid order carries a grant.
		assertThat(count("live_exam_grants", DEMO_GRANT)).isEqualTo(7);
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM live_exam_grants g JOIN live_exam_orders o ON o.id = g.order_id
				WHERE g.id::text LIKE ? AND o.status <> 'SUCCEEDED'
				""", Integer.class, DEMO_GRANT)).isZero();
		assertThat(grantCount(DemoContentSeed.STUDENT_B, null, DemoContentSeed.MODULE_WRITTEN)).isEqualTo(1);
		assertThat(grantCount(DemoContentSeed.STUDENT_B, DemoContentSeed.demo('e', 30), null)).isEqualTo(1);
		assertThat(grantCount(DemoContentSeed.STUDENT_A, DemoContentSeed.demo('e', 2), null)).isEqualTo(1);
		assertThat(grantCount(DemoContentSeed.STUDENT_C, DemoContentSeed.demo('e', 6), null)).isEqualTo(1);
		assertThat(grantCount(DemoContentSeed.STUDENT_C, null, DemoContentSeed.MODULE_MCQ)).isZero();
	}

	@Test
	void theReviewStudentHasAPendingFailedAndCancelledPaymentAndNoAccessFromThem() {
		seed.seed(new String[] { "dev" }, env(), NOW);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_orders WHERE student_id = ? AND status = 'INITIATED' AND id::text LIKE ?",
				Integer.class, DemoContentSeed.STUDENT_D, DEMO_ORDER)).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_orders WHERE student_id = ? AND status = 'FAILED' AND id::text LIKE ?",
				Integer.class, DemoContentSeed.STUDENT_D, DEMO_ORDER)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_orders WHERE student_id = ? AND status = 'CANCELLED' AND id::text LIKE ?",
				Integer.class, DemoContentSeed.STUDENT_D, DEMO_ORDER)).isEqualTo(1);
		// Only a paid order grants access, so none of the review student's four orders has a grant.
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_grants WHERE student_id = ? AND order_id IS NOT NULL",
				Integer.class, DemoContentSeed.STUDENT_D)).isZero();
	}

	@Test
	void thePendingPaymentsAreOlderThanADaySoTheRecheckJobLeavesThemForTheMasterAdmin() {
		seed.seed(new String[] { "dev" }, env(), NOW);
		// The recheck job reads open orders from the last 24 hours only, so none of these is touched by it.
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM live_exam_orders WHERE id::text LIKE ? AND status = 'INITIATED'
				  AND created_at < ?
				""", Integer.class, DEMO_ORDER, java.sql.Timestamp.from(NOW.minus(Duration.ofHours(24))))).isEqualTo(5);
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM live_exam_orders WHERE id::text LIKE ? AND status = 'INITIATED'
				  AND (val_id IS NOT NULL OR payment_channel IS NOT NULL)
				""", Integer.class, DEMO_ORDER)).isZero();
		// One open order per student and target, so the buy button still finds the open one.
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM (SELECT student_id, coalesce(exam_id, module_id) AS target FROM live_exam_orders
				  WHERE id::text LIKE ? AND status = 'INITIATED' GROUP BY 1, 2 HAVING count(*) > 1) d
				""", Integer.class, DEMO_ORDER)).isZero();
	}

	@Test
	void draftPackagesAndExamsAwaitThePublishOfTheMasterAdmin() {
		seed.seed(new String[] { "dev" }, env(), NOW);
		// Two draft packages: b5 saved and ready, b6 only started.
		assertThat(jdbc.queryForObject("SELECT status = 'DRAFT' AND finally_saved AND price IS NOT NULL FROM live_exam_modules WHERE id = ?",
				Boolean.class, DemoContentSeed.MODULE_DRAFT_READY)).isTrue();
		assertThat(jdbc.queryForObject("SELECT status = 'DRAFT' AND NOT finally_saved AND price IS NULL FROM live_exam_modules WHERE id = ?",
				Boolean.class, DemoContentSeed.MODULE_DRAFT_EMPTY)).isTrue();
		assertThat(examCountIn(DemoContentSeed.MODULE_DRAFT_EMPTY)).isZero();
		assertThat(examCountIn(DemoContentSeed.MODULE_DRAFT_READY)).isEqualTo(2);
		// Four draft exams: ready and unfinished, in a draft package and in published ones.
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exams WHERE id::text LIKE ? AND status = 'DRAFT'", Integer.class,
				DEMO_EXAM)).isEqualTo(4);
		assertThat(draft(50)).containsEntry("finally_saved", true).containsEntry("has_paper", true);
		assertThat(draft(51)).containsEntry("finally_saved", false).containsEntry("questions", 10L);
		assertThat(draft(52)).containsEntry("finally_saved", true).containsEntry("questions", 10L).containsEntry("module",
				DemoContentSeed.MODULE_MCQ);
		assertThat(draft(53)).containsEntry("finally_saved", false).containsEntry("has_paper", false).containsEntry("live_at", null);
		// Students see none of it: the public reads take published rows only.
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM live_exams e JOIN live_exam_modules m ON m.id = e.module_id
				WHERE e.id::text LIKE ? AND e.status = 'PUBLISHED' AND m.status = 'DRAFT'
				""", Integer.class, DEMO_EXAM)).isZero();
	}

	@Test
	void twoWrittenPapersAreMarkedButNotReleasedAndOneIsFlaggedReady() {
		seed.seed(new String[] { "dev" }, env(), NOW);
		for (int sitting : new int[] { 7, 8 }) {
			assertThat(jdbc.queryForObject("""
					SELECT status = 'SUBMITTED' AND score IS NOT NULL AND evaluated_at IS NOT NULL AND published_at IS NULL
					  AND evaluated_stored_file_name IS NOT NULL AND answer_stored_file_name IS NOT NULL
					FROM live_exam_participants WHERE id = ?
					""", Boolean.class, DemoContentSeed.demo('7', sitting))).isTrue();
		}
		assertThat(jdbc.queryForObject("SELECT ready_for_release_at IS NOT NULL FROM live_exam_participants WHERE id = ?", Boolean.class,
				DemoContentSeed.demo('7', 7))).isTrue();
		assertThat(jdbc.queryForObject("SELECT ready_for_release_at IS NULL FROM live_exam_participants WHERE id = ?", Boolean.class,
				DemoContentSeed.demo('7', 8))).isTrue();
		// The instructor's four papers still waiting for a mark are unchanged.
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM live_exam_participants p JOIN live_exams e ON e.id = p.exam_id
				WHERE e.exam_type = 'WRITTEN' AND p.status = 'SUBMITTED' AND p.evaluated_at IS NULL AND p.id::text LIKE ?
				""", Integer.class, DEMO_PARTICIPANT)).isEqualTo(4);
	}

	@Test
	void aDatabaseSeededBeforeTheTopUpGetsItWithoutLosingTheBaseData() {
		seed.seed(new String[] { "dev" }, env(), NOW);
		// Make the base marking visible work: the instructor has since marked one paper, which a rerun must not undo.
		jdbc.update("UPDATE live_exam_participants SET score = 41, evaluated_at = now() WHERE id = ?", DemoContentSeed.demo('7', 4));
		removeTopUp();
		assertThat(count("live_exam_modules", DEMO_MODULE)).isEqualTo(6);
		assertThat(count("live_exam_orders", DEMO_ORDER)).isEqualTo(4);

		seed.seed(new String[] { "dev" }, env(), NOW.plus(Duration.ofHours(5)));

		assertCatalog();
		assertThat(jdbc.queryForObject("SELECT score FROM live_exam_participants WHERE id = ?", Integer.class, DemoContentSeed.demo('7', 4)))
			.isEqualTo(41);
		// The base exams keep their dates; only the top-up is dated from the later run.
		assertThat(jdbc.queryForObject("SELECT (live_at AT TIME ZONE 'Asia/Dhaka')::date::text FROM live_exams WHERE id = ?", String.class,
				DemoContentSeed.demo('e', 20))).isEqualTo("2026-10-28");
	}

	private int orderCount(String status) {
		return jdbc.queryForObject("SELECT count(*) FROM live_exam_orders WHERE id::text LIKE ? AND status = ?", Integer.class,
				DEMO_ORDER, status);
	}

	private int examCountIn(UUID module) {
		return jdbc.queryForObject("SELECT count(*) FROM live_exams WHERE module_id = ?", Integer.class, module);
	}

	private Map<String, Object> draft(int number) {
		return jdbc.queryForMap("""
				SELECT e.finally_saved, e.live_at, e.module_id AS module, e.question_stored_file_name IS NOT NULL AS has_paper,
				       (SELECT count(*) FROM live_exam_questions q WHERE q.exam_id = e.id) AS questions
				FROM live_exams e WHERE e.id = ? AND e.status = 'DRAFT'
				""", DemoContentSeed.demo('e', number));
	}

	/** Puts the database back to what an earlier version of the seed left: the base data without the top-up. */
	private void removeTopUp() {
		UUID[] exams = { DemoContentSeed.demo('e', 40), DemoContentSeed.demo('e', 41), DemoContentSeed.demo('e', 50),
				DemoContentSeed.demo('e', 51), DemoContentSeed.demo('e', 52), DemoContentSeed.demo('e', 53) };
		jdbc.update("DELETE FROM live_exam_participants WHERE id IN (?, ?)", DemoContentSeed.demo('7', 7), DemoContentSeed.demo('7', 8));
		jdbc.update("DELETE FROM live_exam_grants WHERE id IN (?, ?)", DemoContentSeed.demo('d', 5), DemoContentSeed.demo('d', 6));
		for (int order = 5; order <= 15; order++) {
			jdbc.update("DELETE FROM live_exam_orders WHERE id = ?", DemoContentSeed.demo('c', order));
		}
		for (UUID exam : exams) {
			jdbc.update("DELETE FROM live_exam_options WHERE question_id IN (SELECT id FROM live_exam_questions WHERE exam_id = ?)", exam);
			jdbc.update("DELETE FROM live_exam_questions WHERE exam_id = ?", exam);
			jdbc.update("UPDATE live_exams SET question_paper_id = NULL WHERE id = ?", exam);
			jdbc.update("DELETE FROM live_exam_question_papers WHERE exam_id = ?", exam);
			jdbc.update("DELETE FROM live_exam_package_exams WHERE exam_id = ?", exam);
			jdbc.update("DELETE FROM live_exams WHERE id = ?", exam);
		}
		jdbc.update("DELETE FROM live_exam_modules WHERE id IN (?, ?)", DemoContentSeed.MODULE_DRAFT_READY,
				DemoContentSeed.MODULE_DRAFT_EMPTY);
	}

	@Test
	void theNoticesAndTheAnnouncementAreWrittenOncePerStudent() {
		seed.seed(new String[] { "dev" }, env(), NOW);
		assertThat(noticeOf(DemoContentSeed.STUDENT_A, "PURCHASE_GRANTED")).isEqualTo(2);
		assertThat(noticeOf(DemoContentSeed.STUDENT_A, "MCQ_SCORE")).isEqualTo(1);
		assertThat(noticeOf(DemoContentSeed.STUDENT_A, "WRITTEN_RESULT")).isEqualTo(1);
		assertThat(noticeOf(DemoContentSeed.STUDENT_B, "PURCHASE_GRANTED")).isEqualTo(2);
		assertThat(noticeOf(DemoContentSeed.STUDENT_C, "PURCHASE_GRANTED")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM announcements WHERE id = ?", Integer.class, DemoContentSeed.ANNOUNCEMENT))
			.isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM student_notices WHERE announcement_id = ?", Integer.class,
				DemoContentSeed.ANNOUNCEMENT)).isEqualTo(4);
	}

	@Test
	void theCleanupRemovesDemoRowsAndWhatTheReviewAddedAndKeepsARealOne() throws Exception {
		seed.seed(new String[] { "dev" }, env(), NOW);
		// What the review adds on demo rows: C sits a seeded exam, and gets a manual grant on another package.
		jdbc.update("""
				INSERT INTO live_exam_participants (id, exam_id, student_id, kind, started_at, deadline_at, status, created_at, updated_at)
				VALUES (?, ?, ?, 'LIVE', now(), now(), 'IN_PROGRESS', now(), now())
				""", UUID.randomUUID(), DemoContentSeed.demo('e', 31), DemoContentSeed.STUDENT_C);
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
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_participants WHERE student_id::text LIKE ?", Integer.class,
				DEMO_STUDENT)).isZero();
		assertThat(count("live_exam_participants", DEMO_PARTICIPANT)).isZero();
		assertThat(count("users", DEMO_STUDENT)).isZero();
		assertThat(count("users", DEMO_INSTRUCTOR)).isZero();
		assertThat(count("users", DEMO_ADMIN)).isZero();
		assertThat(count("live_exam_orders", DEMO_ORDER)).isZero();
		assertThat(count("live_exam_grants", DEMO_GRANT)).isZero();
		assertThat(count("announcements", DEMO_ANNOUNCEMENT)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_orders WHERE tran_id LIKE 'DEMO-%'", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM student_notices WHERE user_id::text LIKE ?", Integer.class, DEMO_STUDENT))
			.isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_instructors WHERE module_id::text LIKE ?", Integer.class, DEMO_MODULE))
			.isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_categories WHERE id = ?", Integer.class, keep)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, realStudent)).isEqualTo(1);
		jdbc.update("DELETE FROM live_exam_categories WHERE id = ?", keep);
	}

	@Test
	void theCleanupScriptIsReadableFromTheApiFolder() throws Exception {
		assertThat(Files.readString(Path.of(CLEANUP))).contains("live_exam_participants", "users", "announcements", "live_exam_orders");
	}

	private void assertCatalog() {
		assertThat(count("live_exam_categories", DEMO_CATEGORY)).isEqualTo(6);
		assertThat(count("live_exam_modules", DEMO_MODULE)).isEqualTo(8);
		assertThat(count("live_exams", DEMO_EXAM)).isEqualTo(32);
		// Seventeen MCQ exams of ten questions (fifteen published, two drafts), four options each.
		assertThat(count("live_exam_questions", DEMO_QUESTION)).isEqualTo(170);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_options WHERE question_id::text LIKE ?", Integer.class,
				DEMO_QUESTION)).isEqualTo(680);
		assertThat(count("live_exam_participants", DEMO_PARTICIPANT)).isEqualTo(8);
		assertThat(count("users", DEMO_STUDENT)).isEqualTo(4);
		assertThat(count("live_exam_orders", DEMO_ORDER)).isEqualTo(15);
		assertThat(count("live_exam_grants", DEMO_GRANT)).isEqualTo(7);
	}

	/** Published exams only: the draft exams are counted by their own test. */
	private int examCount(UUID module, String type) {
		return jdbc.queryForObject("SELECT count(*) FROM live_exams WHERE module_id = ? AND exam_type = ? AND status = 'PUBLISHED'",
				Integer.class, module, type);
	}

	private int grantCount(UUID student, UUID exam, UUID module) {
		return jdbc.queryForObject("""
				SELECT count(*) FROM live_exam_grants WHERE student_id = ? AND revoked_at IS NULL
				  AND (CAST(? AS uuid) IS NULL OR exam_id = CAST(? AS uuid))
				  AND (CAST(? AS uuid) IS NULL OR module_id = CAST(? AS uuid))
				""", Integer.class, student, exam, exam, module, module);
	}

	private int noticeOf(UUID student, String kind) {
		return jdbc.queryForObject("SELECT count(*) FROM student_notices WHERE user_id = ? AND kind = ?", Integer.class, student, kind);
	}

	private int noticeCount() {
		return jdbc.queryForObject("SELECT count(*) FROM student_notices WHERE user_id::text LIKE ?", Integer.class, DEMO_STUDENT);
	}

	private int count(String table, String idPattern) {
		return jdbc.queryForObject("select count(*) from " + table + " where id::text like ?", Integer.class, idPattern);
	}

	private Function<String, String> env() {
		Map<String, String> values = Map.of("SEED_DEMO_PASSWORD", TEST_PASSWORD);
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
