package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
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
class ExamQuestionIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Test
	void questionRecordAndAttach() throws Exception {
		String manage = token(seed("SYSTEM_ADMIN"));
		UUID paper = createPaper(manage);

		String mcqComponent = mockMvc
			.perform(post("/api/v1/exam-papers/" + paper + "/components").header("Authorization", "Bearer " + manage)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\":\"MCQ\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		UUID mcqId = UUID.fromString(json.readTree(mcqComponent).get("id").asText());

		String writtenComponent = mockMvc
			.perform(post("/api/v1/exam-papers/" + paper + "/components").header("Authorization", "Bearer " + manage)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\":\"WRITTEN\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		UUID writtenId = UUID.fromString(json.readTree(writtenComponent).get("id").asText());

		mockMvc.perform(post("/api/v1/exam-papers/" + paper + "/components").header("Authorization", "Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\":\"MCQ\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("COMPONENT_EXISTS"));

		UUID moduleForSection = createModule(manage);
		UUID section = createNode(manage, moduleForSection, """
				{"nodeType":"SECTION","titleEn":"S","titleBn":"বিভাগ"}
				""");
		mockMvc.perform(post("/api/v1/exam-papers/" + section + "/components").header("Authorization", "Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\":\"MCQ\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("NODE_TYPE_REJECTED"));

		assertThat(columns("questions")).doesNotContain("stem_en", "stem_bn", "description_en");

		String mcqQuestion = mockMvc
			.perform(post("/api/v1/exam-components/" + mcqId + "/questions").header("Authorization", "Bearer " + manage)
				.contentType(MediaType.APPLICATION_JSON)
				.content(mcqJson()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.stem").value("What is 2+2?"))
			.andExpect(jsonPath("$.options[0].correct").value(true))
			.andReturn()
			.getResponse()
			.getContentAsString();
		UUID questionId = UUID.fromString(json.readTree(mcqQuestion).get("id").asText());

		mockMvc.perform(post("/api/v1/exam-components/" + writtenId + "/questions").header("Authorization",
				"Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content(writtenJson()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.options").isEmpty());

		mockMvc.perform(post("/api/v1/exam-components/" + mcqId + "/questions").header("Authorization", "Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"stem\":\"x\",\"marks\":1,\"subject\":\"s\",\"options\":[{\"text\":\"a\",\"correct\":true}]}"))
			.andExpect(status().isBadRequest());

		UUID paper2 = createPaper(manage);
		String mcq2 = mockMvc
			.perform(post("/api/v1/exam-papers/" + paper2 + "/components").header("Authorization", "Bearer " + manage)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\":\"MCQ\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		UUID mcq2Id = UUID.fromString(json.readTree(mcq2).get("id").asText());

		mockMvc.perform(post("/api/v1/exam-components/" + mcq2Id + "/questions/" + questionId).header("Authorization",
				"Bearer " + manage))
			.andExpect(status().isNoContent());

		mockMvc.perform(patch("/api/v1/questions/" + questionId).header("Authorization", "Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"stem\":\"Updated stem\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.stem").value("Updated stem"));

		mockMvc.perform(get("/api/v1/exam-components/" + mcq2Id).header("Authorization", "Bearer " + manage))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions[0].stem").value("Updated stem"));

		mockMvc.perform(post("/api/v1/exam-components/" + writtenId + "/questions/" + questionId).header("Authorization",
				"Bearer " + manage))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("QUESTION_TYPE_MISMATCH"));

		mockMvc.perform(delete("/api/v1/exam-components/" + mcqId + "/questions/" + questionId).header("Authorization",
				"Bearer " + manage))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/exam-components/" + mcq2Id).header("Authorization", "Bearer " + manage))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions[0].id").value(questionId.toString()));

		mockMvc.perform(get("/api/v1/questions/" + questionId).header("Authorization", "Bearer " + manage))
			.andExpect(status().isOk());

		String master = token(seed("MASTER_ADMIN"));
		mockMvc.perform(delete("/api/v1/exam-modules/" + moduleIdForPaper(paper) + "/nodes/" + paper).header("Authorization",
				"Bearer " + master))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/exam-components/" + mcqId).header("Authorization", "Bearer " + manage))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/questions/" + questionId).header("Authorization", "Bearer " + manage))
			.andExpect(status().isOk());
	}

	private UUID moduleIdForPaper(UUID paperId) {
		return new JdbcTemplate(dataSource).queryForObject("select module_id from exam_nodes where id = ?", UUID.class,
				paperId);
	}

	private UUID createPaper(String token) throws Exception {
		UUID module = createModule(token);
		return createNode(token, module, """
				{"nodeType":"PAPER","titleEn":"Paper","titleBn":"পেপার"}
				""");
	}

	private UUID createModule(String token) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/exam-modules").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"Q module","titleBn":"প্র","descriptionEn":"d","descriptionBn":"b"}
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

	private Set<String> columns(String table) {
		return new JdbcTemplate(dataSource).queryForList(
				"select column_name from information_schema.columns where table_schema = 'public' and table_name = ?",
				String.class, table)
			.stream()
			.collect(java.util.stream.Collectors.toSet());
	}

	private static String mcqJson() {
		return """
				{
				  "stem":"What is 2+2?",
				  "marks":1,
				  "subject":"Math",
				  "options":[
				    {"text":"4","correct":true},
				    {"text":"5","correct":false}
				  ]
				}
				""";
	}

	private static String writtenJson() {
		return """
				{"stem":"Explain gravity","marks":5,"subject":"Physics","explanation":"brief"}
				""";
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
		return "exam-q-" + UUID.randomUUID() + "@example.com";
	}

}
