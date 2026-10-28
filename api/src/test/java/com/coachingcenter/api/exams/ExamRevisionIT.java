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

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
	void publishingAKeyChangeRescoresTheSubmittedPapersAndLogsIt() throws Exception {
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
		assertThat(changes.get("impact").get("papersToRescore").asInt()).isEqualTo(1);
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"revisionId\":\"" + changes.get("revisionId").asText() + "\"}"))
			.andExpect(status().isOk());

		assertThat(fx.jdbc().queryForObject("select score from live_exam_participants where id = ?", BigDecimal.class, participant))
			.isEqualByComparingTo("0");
		assertThat(fx.jdbc().queryForObject(
				"select rescored from live_exam_change_log where exam_id = ? order by published_at desc limit 1",
				Integer.class, exam)).isEqualTo(1);
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
		assertThat(updateNotices(exam, studentId)).isEqualTo(1);
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

	private JsonNode pending(String token, UUID exam) throws Exception {
		return json.readTree(mockMvc.perform(get("/api/v1/live-exams/" + exam + "/pending-changes")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
	}

	/** Publishes the staged edit as the Master Admin, naming the revision the change list shows. */
	private void publishStaged(String master, UUID exam, String staff) throws Exception {
		JsonNode changes = pending(staff, exam);
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"revisionId\":\"" + changes.get("revisionId").asText() + "\"}"))
			.andExpect(status().isOk());
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
