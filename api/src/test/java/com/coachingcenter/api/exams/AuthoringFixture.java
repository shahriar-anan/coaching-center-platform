package com.coachingcenter.api.exams;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Shared set-up for the NR-01 authoring tests: users, categories, packages, exams, questions and the publish steps. */
final class AuthoringFixture {

	static final String LIVE_AT = "2026-10-20T12:00:00+06:00";

	final MockMvc mockMvc;

	final DataSource dataSource;

	private final PasswordEncoder passwords;

	private final ObjectMapper json;

	AuthoringFixture(MockMvc mockMvc, DataSource dataSource, PasswordEncoder passwords, ObjectMapper json) {
		this.mockMvc = mockMvc;
		this.dataSource = dataSource;
		this.passwords = passwords;
		this.json = json;
	}

	JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}

	UUID seed(String role) {
		UUID id = UUID.randomUUID();
		jdbc().update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, "+8801" + java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999),
				"authoring-" + UUID.randomUUID() + "@example.com", passwords.encode("password1"), role);
		if ("STUDENT".equals(role)) {
			jdbc().update("INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, 'Seed', ?)", id,
					"S" + id.toString().substring(0, 8));
		}
		else {
			jdbc().update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Admin')", id);
		}
		return id;
	}

	String token(UUID userId) throws Exception {
		String phone = jdbc().queryForObject("select phone from users where id = ?", String.class, userId);
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

	String master() throws Exception {
		return token(seed("MASTER_ADMIN"));
	}

	String staff() throws Exception {
		return token(seed("SYSTEM_ADMIN"));
	}

	UUID category(String token, String title) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/live-exam-categories").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"%s","titleBn":"বিভাগ"}
					""".formatted(title)))
			.andExpect(status().isCreated())
			.andReturn();
		return id(result);
	}

	/** A published package in a new category. */
	UUID module(String token, String title) throws Exception {
		return moduleIn(token, category(token, title + " cat"), title);
	}

	UUID moduleIn(String token, UUID category, String title) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/live-exam-modules").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"categoryId":"%s","titleEn":"%s","titleBn":"মডিউল","descriptionEn":"d","descriptionBn":"বি"}
					""".formatted(category, title)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID module = id(created);
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/final-save").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/publish").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		return module;
	}

	/** The create body of an MCQ exam in the 12:00 window; {@code extra} is spliced in as more JSON members. */
	static String examBody(String type, String title, String liveAt, int duration, String extra) {
		String when = liveAt == null ? "" : ",\"liveAt\":\"" + liveAt + "\"";
		String more = extra == null || extra.isBlank() ? "" : "," + extra;
		String maximum = "WRITTEN".equals(type) && (extra == null || !extra.contains("maximum")) ? ",\"maximum\":10" : "";
		return """
				{"examType":"%s","paperLanguage":"BN","titleEn":"%s","titleBn":"পরীক্ষা",
				 "descriptionEn":"English description","descriptionBn":"বাংলা বিবরণ",
				 "rulesEn":"English rules","rulesBn":"বাংলা নিয়ম","durationMinutes":%d,"passPercent":50%s%s%s}
				""".formatted(type, title, duration, when, maximum, more);
	}

	UUID createExam(String token, UUID module, String type, String title, String liveAt, int duration, String extra)
			throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + module + "/exams").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(examBody(type, title, liveAt, duration, extra)))
			.andExpect(status().isCreated())
			.andReturn();
		return id(result);
	}

	UUID createStandalone(String token, UUID category, String type, String title, String liveAt, int duration,
			String extra) throws Exception {
		String more = "\"categoryId\":\"" + category + "\"" + (extra == null || extra.isBlank() ? "" : "," + extra);
		MvcResult result = mockMvc.perform(post("/api/v1/live-exams").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(examBody(type, title, liveAt, duration, more)))
			.andExpect(status().isCreated())
			.andReturn();
		return id(result);
	}

	static String questionJson(String stem, int marks, String explanation, boolean multiple, String... optionsAndMarks) {
		StringBuilder options = new StringBuilder();
		for (String option : optionsAndMarks) {
			boolean correct = option.startsWith("*");
			if (options.length() > 0) {
				options.append(',');
			}
			options.append("{\"text\":\"").append(correct ? option.substring(1) : option).append("\",\"correct\":")
				.append(correct).append('}');
		}
		String explanationField = explanation == null ? "" : ",\"explanation\":\"" + explanation + "\"";
		return "{\"stem\":\"%s\",\"marks\":%d%s,\"multipleAnswer\":%s,\"options\":[%s]}".formatted(stem, marks,
				explanationField, multiple, options);
	}

	/** A single-answer question with a correct "A" and a wrong "B". */
	UUID question(String token, UUID exam, String stem, int marks) throws Exception {
		return question(token, exam, questionJson(stem, marks, "Because", false, "*A", "B"));
	}

	UUID question(String token, UUID exam, String body) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/live-exams/" + exam + "/questions")
			.header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body))
			.andExpect(status().isCreated())
			.andReturn();
		return id(result);
	}

	void uploadPdf(String token, UUID exam) throws Exception {
		mockMvc.perform(multipart("/api/v1/live-exams/" + exam + "/question-pdf")
			.file(new MockMultipartFile("file", "paper.pdf", "application/pdf", tinyPdf("one")))
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questionPdf").value(true));
	}

	void uploadPdf(String token, UUID exam, String marker) throws Exception {
		mockMvc.perform(multipart("/api/v1/live-exams/" + exam + "/question-pdf")
			.file(new MockMultipartFile("file", "paper-" + marker + ".pdf", "application/pdf", tinyPdf(marker)))
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
	}

	static byte[] tinyPdf(String marker) {
		return ("%PDF-1.1\n1 0 obj<</Type/Catalog>>endobj\n% " + marker + "\ntrailer<</Root 1 0 R>>\n%%EOF\n")
			.getBytes(StandardCharsets.US_ASCII);
	}

	void finalSave(String token, UUID exam) throws Exception {
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/final-save").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
	}

	/** Final-saves and publishes a draft exam as the Master Admin. */
	void publishNew(String masterToken, UUID exam) throws Exception {
		finalSave(masterToken, exam);
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + masterToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"));
	}

	JsonNode exam(String token, UUID exam) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	void grantModule(UUID module, UUID student) {
		jdbc().update("insert into live_exam_roster (module_id, student_id, created_at) values (?, ?, now())", module,
				student);
	}

	UUID id(MvcResult result) throws Exception {
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

}
