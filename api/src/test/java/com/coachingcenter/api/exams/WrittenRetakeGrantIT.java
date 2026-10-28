package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;
import com.coachingcenter.api.common.audit.AuditRecorder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Written retake grants (NR-01 D2, R14): one retake, granted on a finished result by staff, revocable while unused. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, WrittenRetakeGrantIT.ClockConfig.class })
class WrittenRetakeGrantIT extends PostgresIntegrationTest {

	private static final Instant TEN_AM = Instant.parse("2026-10-20T04:00:00Z");

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
	private LiveExamDeadlineJob deadlineJob;

	@BeforeEach
	void setClock() {
		clock.set(TEN_AM);
	}

	@Test
	void aWrittenRetakeNeedsAGrantAndTheGrantNeedsAFinishedResult() throws Exception {
		String system = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID module = publishModule(system, "Grant");
		UUID exam = writtenExam(system, module);
		UUID studentId = seed("STUDENT");
		mark(master, module, studentId);
		String student = token(studentId);

		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
		// No grant: a retake is refused, even while the first sitting is still open.
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/retake").header("Authorization", "Bearer " + student))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RETAKE_NOT_ALLOWED"));
		// A grant needs a finished attempt; the open one is not finished.
		grant(system, exam, studentId).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RETAKE_NOT_ALLOWED"));

		// The sitting's deadline (06:50) passes with no PDF: that is a finished, failed-to-submit attempt.
		clock.set(Instant.parse("2026-10-20T06:50:00Z"));
		deadlineJob.settleDue();
		grant(system, exam, studentId).andExpect(status().isCreated())
			.andExpect(jsonPath("$.used").value(false))
			.andExpect(jsonPath("$.reason").value("Lost the first sheet"));
		grant(system, exam, studentId).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("GRANT_ALREADY_ACTIVE"));

		clock.set(Instant.parse("2026-10-20T07:00:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/retake").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.kind").value("RETAKE"))
			.andExpect(jsonPath("$.attemptKind").value("RETAKE"))
			.andExpect(jsonPath("$.attemptNo").value(2));
		// The grant is used: a new one is refused, and so is a second retake.
		grant(system, exam, studentId).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RETAKE_NOT_ALLOWED"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/retake").header("Authorization", "Bearer " + student))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RETAKE_NOT_ALLOWED"));
	}

	@Test
	void aGrantCanBeRevokedWhileUnusedAndAnInstructorCannotGrant() throws Exception {
		String system = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		String instructor = token(seed("INSTRUCTOR"));
		UUID module = publishModule(system, "Revoke");
		UUID exam = writtenExam(system, module);
		UUID studentId = seed("STUDENT");
		mark(master, module, studentId);
		String student = token(studentId);
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
		clock.set(Instant.parse("2026-10-20T06:50:00Z"));
		deadlineJob.settleDue();

		grant(instructor, exam, studentId).andExpect(status().isForbidden());
		UUID grantId = UUID.fromString(json.readTree(grant(system, exam, studentId).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(delete("/api/v1/live-exams/" + exam + "/retake-grants/" + grantId)
			.header("Authorization", "Bearer " + system)).andExpect(status().isNoContent());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/retake").header("Authorization", "Bearer " + student))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RETAKE_NOT_ALLOWED"));

		Integer grants = new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from audit_log where action = ?", Integer.class, AuditRecorder.LIVE_EXAM_RETAKE_GRANT);
		Integer revokes = new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from audit_log where action = ?", Integer.class, AuditRecorder.LIVE_EXAM_RETAKE_REVOKE);
		assertThat(grants).isGreaterThanOrEqualTo(1);
		assertThat(revokes).isGreaterThanOrEqualTo(1);
	}

	@Test
	void theStudentResultAndTheStaffRowsShowTheGrant() throws Exception {
		String system = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID module = publishModule(system, "Rows");
		UUID exam = writtenExam(system, module);
		UUID studentId = seed("STUDENT");
		mark(master, module, studentId);
		String student = token(studentId);
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
		clock.set(Instant.parse("2026-10-20T06:50:00Z"));
		deadlineJob.settleDue();
		grant(system, exam, studentId).andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/results").header("Authorization", "Bearer " + system))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rows.content[0].retakeGrant.used").value(false))
			.andExpect(jsonPath("$.rows.content[0].attemptNo").value(1));
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/result").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.retakeGrant.used").value(false));
	}

	private org.springframework.test.web.servlet.ResultActions grant(String token, UUID exam, UUID studentId) throws Exception {
		return mockMvc.perform(post("/api/v1/live-exams/" + exam + "/retake-grants").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"studentId":"%s","reason":"Lost the first sheet"}
					""".formatted(studentId)));
	}

	private UUID publishModule(String token, String title) throws Exception {
		MvcResult category = mockMvc.perform(post("/api/v1/live-exam-categories").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"%s cat","titleBn":"বিভাগ"}
					""".formatted(title)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID categoryId = UUID.fromString(json.readTree(category.getResponse().getContentAsString()).get("id").asText());
		MvcResult created = mockMvc.perform(post("/api/v1/live-exam-modules").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"categoryId":"%s","titleEn":"%s","titleBn":"মডিউল","descriptionEn":"d","descriptionBn":"বি"}
					""".formatted(categoryId, title)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID module = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/final-save").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/publish").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		return module;
	}

	/** A published written exam with a paper, window 06:00 to 06:40 UTC (12:00 to 12:40 in Dhaka). */
	private UUID writtenExam(String staff, UUID module) throws Exception {
		MvcResult created = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + module + "/exams").header("Authorization", "Bearer " + staff)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "examType":"WRITTEN","paperLanguage":"BN","titleEn":"Noon paper","titleBn":"খাতা",
						  "descriptionEn":"d","descriptionBn":"বি","rulesEn":"English rules","rulesBn":"নিয়ম",
						  "liveAt":"2026-10-20T12:00:00+06:00","durationMinutes":40,"passPercent":50,"maximum":10
						}
						"""))
			.andExpect(status().isCreated())
			.andReturn();
		UUID exam = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		MockMultipartFile paper = new MockMultipartFile("file", "paper.pdf", "application/pdf", tinyPdf());
		mockMvc.perform(multipart("/api/v1/live-exams/" + exam + "/question-pdf").file(paper).header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/final-save").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + token(seed("MASTER_ADMIN"))))
			.andExpect(status().isOk());
		return exam;
	}

	private void mark(String master, UUID module, UUID studentId) throws Exception {
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/roster").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"studentId":"%s"}
					""".formatted(studentId)))
			.andExpect(status().isCreated());
	}

	private UUID seed(String role) {
		UUID id = UUID.randomUUID();
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, uniquePhone(), uniqueEmail(), passwords.encode("password1"), role);
		if ("STUDENT".equals(role)) {
			new JdbcTemplate(dataSource).update(
					"INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, 'Seed', ?)", id,
					"S" + id.toString().substring(0, 8));
		}
		else {
			new JdbcTemplate(dataSource).update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Admin')", id);
		}
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = new JdbcTemplate(dataSource).queryForObject("select phone from users where id = ?", String.class,
				userId);
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"password1"}
					""".formatted(phone)))
			.andExpect(status().isOk())
			.andReturn();
		String body = result.getResponse().getContentAsString();
		String marker = "\"accessToken\":\"";
		int start = body.indexOf(marker) + marker.length();
		return body.substring(start, body.indexOf('"', start));
	}

	private static byte[] tinyPdf() {
		return """
				%PDF-1.1
				1 0 obj<</Type/Catalog>>endobj
				trailer<</Root 1 0 R>>
				%%EOF
				""".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
	}

	private static String uniquePhone() {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return "+8801" + suffix;
	}

	private static String uniqueEmail() {
		return "grant-" + UUID.randomUUID() + "@example.com";
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
