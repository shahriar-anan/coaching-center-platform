package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

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
class ExamComponentIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Test
	void componentConfigurationAndCombinedMaximum() throws Exception {
		String manage = token(seed("SYSTEM_ADMIN"));
		UUID module = createModule(manage);
		UUID paper = createNode(manage, module, """
				{"nodeType":"PAPER","titleEn":"Paper","titleBn":"পেপার"}
				""");
		UUID mcqId = createComponent(manage, paper, "MCQ");
		UUID writtenId = createComponent(manage, paper, "WRITTEN");

		mockMvc.perform(patch("/api/v1/exam-components/" + mcqId).header("Authorization", "Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "descriptionEn":"MCQ rules EN",
					  "descriptionBn":"MCQ rules BN",
					  "startsAt":"2030-01-01T10:00:00Z",
					  "endsAt":"2030-01-01T12:00:00Z",
					  "durationSeconds":3600
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.descriptionEn").value("MCQ rules EN"))
			.andExpect(jsonPath("$.descriptionBn").value("MCQ rules BN"))
			.andExpect(jsonPath("$.durationSeconds").value(3600))
			.andExpect(jsonPath("$.immediateResults").value(false));

		mockMvc.perform(patch("/api/v1/exam-components/" + mcqId).header("Authorization", "Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "descriptionEn":"Only EN",
					  "startsAt":"2030-02-01T10:00:00Z",
					  "endsAt":"2030-02-01T12:00:00Z",
					  "durationSeconds":60
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		mockMvc.perform(patch("/api/v1/exam-components/" + mcqId).header("Authorization", "Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "descriptionEn":"MCQ rules EN",
					  "descriptionBn":"MCQ rules BN",
					  "startsAt":"2030-03-01T12:00:00Z",
					  "endsAt":"2030-03-01T10:00:00Z",
					  "durationSeconds":60
					}
					"""))
			.andExpect(status().isBadRequest());

		mockMvc.perform(patch("/api/v1/exam-components/" + mcqId).header("Authorization", "Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "descriptionEn":"MCQ rules EN",
					  "descriptionBn":"MCQ rules BN",
					  "startsAt":"2030-03-01T10:00:00Z",
					  "endsAt":"2030-03-01T12:00:00Z",
					  "durationSeconds":0
					}
					"""))
			.andExpect(status().isBadRequest());

		mockMvc.perform(patch("/api/v1/exam-components/" + writtenId).header("Authorization", "Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "descriptionEn":"Written rules EN",
					  "descriptionBn":"Written rules BN",
					  "deadlineAt":"2030-06-01T23:59:00Z"
					}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PASS_MARK_REQUIRED"));

		mockMvc.perform(patch("/api/v1/exam-components/" + writtenId).header("Authorization", "Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"durationSeconds\":60}"))
			.andExpect(status().isBadRequest());

		mockMvc.perform(patch("/api/v1/exam-components/" + mcqId).header("Authorization", "Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"deadlineAt\":\"2030-01-01T00:00:00Z\"}"))
			.andExpect(status().isBadRequest());

		MvcResult moduleBefore = mockMvc
			.perform(get("/api/v1/exam-modules/" + module).header("Authorization", "Bearer " + manage))
			.andExpect(status().isOk())
			.andReturn();
		String moduleDescEn = json.readTree(moduleBefore.getResponse().getContentAsString()).get("descriptionEn").asText();

		mockMvc.perform(patch("/api/v1/exam-components/" + writtenId).header("Authorization", "Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "descriptionEn":"Written only EN",
					  "descriptionBn":"Written only BN"
					}
					"""))
			.andExpect(status().isOk());

		MvcResult moduleAfter = mockMvc
			.perform(get("/api/v1/exam-modules/" + module).header("Authorization", "Bearer " + manage))
			.andExpect(status().isOk())
			.andReturn();
		assertThat(json.readTree(moduleAfter.getResponse().getContentAsString()).get("descriptionEn").asText())
			.isEqualTo(moduleDescEn);

		mockMvc.perform(get("/api/v1/exam-components/" + mcqId).header("Authorization", "Bearer " + manage))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.descriptionEn").value("MCQ rules EN"));

		mockMvc.perform(patch("/api/v1/exam-papers/" + paper).header("Authorization", "Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"passPercent\":101}"))
			.andExpect(status().isBadRequest());

		UUID section = createNode(manage, module, """
				{"nodeType":"SECTION","titleEn":"Sec","titleBn":"সেক"}
				""");
		mockMvc.perform(patch("/api/v1/exam-papers/" + section).header("Authorization", "Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"passPercent\":50}"))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/v1/exam-components/" + mcqId + "/schedule").header("Authorization",
				"Bearer " + manage))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PASS_MARK_REQUIRED"));

		mockMvc.perform(patch("/api/v1/exam-papers/" + paper).header("Authorization", "Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"passPercent\":60}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.passPercent").value(60));

		mockMvc.perform(patch("/api/v1/exam-components/" + writtenId).header("Authorization", "Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "descriptionEn":"Written rules EN",
					  "descriptionBn":"Written rules BN",
					  "deadlineAt":"2030-06-01T23:59:00Z"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.deadlineAt").exists());

		Instant futureStart = Instant.now().plusSeconds(3600);
		Instant futureEnd = futureStart.plusSeconds(7200);
		new JdbcTemplate(dataSource).update("""
				UPDATE paper_components
				SET starts_at = ?, ends_at = ?, duration_seconds = 3600,
				    description_en = 'EN', description_bn = 'BN', scheduled = true
				WHERE id = ?
				""", Timestamp.from(futureStart), Timestamp.from(futureEnd), mcqId);

		mockMvc.perform(get("/api/v1/exam-components/" + mcqId).header("Authorization", "Bearer " + manage))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.lifecycle").value("SCHEDULED"));

		Instant activeStart = Instant.now().minusSeconds(60);
		Instant activeEnd = Instant.now().plusSeconds(3600);
		new JdbcTemplate(dataSource).update("UPDATE paper_components SET starts_at = ?, ends_at = ? WHERE id = ?",
				Timestamp.from(activeStart), Timestamp.from(activeEnd), mcqId);
		mockMvc.perform(get("/api/v1/exam-components/" + mcqId).header("Authorization", "Bearer " + manage))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.lifecycle").value("ACTIVE"));

		Instant endedStart = Instant.now().minusSeconds(7200);
		Instant endedEnd = Instant.now().minusSeconds(60);
		new JdbcTemplate(dataSource).update("UPDATE paper_components SET starts_at = ?, ends_at = ? WHERE id = ?",
				Timestamp.from(endedStart), Timestamp.from(endedEnd), mcqId);
		mockMvc.perform(get("/api/v1/exam-components/" + mcqId).header("Authorization", "Bearer " + manage))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.lifecycle").value("ENDED"));

		new JdbcTemplate(dataSource).update(
				"UPDATE paper_components SET scheduled = false, description_en = null WHERE id = ?", mcqId);
		mockMvc.perform(post("/api/v1/exam-components/" + mcqId + "/schedule").header("Authorization",
				"Bearer " + manage))
			.andExpect(status().isBadRequest());

		new JdbcTemplate(dataSource).update("""
				UPDATE paper_components
				SET description_en = 'EN', description_bn = 'BN',
				    starts_at = ?, ends_at = ?, duration_seconds = 60, scheduled = false
				WHERE id = ?
				""", Timestamp.from(futureStart), Timestamp.from(futureEnd), mcqId);
		mockMvc.perform(post("/api/v1/exam-components/" + mcqId + "/schedule").header("Authorization",
				"Bearer " + manage))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.scheduled").value(true));

		mockMvc.perform(post("/api/v1/exam-components/" + writtenId + "/schedule").header("Authorization",
				"Bearer " + manage))
			.andExpect(status().isBadRequest());

		addQuestion(manage, mcqId, mcqJson(2));
		addQuestion(manage, writtenId, writtenJson(5));

		mockMvc.perform(get("/api/v1/exam-papers/" + paper).header("Authorization", "Bearer " + manage))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.combinedMaxMarks").value(7))
			.andExpect(jsonPath("$.weight").doesNotExist());

		addQuestion(manage, mcqId, mcqJson(3));
		mockMvc.perform(get("/api/v1/exam-papers/" + paper).header("Authorization", "Bearer " + manage))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.combinedMaxMarks").value(10));

		UUID mcqOnlyPaper = createNode(manage, module, """
				{"nodeType":"PAPER","titleEn":"Solo","titleBn":"এক"}
				""");
		UUID soloMcq = createComponent(manage, mcqOnlyPaper, "MCQ");
		addQuestion(manage, soloMcq, mcqJson(1));
		mockMvc.perform(get("/api/v1/exam-papers/" + mcqOnlyPaper).header("Authorization", "Bearer " + manage))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.combinedMaxMarks").doesNotExist());
	}

	private void addQuestion(String token, UUID componentId, String body) throws Exception {
		mockMvc.perform(post("/api/v1/exam-components/" + componentId + "/questions").header("Authorization",
				"Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body))
			.andExpect(status().isCreated());
	}

	private UUID createComponent(String token, UUID paperId, String type) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/exam-papers/" + paperId + "/components").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\":\"" + type + "\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private static String mcqJson(int marks) {
		return """
				{
				  "stem":"MCQ %d",
				  "marks":%d,
				  "subject":"S",
				  "options":[{"text":"a","correct":true},{"text":"b","correct":false}]
				}
				""".formatted(marks, marks);
	}

	private static String writtenJson(int marks) {
		return """
				{"stem":"Written %d","marks":%d,"subject":"S"}
				""".formatted(marks, marks);
	}

	private UUID createModule(String token) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/exam-modules").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"Comp module","titleBn":"ম","descriptionEn":"mod en","descriptionBn":"mod bn"}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID createNode(String token, UUID moduleId, String body) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/exam-modules/" + moduleId + "/nodes").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
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
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return "+8801" + suffix;
	}

	private static String uniqueEmail() {
		return "exam-c-" + UUID.randomUUID() + "@example.com";
	}

}
