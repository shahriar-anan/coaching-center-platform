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
class ExamModuleIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Test
	void moduleRecordLifecycleAndRules() throws Exception {
		assertThat(columns("exam_modules")).doesNotContain("access_duration", "expiry_date", "expires_at");

		String systemAdmin = token(seed("SYSTEM_ADMIN"));
		UUID masterActor = seed("MASTER_ADMIN");
		String masterAdmin = token(masterActor);

		String created = mockMvc
			.perform(post("/api/v1/exam-modules").header("Authorization", "Bearer " + systemAdmin)
				.contentType(MediaType.APPLICATION_JSON)
				.content(moduleJson()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.titleEn").value("English Module"))
			.andExpect(jsonPath("$.titleBn").value("বাংলা মডিউল"))
			.andExpect(jsonPath("$.descriptionEn").value("Overview EN"))
			.andExpect(jsonPath("$.descriptionBn").value("সংক্ষিপ্ত বিবরণ"))
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		UUID moduleId = UUID.fromString(json.readTree(created).get("id").asText());

		mockMvc.perform(get("/api/v1/exam-modules/" + moduleId).header("Authorization", "Bearer " + systemAdmin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.descriptionEn").value("Overview EN"))
			.andExpect(jsonPath("$.descriptionBn").value("সংক্ষিপ্ত বিবরণ"));

		mockMvc.perform(patch("/api/v1/exam-modules/" + moduleId).header("Authorization", "Bearer " + systemAdmin)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":1500}"))
			.andExpect(status().isForbidden());

		mockMvc.perform(patch("/api/v1/exam-modules/" + moduleId).header("Authorization", "Bearer " + masterAdmin)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":1500}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.price").value(1500));

		UUID noPrice = createModule(masterAdmin, moduleJson());
		mockMvc.perform(patch("/api/v1/exam-modules/" + noPrice).header("Authorization", "Bearer " + masterAdmin)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"PUBLISHED\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"));

		mockMvc.perform(post("/api/v1/exam-modules").header("Authorization", "Bearer " + systemAdmin)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"Only EN","titleBn":"   ","descriptionEn":"d","descriptionBn":"b"}
					"""))
			.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/v1/exam-modules").header("Authorization", "Bearer " + systemAdmin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content").isArray())
			.andExpect(jsonPath("$.page").exists())
			.andExpect(jsonPath("$.size").exists())
			.andExpect(jsonPath("$.totalElements").exists())
			.andExpect(jsonPath("$.totalPages").exists());

		mockMvc.perform(get("/api/v1/exam-modules")).andExpect(status().isUnauthorized());

		UUID toDelete = createModule(masterAdmin, moduleJson());
		mockMvc.perform(delete("/api/v1/exam-modules/" + toDelete).header("Authorization", "Bearer " + systemAdmin))
			.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/exam-modules/" + toDelete).header("Authorization", "Bearer " + masterAdmin))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/exam-modules/" + toDelete).header("Authorization", "Bearer " + masterAdmin))
			.andExpect(status().isNotFound());
		Integer auditDelete = new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from audit_log where action = 'EXAM_MODULE_DELETE' and entity_type = 'EXAM_MODULE' and entity_id = ? and actor_user_id = ?",
				Integer.class, toDelete, masterActor);
		assertThat(auditDelete).isEqualTo(1);

		UUID toArchive = createModule(masterAdmin, moduleJson());
		mockMvc.perform(post("/api/v1/exam-modules/" + toArchive + "/archive").header("Authorization", "Bearer " + systemAdmin))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/exam-modules/" + toArchive + "/archive").header("Authorization", "Bearer " + masterAdmin))
			.andExpect(status().isNoContent());
		Integer auditArchive = new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from audit_log where action = 'EXAM_MODULE_ARCHIVE' and entity_type = 'EXAM_MODULE' and entity_id = ? and actor_user_id = ?",
				Integer.class, toArchive, masterActor);
		assertThat(auditArchive).isEqualTo(1);
	}

	private UUID createModule(String token, String body) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/exam-modules").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body))
			.andExpect(status().isCreated())
			.andReturn();
		JsonNode node = json.readTree(result.getResponse().getContentAsString());
		return UUID.fromString(node.get("id").asText());
	}

	private Set<String> columns(String table) {
		return new JdbcTemplate(dataSource).queryForList(
				"select column_name from information_schema.columns where table_schema = 'public' and table_name = ?",
				String.class, table)
			.stream()
			.collect(java.util.stream.Collectors.toSet());
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

	private static String moduleJson() {
		return """
				{
				  "titleEn":"English Module",
				  "titleBn":"বাংলা মডিউল",
				  "descriptionEn":"Overview EN",
				  "descriptionBn":"সংক্ষিপ্ত বিবরণ"
				}
				""";
	}

	private static String uniquePhone() {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return "+8801" + suffix;
	}

	private static String uniqueEmail() {
		return "exam-" + UUID.randomUUID() + "@example.com";
	}

}
