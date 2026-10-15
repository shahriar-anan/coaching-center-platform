package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class ExamMcqAttemptIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	private String system;

	private String master;

	private String student;

	private String other;

	private UUID module;

	private UUID mcqImmediate;

	private UUID mcqHidden;

	@BeforeEach
	void seed() throws Exception {
		system = token(seed("SYSTEM_ADMIN"));
		master = token(seed("MASTER_ADMIN"));
		student = registerStudent();
		other = registerStudent();
		module = createModule();
		mockMvc.perform(patch("/api/v1/exam-modules/" + module + "/access").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"accessMode\":\"OPEN\",\"courseId\":null}"))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/exam-modules/" + module).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"PUBLISHED\"}"))
			.andExpect(status().isOk());
		UUID paperImmediate = createPaper();
		UUID paperHidden = createPaper();
		mcqImmediate = setupMcq(paperImmediate, true, 3600);
		mcqHidden = setupMcq(paperHidden, false, 3600);
		addThreeQuestions(mcqImmediate);
		addThreeQuestions(mcqHidden);
	}

	@Test
	void mcqAttemptRules() throws Exception {
		mockMvc.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + mcqImmediate + "/attempts")
			.header("Authorization", "Bearer " + other))
			.andExpect(status().isOk());

		MvcResult start = mockMvc
			.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + mcqImmediate + "/attempts")
				.header("Authorization", "Bearer " + student)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"startedAt\":\"2099-01-01T00:00:00Z\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.serverNow").exists())
			.andExpect(jsonPath("$.deadlineAt").exists())
			.andExpect(jsonPath("$.score").doesNotExist())
			.andExpect(jsonPath("$.questions[0].options[0].correct").doesNotExist())
			.andReturn();
		UUID attemptQuestionId = UUID
			.fromString(json.readTree(start.getResponse().getContentAsString()).get("questions").get(0).get("id").asText());
		UUID correctOption = optionId(attemptQuestionId, true);

		mockMvc.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + mcqImmediate + "/attempts")
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ATTEMPT_ALREADY_USED"));

		mockMvc.perform(get("/api/v1/catalog/exam-modules/" + module + "/components/" + mcqImmediate + "/attempts")
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.questions[0].explanation").doesNotExist());

		UUID wrongOption = optionId(attemptQuestionId, false);
		mockMvc.perform(put("/api/v1/catalog/exam-modules/" + module + "/components/" + mcqImmediate + "/attempts/answers")
			.header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content(answerJson(attemptQuestionId, wrongOption)))
			.andExpect(status().isOk());
		UUID q2 = UUID.fromString(json.readTree(start.getResponse().getContentAsString()).get("questions").get(1).get("id").asText());
		UUID wrong2 = optionId(q2, false);
		mockMvc.perform(put("/api/v1/catalog/exam-modules/" + module + "/components/" + mcqImmediate + "/attempts/answers")
			.header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"answers":[{"attemptQuestionId":"%s","attemptOptionId":"%s"},{"attemptQuestionId":"%s","attemptOptionId":"%s"}]}
					""".formatted(attemptQuestionId, correctOption, q2, wrong2)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions[0].chosenOptionId").value(correctOption.toString()));

		mockMvc.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + mcqImmediate + "/attempts/submit")
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(2))
			.andExpect(jsonPath("$.maxScore").value(6))
			.andExpect(jsonPath("$.passed").value(false))
			.andExpect(jsonPath("$.questions[0].marksAwarded").value(2))
			.andExpect(jsonPath("$.questions[1].marksAwarded").value(0))
			.andExpect(jsonPath("$.questions[2].marksAwarded").value(0))
			.andExpect(jsonPath("$.questions[0].options[0].correct").exists())
			.andExpect(jsonPath("$.rank").doesNotExist());

		mockMvc.perform(get("/api/v1/catalog/exam-modules/" + module + "/components/" + mcqHidden + "/attempts")
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound());
		setupActiveMcq(mcqHidden);
		mockMvc.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + mcqHidden + "/attempts")
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + mcqHidden + "/attempts/submit")
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").doesNotExist());

		mockMvc.perform(get("/api/v1/exam-components/" + mcqHidden + "/attempts").header("Authorization", "Bearer " + system))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].score").exists());
	}

	@Test
	void mcqTimingAccessAndFreeze() throws Exception {
		UUID paid = createModule();
		mockMvc.perform(patch("/api/v1/exam-modules/" + paid).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":100}"))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/exam-modules/" + paid + "/access").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"accessMode\":\"PURCHASE_REQUIRED\",\"courseId\":null}"))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/exam-modules/" + paid).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"PUBLISHED\"}"))
			.andExpect(status().isOk());
		UUID paidPaper = createPaperOn(paid);
		UUID paidMcq = setupMcq(paidPaper, true, 3600);
		postQuestion(paidMcq, 1);
		int before = new JdbcTemplate(dataSource).queryForObject("select count(*) from mcq_attempts", Integer.class);
		mockMvc.perform(post("/api/v1/catalog/exam-modules/" + paid + "/components/" + paidMcq + "/attempts")
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("EXAM_ACCESS_REQUIRED"));
		assertThat(new JdbcTemplate(dataSource).queryForObject("select count(*) from mcq_attempts", Integer.class))
			.isEqualTo(before);

		UUID closed = setupMcq(createPaper(), true, 60);
		postQuestion(closed, 1);
		new JdbcTemplate(dataSource).update("update paper_components set starts_at = now() + interval '1 day' where id = ?",
				closed);
		mockMvc.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + closed + "/attempts")
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("COMPONENT_NOT_ACTIVE"));

		UUID empty = setupMcq(createPaper(), true, 60);
		mockMvc.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + empty + "/attempts")
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("COMPONENT_EMPTY"));

		UUID paper = createPaper();
		UUID mcq = setupMcq(paper, true, 3600);
		postQuestion(mcq, 5);
		postQuestion(mcq, 5);
		MvcResult start = mockMvc
			.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + mcq + "/attempts")
				.header("Authorization", "Bearer " + student)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"startedAt\":\"2099-01-01T00:00:00Z\"}"))
			.andExpect(status().isOk())
			.andReturn();
		java.sql.Timestamp storedStart = new JdbcTemplate(dataSource).queryForObject(
				"select started_at from mcq_attempts where component_id = ?", java.sql.Timestamp.class, mcq);
		assertThat(storedStart.toInstant()).isBefore(Instant.parse("2090-01-01T00:00:00Z"));
		UUID q1 = UUID.fromString(json.readTree(start.getResponse().getContentAsString()).get("questions").get(0).get("id").asText());
		UUID chosen = optionId(q1, true);
		mockMvc.perform(put("/api/v1/catalog/exam-modules/" + module + "/components/" + mcq + "/attempts/answers")
			.header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content(answerJson(q1, chosen)))
			.andExpect(status().isOk());
		mockMvc.perform(put("/api/v1/catalog/exam-modules/" + module + "/components/" + mcq + "/attempts/answers")
			.header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"answers\":[]}"))
			.andExpect(status().isOk());
		assertThat(new JdbcTemplate(dataSource).queryForObject(
				"select attempt_option_id from mcq_answers where attempt_question_id = ?", UUID.class, q1)).isNull();

		mockMvc.perform(put("/api/v1/catalog/exam-modules/" + module + "/components/" + mcq + "/attempts/answers")
			.header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content(answerJson(q1, chosen)))
			.andExpect(status().isOk());
		new JdbcTemplate(dataSource).update(
				"update mcq_attempts set started_at = now() - interval '2 days' where component_id = ?", mcq);
		mockMvc.perform(put("/api/v1/catalog/exam-modules/" + module + "/components/" + mcq + "/attempts/answers")
			.header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"answers\":[]}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ATTEMPT_EXPIRED"));
		assertThat(new JdbcTemplate(dataSource).queryForObject(
				"select attempt_option_id from mcq_answers where attempt_question_id = ?", UUID.class, q1))
			.isEqualTo(chosen);
		assertThat(new JdbcTemplate(dataSource).queryForObject(
				"select status from mcq_attempts where component_id = ?", String.class, mcq)).isEqualTo("SUBMITTED");

		mockMvc.perform(get("/api/v1/catalog/exam-modules/" + module + "/components/" + mcq + "/attempts")
			.header("Authorization", "Bearer " + other))
			.andExpect(status().isForbidden());
	}

	@Test
	void mcqFreezePassWindowAndArchive() throws Exception {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);

		UUID ended = setupMcq(createPaper(), true, 3600);
		postQuestion(ended, 1);
		jdbc.update("update paper_components set ends_at = now() - interval '1 minute' where id = ?", ended);
		int endedBefore = jdbc.queryForObject("select count(*) from mcq_attempts where component_id = ?", Integer.class,
				ended);
		mockMvc.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + ended + "/attempts")
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("COMPONENT_NOT_ACTIVE"));
		assertThat(jdbc.queryForObject("select count(*) from mcq_attempts where component_id = ?", Integer.class, ended))
			.isEqualTo(endedBefore);

		UUID passPaper = createPaper();
		UUID passMcq = setupMcq(passPaper, true, 7200);
		postQuestion(passMcq, 5, "frozen-stem");
		postQuestion(passMcq, 5, "second");
		MvcResult passStart = mockMvc
			.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + passMcq + "/attempts")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.durationSeconds").value(7200))
			.andExpect(jsonPath("$.questions[0].stem").value("frozen-stem"))
			.andReturn();
		JsonNode passBody = json.readTree(passStart.getResponse().getContentAsString());
		UUID starter = UUID.fromString(passBody.get("studentId").asText());
		UUID liveQuestionId = UUID.fromString(passBody.get("questions").get(0).get("questionId").asText());
		UUID passQ1 = UUID.fromString(passBody.get("questions").get(0).get("id").asText());
		UUID passCorrect = optionId(passQ1, true);
		mockMvc.perform(patch("/api/v1/questions/" + liveQuestionId).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"stem":"edited-live","options":[{"text":"yes","correct":false},{"text":"no","correct":true}]}
					"""))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/exam-components/" + passMcq).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"descriptionEn":"EN","descriptionBn":"BN","durationSeconds":120}
					"""))
			.andExpect(status().isOk());
		assertThat(jdbc.queryForObject("select duration_seconds from mcq_attempts where component_id = ?", Integer.class,
				passMcq)).isEqualTo(7200);
		mockMvc.perform(get("/api/v1/catalog/exam-modules/" + module + "/components/" + passMcq + "/attempts")
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions[0].stem").value("frozen-stem"))
			.andExpect(jsonPath("$.durationSeconds").value(7200));
		mockMvc.perform(put("/api/v1/catalog/exam-modules/" + module + "/components/" + passMcq + "/attempts/answers")
			.header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content(answerJson(passQ1, passCorrect)))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + passMcq + "/attempts/submit")
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(5))
			.andExpect(jsonPath("$.maxScore").value(10))
			.andExpect(jsonPath("$.passed").value(true));
		mockMvc.perform(patch("/api/v1/exam-papers/" + passPaper).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"passPercent\":90}"))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/catalog/exam-modules/" + module + "/components/" + passMcq + "/attempts")
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.passed").value(true))
			.andExpect(jsonPath("$.score").value(5));
		mockMvc.perform(get("/api/v1/exam-components/" + passMcq + "/attempts").header("Authorization", "Bearer " + system))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].studentId").value(starter.toString()));

		UUID failPaper = createPaper();
		UUID failMcq = setupMcq(failPaper, true, 3600);
		postQuestion(failMcq, 4, "four");
		postQuestion(failMcq, 6, "six");
		MvcResult failStart = mockMvc
			.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + failMcq + "/attempts")
				.header("Authorization", "Bearer " + other))
			.andExpect(status().isOk())
			.andReturn();
		UUID failQ1 = UUID.fromString(json.readTree(failStart.getResponse().getContentAsString()).get("questions").get(0)
			.get("id").asText());
		mockMvc.perform(put("/api/v1/catalog/exam-modules/" + module + "/components/" + failMcq + "/attempts/answers")
			.header("Authorization", "Bearer " + other)
			.contentType(MediaType.APPLICATION_JSON)
			.content(answerJson(failQ1, optionId(failQ1, true))))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + failMcq + "/attempts/submit")
			.header("Authorization", "Bearer " + other))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(4))
			.andExpect(jsonPath("$.maxScore").value(10))
			.andExpect(jsonPath("$.passed").value(false));

		UUID mixedPaper = createPaper();
		UUID mixedMcq = setupMcq(mixedPaper, true, 3600);
		postQuestion(mixedMcq, 5, "only");
		mockMvc.perform(post("/api/v1/exam-papers/" + mixedPaper + "/components").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\":\"WRITTEN\"}"))
			.andExpect(status().isCreated());
		MvcResult mixedStart = mockMvc
			.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + mixedMcq + "/attempts")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andReturn();
		UUID mixedQ = UUID.fromString(json.readTree(mixedStart.getResponse().getContentAsString()).get("questions").get(0)
			.get("id").asText());
		mockMvc.perform(put("/api/v1/catalog/exam-modules/" + module + "/components/" + mixedMcq + "/attempts/answers")
			.header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content(answerJson(mixedQ, optionId(mixedQ, true))))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + mixedMcq + "/attempts/submit")
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(5))
			.andExpect(jsonPath("$.passed").doesNotExist());

		UUID windowPaper = createPaper();
		UUID window = setupMcq(windowPaper, true, 7200);
		postQuestion(window, 5, "window");
		MvcResult windowStart = mockMvc
			.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + window + "/attempts")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andReturn();
		UUID windowQ = UUID.fromString(json.readTree(windowStart.getResponse().getContentAsString()).get("questions").get(0)
			.get("id").asText());
		UUID windowCorrect = optionId(windowQ, true);
		mockMvc.perform(put("/api/v1/catalog/exam-modules/" + module + "/components/" + window + "/attempts/answers")
			.header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content(answerJson(windowQ, windowCorrect)))
			.andExpect(status().isOk());
		jdbc.update("update paper_components set ends_at = now() - interval '1 second' where id = ?", window);
		mockMvc.perform(put("/api/v1/catalog/exam-modules/" + module + "/components/" + window + "/attempts/answers")
			.header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"answers\":[]}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ATTEMPT_EXPIRED"));
		Timestamp submittedAt = jdbc.queryForObject("select submitted_at from mcq_attempts where component_id = ?",
				Timestamp.class, window);
		Timestamp endsAt = jdbc.queryForObject("select ends_at from paper_components where id = ?", Timestamp.class, window);
		assertThat(submittedAt.toInstant()).isBeforeOrEqualTo(endsAt.toInstant());
		assertThat(jdbc.queryForObject("select score from mcq_attempts where component_id = ?", java.math.BigDecimal.class,
				window)).isEqualByComparingTo("5");
		mockMvc.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + window + "/attempts/submit")
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(5));
		assertThat(jdbc.queryForObject("select submitted_at from mcq_attempts where component_id = ?", Timestamp.class,
				window)).isEqualTo(submittedAt);

		UUID adminWindow = setupMcq(createPaper(), true, 60);
		postQuestion(adminWindow, 2, "admin");
		mockMvc.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + adminWindow + "/attempts")
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());
		jdbc.update("update mcq_attempts set started_at = now() - interval '2 days' where component_id = ?", adminWindow);
		mockMvc.perform(get("/api/v1/exam-components/" + adminWindow + "/attempts").header("Authorization", "Bearer " + system))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].status").value("SUBMITTED"));

		UUID archiveMcq = setupMcq(createPaper(), true, 3600);
		postQuestion(archiveMcq, 2, "archive");
		MvcResult archiveStart = mockMvc
			.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + archiveMcq + "/attempts")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andReturn();
		UUID archiveQ = UUID.fromString(json.readTree(archiveStart.getResponse().getContentAsString()).get("questions")
			.get(0).get("id").asText());
		mockMvc.perform(post("/api/v1/exam-modules/" + module + "/archive").header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + archiveMcq + "/attempts")
			.header("Authorization", "Bearer " + other))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("EXAM_ACCESS_REQUIRED"));
		mockMvc.perform(put("/api/v1/catalog/exam-modules/" + module + "/components/" + archiveMcq + "/attempts/answers")
			.header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content(answerJson(archiveQ, optionId(archiveQ, true))))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + archiveMcq + "/attempts/submit")
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(2));
	}

	private UUID setupMcq(UUID paper, boolean immediate, int duration) throws Exception {
		MvcResult c = mockMvc
			.perform(post("/api/v1/exam-papers/" + paper + "/components").header("Authorization", "Bearer " + system)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\":\"MCQ\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		UUID id = UUID.fromString(json.readTree(c.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(patch("/api/v1/exam-papers/" + paper).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"passPercent\":50}"))
			.andExpect(status().isOk());
		Instant start = Instant.now().minusSeconds(60);
		Instant end = Instant.now().plusSeconds(7200);
		mockMvc.perform(patch("/api/v1/exam-components/" + id).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "descriptionEn":"EN","descriptionBn":"BN",
					  "startsAt":"%s","endsAt":"%s","durationSeconds":%d,
					  "immediateResults":%s
					}
					""".formatted(start, end, duration, immediate)))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/exam-components/" + id + "/schedule").header("Authorization", "Bearer " + system))
			.andExpect(status().isOk());
		return id;
	}

	private void setupActiveMcq(UUID componentId) {
		Instant start = Instant.now().minusSeconds(60);
		Instant end = Instant.now().plusSeconds(7200);
		new JdbcTemplate(dataSource).update(
				"update paper_components set starts_at=?, ends_at=?, scheduled=true, duration_seconds=3600 where id=?",
				Timestamp.from(start), Timestamp.from(end), componentId);
	}

	private void addThreeQuestions(UUID componentId) throws Exception {
		postQuestion(componentId, 2);
		postQuestion(componentId, 2);
		postQuestion(componentId, 2);
	}

	private void postQuestion(UUID componentId, int marks) throws Exception {
		postQuestion(componentId, marks, "Q" + UUID.randomUUID().toString().substring(0, 4));
	}

	private void postQuestion(UUID componentId, int marks, String stem) throws Exception {
		mockMvc.perform(post("/api/v1/exam-components/" + componentId + "/questions").header("Authorization",
				"Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"stem":"%s","marks":%d,"subject":"S","options":[{"text":"yes","correct":true},{"text":"no","correct":false}]}
					""".formatted(stem, marks)))
			.andExpect(status().isCreated());
	}

	private UUID optionId(UUID attemptQuestionId, boolean correct) {
		return new JdbcTemplate(dataSource).queryForObject(
				"select id from mcq_attempt_options where attempt_question_id = ? and correct = ? limit 1", UUID.class,
				attemptQuestionId, correct);
	}

	private static String answerJson(UUID attemptQuestionId, UUID optionId) {
		return """
				{"answers":[{"attemptQuestionId":"%s","attemptOptionId":"%s"}]}
				""".formatted(attemptQuestionId, optionId);
	}

	private UUID createModule() throws Exception {
		MvcResult m = mockMvc.perform(post("/api/v1/exam-modules").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"MCQ mod","titleBn":"ম","descriptionEn":"d","descriptionBn":"b"}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(m.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID createPaperOn(UUID moduleId) throws Exception {
		MvcResult n = mockMvc
			.perform(post("/api/v1/exam-modules/" + moduleId + "/nodes").header("Authorization", "Bearer " + system)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nodeType\":\"PAPER\",\"titleEn\":\"P\",\"titleBn\":\"প\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(n.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID createPaper() throws Exception {
		MvcResult n = mockMvc
			.perform(post("/api/v1/exam-modules/" + module + "/nodes").header("Authorization", "Bearer " + system)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nodeType\":\"PAPER\",\"titleEn\":\"P\",\"titleBn\":\"প\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(n.getResponse().getContentAsString()).get("id").asText());
	}

	private String registerStudent() throws Exception {
		String phone = uniquePhone();
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"S","phone":"%s","email":"%s","password":"password1"}
					""".formatted(phone, uniqueEmail())))
			.andExpect(status().isCreated());
		return tokenFromLogin(phone, "password1");
	}

	private UUID seed(String role) {
		UUID id = UUID.randomUUID();
		String phone = uniquePhone();
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, phone, uniqueEmail(), passwords.encode("password1"), role);
		if ("SYSTEM_ADMIN".equals(role) || "MASTER_ADMIN".equals(role)) {
			new JdbcTemplate(dataSource).update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Admin')", id);
		}
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = new JdbcTemplate(dataSource).queryForObject("select phone from users where id = ?", String.class,
				userId);
		return tokenFromLogin(phone, "password1");
	}

	private String tokenFromLogin(String phone, String password) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"%s"}
					""".formatted(phone, password)))
			.andExpect(status().isOk())
			.andReturn();
		String body = result.getResponse().getContentAsString();
		String marker = "\"accessToken\":\"";
		int start = body.indexOf(marker) + marker.length();
		return body.substring(start, body.indexOf('"', start));
	}

	private static String uniquePhone() {
		return "+8801" + java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
	}

	private static String uniqueEmail() {
		return "mcq-" + UUID.randomUUID() + "@example.com";
	}

}
