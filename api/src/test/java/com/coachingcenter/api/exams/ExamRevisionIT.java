package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Staged edits to a published exam (NR-01 A3, A4): the working copy, the change list, discard, the publish with a revision,
 * the re-score, the update notice, the copy, and the paper versions that each sitting keeps.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, ExamRevisionIT.ClockConfig.class })
class ExamRevisionIT extends PostgresIntegrationTest {

	private static final Instant TEN_AM = Instant.parse("2026-10-20T04:00:00Z");

	/** Inside the window of the exams below (12:00 to 12:40 in Dhaka). */
	private static final Instant IN_WINDOW = Instant.parse("2026-10-20T06:05:00Z");

	private static final String NOON = "2026-10-20T12:00:00+06:00";

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

	private AuthoringFixture fx;

	@BeforeEach
	void setUp() {
		clock.set(TEN_AM);
		fx = new AuthoringFixture(mockMvc, dataSource, passwords, json);
	}

	@Test
	void editsToAPublishedExamStayStagedAndTheStudentsViewChangesOnlyOnPublish() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Staged");
		UUID exam = fx.createExam(staff, module, "MCQ", "Original", NOON, 40, null);
		fx.question(staff, exam, "Stem", 1);
		fx.publishNew(master, exam);
		UUID studentId = fx.seed("STUDENT");
		String student = fx.token(studentId);
		fx.grantModule(module, studentId);

		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titleEn\":\"Renamed\",\"durationMinutes\":50}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.titleEn").value("Renamed"))
			.andExpect(jsonPath("$.pendingChanges").value(true))
			.andExpect(jsonPath("$.status").value("PUBLISHED"));

		mockMvc.perform(get("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.titleEn").value("Original"))
			.andExpect(jsonPath("$.durationMinutes").value(40));
		assertThat(fx.jdbc().queryForObject("select title_en from live_exams where id = ?", String.class, exam))
			.isEqualTo("Original");

		JsonNode changes = pending(staff, exam);
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"revisionId\":\"" + changes.get("revisionId").asText() + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.titleEn").value("Renamed"))
			.andExpect(jsonPath("$.pendingChanges").value(false));

		mockMvc.perform(get("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + student))
			.andExpect(jsonPath("$.titleEn").value("Renamed"))
			.andExpect(jsonPath("$.durationMinutes").value(50));
	}

	@Test
	void theChangeListNamesEachChangeAndWhatPublishingDoes() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Changes");
		UUID exam = fx.createExam(staff, module, "MCQ", "Listed", NOON, 40, null);
		UUID question = fx.question(staff, exam, "Stem", 1);
		fx.publishNew(master, exam);
		UUID studentId = fx.seed("STUDENT");
		fx.grantModule(module, studentId);

		mockMvc.perform(patch("/api/v1/live-exams/" + exam + "/questions/" + question).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(AuthoringFixture.questionJson("New stem", 1, "Because", false, "A", "*B")))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/questions").header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(AuthoringFixture.questionJson("Added", 1, null, false, "*A", "B")))
			.andExpect(status().isCreated());
		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"liveAt\":\"2026-10-20T13:00:00+06:00\",\"passPercent\":60}"))
			.andExpect(status().isOk());

		JsonNode changes = pending(staff, exam);
		assertThat(fieldNames(changes)).contains("liveAt", "passPercent");
		assertThat(questionChanges(changes)).contains("STEM", "CORRECT", "ADDED");
		assertThat(changes.get("impact").get("notifiesStudents").asBoolean()).isTrue();
		assertThat(changes.get("impact").get("studentsToNotify").asInt()).isEqualTo(1);
		assertThat(changes.get("impact").get("papersToRescore").asInt()).isZero();
	}

	@Test
	void discardDropsTheRevisionAndTheWorkingCopyFallsBackToThePublishedRows() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Discard");
		UUID exam = fx.createExam(staff, module, "MCQ", "Kept", NOON, 40, null);
		fx.question(staff, exam, "Stem", 1);
		fx.publishNew(master, exam);

		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titleEn\":\"Dropped\"}"))
			.andExpect(status().isOk());
		mockMvc.perform(delete("/api/v1/live-exams/" + exam + "/pending-changes").header("Authorization", "Bearer " + staff))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/pending-changes").header("Authorization", "Bearer " + staff))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff))
			.andExpect(jsonPath("$.titleEn").value("Kept"))
			.andExpect(jsonPath("$.pendingChanges").value(false));
		assertThat(fx.jdbc().queryForObject(
				"select count(*) from live_exam_revisions where exam_id = ?", Integer.class, exam)).isZero();
	}

	@Test
	void aStaleRevisionIdIsRejectedAndTheExamStaysPublishedAsItWas() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Stale");
		UUID exam = fx.createExam(staff, module, "MCQ", "Stale", NOON, 40, null);
		fx.question(staff, exam, "Stem", 1);
		fx.publishNew(master, exam);
		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titleEn\":\"Newer\"}"))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"revisionId\":\"not-the-current-one\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PENDING_CHANGES_STALE"));
		assertThat(fx.jdbc().queryForObject("select status from live_exams where id = ?", String.class, exam))
			.isEqualTo("PUBLISHED");
		assertThat(fx.jdbc().queryForObject("select title_en from live_exams where id = ?", String.class, exam))
			.isEqualTo("Stale");
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/pending-changes").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
	}

	@Test
	void aSystemAdminStagesButOnlyTheMasterAdminPublishes() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Roles");
		UUID exam = fx.createExam(staff, module, "MCQ", "Roles", NOON, 40, null);
		fx.question(staff, exam, "Stem", 1);
		fx.publishNew(master, exam);
		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titleEn\":\"Staged by staff\"}"))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + staff))
			.andExpect(status().isForbidden());
		JsonNode changes = pending(staff, exam);
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"revisionId\":\"" + changes.get("revisionId").asText() + "\"}"))
			.andExpect(status().isOk());
	}

	@Test
	void publishingAKeyChangeLeavesTheSubmittedPapersAloneAndLogsNoRescore() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Rescore");
		UUID exam = fx.createExam(staff, module, "MCQ", "Rescore", NOON, 40, null);
		UUID question = fx.question(staff, exam, AuthoringFixture.questionJson("Pick", 1, null, true, "*A", "*B", "C"));
		fx.publishNew(master, exam);
		UUID studentId = fx.seed("STUDENT");
		String student = fx.token(studentId);
		fx.grantModule(module, studentId);

		clock.set(IN_WINDOW);
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isOk());
		UUID participant = fx.jdbc().queryForObject(
				"select id from live_exam_participants where exam_id = ? and student_id = ?", UUID.class, exam, studentId);
		for (UUID option : fx.jdbc().queryForList(
				"select id from live_exam_options where question_id = ? and correct order by position", UUID.class, question)) {
			fx.jdbc().update("insert into live_exam_answers (participant_id, question_id, option_id) values (?, ?, ?)",
					participant, question, option);
		}
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/submit").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());
		assertThat(fx.jdbc().queryForObject("select score from live_exam_participants where id = ?", BigDecimal.class, participant))
			.isEqualByComparingTo("1");

		mockMvc.perform(patch("/api/v1/live-exams/" + exam + "/questions/" + question).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(AuthoringFixture.questionJson("Pick", 1, null, true, "*A", "B", "*C")))
			.andExpect(status().isOk());
		JsonNode changes = pending(staff, exam);
		// No submitted paper is re-scored on publish (D10): the submitted attempt keeps the key it was marked with.
		assertThat(changes.get("impact").get("papersToRescore").asInt()).isZero();
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"revisionId\":\"" + changes.get("revisionId").asText() + "\"}"))
			.andExpect(status().isOk());

		assertThat(fx.jdbc().queryForObject("select score from live_exam_participants where id = ?", BigDecimal.class, participant))
			.isEqualByComparingTo("1");
		assertThat(fx.jdbc().queryForObject(
				"select rescored from live_exam_change_log where exam_id = ? order by published_at desc limit 1",
				Integer.class, exam)).isZero();
	}

	@Test
	void theUpdateNoticeGoesOnlyForAChangedTimeOrPaperOrQuestionAndNoMailIsQueued() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Notice");
		UUID exam = fx.createExam(staff, module, "MCQ", "Notice", NOON, 40, null);
		fx.question(staff, exam, "Stem", 1);
		fx.publishNew(master, exam);
		UUID studentId = fx.seed("STUDENT");
		fx.grantModule(module, studentId);

		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titleEn\":\"Only the title\"}"))
			.andExpect(status().isOk());
		publishStaged(master, exam, staff);
		assertThat(updateNotices(exam, studentId)).isZero();

		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"liveAt\":\"2026-10-20T13:00:00+06:00\"}"))
			.andExpect(status().isOk());
		int mailBefore = fx.jdbc().queryForObject("select count(*) from exam_outbound_mail", Integer.class);
		publishStaged(master, exam, staff);
		assertThat(timeNotices(exam, studentId)).as("a live-time change tells the student the time changed").isEqualTo(1);
		assertThat(updateNotices(exam, studentId)).as("and not the general update").isZero();
		assertThat(fx.jdbc().queryForObject("select count(*) from exam_outbound_mail", Integer.class)).isEqualTo(mailBefore);

		assertThat(fx.jdbc().queryForObject(
				"select count(*) from audit_log where action = 'LIVE_EXAM_REVISION_PUBLISH' and entity_id = ?", Integer.class,
				exam)).isEqualTo(2);
		assertThat(fx.jdbc().queryForObject("select count(*) from live_exam_change_log where exam_id = ?", Integer.class, exam))
			.isEqualTo(2);
	}

	@Test
	void aPublishWithoutARevisionIdPublishesTheWorkingCopy() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Compat");
		UUID exam = fx.createExam(staff, module, "MCQ", "Compat", NOON, 40, null);
		fx.question(staff, exam, "Stem", 1);
		fx.publishNew(master, exam);
		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titleEn\":\"Compatible\"}"))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.titleEn").value("Compatible"))
			.andExpect(jsonPath("$.pendingChanges").value(false));
	}

	@Test
	void stagedQuestionsAreInvisibleToStudentsUntilPublishAndTheCopyTakesTheWorkingCopy() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Copy working");
		UUID exam = fx.createExam(staff, module, "MCQ", "Working", NOON, 40, null);
		fx.question(staff, exam, "Stem", 1);
		fx.publishNew(master, exam);
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/questions").header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(AuthoringFixture.questionJson("Staged", 1, null, false, "*A", "B")))
			.andExpect(status().isCreated());
		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titleEn\":\"Staged title\"}"))
			.andExpect(status().isOk());

		assertThat(fx.jdbc().queryForObject("select count(*) from live_exam_questions where exam_id = ? and deleted_at is null",
				Integer.class, exam)).isEqualTo(1);
		mockMvc.perform(get("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff))
			.andExpect(jsonPath("$.questions", org.hamcrest.Matchers.hasSize(2)));

		var copy = mockMvc.perform(post("/api/v1/live-exams/" + exam + "/copy").header("Authorization", "Bearer " + staff))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.titleEn").value("Staged title (copy)"))
			.andExpect(jsonPath("$.questions", org.hamcrest.Matchers.hasSize(2)))
			.andReturn();
		JsonNode copied = json.readTree(copy.getResponse().getContentAsString());
		assertThat(copied.has("liveAt")).isFalse();
		assertThat(copied.get("categoryId").asText()).isEqualTo(fx.jdbc().queryForObject(
				"select category_id from live_exams where id = ?", UUID.class, exam).toString());
	}

	@Test
	void aReplacedPaperReachesOnlyTheSittingsThatStartAfterThePublish() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Papers");
		UUID exam = fx.createExam(staff, module, "WRITTEN", "Paper", NOON, 40, null);
		fx.uploadPdf(staff, exam, "one");
		fx.publishNew(master, exam);
		UUID version1 = fx.jdbc().queryForObject(
				"select id from live_exam_question_papers where exam_id = ? and version = 1", UUID.class, exam);

		UUID first = takeAsNewStudent(module, exam);
		fx.uploadPdf(staff, exam, "two");
		UUID version2 = fx.jdbc().queryForObject(
				"select id from live_exam_question_papers where exam_id = ? and version = 2", UUID.class, exam);
		assertThat(fx.jdbc().queryForObject("select question_paper_id from live_exams where id = ?", UUID.class, exam))
			.isEqualTo(version1);

		UUID beforePublish = takeAsNewStudent(module, exam);
		assertThat(paperOf(beforePublish)).isEqualTo(version1);

		JsonNode changes = pending(staff, exam);
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"revisionId\":\"" + changes.get("revisionId").asText() + "\"}"))
			.andExpect(status().isOk());
		assertThat(fx.jdbc().queryForObject("select question_paper_id from live_exams where id = ?", UUID.class, exam))
			.isEqualTo(version2);

		UUID afterPublish = takeAsNewStudent(module, exam);
		assertThat(paperOf(afterPublish)).isEqualTo(version2);
		assertThat(paperOf(first)).isEqualTo(version1);
		assertThat(fx.jdbc().queryForObject("select count(*) from live_exam_question_papers where id = ?", Integer.class,
				version1)).isEqualTo(1);
	}

	@Test
	void theChangesWaitingBadgeListsStagedExamsNewestFirstAndOnlyForStaff() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Badge");
		UUID first = fx.createExam(staff, module, "MCQ", "Badge first", NOON, 40, null);
		fx.question(staff, first, "Stem", 1);
		UUID second = fx.createExam(staff, module, "MCQ", "Badge second", NOON, 40, null);
		fx.question(staff, second, "Stem", 1);
		fx.publishNew(master, first);
		fx.publishNew(master, second);

		mockMvc.perform(patch("/api/v1/live-exams/" + first).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titleEn\":\"Staged first\"}"))
			.andExpect(status().isOk());
		clock.set(TEN_AM.plusSeconds(60));
		mockMvc.perform(patch("/api/v1/live-exams/" + second).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titleEn\":\"Staged second\"}"))
			.andExpect(status().isOk());

		JsonNode badge = json.readTree(mockMvc.perform(get("/api/v1/live-exam-pending-changes")
			.header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
		int firstAt = indexOf(badge, first);
		int secondAt = indexOf(badge, second);
		assertThat(firstAt).isGreaterThanOrEqualTo(0);
		assertThat(secondAt).isGreaterThanOrEqualTo(0);
		assertThat(secondAt).isLessThan(firstAt);
		assertThat(badge.get("items").get(secondAt).get("kind").asText()).isEqualTo("EXAM");
		assertThat(badge.get("exams").asInt()).isGreaterThanOrEqualTo(2);

		mockMvc.perform(get("/api/v1/live-exam-pending-changes").header("Authorization", "Bearer " + fx.token(fx.seed("STUDENT"))))
			.andExpect(status().isForbidden());
	}

	/** Packages stage their own revisions (B's package revision). Enabled once B's package staging is on the integration branch. */
	@Test
	void aStagedPackageAppearsOnTheChangesWaitingBadge() throws Exception {
		String staff = fx.staff();
		UUID module = fx.module(staff, "Badge package");
		mockMvc.perform(patch("/api/v1/live-exam-modules/" + module).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titleEn\":\"Staged package\"}"))
			.andExpect(status().isOk());
		JsonNode badge = json.readTree(mockMvc.perform(get("/api/v1/live-exam-pending-changes")
			.header("Authorization", "Bearer " + staff))
			.andReturn()
			.getResponse()
			.getContentAsString());
		assertThat(indexOf(badge, module)).isGreaterThanOrEqualTo(0);
	}

	private static int indexOf(JsonNode badge, UUID id) {
		JsonNode items = badge.get("items");
		for (int index = 0; index < items.size(); index++) {
			if (id.toString().equals(items.get(index).get("id").asText())) {
				return index;
			}
		}
		return -1;
	}

	private JsonNode pending(String token, UUID exam) throws Exception {
		return json.readTree(mockMvc.perform(get("/api/v1/live-exams/" + exam + "/pending-changes")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
	}

	@Test
	void aStagedReorderIsKeptListedAndPublishedWithTheNewPositions() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Reorder");
		UUID exam = fx.createExam(staff, module, "MCQ", "Reordered", NOON, 40, null);
		UUID first = fx.question(staff, exam, "First", 1);
		UUID second = fx.question(staff, exam, "Second", 1);
		fx.publishNew(master, exam);
		assertThat(position(first)).isZero();
		assertThat(position(second)).isEqualTo(1);

		// The web sends the whole question with the new position (QuestionInput needs stem and marks).
		mockMvc.perform(patch("/api/v1/live-exams/" + exam + "/questions/" + first).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(atPosition(AuthoringFixture.questionJson("First", 1, "Because", false, "*A", "B"), 1)))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/live-exams/" + exam + "/questions/" + second).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(atPosition(AuthoringFixture.questionJson("Second", 1, "Because", false, "*A", "B"), 0)))
			.andExpect(status().isOk());

		assertThat(position(first)).isZero();
		JsonNode changes = pending(staff, exam);
		assertThat(questionChanges(changes)).containsExactly("POSITION");
		assertThat(changes.get("questions").size()).isEqualTo(2);
		assertThat(stagedPosition(changes, first)).isEqualTo(1);
		assertThat(stagedPosition(changes, second)).isZero();

		publishStaged(master, exam, staff);
		assertThat(position(first)).isEqualTo(1);
		assertThat(position(second)).isZero();
		assertThat(fx.jdbc().queryForObject("select count(*) from live_exam_revisions where exam_id = ?", Integer.class, exam))
			.isZero();
	}

	private int position(UUID question) {
		return fx.jdbc().queryForObject("select position from live_exam_questions where id = ?", Integer.class, question);
	}

	private String atPosition(String body, int position) throws Exception {
		ObjectNode node = (ObjectNode) json.readTree(body);
		node.put("position", position);
		return node.toString();
	}

	private static int stagedPosition(JsonNode changes, UUID question) {
		for (JsonNode change : changes.get("questions")) {
			if (question.toString().equals(change.get("questionId").asText())) {
				return change.get("after").asInt();
			}
		}
		throw new AssertionError("The change list has no entry for the question " + question + ".");
	}

	/** Publishes the staged edit as the Master Admin, naming the revision the change list shows. */
	private void publishStaged(String master, UUID exam, String staff) throws Exception {
		JsonNode changes = pending(staff, exam);
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"revisionId\":\"" + changes.get("revisionId").asText() + "\"}"))
			.andExpect(status().isOk());
	}

	/**
	 * Every staged field reaches every student read in the same request sequence after the publish, with no waiting: the public
	 * exam, its category list (the new category and the old one), the package's exam list, the student's exam read, the routine
	 * and the questions of a running sitting.
	 */
	@Test
	void everyStagedFieldReachesEveryStudentReadAfterThePublish() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Reach");
		UUID exam = fx.createExam(staff, module, "MCQ", "Before", NOON, 40, null);
		UUID oldCategory = fx.jdbc().queryForObject("select category_id from live_exams where id = ?", UUID.class, exam);
		UUID newCategory = fx.category(staff, "Reach moved");
		UUID question = fx.question(staff, exam, "Old stem", 1);
		fx.publishNew(master, exam);
		UUID studentId = fx.seed("STUDENT");
		String student = fx.token(studentId);
		fx.grantModule(module, studentId);

		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"After","titleBn":"পরে","descriptionEn":"New description","descriptionBn":"নতুন বিবরণ",
					 "rulesEn":"New rules","rulesBn":"নতুন নিয়ম","paperLanguage":"EN","passPercent":70,"durationMinutes":55,
					 "liveAt":"2026-10-20T12:30:00+06:00","negativeMarks":0.25,"answersChangeable":false,"previewEnabled":true,
					 "accessDays":30,"categoryId":"%s","price":150,"discountPercent":10,"discountEndsAt":"2026-10-30T00:00:00+06:00"}
					""".formatted(newCategory)))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/live-exams/" + exam + "/questions/" + question).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(AuthoringFixture.questionJson("New stem", 2, "Why", false, "A", "*B")))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/questions").header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(AuthoringFixture.questionJson("Added stem", 1, null, false, "*A", "B")))
			.andExpect(status().isCreated());
		publishStaged(master, exam, staff);

		JsonNode publicExam = read("/api/v1/public/live-exams/" + exam, null);
		assertThat(publicExam.get("titleEn").asText()).isEqualTo("After");
		assertThat(publicExam.get("descriptionEn").asText()).isEqualTo("New description");
		assertThat(publicExam.get("rulesEn").asText()).isEqualTo("New rules");
		assertThat(publicExam.get("paperLanguage").asText()).isEqualTo("EN");
		assertThat(publicExam.get("categoryId").asText()).isEqualTo(newCategory.toString());
		assertThat(publicExam.get("durationMinutes").asInt()).isEqualTo(55);
		assertThat(publicExam.get("previewEnabled").asBoolean()).isTrue();
		assertThat(publicExam.get("liveAt").asText()).startsWith("2026-10-20T12:30:00");
		assertThat(publicExam.get("price").decimalValue()).isEqualByComparingTo("150");

		JsonNode movedTo = read("/api/v1/public/live-exam-categories/" + newCategory + "/exams?examType=MCQ", null);
		assertThat(row(movedTo, exam).get("titleEn").asText()).isEqualTo("After");
		JsonNode leftBehind = read("/api/v1/public/live-exam-categories/" + oldCategory + "/exams?examType=MCQ", null);
		assertThat(ids(leftBehind)).doesNotContain(exam.toString());

		JsonNode packageExam = row(read("/api/v1/public/live-exam-modules/" + module + "/exams", null), exam);
		assertThat(packageExam.get("titleEn").asText()).isEqualTo("After");
		assertThat(packageExam.get("durationMinutes").asInt()).isEqualTo(55);
		assertThat(packageExam.get("price").decimalValue()).isEqualByComparingTo("150");

		JsonNode mine = read("/api/v1/live-exams/" + exam, student);
		assertThat(mine.get("titleEn").asText()).isEqualTo("After");
		assertThat(mine.get("descriptionEn").asText()).isEqualTo("New description");
		assertThat(mine.get("durationMinutes").asInt()).isEqualTo(55);
		assertThat(mine.get("passPercent").asInt()).isEqualTo(70);
		assertThat(mine.get("maximum").asInt()).isEqualTo(3);
		assertThat(mine.get("liveAt").asText()).startsWith("2026-10-20T12:30:00");
		assertThat(mine.get("price").decimalValue()).isEqualByComparingTo("150");

		assertThat(row(read("/api/v1/me/exam-routine?size=100", student), exam).get("titleEn").asText()).isEqualTo("After");

		clock.set(Instant.parse("2026-10-20T06:40:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());
		JsonNode sitting = read("/api/v1/live-exams/" + exam, student);
		java.util.List<String> stems = new java.util.ArrayList<>();
		sitting.get("questions").forEach(row -> stems.add(row.get("stem").asText()));
		assertThat(stems).containsExactly("New stem", "Added stem");
	}

	private JsonNode read(String url, String token) throws Exception {
		MockHttpServletRequestBuilder request = get(url);
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return json.readTree(mockMvc.perform(request)
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
	}

	/** The ids of a page's {@code content}, or of a plain list. */
	private static java.util.List<String> ids(JsonNode page) {
		java.util.List<String> found = new java.util.ArrayList<>();
		(page.has("content") ? page.get("content") : page).forEach(row -> found.add(row.get("id").asText()));
		return found;
	}

	private static JsonNode row(JsonNode page, UUID exam) {
		JsonNode rows = page.has("content") ? page.get("content") : page;
		for (JsonNode row : rows) {
			if (exam.toString().equals(row.get("id").asText())) {
				return row;
			}
		}
		throw new AssertionError("The exam " + exam + " is not listed.");
	}

	private int timeNotices(UUID exam, UUID student) {
		return fx.jdbc().queryForObject(
				"select count(*) from student_notices where exam_id = ? and user_id = ? and kind = 'EXAM_TIME_CHANGED'", Integer.class,
				exam, student);
	}

	private int updateNotices(UUID exam, UUID student) {
		return fx.jdbc().queryForObject(
				"select count(*) from student_notices where exam_id = ? and user_id = ? and kind = 'EXAM_UPDATED'", Integer.class,
				exam, student);
	}

	private UUID takeAsNewStudent(UUID module, UUID exam) throws Exception {
		UUID studentId = fx.seed("STUDENT");
		String student = fx.token(studentId);
		fx.grantModule(module, studentId);
		clock.set(IN_WINDOW);
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isOk());
		return fx.jdbc().queryForObject("select id from live_exam_participants where exam_id = ? and student_id = ?", UUID.class,
				exam, studentId);
	}

	private UUID paperOf(UUID participant) {
		return fx.jdbc().queryForObject("select question_paper_id from live_exam_participants where id = ?", UUID.class,
				participant);
	}

	private static java.util.Set<String> fieldNames(JsonNode changes) {
		java.util.Set<String> names = new java.util.HashSet<>();
		changes.get("fields").forEach(field -> names.add(field.get("field").asText()));
		return names;
	}

	private static java.util.Set<String> questionChanges(JsonNode changes) {
		java.util.Set<String> kinds = new java.util.HashSet<>();
		changes.get("questions").forEach(change -> kinds.add(change.get("change").asText()));
		return kinds;
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
