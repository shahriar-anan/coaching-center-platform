package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import javax.sql.DataSource;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The student's {@code access} object and the other phase 2 additions to the public catalog, with checkout on. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, PublicAccessIT.GatewayConfig.class })
class PublicAccessIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Test
	void aSignedInStudentSeesTheirAccessStateAndWhyTheyCannotBuy() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		UUID category = createCategory(master, "Access cat", null);
		UUID module = publishedModule(master, category, "Access module", "900.00");
		UUID priced = publishedExam(master, module, "Priced", "200.00", "2099-03-01T10:00:00+06:00");
		UUID unpriced = publishedExam(master, module, "Unpriced", null, "2099-03-02T10:00:00+06:00");

		for (String path : new String[] { "/api/v1/public/live-exam-modules/" + module, "/api/v1/public/live-exams/" + priced }) {
			assertThat(read(null, path).has("access")).isFalse();
			assertThat(read(master, path).has("access")).isFalse();
		}
		assertThat(read(null, "/api/v1/public/live-exam-modules/" + module + "/exams").get("content").get(0).has("access"))
			.isFalse();
		assertThat(read(null, "/api/v1/public/live-exam-modules?categoryId=" + category).get("content").get(0).has("access"))
			.isFalse();
		assertThat(read("not-a-token", "/api/v1/public/live-exams/" + priced).has("access")).isFalse();

		assertAccess(moduleAccess(student, module), "NONE", true, null);
		assertAccess(examAccess(student, priced), "NONE", true, null);
		assertAccess(examAccess(student, unpriced), "NONE", false, "PRICE_REQUIRED");
		JsonNode listed = read(student, "/api/v1/public/live-exam-modules/" + module + "/exams").get("content");
		assertAccess(byId(listed, priced).get("access"), "NONE", true, null);
		assertAccess(byId(listed, unpriced).get("access"), "NONE", false, "PRICE_REQUIRED");
		JsonNode modules = read(student, "/api/v1/public/live-exam-modules?categoryId=" + category).get("content");
		assertAccess(byId(modules, module).get("access"), "NONE", true, null);

		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		UUID examGrant = grant(jdbc, studentId, "EXAM", module, priced, order(jdbc, studentId, module, priced, "EXAM"),
				"ORDER");
		assertAccess(examAccess(student, priced), "EXAM", false, "ALREADY_HAS_ACCESS");
		assertAccess(moduleAccess(student, module), "NONE", true, null);
		jdbc.update("update live_exam_grants set source = 'MANUAL', order_id = null where id = ?", examGrant);
		assertAccess(examAccess(student, priced), "MANUAL", false, "ALREADY_HAS_ACCESS");
		revoke(jdbc, examGrant);
		assertAccess(examAccess(student, priced), "NONE", true, null);

		UUID moduleGrant = grant(jdbc, studentId, "MODULE", module, null, order(jdbc, studentId, module, null, "MODULE"),
				"ORDER");
		assertAccess(moduleAccess(student, module), "MODULE", false, "ALREADY_HAS_ACCESS");
		assertAccess(examAccess(student, priced), "MODULE", false, "ALREADY_HAS_ACCESS");
		assertAccess(examAccess(student, unpriced), "MODULE", false, "ALREADY_HAS_ACCESS");
		revoke(jdbc, moduleGrant);
		UUID manualModule = grant(jdbc, studentId, "MODULE", module, null, null, "MANUAL");
		assertAccess(moduleAccess(student, module), "MANUAL", false, "ALREADY_HAS_ACCESS");
		assertAccess(examAccess(student, unpriced), "MANUAL", false, "ALREADY_HAS_ACCESS");
		revoke(jdbc, manualModule);
		assertAccess(moduleAccess(student, module), "NONE", true, null);

		jdbc.update("insert into live_exam_roster (module_id, student_id, created_at) values (?, ?, now())", module, studentId);
		grant(jdbc, studentId, "MODULE", module, null, null, "MANUAL");
		assertAccess(moduleAccess(student, module), "ROSTER", false, "ALREADY_HAS_ACCESS");
		assertAccess(examAccess(student, priced), "ROSTER", false, "ALREADY_HAS_ACCESS");

		UUID phoneless = seed("STUDENT");
		String phonelessToken = token(phoneless);
		jdbc.update("update users set phone = null where id = ?", phoneless);
		assertAccess(moduleAccess(phonelessToken, module), "NONE", false, "PHONE_REQUIRED");
		assertAccess(examAccess(phonelessToken, priced), "NONE", false, "PHONE_REQUIRED");
		assertAccess(examAccess(phonelessToken, unpriced), "NONE", false, "PRICE_REQUIRED");
	}

	@Test
	void checkoutIsReportedOnWhenTheGatewayIsConfigured() throws Exception {
		String body = mockMvc.perform(get("/api/v1/public/exam-site/status"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(json.readTree(body).get("checkoutEnabled").asBoolean()).isTrue();
		assertThat(json.readTree(body).size()).isEqualTo(1);
	}

	@Test
	void aPublicModuleCarriesItsCategoryPathRootFirst() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID root = createCategory(master, "Path root", null);
		UUID middle = createCategory(master, "Path middle", root);
		UUID leaf = createCategory(master, "Path leaf", middle);
		UUID module = publishedModule(master, leaf, "Path module", "300.00");
		UUID top = publishedModule(master, root, "Top module", "300.00");

		JsonNode path = read(null, "/api/v1/public/live-exam-modules/" + module).get("categoryPath");
		assertThat(path.size()).isEqualTo(3);
		assertThat(path.get(0).get("id").asText()).isEqualTo(root.toString());
		assertThat(path.get(0).get("titleEn").asText()).isEqualTo("Path root");
		assertThat(path.get(0).get("titleBn").asText()).isEqualTo("বিভাগ Path root");
		assertThat(path.get(1).get("id").asText()).isEqualTo(middle.toString());
		assertThat(path.get(2).get("id").asText()).isEqualTo(leaf.toString());
		JsonNode listed = byId(read(null, "/api/v1/public/live-exam-modules?categoryId=" + leaf).get("content"), module);
		assertThat(listed.get("categoryPath")).isEqualTo(path);
		JsonNode searched = byId(read(null, "/api/v1/public/live-exam-modules?q=Path%20module&size=100").get("content"),
				module);
		assertThat(searched.get("categoryPath")).isEqualTo(path);
		JsonNode topPath = read(null, "/api/v1/public/live-exam-modules/" + top).get("categoryPath");
		assertThat(topPath.size()).isEqualTo(1);
		assertThat(topPath.get(0).get("id").asText()).isEqualTo(root.toString());
	}

	private static void assertAccess(JsonNode access, String state, boolean buyable, String blockedReason) {
		assertThat(access).as("access").isNotNull();
		assertThat(access.get("state").asText()).isEqualTo(state);
		assertThat(access.get("buyable").asBoolean()).isEqualTo(buyable);
		assertThat(access.has("blockedReason")).as("blockedReason is always written").isTrue();
		if (blockedReason == null) {
			assertThat(access.get("blockedReason").isNull()).isTrue();
		}
		else {
			assertThat(access.get("blockedReason").asText()).isEqualTo(blockedReason);
		}
	}

	private JsonNode moduleAccess(String token, UUID moduleId) throws Exception {
		return read(token, "/api/v1/public/live-exam-modules/" + moduleId).get("access");
	}

	private JsonNode examAccess(String token, UUID examId) throws Exception {
		return read(token, "/api/v1/public/live-exams/" + examId).get("access");
	}

	private JsonNode read(String token, String path) throws Exception {
		var request = get(path);
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return json.readTree(mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString());
	}

	private static JsonNode byId(JsonNode rows, UUID id) {
		for (JsonNode row : rows) {
			if (id.toString().equals(row.get("id").asText())) {
				return row;
			}
		}
		throw new AssertionError("Row not found: " + id);
	}

	private static UUID order(JdbcTemplate jdbc, UUID studentId, UUID module, UUID exam, String scope) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into live_exam_orders (id, student_id, scope, module_id, exam_id, amount, currency, method, tran_id,
				                              status, created_at, updated_at)
				values (?, ?, ?, ?, ?, 100.00, 'BDT', 'SSLCOMMERZ', ?, 'SUCCEEDED', now(), now())
				""", id, studentId, scope, module, exam, "PA-" + id.toString().substring(0, 18));
		return id;
	}

	private static UUID grant(JdbcTemplate jdbc, UUID studentId, String scope, UUID module, UUID exam, UUID order,
			String source) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into live_exam_grants (id, student_id, scope, module_id, exam_id, order_id, source, created_at)
				values (?, ?, ?, ?, ?, ?, ?, now())
				""", id, studentId, scope, module, exam, order, source);
		return id;
	}

	private static void revoke(JdbcTemplate jdbc, UUID grantId) {
		jdbc.update("update live_exam_grants set revoked_at = now() where id = ?", grantId);
	}

	private UUID createCategory(String token, String title, UUID parentId) throws Exception {
		String parent = parentId == null ? "" : ",\"parentId\":\"" + parentId + "\"";
		MvcResult result = mockMvc.perform(post("/api/v1/live-exam-categories").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"%s","titleBn":"বিভাগ %s"%s}
					""".formatted(title, title, parent)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID publishedModule(String token, UUID categoryId, String title, String price) throws Exception {
		String priceField = price == null ? "" : ",\"price\":" + price;
		MvcResult created = mockMvc.perform(post("/api/v1/live-exam-modules").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"categoryId":"%s","titleEn":"%s","titleBn":"মডিউল","descriptionEn":"d","descriptionBn":"বি"%s}
					""".formatted(categoryId, title, priceField)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID module = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/final-save").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/publish").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		return module;
	}

	private UUID publishedExam(String token, UUID moduleId, String title, String price, String liveAt) throws Exception {
		String priceField = price == null ? "" : ",\"price\":" + price;
		MvcResult created = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + moduleId + "/exams").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "examType":"MCQ","paperLanguage":"BN","titleEn":"%s","titleBn":"পরীক্ষা",
						  "descriptionEn":"d","descriptionBn":"বি","rulesEn":"r","rulesBn":"র",
						  "liveAt":"%s","durationMinutes":40,"passPercent":50%s
						}
						""".formatted(title, liveAt, priceField)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID exam = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/questions").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"stem":"Stem","marks":1,"options":[{"text":"Yes","correct":true},{"text":"No","correct":false}]}
					"""))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/final-save").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		return exam;
	}

	private UUID seed(String role) {
		UUID id = UUID.randomUUID();
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, uniquePhone(), "public-access-" + id + "@example.com", passwords.encode("password1"), role);
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

	private static String uniquePhone() {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return "+8801" + suffix;
	}

	@TestConfiguration
	static class GatewayConfig {

		@Bean
		@Primary
		FakeLiveExamPaymentGateway fakeGateway() {
			return new FakeLiveExamPaymentGateway();
		}

	}

}
