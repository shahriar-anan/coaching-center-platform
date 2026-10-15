package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
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
class ExamAnalyticsIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	private String system;

	private String student;

	private UUID module;

	private UUID paper;

	private UUID mcq;

	@BeforeEach
	void seed() throws Exception {
		system = token(seed("SYSTEM_ADMIN"));
		student = registerStudent();
		module = publishOpenModule();
		grantOpenAccess(student);
		paper = createPaper();
		mcq = setupMcq(paper);
		addMcqQuestion(mcq, 5);
		addMcqQuestion(mcq, 3);
	}

	@Test
	void leaderboardAndQuestionStats() throws Exception {
		String s2 = registerStudent("S2");
		String s3 = registerStudent("S3");
		grantOpenAccess(s2);
		grantOpenAccess(s3);

		registerStudent("Absent");
		submitMcq(student, true, true);
		submitMcq(s2, true, true);
		submitMcq(s3, true, false);

		endMcq(mcq);
		MvcResult adminBoard = mockMvc.perform(get("/api/v1/exam-papers/" + paper + "/leaderboard").header("Authorization", "Bearer " + system))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(3))
			.andExpect(jsonPath("$.content[0].rank").value(1))
			.andExpect(jsonPath("$.content[0].score").value(8))
			.andExpect(jsonPath("$.content[1].rank").value(1))
			.andExpect(jsonPath("$.content[1].score").value(8))
			.andExpect(jsonPath("$.content[2].rank").value(3))
			.andExpect(jsonPath("$.content[2].score").value(5))
			.andExpect(jsonPath("$.content[0].phone").doesNotExist())
			.andExpect(jsonPath("$.content[0].email").doesNotExist())
			.andReturn();

		mockMvc
			.perform(get("/api/v1/catalog/exam-modules/" + module + "/papers/" + paper + "/leaderboard")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("RESULTS_NOT_PUBLISHED"));

		mockMvc.perform(post("/api/v1/exam-components/" + mcq + "/results/publish").header("Authorization",
				"Bearer " + system))
			.andExpect(status().isNoContent());

		MvcResult studentBoard = mockMvc
			.perform(get("/api/v1/catalog/exam-modules/" + module + "/papers/" + paper + "/leaderboard")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(3))
			.andExpect(jsonPath("$.content[2].rank").value(3))
			.andReturn();
		assertThat(json.readTree(studentBoard.getResponse().getContentAsString()).get("content"))
			.isEqualTo(json.readTree(adminBoard.getResponse().getContentAsString()).get("content"));

		mockMvc
			.perform(get("/api/v1/catalog/exam-modules/" + module + "/papers/" + paper + "/result")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rank").value(1))
			.andExpect(jsonPath("$.cohortSize").value(3));

		MvcResult stats = mockMvc
			.perform(get("/api/v1/exam-components/" + mcq + "/question-stats").header("Authorization", "Bearer " + system))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode rows = json.readTree(stats.getResponse().getContentAsString());
		assertThat(rows).anySatisfy(row -> {
			assertThat(row.get("attempts").asInt()).isEqualTo(3);
			assertThat(row.get("correctCount").asInt()).isEqualTo(2);
			assertThat(row.get("correctPercent").asInt()).isEqualTo(67);
		});

		mockMvc
			.perform(get("/api/v1/exam-components/" + mcq + "/question-stats").header("Authorization", "Bearer " + student))
			.andExpect(status().isForbidden());
	}

	@Test
	void optionStatsWrittenAveragesAndImmediateBoard() throws Exception {
		String correctStudent = registerStudent("Correct");
		String wrongStudent = registerStudent("Wrong");
		String sitting = registerStudent("Sitting");
		UUID statsPaper = createPaper();
		UUID statsMcq = setupMcq(statsPaper);
		MvcResult created = mockMvc
			.perform(post("/api/v1/exam-components/" + statsMcq + "/questions").header("Authorization", "Bearer " + system)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"stem":"Pick","marks":4,"subject":"S","explanation":"E","options":[{"text":"yes","correct":true},{"text":"no","correct":false}]}
						"""))
			.andExpect(status().isCreated())
			.andReturn();
		UUID liveQuestionId = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		submitSingle(statsMcq, correctStudent, true);
		submitSingle(statsMcq, wrongStudent, false);
		mockMvc.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + statsMcq + "/attempts")
			.header("Authorization", "Bearer " + sitting))
			.andExpect(status().isOk());

		MvcResult stats = mockMvc
			.perform(get("/api/v1/exam-components/" + statsMcq + "/question-stats").header("Authorization", "Bearer " + system))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode row = json.readTree(stats.getResponse().getContentAsString()).get(0);
		assertThat(row.get("attempts").asInt()).isEqualTo(2);
		assertThat(row.get("correctCount").asInt()).isEqualTo(1);
		assertThat(row.get("correctPercent").asInt()).isEqualTo(50);
		assertThat(row.get("options")).anySatisfy(option -> {
			assertThat(option.get("text").asText()).isEqualTo("yes");
			assertThat(option.get("selectedCount").asInt()).isEqualTo(1);
		});
		assertThat(row.get("options")).anySatisfy(option -> {
			assertThat(option.get("text").asText()).isEqualTo("no");
			assertThat(option.get("selectedCount").asInt()).isEqualTo(1);
		});

		mockMvc.perform(patch("/api/v1/questions/" + liveQuestionId).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"options":[{"text":"later-a","correct":true},{"text":"later-b","correct":false}]}
					"""))
			.andExpect(status().isOk());
		MvcResult afterDelete = mockMvc
			.perform(get("/api/v1/exam-components/" + statsMcq + "/question-stats").header("Authorization", "Bearer " + system))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode frozenOptions = json.readTree(afterDelete.getResponse().getContentAsString()).get(0).get("options");
		assertThat(frozenOptions).anySatisfy(option -> assertThat(option.get("text").asText()).isEqualTo("yes"));

		UUID immediatePaper = createPaper();
		UUID immediate = setupImmediate(immediatePaper);
		addMcqQuestion(immediate, 5);
		submitSingle(immediate, student, true);
		mockMvc
			.perform(get("/api/v1/catalog/exam-modules/" + module + "/papers/" + immediatePaper + "/leaderboard")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("RESULTS_NOT_PUBLISHED"));
		mockMvc
			.perform(get("/api/v1/catalog/exam-modules/" + module + "/components/" + immediate + "/attempts")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(5));

		UUID writtenPaper = createPaper();
		mockMvc.perform(patch("/api/v1/exam-papers/" + writtenPaper).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"passPercent\":50}"))
			.andExpect(status().isOk());
		UUID written = createComponent(writtenPaper, "WRITTEN");
		mockMvc.perform(patch("/api/v1/exam-components/" + written).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"descriptionEn":"EN","descriptionBn":"BN","deadlineAt":"2099-01-01T00:00:00Z"}
					"""))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/exam-components/" + written + "/questions").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"stem\":\"W\",\"marks\":4,\"subject\":\"S\",\"explanation\":\"E\"}"))
			.andExpect(status().isCreated());
		submitWritten(written, correctStudent);
		submitWritten(written, wrongStudent);
		submitWritten(written, sitting);
		java.util.List<UUID> pending = new JdbcTemplate(dataSource).query("""
				select id from written_submissions
				where component_id = ? and status = 'UNDER_REVIEW'
				order by created_at
				""", (rs, n) -> UUID.fromString(rs.getString(1)), written);
		assertThat(pending).hasSize(3);
		evaluate(written, pending.get(0), 2);
		evaluate(written, pending.get(1), 4);

		MvcResult writtenStats = mockMvc
			.perform(get("/api/v1/exam-components/" + written + "/question-stats").header("Authorization", "Bearer " + system))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode writtenRow = json.readTree(writtenStats.getResponse().getContentAsString()).get(0);
		assertThat(writtenRow.get("evaluatedCount").asInt()).isEqualTo(2);
		assertThat(new java.math.BigDecimal(writtenRow.get("averageMarks").asText())).isEqualByComparingTo("3");
		assertThat(writtenRow.get("averagePercent").asInt()).isEqualTo(75);
	}

	private void submitSingle(UUID componentId, String token, boolean correct) throws Exception {
		MvcResult start = mockMvc
			.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + componentId + "/attempts")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode question = json.readTree(start.getResponse().getContentAsString()).get("questions").get(0);
		UUID attemptQuestionId = UUID.fromString(question.get("id").asText());
		UUID optionId = UUID.fromString(question.get("options").get(correct ? 0 : 1).get("id").asText());
		mockMvc.perform(put("/api/v1/catalog/exam-modules/" + module + "/components/" + componentId + "/attempts/answers")
			.header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"answers":[{"attemptQuestionId":"%s","attemptOptionId":"%s"}]}
					""".formatted(attemptQuestionId, optionId)))
			.andExpect(status().isOk());
		mockMvc
			.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + componentId + "/attempts/submit")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
	}

	private void submitWritten(UUID componentId, String token) throws Exception {
		mockMvc
			.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.multipart("/api/v1/catalog/exam-modules/" + module + "/components/" + componentId + "/submission/images")
				.file(new MockMultipartFile("file", "a.jpg", "image/jpeg", tinyJpeg()))
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		mockMvc
			.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + componentId + "/submission/submit")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
	}

	private void evaluate(UUID componentId, UUID submissionId, int marks) throws Exception {
		UUID questionId = new JdbcTemplate(dataSource).queryForObject("""
				select id from written_submission_questions where submission_id = ? order by position limit 1
				""", UUID.class, submissionId);
		mockMvc
			.perform(post("/api/v1/exam-components/" + componentId + "/submissions/" + submissionId + "/evaluation")
				.header("Authorization", "Bearer " + system)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"feedback":"Noted","marks":[{"submissionQuestionId":"%s","marks":%d}]}
						""".formatted(questionId, marks)))
			.andExpect(status().isOk());
	}

	private UUID setupImmediate(UUID paperId) throws Exception {
		UUID id = createComponent(paperId, "MCQ");
		mockMvc.perform(patch("/api/v1/exam-papers/" + paperId).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"passPercent\":50}"))
			.andExpect(status().isOk());
		Instant start = Instant.now().minusSeconds(3600);
		Instant end = Instant.now().plusSeconds(3600);
		mockMvc.perform(patch("/api/v1/exam-components/" + id).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"descriptionEn":"EN","descriptionBn":"BN","startsAt":"%s","endsAt":"%s","durationSeconds":3600,"immediateResults":true}
					""".formatted(start, end)))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/exam-components/" + id + "/schedule").header("Authorization", "Bearer " + system))
			.andExpect(status().isOk());
		return id;
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

	private void submitMcq(String token, boolean firstCorrect, boolean secondCorrect) throws Exception {
		MvcResult start = mockMvc
			.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + mcq + "/attempts")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode body = json.readTree(start.getResponse().getContentAsString());
		StringBuilder answers = new StringBuilder("{\"answers\":[");
		for (int i = 0; i < 2; i++) {
			JsonNode question = body.get("questions").get(i);
			UUID attemptQuestionId = UUID.fromString(question.get("id").asText());
			boolean correct = i == 0 ? firstCorrect : secondCorrect;
			UUID optionId = UUID.fromString(question.get("options").get(correct ? 0 : 1).get("id").asText());
			if (i > 0) {
				answers.append(',');
			}
			answers.append("{\"attemptQuestionId\":\"").append(attemptQuestionId).append("\",\"attemptOptionId\":\"")
				.append(optionId).append("\"}");
		}
		answers.append("]}");
		mockMvc.perform(put("/api/v1/catalog/exam-modules/" + module + "/components/" + mcq + "/attempts/answers")
			.header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(answers.toString()))
			.andExpect(status().isOk());
		mockMvc
			.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + mcq + "/attempts/submit")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
	}

	private void endMcq(UUID componentId) {
		new JdbcTemplate(dataSource).update(
				"update paper_components set starts_at=?, ends_at=?, scheduled=true where id=?",
				Timestamp.from(Instant.now().minusSeconds(7200)), Timestamp.from(Instant.now().minusSeconds(60)),
				componentId);
	}

	private void addMcqQuestion(UUID componentId, int marks) throws Exception {
		mockMvc.perform(post("/api/v1/exam-components/" + componentId + "/questions").header("Authorization",
				"Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"stem":"Q%s","marks":%d,"subject":"S","explanation":"E","options":[{"text":"yes","correct":true},{"text":"no","correct":false}]}
					""".formatted(marks, marks)))
			.andExpect(status().isCreated());
	}

	private UUID setupMcq(UUID paperId) throws Exception {
		UUID id = createComponent(paperId, "MCQ");
		mockMvc.perform(patch("/api/v1/exam-papers/" + paperId).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"passPercent\":50}"))
			.andExpect(status().isOk());
		Instant start = Instant.now().minusSeconds(3600);
		Instant end = Instant.now().plusSeconds(3600);
		mockMvc.perform(patch("/api/v1/exam-components/" + id).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"descriptionEn":"EN","descriptionBn":"BN","startsAt":"%s","endsAt":"%s","durationSeconds":3600,"immediateResults":false}
					""".formatted(start, end)))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/exam-components/" + id + "/schedule").header("Authorization", "Bearer " + system))
			.andExpect(status().isOk());
		return id;
	}

	private UUID createComponent(UUID paperId, String type) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/exam-papers/" + paperId + "/components").header("Authorization", "Bearer " + system)
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

	private void grantOpenAccess(String token) throws Exception {
		mockMvc.perform(patch("/api/v1/exam-modules/" + module + "/access").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"accessMode\":\"OPEN\",\"courseId\":null}"))
			.andExpect(status().isOk());
	}

	private UUID publishOpenModule() throws Exception {
		MvcResult m = mockMvc.perform(post("/api/v1/exam-modules").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titleEn\":\"A\",\"titleBn\":\"ম\",\"descriptionEn\":\"d\",\"descriptionBn\":\"b\"}"))
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
		return registerStudent("Student");
	}

	private String registerStudent(String name) throws Exception {
		String phone = uniquePhone();
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"%s","phone":"%s","email":"%s","password":"password1"}
					""".formatted(name, phone, uniqueEmail())))
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
		return "analytics-" + UUID.randomUUID() + "@example.com";
	}

}
