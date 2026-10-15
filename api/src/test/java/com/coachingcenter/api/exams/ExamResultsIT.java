package com.coachingcenter.api.exams;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import javax.imageio.ImageIO;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
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
class ExamResultsIT extends PostgresIntegrationTest {

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

	private UUID studentUserId;

	private UUID module;

	private UUID dualPaper;

	private UUID mcqHidden;

	private UUID written;

	private UUID mcqOnlyPaper;

	private UUID mcqOnly;

	@BeforeEach
	void seed() throws Exception {
		system = token(seed("SYSTEM_ADMIN"));
		master = token(seed("MASTER_ADMIN"));
		student = registerStudent();
		module = publishOpenModule();
		grantOpenAccess(student);
		dualPaper = createPaper();
		mcqHidden = setupMcq(dualPaper, false);
		written = setupWritten(dualPaper);
		addMcqQuestions(mcqHidden);
		addWrittenQuestions(written);
		submitWritten();
		submitMcq(mcqHidden, true);
		endMcq(mcqHidden);

		mcqOnlyPaper = createPaper();
		mcqOnly = setupMcq(mcqOnlyPaper, true);
		addMcqQuestions(mcqOnly);
		submitMcq(mcqOnly, true);
		endMcq(mcqOnly);
	}

	@Test
	void resultsPublishAndVisibility() throws Exception {
		String stranger = registerOther();
		mockMvc
			.perform(get("/api/v1/catalog/exam-modules/" + module + "/components/" + mcqOnly + "/result")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(6))
			.andExpect(jsonPath("$.mcqQuestions[0].options[0].correct").exists())
			.andExpect(jsonPath("$.mcqQuestions[0].explanation").value("E"))
			.andExpect(jsonPath("$.rank").doesNotExist());

		mockMvc
			.perform(get("/api/v1/catalog/exam-modules/" + module + "/papers/" + dualPaper + "/result")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("RESULTS_NOT_PUBLISHED"));

		mockMvc
			.perform(get("/api/v1/exam-components/" + mcqHidden + "/students/" + studentUserId + "/result")
				.header("Authorization", "Bearer " + system))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(6))
			.andExpect(jsonPath("$.mcqQuestions[0].marksAwarded").exists())
			.andExpect(jsonPath("$.mcqQuestions[0].options[0].correct").exists());

		UUID submissionId = submissionId(written);
		UUID q1 = submissionQuestionId(submissionId, 0);
		UUID q2 = submissionQuestionId(submissionId, 1);

		mockMvc
			.perform(post("/api/v1/exam-components/" + written + "/submissions/" + submissionId + "/evaluation")
				.header("Authorization", "Bearer " + student)
				.contentType(MediaType.APPLICATION_JSON)
				.content(evalJson(q1, q2, 0, 6, "Nice work")))
			.andExpect(status().isForbidden());

		mockMvc
			.perform(post("/api/v1/exam-components/" + written + "/submissions/" + submissionId + "/evaluation")
				.header("Authorization", "Bearer " + system)
				.contentType(MediaType.APPLICATION_JSON)
				.content(evalJson(q1, q2, 0, 6, "Nice work")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions[0].marksAwarded").value(0))
			.andExpect(jsonPath("$.questions[1].marksAwarded").value(6));

		mockMvc
			.perform(post("/api/v1/exam-components/" + written + "/submissions/" + submissionId + "/evaluation")
				.header("Authorization", "Bearer " + master)
				.contentType(MediaType.APPLICATION_JSON)
				.content(evalJson(q1, q2, 2, 4, "Adjusted")))
			.andExpect(status().isOk());

		mockMvc
			.perform(post("/api/v1/exam-components/" + written + "/submissions/" + submissionId + "/evaluation")
				.header("Authorization", "Bearer " + system)
				.contentType(MediaType.APPLICATION_JSON)
				.content(evalJson(q1, q2, -1, 4, "x")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MARKS_OUT_OF_RANGE"));
		mockMvc
			.perform(post("/api/v1/exam-components/" + written + "/submissions/" + submissionId + "/evaluation")
				.header("Authorization", "Bearer " + system)
				.contentType(MediaType.APPLICATION_JSON)
				.content(evalJson(q1, q2, 2, 7, "high")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MARKS_OUT_OF_RANGE"));
		mockMvc
			.perform(post("/api/v1/exam-components/" + written + "/submissions/" + submissionId + "/evaluation")
				.header("Authorization", "Bearer " + system)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"feedback":"missing","marks":[{"submissionQuestionId":"%s","marks":1}]}
						""".formatted(q1)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MARKS_OUT_OF_RANGE"));
		mockMvc
			.perform(post("/api/v1/exam-components/" + written + "/submissions/" + submissionId + "/evaluation")
				.header("Authorization", "Bearer " + system)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"feedback":"extra","marks":[{"submissionQuestionId":"%s","marks":1},{"submissionQuestionId":"%s","marks":1},{"submissionQuestionId":"%s","marks":1}]}
						""".formatted(q1, q2, UUID.randomUUID())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MARKS_OUT_OF_RANGE"));

		mockMvc
			.perform(get("/api/v1/catalog/exam-modules/" + module + "/components/" + written + "/result")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("RESULTS_NOT_PUBLISHED"));

		mockMvc.perform(post("/api/v1/exam-components/" + mcqHidden + "/results/publish").header("Authorization",
				"Bearer " + system))
			.andExpect(status().isNoContent());
		mockMvc
			.perform(get("/api/v1/catalog/exam-modules/" + module + "/papers/" + dualPaper + "/result")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.combinedScore").doesNotExist())
			.andExpect(jsonPath("$.passed").doesNotExist());

		mockMvc.perform(post("/api/v1/exam-components/" + written + "/results/publish").header("Authorization",
				"Bearer " + system))
			.andExpect(status().isNoContent());

		mockMvc
			.perform(post("/api/v1/exam-components/" + written + "/submissions/" + submissionId + "/evaluation")
				.header("Authorization", "Bearer " + system)
				.contentType(MediaType.APPLICATION_JSON)
				.content(evalJson(q1, q2, 1, 1, "Late")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESULTS_PUBLISHED"));

		mockMvc
			.perform(get("/api/v1/catalog/exam-modules/" + module + "/components/" + mcqHidden + "/result")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(6))
			.andExpect(jsonPath("$.maxScore").value(6))
			.andExpect(jsonPath("$.mcqQuestions[0].marksAwarded").exists())
			.andExpect(jsonPath("$.mcqQuestions[0].chosenOptionId").exists())
			.andExpect(jsonPath("$.mcqQuestions[0].options[0].correct").exists())
			.andExpect(jsonPath("$.mcqQuestions[0].explanation").value("E"));
		mockMvc
			.perform(get("/api/v1/catalog/exam-modules/" + module + "/components/" + mcqHidden + "/result")
				.header("Authorization", "Bearer " + stranger))
			.andExpect(status().isNotFound());

		mockMvc
			.perform(get("/api/v1/catalog/exam-modules/" + module + "/components/" + written + "/result")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.writtenQuestions[0].explanation").exists())
			.andExpect(jsonPath("$.feedback").value("Adjusted"));

		MvcResult paperResult = mockMvc
			.perform(get("/api/v1/catalog/exam-modules/" + module + "/papers/" + dualPaper + "/result")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.combinedScore").exists())
			.andExpect(jsonPath("$.passed").exists())
			.andReturn();
		boolean passedBefore = json.readTree(paperResult.getResponse().getContentAsString()).get("passed").asBoolean();
		new JdbcTemplate(dataSource).update("update written_submissions set pass_percent = 100 where component_id = ?",
				written);
		mockMvc
			.perform(get("/api/v1/catalog/exam-modules/" + module + "/papers/" + dualPaper + "/result")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.passed").value(passedBefore));

		mockMvc
			.perform(get("/api/v1/catalog/exam-modules/" + module + "/papers/" + mcqOnlyPaper + "/result")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.combinedScore").doesNotExist())
			.andExpect(jsonPath("$.passed").value(true));

		mockMvc.perform(post("/api/v1/exam-components/" + mcqOnly + "/results/publish").header("Authorization",
				"Bearer " + system))
			.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/v1/exam-components/" + mcqOnly + "/results/unpublish").header("Authorization",
				"Bearer " + system))
			.andExpect(status().isNoContent());
		mockMvc
			.perform(get("/api/v1/catalog/exam-modules/" + module + "/components/" + mcqOnly + "/result")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(6))
			.andExpect(jsonPath("$.mcqQuestions[0].explanation").value("E"))
			.andExpect(jsonPath("$.rank").doesNotExist());
	}

	@Test
	void mcqBoundaryAndDraftPublish() throws Exception {
		UUID boundaryPaper = createPaper();
		UUID boundary = setupMcq(boundaryPaper, true);
		addQuestion(boundary, 5);
		addQuestion(boundary, 5);
		submitOneOf(boundary, 0);
		mockMvc
			.perform(get("/api/v1/catalog/exam-modules/" + module + "/papers/" + boundaryPaper + "/result")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.combinedScore").doesNotExist())
			.andExpect(jsonPath("$.passed").value(true));

		UUID belowPaper = createPaper();
		UUID below = setupMcq(belowPaper, true);
		addQuestion(below, 4);
		addQuestion(below, 6);
		submitOneOf(below, 0);
		mockMvc
			.perform(get("/api/v1/catalog/exam-modules/" + module + "/papers/" + belowPaper + "/result")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.passed").value(false));

		UUID draftPaper = createPaper();
		UUID draftWritten = setupWritten(draftPaper);
		addWrittenQuestions(draftWritten);
		String second = registerOther();
		String draftStudent = registerOther();
		submitWrittenOn(draftWritten, student);
		submitWrittenOn(draftWritten, second);
		mockMvc
			.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.multipart("/api/v1/catalog/exam-modules/" + module + "/components/" + draftWritten + "/submission/images")
				.file(new MockMultipartFile("file", "a.jpg", "image/jpeg", tinyJpeg()))
				.header("Authorization", "Bearer " + draftStudent))
			.andExpect(status().isOk());
		UUID draftId = new JdbcTemplate(dataSource).queryForObject(
				"select id from written_submissions where component_id = ? and status = 'DRAFT'", UUID.class, draftWritten);
		mockMvc
			.perform(post("/api/v1/exam-components/" + draftWritten + "/submissions/" + draftId + "/evaluation")
				.header("Authorization", "Bearer " + system)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"feedback\":\"no\",\"marks\":[]}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SUBMISSION_LOCKED"));

		for (String token : new String[] { student, second }) {
			UUID id = new JdbcTemplate(dataSource).queryForObject(
					"select id from written_submissions where component_id = ? and status = 'UNDER_REVIEW' order by created_at limit 1",
					UUID.class, draftWritten);
			UUID first = submissionQuestionId(id, 0);
			UUID next = submissionQuestionId(id, 1);
			mockMvc
				.perform(post("/api/v1/exam-components/" + draftWritten + "/submissions/" + id + "/evaluation")
					.header("Authorization", "Bearer " + system)
					.contentType(MediaType.APPLICATION_JSON)
					.content(evalJson(first, next, 2, 4, "Done")))
				.andExpect(status().isOk());
		}
		mockMvc.perform(post("/api/v1/exam-components/" + draftWritten + "/results/publish").header("Authorization",
				"Bearer " + system))
			.andExpect(status().isNoContent());
		mockMvc
			.perform(get("/api/v1/catalog/exam-modules/" + module + "/components/" + draftWritten + "/result")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(6));
		mockMvc
			.perform(get("/api/v1/catalog/exam-modules/" + module + "/components/" + draftWritten + "/result")
				.header("Authorization", "Bearer " + second))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(6));
		mockMvc
			.perform(get("/api/v1/catalog/exam-modules/" + module + "/components/" + draftWritten + "/result")
				.header("Authorization", "Bearer " + draftStudent))
			.andExpect(status().isNotFound());
		mockMvc
			.perform(get("/api/v1/exam-papers/" + draftPaper + "/leaderboard").header("Authorization", "Bearer " + system))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(2))
			.andExpect(jsonPath("$.content[0].score").value(6));
	}

	@Test
	void publishGuards() throws Exception {
		UUID paper = createPaper();
		UUID mcq = setupMcq(paper, false);
		addMcqQuestions(mcq);
		mockMvc.perform(post("/api/v1/exam-components/" + mcq + "/results/publish").header("Authorization",
				"Bearer " + system))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("COMPONENT_NOT_ENDED"));

		UUID w = setupWritten(paper);
		addWrittenQuestions(w);
		submitWrittenOn(w);
		mockMvc.perform(post("/api/v1/exam-components/" + w + "/results/publish").header("Authorization",
				"Bearer " + system))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESULTS_NOT_READY"));
	}

	private String evalJson(UUID q1, UUID q2, int m1, int m2, String feedback) {
		return """
				{"feedback":"%s","marks":[{"submissionQuestionId":"%s","marks":%d},{"submissionQuestionId":"%s","marks":%d}]}
				""".formatted(feedback, q1, m1, q2, m2);
	}

	private UUID submissionId(UUID componentId) {
		return new JdbcTemplate(dataSource).queryForObject(
				"select id from written_submissions where component_id = ? limit 1", UUID.class, componentId);
	}

	private UUID submissionQuestionId(UUID submissionId, int index) {
		return new JdbcTemplate(dataSource).queryForObject("""
				select id from written_submission_questions
				where submission_id = ?
				order by position
				offset ? limit 1
				""", UUID.class, submissionId, index);
	}

	private void submitWritten() throws Exception {
		mockMvc
			.perform(
				org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.multipart("/api/v1/catalog/exam-modules/" + module + "/components/" + written + "/submission/images")
					.file(new MockMultipartFile("file", "a.jpg", "image/jpeg", tinyJpeg()))
					.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());
		mockMvc
			.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + written + "/submission/submit")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());
	}

	private void submitWrittenOn(UUID componentId) throws Exception {
		submitWrittenOn(componentId, student);
	}

	private void submitWrittenOn(UUID componentId, String token) throws Exception {
		mockMvc
			.perform(
				org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.multipart("/api/v1/catalog/exam-modules/" + module + "/components/" + componentId + "/submission/images")
					.file(new MockMultipartFile("file", "a.jpg", "image/jpeg", tinyJpeg()))
					.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		mockMvc
			.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + componentId + "/submission/submit")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
	}

	private void addQuestion(UUID componentId, int marks) throws Exception {
		mockMvc.perform(post("/api/v1/exam-components/" + componentId + "/questions").header("Authorization",
				"Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"stem":"Q","marks":%d,"subject":"S","explanation":"E","options":[{"text":"yes","correct":true},{"text":"no","correct":false}]}
					""".formatted(marks)))
			.andExpect(status().isCreated());
	}

	private void submitOneOf(UUID componentId, int questionIndex) throws Exception {
		MvcResult start = mockMvc
			.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + componentId + "/attempts")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode question = json.readTree(start.getResponse().getContentAsString()).get("questions").get(questionIndex);
		UUID attemptQuestionId = UUID.fromString(question.get("id").asText());
		UUID optionId = UUID.fromString(question.get("options").get(0).get("id").asText());
		mockMvc.perform(put("/api/v1/catalog/exam-modules/" + module + "/components/" + componentId + "/attempts/answers")
			.header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"answers":[{"attemptQuestionId":"%s","attemptOptionId":"%s"}]}
					""".formatted(attemptQuestionId, optionId)))
			.andExpect(status().isOk());
		mockMvc
			.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + componentId + "/attempts/submit")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());
	}

	private void submitMcq(UUID componentId, boolean allCorrect) throws Exception {
		MvcResult start = mockMvc
			.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + componentId + "/attempts")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode body = json.readTree(start.getResponse().getContentAsString());
		StringBuilder answers = new StringBuilder("{\"answers\":[");
		boolean first = true;
		for (JsonNode question : body.get("questions")) {
			UUID attemptQuestionId = UUID.fromString(question.get("id").asText());
			UUID optionId = UUID.fromString(question.get("options").get(allCorrect ? 0 : 1).get("id").asText());
			if (!first) {
				answers.append(',');
			}
			first = false;
			answers.append("{\"attemptQuestionId\":\"").append(attemptQuestionId).append("\",\"attemptOptionId\":\"")
				.append(optionId).append("\"}");
		}
		answers.append("]}");
		mockMvc.perform(put("/api/v1/catalog/exam-modules/" + module + "/components/" + componentId + "/attempts/answers")
			.header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content(answers.toString()))
			.andExpect(status().isOk());
		mockMvc
			.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + componentId + "/attempts/submit")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());
	}

	private void endMcq(UUID componentId) {
		Instant start = Instant.now().minusSeconds(7200);
		Instant end = Instant.now().minusSeconds(60);
		new JdbcTemplate(dataSource).update(
				"update paper_components set starts_at=?, ends_at=?, scheduled=true where id=?",
				Timestamp.from(start), Timestamp.from(end), componentId);
	}

	private UUID setupMcq(UUID paper, boolean immediate) throws Exception {
		UUID id = createComponent(paper, "MCQ");
		mockMvc.perform(patch("/api/v1/exam-papers/" + paper).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"passPercent\":50}"))
			.andExpect(status().isOk());
		Instant start = Instant.now().minusSeconds(3600);
		Instant end = Instant.now().plusSeconds(3600);
		mockMvc.perform(patch("/api/v1/exam-components/" + id).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"descriptionEn":"EN","descriptionBn":"BN","startsAt":"%s","endsAt":"%s","durationSeconds":3600,"immediateResults":%s}
					""".formatted(start, end, immediate)))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/exam-components/" + id + "/schedule").header("Authorization", "Bearer " + system))
			.andExpect(status().isOk());
		return id;
	}

	private UUID setupWritten(UUID paper) throws Exception {
		UUID id = createComponent(paper, "WRITTEN");
		mockMvc.perform(patch("/api/v1/exam-papers/" + paper).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"passPercent\":50}"))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/exam-components/" + id).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"descriptionEn":"EN","descriptionBn":"BN","deadlineAt":"2099-01-01T00:00:00Z"}
					"""))
			.andExpect(status().isOk());
		return id;
	}

	private void addMcqQuestions(UUID componentId) throws Exception {
		for (int marks : new int[] { 2, 2, 2 }) {
			mockMvc.perform(post("/api/v1/exam-components/" + componentId + "/questions").header("Authorization",
					"Bearer " + system)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"stem":"Q","marks":%d,"subject":"S","explanation":"E","options":[{"text":"yes","correct":true},{"text":"no","correct":false}]}
						""".formatted(marks)))
				.andExpect(status().isCreated());
		}
	}

	private void addWrittenQuestions(UUID componentId) throws Exception {
		mockMvc.perform(post("/api/v1/exam-components/" + componentId + "/questions").header("Authorization",
				"Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"stem\":\"W1\",\"marks\":4,\"subject\":\"S\",\"explanation\":\"E1\"}"))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/exam-components/" + componentId + "/questions").header("Authorization",
				"Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"stem\":\"W2\",\"marks\":6,\"subject\":\"S\",\"explanation\":\"E2\"}"))
			.andExpect(status().isCreated());
	}

	private static byte[] tinyJpeg() throws Exception {
		BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(Color.GREEN);
		g.fillRect(0, 0, 40, 40);
		g.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "jpg", out);
		return out.toByteArray();
	}

	private UUID createComponent(UUID paper, String type) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/exam-papers/" + paper + "/components").header("Authorization", "Bearer " + system)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\":\"" + type + "\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
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

	private void grantOpenAccess(String studentToken) throws Exception {
		mockMvc.perform(patch("/api/v1/exam-modules/" + module + "/access").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"accessMode\":\"OPEN\",\"courseId\":null}"))
			.andExpect(status().isOk());
	}

	private UUID publishOpenModule() throws Exception {
		MvcResult m = mockMvc.perform(post("/api/v1/exam-modules").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titleEn\":\"R\",\"titleBn\":\"ম\",\"descriptionEn\":\"d\",\"descriptionBn\":\"b\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		UUID id = UUID.fromString(json.readTree(m.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(patch("/api/v1/exam-modules/" + id).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"PUBLISHED\"}"))
			.andExpect(status().isOk());
		return id;
	}

	private String registerStudent() throws Exception {
		String phone = uniquePhone();
		String token = registerPhone(phone);
		studentUserId = new JdbcTemplate(dataSource).queryForObject("select id from users where phone = ?", UUID.class,
				phone);
		return token;
	}

	private String registerOther() throws Exception {
		return registerPhone(uniquePhone());
	}

	private String registerPhone(String phone) throws Exception {
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
		return "results-" + UUID.randomUUID() + "@example.com";
	}

}
