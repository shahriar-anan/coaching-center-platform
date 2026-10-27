package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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

/**
 * Archive hides a published exam from students and keeps it readable for staff (2026-10-27).
 * Only the Master Admin archives. An archived exam cannot be changed or published.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class LiveExamArchiveIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Test
	void masterArchivesAPublishedExamAndStudentsLoseItWhileStaffStillReadIt() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		String systemAdmin = token(seed("SYSTEM_ADMIN"));
		UUID module = publishModule(master, "Archive module");
		UUID exam = createMcqExam(master, module, "Archive me", "আর্কাইভ");
		addQuestion(master, exam);
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/final-save").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"));
		assertThat(publicStatus(exam)).isEqualTo(200);

		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/archive").header("Authorization", "Bearer " + systemAdmin))
			.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/archive").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ARCHIVED"));

		assertThat(publicStatus(exam)).isEqualTo(404);
		assertThat(publicModuleExamIds(module)).doesNotContain(exam);

		mockMvc.perform(get("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ARCHIVED"));
	}

	@Test
	void anArchivedExamCannotBeChangedOrPublishedAndArchivingTwiceIsHarmless() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID module = publishModule(master, "Archive edit module");
		UUID exam = createMcqExam(master, module, "Locked after archive", "বন্ধ");

		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/archive").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/archive").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ARCHIVED"));

		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titleEn\":\"Renamed\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EXAM_ARCHIVED"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EXAM_ARCHIVED"));
	}

	private int publicStatus(UUID exam) throws Exception {
		return mockMvc.perform(get("/api/v1/public/live-exams/" + exam)).andReturn().getResponse().getStatus();
	}

	private java.util.List<UUID> publicModuleExamIds(UUID module) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/public/live-exam-modules/" + module + "/exams")).andReturn();
		JsonNode body = json.readTree(result.getResponse().getContentAsString());
		JsonNode content = body.isArray() ? body : body.get("content");
		java.util.List<UUID> ids = new java.util.ArrayList<>();
		if (content != null) {
			content.forEach(row -> ids.add(UUID.fromString(row.get("id").asText())));
		}
		return ids;
	}

	private UUID publishModule(String token, String title) throws Exception {
		UUID category = createCategory(token, title + " cat", "বিভাগ");
		MvcResult created = mockMvc.perform(post("/api/v1/live-exam-modules").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"categoryId":"%s","titleEn":"%s","titleBn":"মডিউল","descriptionEn":"d","descriptionBn":"বি"}
					""".formatted(category, title)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID module = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/final-save").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/publish").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		return module;
	}

	private UUID createCategory(String token, String titleEn, String titleBn) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/live-exam-categories").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"%s","titleBn":"%s"}
					""".formatted(titleEn, titleBn)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID createMcqExam(String token, UUID moduleId, String titleEn, String titleBn) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + moduleId + "/exams").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "examType":"MCQ",
						  "paperLanguage":"BN",
						  "titleEn":"%s",
						  "titleBn":"%s",
						  "descriptionEn":"English description",
						  "descriptionBn":"বাংলা বিবরণ",
						  "rulesEn":"English rules",
						  "rulesBn":"বাংলা নিয়ম",
						  "liveAt":"2026-12-20T12:00:00+06:00",
						  "durationMinutes":30,
						  "passPercent":40
						}
						""".formatted(titleEn, titleBn)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private void addQuestion(String token, UUID exam) throws Exception {
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/questions").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"stem":"Stem","marks":1,"options":[{"text":"A","correct":true},{"text":"B","correct":false}]}
					"""))
			.andExpect(status().isCreated());
	}

	private UUID seed(String role) {
		UUID id = UUID.randomUUID();
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, uniquePhone(), uniqueEmail(), passwords.encode("password1"), role);
		new JdbcTemplate(dataSource).update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Admin')", id);
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

	private static String uniquePhone() {
		return "+8801" + String.format("%09d", ThreadLocalRandom.current().nextInt(1_000_000_000));
	}

	private static String uniqueEmail() {
		return "archive-" + UUID.randomUUID() + "@example.test";
	}

}
