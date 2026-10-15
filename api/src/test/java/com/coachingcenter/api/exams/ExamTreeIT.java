package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class ExamTreeIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Test
	void contentTreeRules() throws Exception {
		String manage = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID module = createModule(manage);

		UUID rootSection = createNode(manage, module, sectionJson("Root section", "মূল বিভাগ"));
		UUID nestedSection = createNode(manage, module, """
				{"parentId":"%s","nodeType":"SECTION","titleEn":"Nested section","titleBn":"নেস্টেড"}
				""".formatted(rootSection));
		UUID nestedPaper = createNode(manage, module, """
				{"parentId":"%s","nodeType":"PAPER","titleEn":"Nested paper","titleBn":"নেস্টেড পেপার"}
				""".formatted(nestedSection));
		UUID secondRoot = createNode(manage, module, sectionJson("Second root", "দ্বিতীয়"));
		mockMvc.perform(post("/api/v1/exam-modules/" + module + "/nodes/reorder").header("Authorization", "Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"nodeIds":["%s","%s"]}
					""".formatted(secondRoot, rootSection)))
			.andExpect(status().isNoContent());
		UUID rootPaper = createNode(manage, module, """
				{"nodeType":"PAPER","titleEn":"Root paper","titleBn":"রুট পেপার"}
				""");

		MvcResult listResult = mockMvc
			.perform(get("/api/v1/exam-modules/" + module + "/nodes").header("Authorization", "Bearer " + manage))
			.andExpect(status().isOk())
			.andReturn();
		for (JsonNode node : json.readTree(listResult.getResponse().getContentAsString())) {
			assertThat(node.has("descriptionEn")).isFalse();
			assertThat(node.has("description")).isFalse();
			assertThat(node.get("titleEn").asText()).isNotBlank();
			assertThat(node.get("titleBn").asText()).isNotBlank();
		}

		mockMvc.perform(patch("/api/v1/exam-modules/" + module + "/nodes/" + rootSection).header("Authorization",
				"Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"parentId":"%s"}
					""".formatted(nestedPaper)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("NODE_TYPE_REJECTED"));
		mockMvc.perform(patch("/api/v1/exam-modules/" + module + "/nodes/" + rootSection).header("Authorization",
				"Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"parentId":"%s"}
					""".formatted(nestedSection)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("NODE_TYPE_REJECTED"));
		mockMvc.perform(post("/api/v1/exam-modules/" + module + "/nodes").header("Authorization", "Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"nodeType":"EXAM","titleEn":"Bad","titleBn":"খারাপ"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("NODE_TYPE_REJECTED"));

		mockMvc.perform(delete("/api/v1/exam-modules/" + module + "/nodes/" + rootPaper).header("Authorization",
				"Bearer " + manage))
			.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/exam-modules/" + module + "/nodes/" + rootPaper).header("Authorization",
				"Bearer " + master))
			.andExpect(status().isNoContent());
	}

	@Test
	void sectionDeleteAndPaperMove() throws Exception {
		String manage = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID module = createModule(manage);

		UUID emptyParent = createNode(manage, module, sectionJson("Empty parent", "খালি"));
		UUID emptyChild = createNode(manage, module, """
				{"parentId":"%s","nodeType":"SECTION","titleEn":"Empty child","titleBn":"খালি চাইল্ড"}
				""".formatted(emptyParent));
		mockMvc.perform(delete("/api/v1/exam-modules/" + module + "/nodes/" + emptyParent).header("Authorization",
				"Bearer " + manage))
			.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/exam-modules/" + module + "/nodes/" + emptyParent).header("Authorization",
				"Bearer " + master))
			.andExpect(status().isNoContent());
		assertThat(nodeIds(manage, module)).doesNotContain(emptyParent, emptyChild);

		UUID blocked = createNode(manage, module, sectionJson("Has paper", "পেপার আছে"));
		UUID directPaper = createNode(manage, module, """
				{"parentId":"%s","nodeType":"PAPER","titleEn":"Direct","titleBn":"সরাসরি"}
				""".formatted(blocked));
		mockMvc.perform(delete("/api/v1/exam-modules/" + module + "/nodes/" + blocked).header("Authorization",
				"Bearer " + master))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SECTION_NOT_EMPTY"));
		assertThat(nodeIds(manage, module)).contains(blocked, directPaper);

		UUID outer = createNode(manage, module, sectionJson("Outer", "বাইরে"));
		UUID inner = createNode(manage, module, """
				{"parentId":"%s","nodeType":"SECTION","titleEn":"Inner","titleBn":"ভিতরে"}
				""".formatted(outer));
		UUID nestedPaper = createNode(manage, module, """
				{"parentId":"%s","nodeType":"PAPER","titleEn":"Nested","titleBn":"নেস্টেড"}
				""".formatted(inner));
		mockMvc.perform(delete("/api/v1/exam-modules/" + module + "/nodes/" + outer).header("Authorization",
				"Bearer " + master))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SECTION_NOT_EMPTY"));
		assertThat(nodeIds(manage, module)).contains(outer, inner, nestedPaper);

		UUID sectionA = createNode(manage, module, sectionJson("Section A", "এ"));
		UUID moving = createNode(manage, module, """
				{"parentId":"%s","nodeType":"PAPER","titleEn":"Moving","titleBn":"সরছে"}
				""".formatted(sectionA));
		UUID sectionB = createNode(manage, module, sectionJson("Section B", "বি"));
		createNode(manage, module, """
				{"parentId":"%s","nodeType":"PAPER","titleEn":"Staying","titleBn":"থাকছে"}
				""".formatted(sectionB));
		mockMvc.perform(patch("/api/v1/exam-modules/" + module + "/nodes/" + moving).header("Authorization",
				"Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"parentId":"%s"}
					""".formatted(sectionB)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.parentId").value(sectionB.toString()))
			.andExpect(jsonPath("$.position").value(1));
		mockMvc.perform(patch("/api/v1/exam-modules/" + module + "/nodes/" + moving).header("Authorization",
				"Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"parentId\":null}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.parentId").doesNotExist())
			.andExpect(jsonPath("$.position").value(4));
	}

	private java.util.Set<UUID> nodeIds(String token, UUID moduleId) throws Exception {
		MvcResult result = mockMvc
			.perform(get("/api/v1/exam-modules/" + moduleId + "/nodes").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn();
		java.util.Set<UUID> ids = new java.util.HashSet<>();
		for (JsonNode node : json.readTree(result.getResponse().getContentAsString())) {
			ids.add(UUID.fromString(node.get("id").asText()));
		}
		return ids;
	}

	private UUID createModule(String token) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/exam-modules").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "titleEn":"Tree module",
					  "titleBn":"ট্রি",
					  "descriptionEn":"d",
					  "descriptionBn":"b"
					}
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

	private static String sectionJson(String titleEn, String titleBn) {
		return """
				{"nodeType":"SECTION","titleEn":"%s","titleBn":"%s"}
				""".formatted(titleEn, titleBn);
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
		return "exam-tree-" + UUID.randomUUID() + "@example.com";
	}

}
