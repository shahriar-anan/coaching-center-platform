package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Setup for the NR-01 package and catalog tests: accounts, categories, packages, exams and their publish steps, and the
 * roster rows that give a student a package. The assertions stay in each test class.
 */
final class PackageFixtures {

	private final MockMvc mockMvc;

	private final ObjectMapper json;

	private final JdbcTemplate jdbc;

	private final PasswordEncoder passwords;

	PackageFixtures(MockMvc mockMvc, ObjectMapper json, DataSource dataSource, PasswordEncoder passwords) {
		this.mockMvc = mockMvc;
		this.json = json;
		this.jdbc = new JdbcTemplate(dataSource);
		this.passwords = passwords;
	}

	UUID seed(String role) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, uniquePhone(), uniqueEmail(), passwords.encode("password1"), role);
		if ("STUDENT".equals(role)) {
			jdbc.update("INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, 'Seed', ?)", id,
					"S" + id.toString().substring(0, 8));
		}
		else {
			jdbc.update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Admin')", id);
		}
		return id;
	}

	String token(UUID userId) throws Exception {
		String phone = jdbc.queryForObject("select phone from users where id = ?", String.class, userId);
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

	UUID createCategory(String token, String titleEn) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/live-exam-categories").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"%s","titleBn":"বিভাগ"}
					""".formatted(titleEn)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	UUID createPackage(String token, UUID categoryId, String titleEn) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/live-exam-modules").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"categoryId":"%s","titleEn":"%s","titleBn":"প্যাকেজ","descriptionEn":"d","descriptionBn":"বি"}
					""".formatted(categoryId, titleEn)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	/** A draft MCQ exam inside a package. {@code liveAt} may be null for an undated exam. */
	UUID createExam(String token, UUID packageId, String titleEn, String liveAt) throws Exception {
		return createExam(token, packageId, "MCQ", titleEn, liveAt);
	}

	/** A draft exam of either type inside a package. A written exam takes a maximum mark. */
	UUID createExam(String token, UUID packageId, String examType, String titleEn, String liveAt) throws Exception {
		String live = liveAt == null ? "" : ",\"liveAt\":\"" + liveAt + "\"";
		String maximum = "WRITTEN".equals(examType) ? ",\"maximum\":20" : "";
		MvcResult result = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + packageId + "/exams").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "examType":"%s","paperLanguage":"BN","titleEn":"%s","titleBn":"খাতা",
						  "descriptionEn":"English description","descriptionBn":"বাংলা বিবরণ",
						  "rulesEn":"English rules","rulesBn":"বাংলা নিয়ম",
						  "durationMinutes":40,"passPercent":50%s%s
						}
						""".formatted(examType, titleEn, live, maximum)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	/** Runs a statement on the test database, for the states the API does not reach yet (a practice exam, a staged row). */
	void sql(String statement, Object... args) {
		jdbc.update(statement, args);
	}

	void addQuestion(String token, UUID examId) throws Exception {
		mockMvc.perform(post("/api/v1/live-exams/" + examId + "/questions").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"stem":"Which?","marks":1,"options":[{"text":"Right","correct":true},{"text":"Wrong","correct":false}]}
					"""))
			.andExpect(status().isCreated());
	}

	/** Final-saves and publishes a draft exam as the Master Admin, who alone publishes an exam. */
	void publishExam(String token, UUID examId) throws Exception {
		mockMvc.perform(post("/api/v1/live-exams/" + examId + "/final-save").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		String master = token(seed("MASTER_ADMIN"));
		mockMvc.perform(post("/api/v1/live-exams/" + examId + "/publish").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"));
	}

	void finalSaveAndPublish(String token, UUID packageId) throws Exception {
		mockMvc.perform(post("/api/v1/live-exam-modules/" + packageId + "/final-save").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + packageId + "/publish").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"));
	}

	/** Gives a student a roster row on the package, created at {@code createdAt} (the grant start, R4). */
	void markRoster(UUID packageId, UUID studentId, Instant createdAt) {
		jdbc.update("insert into live_exam_roster (module_id, student_id, created_at) values (?, ?, ?)", packageId,
				studentId, java.sql.Timestamp.from(createdAt));
	}

	/** Reads a public (or staff) list and returns its {@code content} array. */
	JsonNode content(String token, String path) throws Exception {
		MvcResult result = mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString()).get("content");
	}

	JsonNode body(String token, String path) throws Exception {
		MvcResult result = mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	static List<UUID> ids(JsonNode nodes) {
		List<UUID> ids = new ArrayList<>();
		if (nodes == null || !nodes.isArray()) {
			return ids;
		}
		for (JsonNode node : nodes) {
			ids.add(UUID.fromString(node.get("id").asText()));
		}
		return ids;
	}

	static void assertOrder(List<UUID> actual, UUID... expected) {
		assertThat(actual).containsExactly(expected);
	}

	private static String uniquePhone() {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return "+8801" + suffix;
	}

	private static String uniqueEmail() {
		return "nr01-b-" + UUID.randomUUID() + "@example.com";
	}

}
