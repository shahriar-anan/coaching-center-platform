package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;
import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.files.LocalFileStorage;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reviews of exams and packages (reviews plan, sections 4 and 6). A student reviews what they sat, or a package they hold
 * with a sitting of one of its exams. A comment waits for staff, stars count at once, the public sees approved comments and
 * an average from three ratings, and staff moderate. The window is 12:00 to 12:40 Dhaka (06:00 to 06:40 UTC).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, ExamReviewIT.ClockConfig.class })
class ExamReviewIT extends PostgresIntegrationTest {

	private static final Instant BEFORE = Instant.parse("2026-10-20T04:00:00Z");

	private static final Instant SIT_AT = Instant.parse("2026-10-20T06:10:00Z");

	private static final Instant SUBMIT_AT = Instant.parse("2026-10-20T06:12:00Z");

	private static final Instant ROSTER_SINCE = Instant.parse("2026-10-01T00:00:00Z");

	/** A featuring time later than every other test's clock, so a featured review sorts first. */
	private static final Instant FEATURE_AT = Instant.parse("2026-10-30T10:00:00Z");

	/** When the reviewer's picture was last changed. The URLs carry this time, in milliseconds. */
	private static final Instant PICTURE_UPDATED = Instant.parse("2026-10-21T08:00:00Z");

	private static final MediaType JSON = MediaType.APPLICATION_JSON;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private MutableClock clock;

	@Autowired
	private LocalFileStorage fileStorage;

	private AuthoringFixture fx;

	private PackageFixtures pf;

	@BeforeEach
	void setUp() {
		clock.set(BEFORE);
		fx = new AuthoringFixture(mockMvc, dataSource, passwords, json);
		pf = new PackageFixtures(mockMvc, json, dataSource, passwords);
	}

	@Test
	void aStudentReviewsAnExamOnlyAfterSittingIt() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Review exam category");
		UUID exam = standalone(staff, master, category, "Review exam");
		UUID student = fx.seed("STUDENT");
		UUID stranger = fx.seed("STUDENT");
		grant(master, student, exam);
		grant(master, stranger, exam);
		String token = fx.token(student);
		String strangerToken = fx.token(stranger);

		JsonNode before = read(send(get(me("exam", exam)), token).andExpect(status().isOk()));
		assertThat(before.get("canReview").asBoolean()).isFalse();
		assertThat(before.get("reviewed").asBoolean()).isFalse();
		assertThat(before.has("review")).isFalse();
		send(put(me("exam", exam)).contentType(JSON).content("{\"rating\":5}"), token)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("REVIEW_NOT_ALLOWED"));

		// The stranger has access to the exam but has never sat it.
		send(put(me("exam", exam)).contentType(JSON).content("{\"rating\":5}"), strangerToken)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("REVIEW_NOT_ALLOWED"));

		sitAndSubmit(token, exam);
		JsonNode eligible = read(send(get(me("exam", exam)), token).andExpect(status().isOk()));
		assertThat(eligible.get("canReview").asBoolean()).isTrue();
		assertThat(eligible.get("reviewed").asBoolean()).isFalse();

		JsonNode saved = rate(token, "exam", exam, "{\"rating\":4}");
		assertThat(saved.get("rating").asInt()).isEqualTo(4);
		assertThat(saved.get("status").asText()).isEqualTo("APPROVED");
		assertThat(saved.has("comment")).isTrue();
		assertThat(saved.get("comment").isNull()).isTrue();
		assertThat(saved.get("hideReason").isNull()).isTrue();
	}

	@Test
	void aPackageReviewNeedsTheHeldPackageAndASittingOfOneOfItsExams() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = pf.createCategory(staff, "Review package category");
		UUID pkg = pf.createPackage(staff, category, "Review package");
		UUID inside = pf.createExam(staff, pkg, "MCQ", "Review inside paper", AuthoringFixture.LIVE_AT);
		pf.addQuestion(staff, inside);
		pf.publishExam(staff, inside);
		pf.finalSaveAndPublish(staff, pkg);
		UUID outside = standalone(staff, master, category, "Review outside paper");

		UUID holder = fx.seed("STUDENT");
		UUID sitter = fx.seed("STUDENT");
		UUID sitterOutside = fx.seed("STUDENT");
		UUID examOnly = fx.seed("STUDENT");
		pf.markRoster(pkg, holder, ROSTER_SINCE);
		pf.markRoster(pkg, sitter, ROSTER_SINCE);
		pf.markRoster(pkg, sitterOutside, ROSTER_SINCE);
		grant(master, sitter, inside);
		grant(master, sitterOutside, outside);
		grant(master, examOnly, inside);
		String holderToken = fx.token(holder);
		String sitterToken = fx.token(sitter);
		String sitterOutsideToken = fx.token(sitterOutside);
		String examOnlyToken = fx.token(examOnly);

		sitAndSubmit(sitterToken, inside);
		sitAndSubmit(sitterOutsideToken, outside);
		sitAndSubmit(examOnlyToken, inside);

		// Holds the package but has sat nothing in it.
		send(put(me("package", pkg)).contentType(JSON).content("{\"rating\":5}"), holderToken)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("REVIEW_NOT_ALLOWED"));
		// Holds the package, but the only exam sat is outside it.
		send(put(me("package", pkg)).contentType(JSON).content("{\"rating\":5}"), sitterOutsideToken)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("REVIEW_NOT_ALLOWED"));
		// Sat an exam inside the package, but holds no package.
		send(put(me("package", pkg)).contentType(JSON).content("{\"rating\":5}"), examOnlyToken)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("REVIEW_NOT_ALLOWED"));
		JsonNode notHeld = read(send(get(me("package", pkg)), examOnlyToken).andExpect(status().isOk()));
		assertThat(notHeld.get("canReview").asBoolean()).isFalse();

		// Holds it and sat one of its exams: allowed. One rating is counted, and one is never an average.
		JsonNode eligible = read(send(get(me("package", pkg)), sitterToken).andExpect(status().isOk()));
		assertThat(eligible.get("canReview").asBoolean()).isTrue();
		JsonNode saved = rate(sitterToken, "package", pkg, "{\"rating\":5}");
		assertThat(saved.get("status").asText()).isEqualTo("APPROVED");
		JsonNode module = read(send(get("/api/v1/public/live-exam-modules/" + pkg), null).andExpect(status().isOk()));
		assertThat(module.get("ratingCount").asInt()).isEqualTo(1);
		assertThat(module.get("ratingAverage").isNull()).isTrue();
	}

	@Test
	void oneReviewPerTargetAndAPutReplacesIt() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Replace category");
		UUID exam = standalone(staff, master, category, "Replace exam");
		UUID student = fx.seed("STUDENT");
		grant(master, student, exam);
		String token = fx.token(student);
		sitAndSubmit(token, exam);

		JsonNode first = rate(token, "exam", exam, "{\"rating\":3}");
		JsonNode second = rate(token, "exam", exam, "{\"rating\":5}");
		assertThat(second.get("id").asText()).isEqualTo(first.get("id").asText());
		assertThat(second.get("rating").asInt()).isEqualTo(5);
		assertThat(fx.jdbc().queryForObject("select count(*) from live_exam_reviews where exam_id = ?", Integer.class, exam))
			.isEqualTo(1);

		send(delete(me("exam", exam)), token).andExpect(status().isNoContent());
		JsonNode gone = read(send(get(me("exam", exam)), token).andExpect(status().isOk()));
		assertThat(gone.get("reviewed").asBoolean()).isFalse();
		assertThat(gone.get("canReview").asBoolean()).isTrue();
		send(delete(me("exam", exam)), token).andExpect(status().isNoContent());

		// A target that does not exist, and a kind that is neither exam nor package, are not found.
		send(put(me("exam", UUID.randomUUID())).contentType(JSON).content("{\"rating\":4}"), token)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		send(put("/api/v1/me/reviews/course/" + exam).contentType(JSON).content("{\"rating\":4}"), token)
			.andExpect(status().isNotFound());
		send(get("/api/v1/me/reviews/course/" + exam), token).andExpect(status().isNotFound());
	}

	@Test
	void theBodyIsCheckedAndACommentIsTrimmed() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Body category");
		UUID exam = standalone(staff, master, category, "Body exam");
		UUID student = fx.seed("STUDENT");
		grant(master, student, exam);
		String token = fx.token(student);
		sitAndSubmit(token, exam);

		for (String body : new String[] { "{\"rating\":0}", "{\"rating\":6}", "{\"rating\":\"4\"}", "{\"rating\":3.5}",
				"{}", "{\"rating\":3,\"comment\":42}", "{\"rating\":3,\"comment\":\"bad\\u0007 text\"}",
				"{\"rating\":3,\"comment\":\"" + "x".repeat(1001) + "\"}" }) {
			send(put(me("exam", exam)).contentType(JSON).content(body), token)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").exists());
		}
		send(put(me("exam", exam)).contentType(JSON).content("{\"rating\":0}"), token)
			.andExpect(jsonPath("$.fieldErrors[0].field").value("rating"));

		// A blank comment is no comment: stars only, approved at once.
		JsonNode blank = rate(token, "exam", exam, "{\"rating\":3,\"comment\":\"   \"}");
		assertThat(blank.get("status").asText()).isEqualTo("APPROVED");
		assertThat(blank.get("comment").isNull()).isTrue();

		// A padded comment is saved trimmed, and waits for staff.
		JsonNode padded = rate(token, "exam", exam, "{\"rating\":3,\"comment\":\"  Clear and fair  \"}");
		assertThat(padded.get("comment").asText()).isEqualTo("Clear and fair");
		assertThat(padded.get("status").asText()).isEqualTo("PENDING");

		// Exactly 1000 characters is allowed.
		JsonNode longest = rate(token, "exam", exam, "{\"rating\":3,\"comment\":\"" + "y".repeat(1000) + "\"}");
		assertThat(longest.get("comment").asText()).hasSize(1000);
	}

	@Test
	void aCommentWaitsForApprovalAndAnEditSendsItBackToPending() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Moderation category");
		UUID exam = standalone(staff, master, category, "Moderation exam");
		UUID student = fx.seed("STUDENT");
		grant(master, student, exam);
		String token = fx.token(student);
		sitAndSubmit(token, exam);

		JsonNode written = rate(token, "exam", exam, "{\"rating\":4,\"comment\":\"Clear explanations\"}");
		assertThat(written.get("status").asText()).isEqualTo("PENDING");
		String review = written.get("id").asText();

		// Only the stars change: the comment is the same, so the status stays.
		assertThat(rate(token, "exam", exam, "{\"rating\":2,\"comment\":\"Clear explanations\"}").get("status").asText())
			.isEqualTo("PENDING");

		assertThat(moderate(staff, review, "{\"status\":\"APPROVED\"}").get("status").asText()).isEqualTo("APPROVED");
		assertThat(rate(token, "exam", exam, "{\"rating\":5,\"comment\":\"Clear explanations\"}").get("status").asText())
			.isEqualTo("APPROVED");

		// A changed comment goes back to the queue.
		JsonNode edited = rate(token, "exam", exam, "{\"rating\":5,\"comment\":\"Clear explanations and pace\"}");
		assertThat(edited.get("status").asText()).isEqualTo("PENDING");
		assertThat(edited.get("id").asText()).isEqualTo(review);

		// Staff approve again, then the student takes the comment away: stars only, so approved.
		moderate(staff, review, "{\"status\":\"APPROVED\"}");
		JsonNode removed = rate(token, "exam", exam, "{\"rating\":5}");
		assertThat(removed.get("status").asText()).isEqualTo("APPROVED");
		assertThat(removed.get("comment").isNull()).isTrue();
		// A star-only edit with no comment before or after keeps the status: it is not reset to pending.
		assertThat(rate(token, "exam", exam, "{\"rating\":2}").get("status").asText()).isEqualTo("APPROVED");

		// A new comment is pending again.
		assertThat(rate(token, "exam", exam, "{\"rating\":5,\"comment\":\"New words\"}").get("status").asText())
			.isEqualTo("PENDING");
	}

	@Test
	void aHiddenReviewStaysHiddenWhenTheStudentEditsIt() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Hidden category");
		UUID exam = standalone(staff, master, category, "Hidden exam");
		UUID student = fx.seed("STUDENT");
		UUID other = fx.seed("STUDENT");
		grant(master, student, exam);
		grant(master, other, exam);
		String token = fx.token(student);
		String otherToken = fx.token(other);
		sitAndSubmit(token, exam);
		sitAndSubmit(otherToken, exam);

		JsonNode written = rate(token, "exam", exam, "{\"rating\":1,\"comment\":\"Harsh words\"}");
		String review = written.get("id").asText();
		rate(otherToken, "exam", exam, "{\"rating\":5}");
		assertThat(publicReviews("exam", exam).get("ratingCount").asInt()).isEqualTo(2);

		JsonNode hidden = moderate(staff, review, "{\"status\":\"HIDDEN\",\"reason\":\"Off topic\"}");
		assertThat(hidden.get("status").asText()).isEqualTo("HIDDEN");
		assertThat(hidden.get("hideReason").asText()).isEqualTo("Off topic");
		assertThat(hidden.get("featured").asBoolean()).isFalse();
		assertThat(fx.jdbc().queryForObject("select count(*) from audit_log where action = ? and entity_id = ?", Integer.class,
				AuditRecorder.LIVE_EXAM_REVIEW_HIDE, UUID.fromString(review))).isEqualTo(1);

		// The student still sees the status and the reason, and an edit keeps the review hidden.
		JsonNode mine = read(send(get(me("exam", exam)), token).andExpect(status().isOk()));
		assertThat(mine.get("review").get("status").asText()).isEqualTo("HIDDEN");
		assertThat(mine.get("review").get("hideReason").asText()).isEqualTo("Off topic");
		JsonNode edited = rate(token, "exam", exam, "{\"rating\":2,\"comment\":\"Changed words\"}");
		assertThat(edited.get("status").asText()).isEqualTo("HIDDEN");
		assertThat(edited.get("hideReason").asText()).isEqualTo("Off topic");
		assertThat(edited.get("rating").asInt()).isEqualTo(2);
		JsonNode stripped = rate(token, "exam", exam, "{\"rating\":2}");
		assertThat(stripped.get("status").asText()).isEqualTo("HIDDEN");

		// Hidden stars leave the average; the other student's five stars remain.
		JsonNode afterHide = publicReviews("exam", exam);
		assertThat(afterHide.get("ratingCount").asInt()).isEqualTo(1);
		assertThat(afterHide.get("page").get("totalElements").asInt()).isZero();

		// Staff approve it: the stars count again, and the reason goes.
		JsonNode approved = moderate(staff, review, "{\"status\":\"APPROVED\"}");
		assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
		assertThat(approved.get("hideReason").isNull()).isTrue();
		assertThat(publicReviews("exam", exam).get("ratingCount").asInt()).isEqualTo(2);
		assertThat(fx.jdbc().queryForObject("select count(*) from audit_log where action = ? and entity_id = ?", Integer.class,
				AuditRecorder.LIVE_EXAM_REVIEW_APPROVE, UUID.fromString(review))).isEqualTo(1);
	}

	@Test
	void thePublicListShowsOnlyApprovedCommentsWithoutAnyIdentityAndCountsActiveStudents() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Public list category");
		UUID exam = standalone(staff, master, category, "Public list exam");
		UUID first = seededStudent(master, exam, "Nusrat Jahan");
		UUID second = seededStudent(master, exam, "Mahbub");
		UUID third = seededStudent(master, exam, "Farhana Akter Rahman");
		UUID fourth = seededStudent(master, exam, "Rafiq Hasan");
		String firstToken = fx.token(first);
		String secondToken = fx.token(second);
		String thirdToken = fx.token(third);
		String fourthToken = fx.token(fourth);

		rate(firstToken, "exam", exam, "{\"rating\":5,\"comment\":\"Great\"}");
		rate(secondToken, "exam", exam, "{\"rating\":4}");
		String approvedComment = rate(thirdToken, "exam", exam, "{\"rating\":3,\"comment\":\"Fine\"}").get("id").asText();
		String hiddenComment = rate(fourthToken, "exam", exam, "{\"rating\":1,\"comment\":\"Bad\"}").get("id").asText();
		moderate(staff, approvedComment, "{\"status\":\"APPROVED\"}");
		moderate(staff, hiddenComment, "{\"status\":\"HIDDEN\",\"reason\":\"Spam\"}");

		// Pending and hidden are not in the list; the average and stars count pending and approved, not hidden.
		JsonNode page = publicReviews("exam", exam);
		assertThat(page.get("ratingCount").asInt()).isEqualTo(3);
		assertThat(page.get("ratingAverage").asDouble()).isEqualTo(4.0);
		assertThat(page.get("stars").get("1").asInt()).isZero();
		assertThat(page.get("stars").get("3").asInt()).isEqualTo(1);
		assertThat(page.get("stars").get("4").asInt()).isEqualTo(1);
		assertThat(page.get("stars").get("5").asInt()).isEqualTo(1);
		assertThat(page.get("page").get("totalElements").asInt()).isEqualTo(1);
		JsonNode only = page.get("page").get("content").get(0);
		assertThat(only.get("name").asText()).isEqualTo("Farhana R.");
		assertThat(only.get("comment").asText()).isEqualTo("Fine");
		assertThat(only.get("rating").asInt()).isEqualTo(3);
		assertThat(only.has("studentId")).isFalse();
		assertThat(only.has("phone")).isFalse();
		assertThat(only.has("email")).isFalse();

		String body = send(get("/api/v1/public/reviews/exam/" + exam), null).andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain(third.toString());
		assertThat(body).doesNotContain(fx.jdbc().queryForObject("select phone from users where id = ?", String.class, third));
		assertThat(body).doesNotContain(fx.jdbc().queryForObject("select email from users where id = ?", String.class, third));
		assertThat(body).doesNotContain("Great");
		assertThat(body).doesNotContain("Spam");

		// The public exam view carries the same average and count.
		JsonNode examView = read(send(get("/api/v1/public/live-exams/" + exam), null).andExpect(status().isOk()));
		assertThat(examView.get("ratingCount").asInt()).isEqualTo(3);
		assertThat(examView.get("ratingAverage").asDouble()).isEqualTo(4.0);

		// A deactivated student's ratings leave the average: two left, so no average.
		fx.jdbc().update("update users set status = 'INACTIVE' where id = ?", first);
		JsonNode afterDeactivation = publicReviews("exam", exam);
		assertThat(afterDeactivation.get("ratingCount").asInt()).isEqualTo(2);
		assertThat(afterDeactivation.get("ratingAverage").isNull()).isTrue();

		// An archived exam keeps its approved comments.
		send(post("/api/v1/live-exams/" + exam + "/archive").header("Authorization", "Bearer " + master), null)
			.andExpect(status().isOk());
		assertThat(publicReviews("exam", exam).get("page").get("totalElements").asInt()).isEqualTo(1);
	}

	@Test
	void theAverageIsNullBelowThreeRatingsAndRoundedToOneDecimalFromThree() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Average category");
		UUID exam = standalone(staff, master, category, "Average exam");
		UUID a = seededStudent(master, exam, "Average A");
		UUID b = seededStudent(master, exam, "Average B");
		UUID c = seededStudent(master, exam, "Average C");
		rate(fx.token(a), "exam", exam, "{\"rating\":5}");
		rate(fx.token(b), "exam", exam, "{\"rating\":4}");

		JsonNode two = publicReviews("exam", exam);
		assertThat(two.get("ratingCount").asInt()).isEqualTo(2);
		assertThat(two.get("ratingAverage").isNull()).isTrue();
		assertThat(two.get("stars").get("5").asInt()).isEqualTo(1);
		assertThat(two.get("stars").get("2").asInt()).isZero();
		assertThat(two.get("page").get("totalElements").asInt()).isZero();

		rate(fx.token(c), "exam", exam, "{\"rating\":4}");
		JsonNode three = publicReviews("exam", exam);
		assertThat(three.get("ratingCount").asInt()).isEqualTo(3);
		assertThat(three.get("ratingAverage").asDouble()).isEqualTo(4.3);
		assertThat(three.get("stars").get("4").asInt()).isEqualTo(2);
	}

	@Test
	void featuredReviewsNeedAnApprovedCommentAndHidingClearsFeatured() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Featured category");
		UUID exam = standalone(staff, master, category, "Featured exam");
		UUID commented = seededStudent(master, exam, "Featured Writer");
		UUID starsOnly = seededStudent(master, exam, "Featured Stars");
		String commentedToken = fx.token(commented);
		String starsToken = fx.token(starsOnly);
		String review = rate(commentedToken, "exam", exam, "{\"rating\":5,\"comment\":\"Excellent paper\"}").get("id").asText();
		String stars = rate(starsToken, "exam", exam, "{\"rating\":4}").get("id").asText();

		featureExpecting(staff, review, "{\"featured\":true}", 409, "REVIEW_NOT_FEATURABLE");
		featureExpecting(staff, stars, "{\"featured\":true}", 409, "REVIEW_NOT_FEATURABLE");
		send(put("/api/v1/staff/reviews/" + review + "/featured").contentType(JSON).content("{\"featured\":\"yes\"}"),
				staff)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("featured"));

		moderate(staff, review, "{\"status\":\"APPROVED\"}");
		clock.set(FEATURE_AT);
		JsonNode featured = feature(staff, review, true);
		assertThat(featured.get("featured").asBoolean()).isTrue();
		assertThat(fx.jdbc().queryForObject("select count(*) from audit_log where action = ? and entity_id = ?", Integer.class,
				AuditRecorder.LIVE_EXAM_REVIEW_FEATURE, UUID.fromString(review))).isEqualTo(1);
		assertThat(featuredIds()).contains(review);
		JsonNode shown = featuredRow(review);
		assertThat(shown.get("kind").asText()).isEqualTo("exam");
		assertThat(shown.get("targetId").asText()).isEqualTo(exam.toString());
		assertThat(shown.get("targetTitleEn").asText()).isEqualTo("Featured exam");
		assertThat(shown.get("name").asText()).isEqualTo("Featured W.");

		// A student's edit to the comment clears featured and sends the review back to the queue.
		rate(commentedToken, "exam", exam, "{\"rating\":5,\"comment\":\"Excellent paper, clearer now\"}");
		assertThat(featuredIds()).doesNotContain(review);

		// Approve, feature, then hide with a reason: hiding clears featured.
		moderate(staff, review, "{\"status\":\"APPROVED\"}");
		feature(staff, review, true);
		assertThat(featuredIds()).contains(review);
		JsonNode hidden = moderate(staff, review, "{\"status\":\"HIDDEN\",\"reason\":\"Rude\"}");
		assertThat(hidden.get("featured").asBoolean()).isFalse();
		assertThat(featuredIds()).doesNotContain(review);
		featureExpecting(staff, review, "{\"featured\":true}", 409, "REVIEW_NOT_FEATURABLE");
	}

	@Test
	void staffQueueShowsPendingFirstFiltersAndCountsPending() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Queue category");
		UUID exam = standalone(staff, master, category, "Queue exam");
		UUID writer = seededStudent(master, exam, "Nusrat Jahan");
		UUID stars = seededStudent(master, exam, "Stars Only");
		UUID approvedLater = seededStudent(master, exam, "Approved Later");
		UUID hiddenNow = seededStudent(master, exam, "Hidden Now");
		long pendingBefore = pendingCount(staff);

		String pendingReview = rate(fx.token(writer), "exam", exam, "{\"rating\":5,\"comment\":\"Waiting\"}").get("id").asText();
		rate(fx.token(stars), "exam", exam, "{\"rating\":4}");
		String laterReview = rate(fx.token(approvedLater), "exam", exam, "{\"rating\":3,\"comment\":\"Later\"}").get("id").asText();
		String hiddenReview = rate(fx.token(hiddenNow), "exam", exam, "{\"rating\":2,\"comment\":\"Spam\"}").get("id").asText();
		moderate(staff, laterReview, "{\"status\":\"APPROVED\"}");
		moderate(staff, hiddenReview, "{\"status\":\"HIDDEN\",\"reason\":\"Spam\"}");
		assertThat(pendingCount(staff)).isEqualTo(pendingBefore + 1);

		JsonNode queue = read(send(get("/api/v1/staff/reviews").param("targetId", exam.toString()).param("size", "20"),
				staff).andExpect(status().isOk()));
		assertThat(queue.get("totalElements").asInt()).isEqualTo(4);

		// The waiting tab: the one pending review, with the full row.
		JsonNode waiting = read(send(get("/api/v1/staff/reviews").param("targetId", exam.toString()).param("status", "PENDING"),
				staff).andExpect(status().isOk()));
		assertThat(waiting.get("totalElements").asInt()).isEqualTo(1);
		JsonNode top = waiting.get("content").get(0);
		assertThat(top.get("id").asText()).isEqualTo(pendingReview);
		assertThat(top.get("status").asText()).isEqualTo("PENDING");
		assertThat(top.get("studentName").asText()).isEqualTo("Nusrat Jahan");
		assertThat(top.get("kind").asText()).isEqualTo("exam");
		assertThat(top.get("targetTitleEn").asText()).isEqualTo("Queue exam");
		assertThat(top.get("rating").asInt()).isEqualTo(5);
		assertThat(top.get("comment").asText()).isEqualTo("Waiting");
		assertThat(top.get("featured").asBoolean()).isFalse();
		assertThat(top.has("studentId")).isTrue();
		assertThat(top.has("createdAt")).isTrue();
		assertThat(top.has("moderatedAt")).isTrue();

		JsonNode hiddenRows = read(send(get("/api/v1/staff/reviews").param("targetId", exam.toString())
			.param("status", "HIDDEN"), staff).andExpect(status().isOk()));
		assertThat(hiddenRows.get("totalElements").asInt()).isEqualTo(1);
		assertThat(hiddenRows.get("content").get(0).get("hideReason").asText()).isEqualTo("Spam");

		JsonNode fourStars = read(send(get("/api/v1/staff/reviews").param("targetId", exam.toString())
			.param("rating", "4"), staff).andExpect(status().isOk()));
		assertThat(fourStars.get("totalElements").asInt()).isEqualTo(1);

		JsonNode packagesOnly = read(send(get("/api/v1/staff/reviews").param("targetId", exam.toString())
			.param("kind", "package"), staff).andExpect(status().isOk()));
		assertThat(packagesOnly.get("totalElements").asInt()).isZero();

		send(get("/api/v1/staff/reviews").param("status", "SOMETHING"), staff)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("status"));
		send(get("/api/v1/staff/reviews").param("kind", "course"), staff)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("kind"));
		send(get("/api/v1/staff/reviews").param("rating", "9"), staff)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("rating"));
	}

	@Test
	void theQueueSearchesFiltersAndSortsAndTheCountsIgnoreTheStatus() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Search category");
		UUID exam = standalone(staff, master, category, "Search exam");
		UUID nusrat = seededStudent(master, exam, "Nusrat Jahan");
		UUID rafiq = seededStudent(master, exam, "Rafiq Hasan");
		UUID farhana = seededStudent(master, exam, "Farhana Akter");
		UUID karim = seededStudent(master, exam, "Karim Uddin");
		// One review a minute apart: nusrat is the oldest, karim the newest.
		clock.set(Instant.parse("2026-10-20T06:20:00Z"));
		String nusratReview = rate(fx.token(nusrat), "exam", exam, "{\"rating\":5,\"comment\":\"Excellent paper\"}").get("id")
			.asText();
		clock.set(Instant.parse("2026-10-20T06:21:00Z"));
		String rafiqReview = rate(fx.token(rafiq), "exam", exam, "{\"rating\":2,\"comment\":\"Too long\"}").get("id").asText();
		clock.set(Instant.parse("2026-10-20T06:22:00Z"));
		String farhanaReview = rate(fx.token(farhana), "exam", exam, "{\"rating\":4}").get("id").asText();
		clock.set(Instant.parse("2026-10-20T06:23:00Z"));
		String karimReview = rate(fx.token(karim), "exam", exam, "{\"rating\":3}").get("id").asText();

		// Newest first by default, then oldest, lowest stars and highest stars.
		assertThat(ids(queue(staff, exam))).containsExactly(karimReview, farhanaReview, rafiqReview, nusratReview);
		assertThat(ids(queue(staff, exam, "sort", "newest"))).containsExactly(karimReview, farhanaReview, rafiqReview,
				nusratReview);
		assertThat(ids(queue(staff, exam, "sort", "oldest"))).containsExactly(nusratReview, rafiqReview, farhanaReview,
				karimReview);
		assertThat(ids(queue(staff, exam, "sort", "lowest"))).containsExactly(rafiqReview, karimReview, farhanaReview,
				nusratReview);
		assertThat(ids(queue(staff, exam, "sort", "highest"))).containsExactly(nusratReview, farhanaReview, karimReview,
				rafiqReview);
		send(get("/api/v1/staff/reviews").param("sort", "popular"), staff)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("sort"));

		// Search by name in any case and in part, by student code, by phone. % and _ are plain text.
		assertThat(ids(queue(staff, exam, "q", "nusrat"))).containsExactly(nusratReview);
		assertThat(ids(queue(staff, exam, "q", "  RAFIQ hasa "))).containsExactly(rafiqReview);
		String code = fx.jdbc().queryForObject("select student_code from student_profiles where user_id = ?", String.class,
				farhana);
		assertThat(ids(queue(staff, exam, "q", code.toUpperCase()))).containsExactly(farhanaReview);
		String phone = fx.jdbc().queryForObject("select phone from users where id = ?", String.class, karim);
		assertThat(ids(queue(staff, exam, "q", phone.substring(4)))).containsExactly(karimReview);
		assertThat(queue(staff, exam, "q", "%").get("totalElements").asInt()).isZero();
		assertThat(queue(staff, exam, "q", "_").get("totalElements").asInt()).isZero();
		send(get("/api/v1/staff/reviews").param("q", "x".repeat(101)), staff)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("q"));

		// Featured: approve and feature the nusrat review, then filter both ways.
		moderate(staff, nusratReview, "{\"status\":\"APPROVED\"}");
		feature(staff, nusratReview, true);
		assertThat(ids(queue(staff, exam, "featured", "true"))).containsExactly(nusratReview);
		assertThat(ids(queue(staff, exam, "featured", "false"))).containsExactlyInAnyOrder(rafiqReview, farhanaReview,
				karimReview);
		send(get("/api/v1/staff/reviews").param("featured", "yes"), staff)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("featured"));

		// Counts with the state now: nusrat approved and featured, rafiq pending, farhana and karim approved.
		assertCounts(counts(staff, exam), 1, 3, 0, 1);
		moderate(staff, karimReview, "{\"status\":\"HIDDEN\",\"reason\":\"Spam\"}");
		assertCounts(counts(staff, exam), 1, 2, 1, 1);
		// The status is ignored by the counts, so every tab badge stays right under a status filter.
		assertCounts(counts(staff, exam, "status", "HIDDEN"), 1, 2, 1, 1);
		assertCounts(counts(staff, exam, "status", "PENDING"), 1, 2, 1, 1);
		assertCounts(counts(staff, exam, "rating", "4"), 0, 1, 0, 0);
		assertCounts(counts(staff, exam, "q", "rafiq"), 1, 0, 0, 0);
		assertCounts(counts(staff, exam, "featured", "true"), 0, 1, 0, 1);
		assertCounts(counts(staff, exam, "kind", "package"), 0, 0, 0, 0);

		// The queue and the counts agree: the hidden tab, the search, and the featured-and-approved filter.
		assertThat(ids(queue(staff, exam, "status", "HIDDEN", "q", "karim"))).containsExactly(karimReview);
		assertThat(ids(queue(staff, exam, "status", "APPROVED", "featured", "true"))).containsExactly(nusratReview);
		assertThat(queue(staff, exam, "status", "PENDING", "featured", "true").get("totalElements").asInt()).isZero();
	}

	@Test
	void anInstructorReadsOnlyTheReviewsOfTheirOwnExamsAndCannotWrite() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = pf.createCategory(staff, "Instructor category");
		UUID pkg = pf.createPackage(staff, category, "Instructor package");
		UUID mine = pf.createExam(staff, pkg, "MCQ", "Instructor paper", AuthoringFixture.LIVE_AT);
		pf.addQuestion(staff, mine);
		pf.publishExam(staff, mine);
		pf.finalSaveAndPublish(staff, pkg);
		UUID theirs = standalone(staff, master, category, "Other instructor paper");

		UUID instructor = fx.seed("INSTRUCTOR");
		UUID otherInstructor = fx.seed("INSTRUCTOR");
		fx.jdbc().update("update live_exams set instructor_id = ? where id = ?", instructor, mine);
		fx.jdbc().update("update live_exams set instructor_id = ? where id = ?", otherInstructor, theirs);
		String instructorToken = fx.token(instructor);

		UUID student = fx.seed("STUDENT");
		UUID other = fx.seed("STUDENT");
		pf.markRoster(pkg, student, ROSTER_SINCE);
		grant(master, other, theirs);
		String token = fx.token(student);
		String otherToken = fx.token(other);
		sitAndSubmit(token, mine);
		sitAndSubmit(otherToken, theirs);
		String examReview = rate(token, "exam", mine, "{\"rating\":4,\"comment\":\"Instructor view\"}").get("id").asText();
		String packageReview = rate(token, "package", pkg, "{\"rating\":5}").get("id").asText();
		rate(otherToken, "exam", theirs, "{\"rating\":2,\"comment\":\"Not yours\"}");

		JsonNode examRows = read(send(get("/api/v1/staff/reviews").param("targetId", mine.toString()), instructorToken)
			.andExpect(status().isOk()));
		assertThat(examRows.get("totalElements").asInt()).isEqualTo(1);
		assertThat(examRows.get("content").get(0).get("id").asText()).isEqualTo(examReview);
		assertThat(examRows.get("content").get(0).get("studentName").asText()).isEqualTo("Seed");

		JsonNode packageRows = read(send(get("/api/v1/staff/reviews").param("targetId", pkg.toString()), instructorToken)
			.andExpect(status().isOk()));
		assertThat(packageRows.get("totalElements").asInt()).isEqualTo(1);
		assertThat(packageRows.get("content").get(0).get("id").asText()).isEqualTo(packageReview);

		JsonNode notMine = read(send(get("/api/v1/staff/reviews").param("targetId", theirs.toString()), instructorToken)
			.andExpect(status().isOk()));
		assertThat(notMine.get("totalElements").asInt()).isZero();

		// The counts follow the same scope: their own exam and package, and nothing of the other instructor's.
		assertCounts(counts(instructorToken, mine), 1, 0, 0, 0);
		assertCounts(counts(instructorToken, theirs), 0, 0, 0, 0);
		assertCounts(counts(instructorToken, null), 1, 1, 0, 0);
		send(get("/api/v1/staff/reviews/counts"), instructorToken).andExpect(status().isOk());

		send(put("/api/v1/staff/reviews/" + examReview + "/status").contentType(JSON).content("{\"status\":\"APPROVED\"}"),
				instructorToken)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		send(put("/api/v1/staff/reviews/" + examReview + "/featured").contentType(JSON).content("{\"featured\":true}"),
				instructorToken)
			.andExpect(status().isForbidden());
		send(get("/api/v1/staff/reviews/pending-count"), instructorToken).andExpect(status().isForbidden());
	}

	@Test
	void studentAndAnonymousAccessRules() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Access category");
		UUID exam = standalone(staff, master, category, "Access exam");
		UUID student = fx.seed("STUDENT");
		grant(master, student, exam);
		String token = fx.token(student);
		sitAndSubmit(token, exam);
		String review = rate(token, "exam", exam, "{\"rating\":5,\"comment\":\"Access\"}").get("id").asText();

		// Without a sign-in, the student and staff routes are not allowed.
		send(get("/api/v1/me/reviews"), null).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		send(put(me("exam", exam)).contentType(JSON).content("{\"rating\":5}"), null).andExpect(status().isUnauthorized());
		send(get("/api/v1/staff/reviews"), null).andExpect(status().isUnauthorized());
		send(get("/api/v1/staff/reviews/counts"), null).andExpect(status().isUnauthorized());

		// A student has no staff access, and staff have no student access.
		send(get("/api/v1/staff/reviews"), token).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		send(get("/api/v1/staff/reviews/pending-count"), token).andExpect(status().isForbidden());
		send(get("/api/v1/staff/reviews/counts"), token).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		send(put("/api/v1/staff/reviews/" + review + "/status").contentType(JSON).content("{\"status\":\"HIDDEN\",\"reason\":\"x\"}"),
				token)
			.andExpect(status().isForbidden());
		send(put("/api/v1/staff/reviews/" + review + "/featured").contentType(JSON).content("{\"featured\":true}"), token)
			.andExpect(status().isForbidden());
		send(get("/api/v1/me/reviews"), staff).andExpect(status().isForbidden());

		// Anyone may read the public views; unknown kinds and unknown targets are not found.
		send(get("/api/v1/public/reviews/exam/" + exam), null).andExpect(status().isOk());
		send(get("/api/v1/public/reviews/featured"), null).andExpect(status().isOk());
		send(get("/api/v1/public/reviews/course/" + exam), null).andExpect(status().isNotFound());
		send(get("/api/v1/public/reviews/exam/" + UUID.randomUUID()), null).andExpect(status().isNotFound());

		// Staff with the moderate permission read the count.
		assertThat(pendingCount(staff)).isGreaterThanOrEqualTo(1);
	}

	@Test
	void myReviewsListsNewestFirstAndDropsRemovedTargets() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Mine category");
		UUID first = standalone(staff, master, category, "Mine first exam");
		UUID second = standalone(staff, master, category, "Mine second exam");
		UUID student = fx.seed("STUDENT");
		grant(master, student, first);
		grant(master, student, second);
		String token = fx.token(student);

		sitAndSubmit(token, first);
		clock.set(Instant.parse("2026-10-20T06:20:00Z"));
		rate(token, "exam", first, "{\"rating\":4}");
		sitAndSubmit(token, second);
		clock.set(Instant.parse("2026-10-20T06:30:00Z"));
		rate(token, "exam", second, "{\"rating\":2,\"comment\":\"Second\"}");

		JsonNode mine = read(send(get("/api/v1/me/reviews"), token).andExpect(status().isOk()));
		assertThat(mine.size()).isEqualTo(2);
		assertThat(mine.get(0).get("targetTitleEn").asText()).isEqualTo("Mine second exam");
		assertThat(mine.get(0).get("kind").asText()).isEqualTo("exam");
		assertThat(mine.get(0).get("targetId").asText()).isEqualTo(second.toString());
		assertThat(mine.get(0).get("review").get("status").asText()).isEqualTo("PENDING");
		assertThat(mine.get(1).get("targetTitleEn").asText()).isEqualTo("Mine first exam");

		// Staff remove the first exam: its review drops from the list and cannot be read or changed.
		fx.jdbc().update("update live_exams set deleted_at = now() where id = ?", first);
		JsonNode afterRemoval = read(send(get("/api/v1/me/reviews"), token).andExpect(status().isOk()));
		assertThat(afterRemoval.size()).isEqualTo(1);
		send(get(me("exam", first)), token).andExpect(status().isNotFound());
		send(put(me("exam", first)).contentType(JSON).content("{\"rating\":1}"), token).andExpect(status().isNotFound());
	}

	// ---- helpers -------------------------------------------------------------------------------------------------

	/** A reviewer's picture on the reviews: the public list and the featured list carry its URL, the picture is served only for an approved review (the review id is the key, so no student id appears), and the staff queue has its own route that needs a staff sign-in. */
	@Test
	void aReviewerPictureShowsOnApprovedReviewsAndTheStaffQueueOnly() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Reviewer photo category");
		UUID exam = standalone(staff, master, category, "Reviewer photo exam");
		UUID withPicture = seededStudent(master, exam, "Nusrat Jahan");
		UUID noPicture = seededStudent(master, exam, "Mahbub");
		UUID waiting = seededStudent(master, exam, "Rafiq Hasan");
		givePicture(withPicture);
		givePicture(waiting);
		String approved = rate(fx.token(withPicture), "exam", exam, "{\"rating\":5,\"comment\":\"Clear picture review\"}")
			.get("id").asText();
		String plain = rate(fx.token(noPicture), "exam", exam, "{\"rating\":4,\"comment\":\"Plain review\"}").get("id").asText();
		String pending = rate(fx.token(waiting), "exam", exam, "{\"rating\":3,\"comment\":\"Waiting review\"}").get("id").asText();
		moderate(staff, approved, "{\"status\":\"APPROVED\"}");
		moderate(staff, plain, "{\"status\":\"APPROVED\"}");

		// The public list: the picture's URL for the author with a picture, null (not absent) for the others, and no student id.
		JsonNode content = publicReviews("exam", exam).get("page").get("content");
		JsonNode shown = rowWithId(content, approved);
		assertThat(shown.get("photoUrl").asText())
			.isEqualTo("/api/v1/public/reviews/" + approved + "/photo?v=" + PICTURE_UPDATED.toEpochMilli());
		assertThat(rowWithId(content, plain).get("photoUrl").isNull()).isTrue();
		assertThat(content.toString()).doesNotContain(withPicture.toString());

		// Served for the approved review, cached for an hour; a plain author's review or a pending one has no picture to serve.
		send(get("/api/v1/public/reviews/" + approved + "/photo"), null)
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "public, max-age=3600"))
			.andExpect(header().string("Content-Type", "image/jpeg"));
		send(get("/api/v1/public/reviews/" + plain + "/photo"), null).andExpect(status().isNotFound());
		send(get("/api/v1/public/reviews/" + pending + "/photo"), null).andExpect(status().isNotFound());

		// The featured list carries the same URL once the review is featured.
		feature(staff, approved, true);
		assertThat(featuredRow(approved).get("photoUrl").asText()).isEqualTo(shown.get("photoUrl").asText());

		// The staff queue: the URL is the staff route, which needs a staff sign-in and is never cached by shared caches.
		JsonNode queued = rowWithId(queue(staff, exam, "status", "APPROVED").get("content"), approved);
		assertThat(queued.get("photoUrl").asText())
			.isEqualTo("/api/v1/staff/reviews/" + approved + "/photo?v=" + PICTURE_UPDATED.toEpochMilli());
		send(get("/api/v1/staff/reviews/" + approved + "/photo"), staff)
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "private, no-cache"));
		send(get("/api/v1/staff/reviews/" + approved + "/photo"), fx.token(withPicture)).andExpect(status().isForbidden());
		send(get("/api/v1/staff/reviews/" + plain + "/photo"), staff).andExpect(status().isNotFound());
	}

	/** Gives a student a stored profile picture as the upload would. The bytes are served as they are, so they need no image. */
	private void givePicture(UUID student) throws Exception {
		String stored = UUID.randomUUID().toString();
		byte[] bytes = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4 };
		Files.createDirectories(fileStorage.rootDirectory());
		Files.write(fileStorage.resolve(stored), bytes);
		fx.jdbc().update("""
				insert into student_photos (user_id, stored_name, content_type, byte_size, updated_at)
				values (?, ?, 'image/jpeg', ?, ?)
				""", student, stored, (long) bytes.length, Timestamp.from(PICTURE_UPDATED));
	}

	private static JsonNode rowWithId(JsonNode content, String id) {
		for (JsonNode row : content) {
			if (row.get("id").asText().equals(id)) {
				return row;
			}
		}
		throw new AssertionError("The review is not in the list: " + id);
	}

	/** A published standalone MCQ exam in the category, with one question. */
	private UUID standalone(String staff, String master, UUID category, String title) throws Exception {
		UUID exam = fx.createStandalone(staff, category, "MCQ", title, AuthoringFixture.LIVE_AT, 40, null);
		fx.question(staff, exam, "Two plus two", 2);
		fx.publishNew(master, exam);
		return exam;
	}

	private void grant(String master, UUID student, UUID exam) throws Exception {
		send(post("/api/v1/live-exam-grants").contentType(JSON)
			.content("""
					{"studentId":"%s","scope":"EXAM","moduleId":null,"examId":"%s","reason":"Review test grant"}
					""".formatted(student, exam)), master)
			.andExpect(status().isCreated());
	}

	/** A student with a name, who holds the exam through a grant and has sat it. The name is what staff and the public see. */
	private UUID seededStudent(String master, UUID exam, String fullName) throws Exception {
		UUID student = fx.seed("STUDENT");
		fx.jdbc().update("update student_profiles set full_name = ? where user_id = ?", fullName, student);
		grant(master, student, exam);
		sitAndSubmit(fx.token(student), exam);
		return student;
	}

	/** Sits the exam inside the window and submits it, as the sitting tests do. Leaves the clock at the submit time. */
	private void sitAndSubmit(String token, UUID exam) throws Exception {
		clock.set(SIT_AT);
		send(post("/api/v1/live-exams/" + exam + "/take").contentType(JSON).content("{}"), token)
			.andExpect(status().isOk());
		clock.set(SUBMIT_AT);
		send(post("/api/v1/live-exams/" + exam + "/submit"), token).andExpect(status().isOk());
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

	private JsonNode rate(String token, String kind, UUID target, String body) throws Exception {
		return read(send(put(me(kind, target)).contentType(JSON).content(body), token).andExpect(status().isOk()));
	}

	private JsonNode moderate(String staff, String review, String body) throws Exception {
		return read(send(put("/api/v1/staff/reviews/" + review + "/status").contentType(JSON).content(body), staff)
			.andExpect(status().isOk()));
	}

	private JsonNode feature(String staff, String review, boolean featured) throws Exception {
		return read(send(put("/api/v1/staff/reviews/" + review + "/featured").contentType(JSON)
			.content("{\"featured\":" + featured + "}"), staff).andExpect(status().isOk()));
	}

	private void featureExpecting(String staff, String review, String body, int code, String error) throws Exception {
		send(put("/api/v1/staff/reviews/" + review + "/featured").contentType(JSON).content(body), staff)
			.andExpect(status().is(code))
			.andExpect(jsonPath("$.code").value(error));
	}

	private JsonNode publicReviews(String kind, UUID target) throws Exception {
		return read(send(get("/api/v1/public/reviews/" + kind + "/" + target), null).andExpect(status().isOk()));
	}

	private java.util.List<String> featuredIds() throws Exception {
		JsonNode list = read(send(get("/api/v1/public/reviews/featured"), null).andExpect(status().isOk()));
		java.util.List<String> ids = new java.util.ArrayList<>();
		list.forEach(node -> ids.add(node.get("id").asText()));
		assertThat(list.size()).isLessThanOrEqualTo(9);
		return ids;
	}

	private JsonNode featuredRow(String review) throws Exception {
		JsonNode list = read(send(get("/api/v1/public/reviews/featured"), null).andExpect(status().isOk()));
		for (JsonNode node : list) {
			if (node.get("id").asText().equals(review)) {
				return node;
			}
		}
		throw new AssertionError("The review is not in the featured list: " + review);
	}

	/** The staff queue for one target, with extra name and value pairs (status, q, sort, featured, ...). */
	private JsonNode queue(String token, UUID exam, String... pairs) throws Exception {
		return read(send(withParams(get("/api/v1/staff/reviews").param("targetId", exam.toString()), pairs), token)
			.andExpect(status().isOk()));
	}

	/** The tab counts for one target (or for the whole scope when {@code exam} is null), with extra pairs. */
	private JsonNode counts(String token, UUID exam, String... pairs) throws Exception {
		MockHttpServletRequestBuilder request = get("/api/v1/staff/reviews/counts");
		if (exam != null) {
			request.param("targetId", exam.toString());
		}
		return read(send(withParams(request, pairs), token).andExpect(status().isOk()));
	}

	private static MockHttpServletRequestBuilder withParams(MockHttpServletRequestBuilder request, String... pairs) {
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			request.param(pairs[i], pairs[i + 1]);
		}
		return request;
	}

	private static void assertCounts(JsonNode counts, int pending, int approved, int hidden, int featured) {
		assertThat(counts.get("pending").asInt()).as("pending").isEqualTo(pending);
		assertThat(counts.get("approved").asInt()).as("approved").isEqualTo(approved);
		assertThat(counts.get("hidden").asInt()).as("hidden").isEqualTo(hidden);
		assertThat(counts.get("featured").asInt()).as("featured").isEqualTo(featured);
	}

	/** The review ids of one page of the queue, in order. */
	private static java.util.List<String> ids(JsonNode page) {
		java.util.List<String> ids = new java.util.ArrayList<>();
		page.get("content").forEach(node -> ids.add(node.get("id").asText()));
		return ids;
	}

	private long pendingCount(String staff) throws Exception {
		return read(send(get("/api/v1/staff/reviews/pending-count"), staff).andExpect(status().isOk())).get("count").asLong();
	}

	private static String me(String kind, UUID id) {
		return "/api/v1/me/reviews/" + kind + "/" + id;
	}

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(Instant.parse("2026-10-20T04:00:00Z"));
		}

	}

}
