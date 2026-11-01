package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.AuthService;
import com.coachingcenter.api.auth.CapturingEmailConfig;
import com.coachingcenter.api.auth.ClientType;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The sample seed (plan section 10). After a run on a fixed day it checks the counts and states of sections 5 and 6, the accounts of
 * section 4, unique ids and the refusals, and that a second run changes nothing. Then it calls the real endpoints: the catalog and its
 * empty category, Student 1's results, the leaderboard, the review averages, the pending orders, each teacher's marking queue, each
 * announcement's audience, and the empty lists of Student 6. The run day is 1 November 2026 in Dhaka.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, SampleSeedIT.ClockConfig.class })
class SampleSeedIT extends PostgresIntegrationTest {

	/** 1 November 2026, 12:00 in Dhaka. */
	private static final Instant NOW = Instant.parse("2026-11-01T06:00:00Z");

	private static final String CLEANUP = ".cursor/progress/ui-delivery/demo-cleanup.sql";

	/** The sample password for the test only; the seed reads it from its environment, as the real run does. */
	private static final String PASSWORD = "sample-pass-1234";

	private static final String MASTER_PHONE = "+8801700000001";

	private static final String MASTER_PASSWORD = "bootstrap-password-1";

	private static final String CATEGORY = "a0000000-0000-4000-8000-%";

	private static final String MODULE = "b0000000-0000-4000-8000-%";

	private static final String MEMBERSHIP = "10000000-0000-4000-8000-%";

	private static final String EXAM = "e0000000-0000-4000-8000-%";

	private static final String QUESTION = "f0000000-0000-4000-8000-%";

	private static final String OPTION = "90000000-0000-4000-8000-%";

	private static final String PAPER = "20000000-0000-4000-8000-%";

	private static final String SITTING = "70000000-0000-4000-8000-%";

	private static final String ORDER = "c0000000-0000-4000-8000-%";

	private static final String GRANT = "d0000000-0000-4000-8000-%";

	private static final String RETAKE = "00000000-0000-4000-8000-%";

	private static final String ANNOUNCEMENT = "80000000-0000-4000-8000-%";

	private static final String REVIEW = "30000000-0000-4000-8000-%";

	private static final String FAVOURITE = "10000000-0000-4000-8000-%";

	private static final String STUDENT = "50000000-0000-4000-8000-%";

	private static final String TEACHER = "60000000-0000-4000-8000-%";

	private static final String STAFF = "40000000-0000-4000-8000-%";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private SampleSeed seed;

	@Autowired
	private AuthService auth;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private MutableClock clock;

	private JdbcTemplate jdbc;

	@BeforeEach
	void cleanFirst() throws Exception {
		clock.set(NOW);
		jdbc = new JdbcTemplate(dataSource);
		cleanup();
	}

	@AfterEach
	void cleanAfter() throws Exception {
		cleanup();
	}

	@Test
	void theSeedRefusesProdAnyProfileThatIsNotStagingOrDevAndAMissingPassword() {
		assertThatThrownBy(() -> seed.seed(new String[] { "prod", "staging" }, env(), NOW))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("prod");
		assertThatThrownBy(() -> seed.seed(new String[] { "test" }, env(), NOW))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("staging or dev");
		assertThatThrownBy(() -> seed.seed(new String[] { "dev" }, key -> null, NOW))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("SEED_SAMPLE_PASSWORD is required.");
		assertThat(count("live_exam_categories", CATEGORY)).isZero();
		assertThat(count("users", STUDENT)).isZero();
	}

	@Test
	void aDatabaseWithRowsOfAnotherSeedIsRefusedAndNothingIsWritten() {
		// A row with the sample's id for category 1 but other content: an older seed's row, so the sample refuses.
		jdbc.update("""
				INSERT INTO live_exam_categories (id, title_en, title_bn, position, created_at, updated_at)
				VALUES (?, 'Other seed', 'অন্য', 0, now(), now())
				""", SampleSeed.id('a', 1));
		assertThatThrownBy(() -> seed.seed(new String[] { "dev" }, env(), NOW))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("another seed");
		assertThat(count("users", STUDENT)).isZero();
		assertThat(count("live_exams", EXAM)).isZero();
	}

	@Test
	void theLoadedCatalogAndExamStatesMatchSections5() {
		seed.seed(new String[] { "dev" }, env(), NOW);
		assertCounts();
		assertThat(count("live_exams", EXAM)).isEqualTo(20);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exams WHERE id::text LIKE ? AND exam_type = 'MCQ'", Integer.class,
				EXAM)).isEqualTo(14);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exams WHERE id::text LIKE ? AND exam_type = 'WRITTEN'", Integer.class,
				EXAM)).isEqualTo(6);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exams WHERE id::text LIKE ? AND status = 'PUBLISHED'", Integer.class,
				EXAM)).isEqualTo(19);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exams WHERE id::text LIKE ? AND status = 'DRAFT'", Integer.class,
				EXAM)).isEqualTo(1);
		// Categories: two roots, and the empty Short Courses category under Bar Council Enrolment.
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_categories WHERE id::text LIKE ? AND parent_id IS NULL",
				Integer.class, CATEGORY)).isEqualTo(2);
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM live_exam_categories c WHERE c.id::text LIKE ? AND c.title_en = 'Short Courses'
				  AND NOT EXISTS (SELECT 1 FROM live_exams e WHERE e.category_id = c.id)
				""", Integer.class, CATEGORY)).isEqualTo(1);
		// Every state of the table is present.
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exams WHERE id = ? AND ends_at < ?", Integer.class,
				SampleSeed.id('e', 1), ts(NOW))).as("exam 1 is finished").isEqualTo(1);
		for (int number : new int[] { 2, 5, 9, 11, 15 }) {
			assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exams WHERE id = ? AND live_at < ? AND ends_at > ?", Integer.class,
					SampleSeed.id('e', number), ts(NOW), ts(NOW))).as("exam %d is open now", number).isEqualTo(1);
		}
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exams WHERE id = ? AND live_at > ?", Integer.class,
				SampleSeed.id('e', 3), ts(NOW))).as("exam 3 is upcoming").isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT live_at IS NULL FROM live_exams WHERE id = ?", Boolean.class, SampleSeed.id('e', 18)))
			.as("exam 18 has no date").isTrue();
		assertThat(jdbc.queryForObject("SELECT mode FROM live_exams WHERE id = ?", String.class, SampleSeed.id('e', 19)))
			.as("exam 19 is practice").isEqualTo("PRACTICE");
		// Free, discounted, popular, preview, negative marking and access days.
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exams WHERE id::text LIKE ? AND is_free", Integer.class, EXAM))
			.isEqualTo(5);
		assertThat(jdbc.queryForObject("SELECT discount_percent = 25 FROM live_exams WHERE id = ?", Boolean.class,
				SampleSeed.id('e', 6))).isTrue();
		assertThat(jdbc.queryForObject("SELECT popular FROM live_exams WHERE id = ?", Boolean.class, SampleSeed.id('e', 10))).isTrue();
		assertThat(jdbc.queryForObject("SELECT popular FROM live_exam_modules WHERE id = ?", Boolean.class, SampleSeed.id('b', 2)))
			.isTrue();
		assertThat(jdbc.queryForObject("SELECT discount_percent = 25 FROM live_exam_modules WHERE id = ?", Boolean.class,
				SampleSeed.id('b', 1))).isTrue();
		assertThat(jdbc.queryForObject("SELECT preview_enabled FROM live_exams WHERE id = ?", Boolean.class, SampleSeed.id('e', 7)))
			.isTrue();
		assertThat(jdbc.queryForObject("SELECT negative_marks FROM live_exams WHERE id = ?", BigDecimal.class, SampleSeed.id('e', 2)))
			.isEqualByComparingTo("0.25");
		assertThat(jdbc.queryForObject("SELECT access_days FROM live_exams WHERE id = ?", Integer.class, SampleSeed.id('e', 17)))
			.isEqualTo(30);
		// Exam 12 sits in two packages; the question bank holds twelve items.
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_package_exams WHERE exam_id = ?", Integer.class,
				SampleSeed.id('e', 12))).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM question_bank_items WHERE bank_id = ?", Integer.class,
				SampleSeed.id('f', 9000))).isEqualTo(12);
		// Every MCQ paper has ten questions of five marks.
		assertThat(jdbc.queryForList("""
				SELECT e.id FROM live_exams e WHERE e.id::text LIKE ? AND e.exam_type = 'MCQ'
				  AND (SELECT count(*) FROM live_exam_questions q WHERE q.exam_id = e.id) <> 10
				""", UUID.class, EXAM)).isEmpty();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_questions WHERE id::text LIKE ? AND marks <> 5", Integer.class,
				QUESTION)).isZero();
		assertThat(count("live_exam_question_papers", PAPER)).isEqualTo(6);
		// Sittings: twenty-two, one failed to submit, none left in progress.
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_participants WHERE id::text LIKE ? AND status = 'FAILED_TO_SUBMIT'",
				Integer.class, SITTING)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_participants WHERE id::text LIKE ? AND status = 'IN_PROGRESS'",
				Integer.class, SITTING)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_answers WHERE participant_id::text LIKE ?", Integer.class,
				SITTING)).isEqualTo(145);
	}

	@Test
	void theSittingsOrdersGrantsReviewsAndNoticesMatchSections6() {
		seed.seed(new String[] { "dev" }, env(), NOW);
		// Exam 1: the fixed scores, and the pass line of 20 out of 50.
		assertThat(score(1, 1)).isEqualByComparingTo("40");
		assertThat(score(1, 2)).isEqualByComparingTo("15");
		assertThat(passed(1, 2)).isFalse();
		assertThat(passed(1, 3)).isTrue();
		// Exam 2 has negative marking: seven right and three wrong is 35 less 0.75.
		assertThat(score(2, 2)).isEqualByComparingTo("34.25");
		assertThat(score(2, 4)).isEqualByComparingTo("14.5");
		// Exam 12 (written): a published pass and a published fail, and one who failed to submit with no upload.
		assertThat(jdbc.queryForObject("SELECT published_at IS NOT NULL AND score = 32 FROM live_exam_participants WHERE id = ?",
				Boolean.class, SampleSeed.id('7', 16))).isTrue();
		assertThat(jdbc.queryForObject("SELECT status = 'FAILED_TO_SUBMIT' AND submitted_at IS NULL FROM live_exam_participants WHERE id = ?",
				Boolean.class, SampleSeed.id('7', 18))).isTrue();
		// Exam 13: marked but not released.
		assertThat(jdbc.queryForObject("SELECT evaluated_at IS NOT NULL AND published_at IS NULL FROM live_exam_participants WHERE id = ?",
				Boolean.class, SampleSeed.id('7', 19))).isTrue();
		// Three submitted sittings wait for marking: exam 13 (Student 3), exam 14 (Student 1) and exam 17 (Student 2).
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM live_exam_participants WHERE id::text LIKE ? AND status = 'SUBMITTED' AND evaluated_at IS NULL
				  AND exam_id IN (?, ?, ?)
				""", Integer.class, SITTING, SampleSeed.id('e', 13), SampleSeed.id('e', 14), SampleSeed.id('e', 17))).isEqualTo(3);
		// Orders: five paid, one failed, one pending, one cancelled; receipts for the paid ones only.
		assertThat(orders("SUCCEEDED")).isEqualTo(5);
		assertThat(orders("FAILED")).isEqualTo(1);
		assertThat(orders("INITIATED")).isEqualTo(1);
		assertThat(orders("CANCELLED")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_orders WHERE id::text LIKE ? AND receipt_no IS NOT NULL",
				Integer.class, ORDER)).isEqualTo(5);
		// Grants: five from paid orders and two manual; the retake is separate and unused.
		assertThat(count("live_exam_grants", GRANT)).isEqualTo(7);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_grants WHERE id::text LIKE ? AND source = 'MANUAL'", Integer.class,
				GRANT)).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_retake_grants WHERE id::text LIKE ? AND used_participant_id IS NULL",
				Integer.class, RETAKE)).isEqualTo(1);
		// Reviews: four approved, two waiting, one hidden; the two featured ones are approved with a comment.
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_reviews WHERE id::text LIKE ? AND status = 'APPROVED'",
				Integer.class, REVIEW)).isEqualTo(4);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_reviews WHERE id::text LIKE ? AND status = 'PENDING'",
				Integer.class, REVIEW)).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_reviews WHERE id::text LIKE ? AND status = 'HIDDEN'",
				Integer.class, REVIEW)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exam_reviews WHERE id::text LIKE ? AND featured", Integer.class,
				REVIEW)).isEqualTo(2);
		// Announcements: six, one withdrawn. Notices: 7 purchases, 15 MCQ scores, 2 published results, 1 retake, 14 announcement rows.
		assertThat(count("announcements", ANNOUNCEMENT)).isEqualTo(6);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM announcements WHERE id::text LIKE ? AND withdrawn_at IS NOT NULL",
				Integer.class, ANNOUNCEMENT)).isEqualTo(1);
		assertThat(noticeCount("%")).isEqualTo(39);
		assertThat(noticeCount("MCQ_SCORE")).isEqualTo(15);
		assertThat(noticeCount("WRITTEN_RESULT")).isEqualTo(2);
		assertThat(noticeCount("RETAKE_AVAILABLE")).isOne();
		assertThat(noticeCount("ANNOUNCEMENT")).isEqualTo(14);
		assertThat(noticeCount("PURCHASE_GRANTED")).isEqualTo(7);
		// Instructors: three exams each, assigned on the exam itself.
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exams WHERE id::text LIKE ? AND instructor_id = ?", Integer.class,
				EXAM, SampleSeed.id('6', 1))).isEqualTo(3);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM live_exams WHERE id::text LIKE ? AND instructor_id = ?", Integer.class,
				EXAM, SampleSeed.id('6', 2))).isEqualTo(3);
		// The wish list: Student 1 has exam 2 and package 2; Student 5 has exam 11.
		assertThat(count("student_favourites", FAVOURITE)).isEqualTo(3);
	}

	@Test
	void theAccountsAreExactlyThePlansAndEverySampleAccountSignsInByPhoneAndEmail() {
		int mastersBefore = jdbc.queryForObject("SELECT count(*) FROM users WHERE role = 'MASTER_ADMIN'", Integer.class);
		seed.seed(new String[] { "dev" }, env(), NOW);
		assertThat(count("users", STUDENT)).isEqualTo(6);
		assertThat(count("users", TEACHER)).isEqualTo(2);
		assertThat(count("users", STAFF)).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE role = 'MASTER_ADMIN'", Integer.class))
			.as("the seed does not create the Master Admin").isEqualTo(mastersBefore);
		for (DemoContentSeed.Account account : SampleSeed.ACCOUNTS) {
			assertThat(jdbc.queryForObject("SELECT role || ':' || status FROM users WHERE id = ?", String.class, account.id()))
				.isEqualTo(account.role() + ":ACTIVE");
			assertThat(auth.login(account.phone(), PASSWORD, ClientType.WEB, "127.0.0.1", "test").accessToken()).isNotBlank();
			assertThat(auth.login(account.email(), PASSWORD, ClientType.WEB, "127.0.0.1", "test").accessToken()).isNotBlank();
		}
		assertThat(jdbc.queryForObject("SELECT count(*) FROM student_profiles WHERE user_id::text LIKE ? AND student_code IS NOT NULL",
				Integer.class, STUDENT)).isEqualTo(6);
	}

	@Test
	void aSecondRunChangesNothing() {
		seed.seed(new String[] { "dev" }, env(), NOW);
		int notices = noticeCount("%");
		int sittings = count("live_exam_participants", SITTING);
		Instant liveAt = jdbc.queryForObject("SELECT live_at FROM live_exams WHERE id = ?", Timestamp.class, SampleSeed.id('e', 3))
			.toInstant();

		seed.seed(new String[] { "dev" }, env(), NOW.plusSeconds(3 * 86_400));

		assertCounts();
		assertThat(noticeCount("%")).isEqualTo(notices);
		assertThat(count("live_exam_participants", SITTING)).isEqualTo(sittings);
		assertThat(jdbc.queryForObject("SELECT live_at FROM live_exams WHERE id = ?", Timestamp.class, SampleSeed.id('e', 3))
			.toInstant()).isEqualTo(liveAt);
	}

	@Test
	void theEndpointsShowTheCatalogTheResultsTheReviewsAndTheInboxes() throws Exception {
		seed.seed(new String[] { "dev" }, env(), NOW);
		String master = token(MASTER_PHONE, MASTER_PASSWORD);
		String student1 = token("+8801711000011", PASSWORD);
		String student2 = token("+8801711000012", PASSWORD);
		String student6 = token("+8801711000016", PASSWORD);
		String teacher1 = token("+8801711000003", PASSWORD);
		String teacher2 = token("+8801711000004", PASSWORD);

		// The public category tree: the seed's two roots (other tests share this database, so only the seed's ids count), and
		// the Bar Council branch holds the empty Short Courses category.
		JsonNode tree = read(send(get("/api/v1/public/live-exam-categories"), null).andExpect(status().isOk()));
		JsonNode bar = null;
		int seedRoots = 0;
		for (JsonNode root : tree) {
			String id = root.get("id").asText();
			if (id.equals(SampleSeed.id('a', 1).toString()) || id.equals(SampleSeed.id('a', 4).toString())) {
				seedRoots++;
			}
			if (id.equals(SampleSeed.id('a', 4).toString())) {
				bar = root;
			}
		}
		assertThat(seedRoots).isEqualTo(2);
		assertThat(bar).isNotNull();
		JsonNode shortCourses = null;
		for (JsonNode child : bar.get("children")) {
			if ("Short Courses".equals(child.get("titleEn").asText())) {
				shortCourses = child;
			}
		}
		assertThat(shortCourses).isNotNull();
		assertThat(shortCourses.get("exams").size()).isZero();
		// The popular filter shows package 2.
		JsonNode popular = read(send(get("/api/v1/public/live-exam-modules").param("popular", "true").param("size", "50"), null)
			.andExpect(status().isOk()));
		assertThat(popular.get("content").toString()).contains(SampleSeed.id('b', 2).toString());

		// Student 1's results: five rows, with a published result and one still waiting for marking.
		JsonNode results = read(send(get("/api/v1/me/exam-results").param("size", "50"), student1).andExpect(status().isOk()));
		assertThat(results.get("totalElements").asInt()).isEqualTo(5);
		Set<String> states = new HashSet<>();
		for (JsonNode row : results.get("content")) {
			states.add(row.get("status").asText());
		}
		assertThat(states).contains("RESULT_READY", "AWAITING_RESULT");

		// The board lists the passers of exam 1 (three); the two below the pass mark are counted for staff in belowPass.
		JsonNode board = read(send(get("/api/v1/live-exams/" + SampleSeed.id('e', 1) + "/leaderboard"), master)
			.andExpect(status().isOk()));
		assertThat(board.get("rows").size()).isEqualTo(3);
		assertThat(board.get("belowPass").asInt()).isEqualTo(2);

		// Review averages come from three counted ratings, with the hidden one left out. The public page shows approved comments only.
		JsonNode examReviews = read(send(get("/api/v1/public/reviews/exam/" + SampleSeed.id('e', 1)), null)
			.andExpect(status().isOk()));
		assertThat(examReviews.get("ratingAverage").asDouble()).isEqualTo(4.7);
		assertThat(examReviews.get("ratingCount").asInt()).isEqualTo(3);
		assertThat(examReviews.get("page").get("totalElements").asInt()).isEqualTo(1);
		assertThat(examReviews.toString()).doesNotContain("Too many questions on one act.");
		JsonNode packageReviews = read(send(get("/api/v1/public/reviews/package/" + SampleSeed.id('b', 2)), null)
			.andExpect(status().isOk()));
		assertThat(packageReviews.get("ratingAverage").asDouble()).isEqualTo(4.7);
		assertThat(packageReviews.get("ratingCount").asInt()).isEqualTo(3);

		// The pending orders queue holds Student 6's package 3 payment. Other tests share this database, so the queue is narrowed to
		// Student 6 (by email) and the seed's own order id is required.
		JsonNode pending = read(send(get("/api/v1/live-exam-orders").param("status", "INITIATED").param("q", "student6@example.com"),
				master).andExpect(status().isOk()));
		assertThat(pending.get("totalElements").asInt()).isEqualTo(1);
		assertThat(pending.get("content").toString()).contains(SampleSeed.id('c', 6).toString());

		// Each teacher's marking queue: teacher 1 has one script awaiting (exam 13), teacher 2 has two (exams 14 and 17).
		assertThat(read(send(get("/api/v1/live-exam-submissions").param("status", "AWAITING"), teacher1)
			.andExpect(status().isOk())).get("totalElements").asInt()).isEqualTo(1);
		assertThat(read(send(get("/api/v1/live-exam-submissions").param("status", "AWAITING"), teacher2)
			.andExpect(status().isOk())).get("totalElements").asInt()).isEqualTo(2);

		// Each announcement reaches the right students: the notices its send wrote, one per recipient.
		// ALL reaches every active student in the database, which may include rows of other tests: the set is compared with the rule.
		Set<UUID> active = Set.copyOf(jdbc.queryForList("SELECT id FROM users WHERE role = 'STUDENT' AND status = 'ACTIVE'"
			+ " AND deleted_at IS NULL", UUID.class));
		assertThat(recipients(announcementId("audience = 'ALL'"))).isEqualTo(active)
			.contains(id5(1), id5(2), id5(3), id5(4), id5(5), id5(6));
		assertThat(recipients(announcementId("module_id = '" + SampleSeed.id('b', 2) + "'"))).containsExactlyInAnyOrder(id5(1), id5(4),
				id5(5));
		assertThat(recipients(announcementId("exam_id = '" + SampleSeed.id('e', 17) + "'"))).containsExactlyInAnyOrder(id5(2));
		assertThat(recipients(announcementId("audience = 'SELECTED'"))).containsExactlyInAnyOrder(id5(1), id5(2));
		assertThat(recipients(announcementId("exam_id = '" + SampleSeed.id('e', 11) + "'"))).containsExactlyInAnyOrder(id5(5));
		assertThat(recipients(announcementId("module_id = '" + SampleSeed.id('b', 1) + "' AND withdrawn_at IS NOT NULL")))
			.containsExactlyInAnyOrder(id5(3));
		// The package page shows the announcement to a holder only (Student 1 holds package 2, Student 2 does not).
		JsonNode onPackage = read(send(get("/api/v1/me/announcements").param("kind", "package")
			.param("id", SampleSeed.id('b', 2).toString()), student1).andExpect(status().isOk()));
		assertThat(onPackage.size()).isEqualTo(1);
		JsonNode notPackage = read(send(get("/api/v1/me/announcements").param("kind", "package")
			.param("id", SampleSeed.id('b', 2).toString()), student2).andExpect(status().isOk()));
		assertThat(notPackage.size()).isZero();

		// Student 6 sees the empty states: no results, and only the welcome announcement in the inbox.
		JsonNode empty = read(send(get("/api/v1/me/exam-results").param("size", "50"), student6).andExpect(status().isOk()));
		assertThat(empty.get("totalElements").asInt()).isZero();
		JsonNode inbox = read(send(get("/api/v1/me/notices").param("group", "ANNOUNCEMENTS").param("size", "50"), student6)
			.andExpect(status().isOk()));
		assertThat(inbox.get("totalElements").asInt()).isEqualTo(1);
	}

	private void assertCounts() {
		assertThat(count("live_exam_categories", CATEGORY)).isEqualTo(8);
		assertThat(count("live_exam_modules", MODULE)).isEqualTo(3);
		assertThat(count("live_exams", EXAM)).isEqualTo(20);
		assertThat(count("live_exam_package_exams", MEMBERSHIP)).isEqualTo(11);
		assertThat(count("live_exam_participants", SITTING)).isEqualTo(22);
		assertThat(count("live_exam_orders", ORDER)).isEqualTo(8);
		assertThat(count("live_exam_grants", GRANT)).isEqualTo(7);
		assertThat(count("live_exam_reviews", REVIEW)).isEqualTo(7);
		assertThat(count("announcements", ANNOUNCEMENT)).isEqualTo(6);
		assertThat(count("question_banks", QUESTION)).isEqualTo(1);
		assertThat(count("questions", QUESTION)).isEqualTo(12);
		assertThat(count("live_exam_questions", QUESTION)).isEqualTo(140);
		assertThat(count("live_exam_options", OPTION)).isEqualTo(560);
		assertThat(count("users", STUDENT)).isEqualTo(6);
	}

	/** The one announcement that matches a condition on the announcements table. */
	private UUID announcementId(String condition) {
		return jdbc.queryForObject("SELECT id FROM announcements WHERE id::text LIKE ? AND " + condition, UUID.class, ANNOUNCEMENT);
	}

	/** The students an announcement's notices went to: one row per recipient. */
	private Set<UUID> recipients(UUID announcement) {
		return Set.copyOf(jdbc.queryForList("SELECT user_id FROM student_notices WHERE announcement_id = ?", UUID.class, announcement));
	}

	private int orders(String status) {
		return jdbc.queryForObject("SELECT count(*) FROM live_exam_orders WHERE id::text LIKE ? AND status = ?", Integer.class, ORDER,
				status);
	}

	private int noticeCount(String kind) {
		if ("%".equals(kind)) {
			return jdbc.queryForObject("SELECT count(*) FROM student_notices WHERE user_id::text LIKE ?", Integer.class, STUDENT);
		}
		return jdbc.queryForObject("SELECT count(*) FROM student_notices WHERE user_id::text LIKE ? AND kind = ?", Integer.class,
				STUDENT, kind);
	}

	private BigDecimal score(int exam, int student) {
		return jdbc.queryForObject("SELECT score FROM live_exam_participants WHERE exam_id = ? AND student_id = ?", BigDecimal.class,
				SampleSeed.id('e', exam), SampleSeed.id('5', student));
	}

	private boolean passed(int exam, int student) {
		return jdbc.queryForObject("SELECT passed FROM live_exam_participants WHERE exam_id = ? AND student_id = ?", Boolean.class,
				SampleSeed.id('e', exam), SampleSeed.id('5', student));
	}

	private int count(String table, String idPattern) {
		return jdbc.queryForObject("select count(*) from " + table + " where id::text like ?", Integer.class, idPattern);
	}

	private String token(String phone, String password) {
		return auth.login(phone, password, ClientType.WEB, "127.0.0.1", "test").accessToken();
	}

	private ResultActions send(MockHttpServletRequestBuilder request, String token) throws Exception {
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return mockMvc.perform(request);
	}

	private JsonNode read(ResultActions actions) throws Exception {
		return json.readTree(actions.andReturn().getResponse().getContentAsString());
	}

	private UUID id5(int n) {
		return SampleSeed.id('5', n);
	}

	private static Timestamp ts(Instant instant) {
		return Timestamp.from(instant);
	}

	private Function<String, String> env() {
		Map<String, String> values = Map.of("SEED_SAMPLE_PASSWORD", PASSWORD);
		return values::get;
	}

	/** Clears the sample's rows (the reviews and the bank first), then runs the shared demo cleanup for the rest of the ids. */
	private void cleanup() throws Exception {
		jdbc.update("DELETE FROM live_exam_reviews WHERE exam_id::text LIKE ? OR module_id::text LIKE ? OR student_id::text LIKE ?",
				EXAM, MODULE, STUDENT);
		jdbc.update("DELETE FROM question_bank_items WHERE bank_id::text LIKE ? OR question_id::text LIKE ?", QUESTION, QUESTION);
		jdbc.update("DELETE FROM question_banks WHERE id::text LIKE ?", QUESTION);
		jdbc.update("DELETE FROM question_options WHERE question_id::text LIKE ?", QUESTION);
		jdbc.update("DELETE FROM questions WHERE id::text LIKE ?", QUESTION);
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

	/** Pins the clock the endpoints and the notice writer read, to the run day. */
	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(NOW);
		}

	}

}
