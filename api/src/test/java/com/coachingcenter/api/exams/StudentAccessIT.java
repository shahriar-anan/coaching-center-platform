package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class StudentAccessIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Test
	void staffSeeEveryReasonAStudentCanSitAndNobodyElseCan() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String system = token(seed("SYSTEM_ADMIN", "Admin"));
		String instructor = token(seed("INSTRUCTOR", "Teacher"));
		UUID studentId = seedStudent();
		String student = token(studentId);
		UUID module = createModule(master, "Access " + UUID.randomUUID().toString().substring(0, 8));
		UUID examA = createExam(master, module, "Exam A");
		UUID examB = createExam(master, module, "Exam B");
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		jdbc.update("insert into live_exam_roster (module_id, student_id, created_at) values (?, ?, now())", module, studentId);
		String moduleTran = "ACC-M-" + UUID.randomUUID().toString().substring(0, 12);
		String examTran = "ACC-E-" + UUID.randomUUID().toString().substring(0, 12);
		UUID moduleOrder = order(jdbc, studentId, module, null, "MODULE", moduleTran);
		jdbc.update("insert into live_exam_grants (id, student_id, scope, module_id, exam_id, order_id, source, created_at) "
				+ "values (gen_random_uuid(), ?, 'MODULE', ?, null, ?, 'ORDER', now())", studentId, module, moduleOrder);
		UUID examOrder = order(jdbc, studentId, module, examA, "EXAM", examTran);
		jdbc.update("insert into live_exam_grants (id, student_id, scope, module_id, exam_id, order_id, source, created_at) "
				+ "values (gen_random_uuid(), ?, 'EXAM', ?, ?, ?, 'ORDER', now())", studentId, module, examA, examOrder);

		MvcResult result = mockMvc.perform(get("/api/v1/students/" + studentId + "/live-exam-access")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.content[0].via").value("EXAM_PURCHASE"))
			.andExpect(jsonPath("$.content[0].moduleId").value(module.toString()))
			.andExpect(jsonPath("$.content[0].exams.length()").value(1))
			.andExpect(jsonPath("$.content[0].exams[0].id").value(examA.toString()))
			.andExpect(jsonPath("$.content[0].exams[0].titleEn").value("Exam A"))
			.andExpect(jsonPath("$.content[0].orderId").value(examOrder.toString()))
			.andExpect(jsonPath("$.content[0].tranId").value(examTran))
			.andExpect(jsonPath("$.content[1].via").value("MODULE_PURCHASE"))
			.andExpect(jsonPath("$.content[1].exams.length()").value(0))
			.andExpect(jsonPath("$.content[1].orderId").value(moduleOrder.toString()))
			.andExpect(jsonPath("$.content[2].via").value("ROSTER"))
			.andExpect(jsonPath("$.content[2].orderId").doesNotExist())
			.andExpect(jsonPath("$.content[2].tranId").doesNotExist())
			.andExpect(jsonPath("$.content[2].moduleTitleEn").exists())
			.andExpect(jsonPath("$.content[0].expired").value(false))
			.andExpect(jsonPath("$.content[1].expired").value(false))
			.andExpect(jsonPath("$.content[2].expired").value(false))
			.andReturn();
		assertThat(result.getResponse().getContentAsString()).doesNotContain(examB.toString());

		mockMvc.perform(get("/api/v1/students/" + studentId + "/live-exam-access").header("Authorization", "Bearer " + system))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(3));
		mockMvc.perform(get("/api/v1/students/" + studentId + "/live-exam-access").header("Authorization", "Bearer " + system)
			.param("size", "1")
			.param("page", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].via").value("ROSTER"));
		mockMvc.perform(get("/api/v1/students/" + UUID.randomUUID() + "/live-exam-access").header("Authorization", "Bearer " + master))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		mockMvc.perform(get("/api/v1/students/" + studentId + "/live-exam-access").header("Authorization", "Bearer " + instructor))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		mockMvc.perform(get("/api/v1/students/" + studentId + "/live-exam-access").header("Authorization", "Bearer " + student))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/students/" + studentId + "/live-exam-access")).andExpect(status().isUnauthorized());

		// Deleting the package ends its roster and package rows; the bought exam stays the student's (R5).
		mockMvc.perform(delete("/api/v1/live-exam-modules/" + module).header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/students/" + studentId + "/live-exam-access").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].via").value("EXAM_PURCHASE"))
			.andExpect(jsonPath("$.content[0].exams[0].id").value(examA.toString()));
	}

	@Test
	void aStudentSeesOnlyTheirOwnActiveAccessWithItsReason() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID studentId = seedStudent();
		String student = token(studentId);
		UUID otherId = seedStudent();
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		UUID rostered = createModule(master, "Rostered " + suffix);
		UUID bought = createModule(master, "Bought " + suffix);
		UUID granted = createModule(master, "Granted " + suffix);
		UUID revoked = createModule(master, "Revoked " + suffix);
		UUID single = createModule(master, "Single " + suffix);
		UUID examBought = createExam(master, single, "Bought exam");
		UUID examGranted = createExam(master, single, "Granted exam");
		UUID examBundled = createExam(master, single, "Bundled exam");
		UUID examRevoked = createExam(master, single, "Revoked exam");
		UUID otherExam = createExam(master, single, "Other exam");
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		jdbc.update("insert into live_exam_roster (module_id, student_id, created_at) values (?, ?, now())", rostered, studentId);
		UUID moduleOrder = order(jdbc, studentId, bought, null, "MODULE", "ME-M-" + suffix);
		grant(jdbc, studentId, "MODULE", bought, null, moduleOrder, "ORDER", false);
		grant(jdbc, studentId, "MODULE", granted, null, null, "MANUAL", false);
		grant(jdbc, studentId, "MODULE", revoked, null, order(jdbc, studentId, revoked, null, "MODULE", "ME-R-" + suffix),
				"ORDER", true);
		UUID examOrder = order(jdbc, studentId, single, examBought, "EXAM", "ME-E-" + suffix);
		grant(jdbc, studentId, "EXAM", single, examBought, examOrder, "ORDER", false);
		grant(jdbc, studentId, "EXAM", single, examGranted, null, "MANUAL", false);
		UUID bundleOrder = order(jdbc, studentId, single, null, "BUNDLE", "ME-B-" + suffix);
		grant(jdbc, studentId, "EXAM", single, examBundled, bundleOrder, "BUNDLE", false);
		grant(jdbc, studentId, "EXAM", single, examRevoked, null, "MANUAL", true);
		jdbc.update("insert into live_exam_roster (module_id, student_id, created_at) values (?, ?, now())", single, otherId);
		grant(jdbc, otherId, "EXAM", single, otherExam, null, "MANUAL", false);

		MvcResult result = mockMvc.perform(get("/api/v1/me/live-exam-access").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.modules.length()").value(3))
			.andExpect(jsonPath("$.exams.length()").value(3))
			.andReturn();
		JsonNode body = json.readTree(result.getResponse().getContentAsString());
		assertThat(moduleVia(body, rostered)).isEqualTo("ROSTER");
		assertThat(moduleVia(body, bought)).isEqualTo("MODULE");
		assertThat(moduleVia(body, granted)).isEqualTo("MANUAL");
		assertThat(moduleVia(body, revoked)).isNull();
		assertThat(row(body.get("modules"), "moduleId", rostered).has("orderId")).isFalse();
		assertThat(row(body.get("modules"), "moduleId", bought).get("orderId").asText()).isEqualTo(moduleOrder.toString());
		assertThat(row(body.get("modules"), "moduleId", granted).has("orderId")).isFalse();
		assertThat(row(body.get("modules"), "moduleId", bought).get("expired").asBoolean()).isFalse();
		JsonNode exams = body.get("exams");
		assertThat(row(exams, "examId", examBought).get("via").asText()).isEqualTo("EXAM");
		assertThat(row(exams, "examId", examBought).get("moduleId").asText()).isEqualTo(single.toString());
		assertThat(row(exams, "examId", examBought).get("orderId").asText()).isEqualTo(examOrder.toString());
		assertThat(row(exams, "examId", examBought).get("expired").asBoolean()).isFalse();
		assertThat(row(exams, "examId", examGranted).get("via").asText()).isEqualTo("MANUAL");
		assertThat(row(exams, "examId", examBundled).get("via").asText()).isEqualTo("EXAM");
		assertThat(row(exams, "examId", examBundled).get("orderId").asText()).isEqualTo(bundleOrder.toString());
		String raw = result.getResponse().getContentAsString();
		assertThat(raw).doesNotContain(examRevoked.toString()).doesNotContain(otherExam.toString());

		mockMvc.perform(get("/api/v1/me/live-exam-access").header("Authorization", "Bearer " + token(otherId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.modules.length()").value(1))
			.andExpect(jsonPath("$.modules[0].moduleId").value(single.toString()))
			.andExpect(jsonPath("$.modules[0].via").value("ROSTER"))
			.andExpect(jsonPath("$.exams.length()").value(1))
			.andExpect(jsonPath("$.exams[0].examId").value(otherExam.toString()));
		mockMvc.perform(get("/api/v1/me/live-exam-access").header("Authorization", "Bearer " + master))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		mockMvc.perform(get("/api/v1/me/live-exam-access")).andExpect(status().isUnauthorized());

		mockMvc.perform(delete("/api/v1/live-exam-modules/" + bought).header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/me/live-exam-access").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.modules.length()").value(2));
	}

	private static String moduleVia(JsonNode body, UUID moduleId) {
		JsonNode row = row(body.get("modules"), "moduleId", moduleId);
		return row == null ? null : row.get("via").asText();
	}

	private static JsonNode row(JsonNode rows, String field, UUID id) {
		for (JsonNode row : rows) {
			if (id.toString().equals(row.get(field).asText())) {
				return row;
			}
		}
		return null;
	}

	private static UUID grant(JdbcTemplate jdbc, UUID studentId, String scope, UUID module, UUID exam, UUID order,
			String source, boolean revoked) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into live_exam_grants (id, student_id, scope, module_id, exam_id, order_id, source, created_at, revoked_at)
				values (?, ?, ?, ?, ?, ?, ?, now(), case when cast(? as boolean) then now() end)
				""", id, studentId, scope, module, exam, order, source, revoked);
		return id;
	}

	private static UUID order(JdbcTemplate jdbc, UUID studentId, UUID module, UUID exam, String scope, String tranId) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into live_exam_orders (id, student_id, scope, module_id, exam_id, amount, currency, method, tran_id,
				                              status, created_at, updated_at)
				values (?, ?, ?, ?, ?, 100.00, 'BDT', 'SSLCOMMERZ', ?, 'SUCCEEDED', now(), now())
				""", id, studentId, scope, module, exam, tranId);
		return id;
	}

	private UUID createModule(String token, String title) throws Exception {
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
		return UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID createExam(String token, UUID module, String title) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/exams")
			.header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "examType":"MCQ","paperLanguage":"BN","titleEn":"%s","titleBn":"পরীক্ষা",
					  "descriptionEn":"d","descriptionBn":"বি","rulesEn":"r","rulesBn":"র",
					  "durationMinutes":40,"passPercent":50
					}
					""".formatted(title)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID seed(String role, String name) {
		UUID id = UUID.randomUUID();
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, uniquePhone(), uniqueEmail(), passwords.encode("password1"), role);
		new JdbcTemplate(dataSource).update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?)", id, name);
		return id;
	}

	private UUID seedStudent() {
		UUID id = UUID.randomUUID();
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, 'STUDENT', 'ACTIVE', now(), now())
				""", id, uniquePhone(), uniqueEmail(), passwords.encode("password1"));
		new JdbcTemplate(dataSource).update(
				"INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, 'Access Student', ?)", id,
				"S" + id.toString().substring(0, 8));
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

	private static String uniqueEmail() {
		return "student-access-" + UUID.randomUUID() + "@example.com";
	}

}
