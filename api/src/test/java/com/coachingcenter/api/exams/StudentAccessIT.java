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
		jdbc.update("insert into live_exam_grants (id, student_id, scope, module_id, exam_id, order_id, created_at) "
				+ "values (gen_random_uuid(), ?, 'MODULE', ?, null, ?, now())", studentId, module, moduleOrder);
		UUID examOrder = order(jdbc, studentId, module, examA, "EXAM", examTran);
		jdbc.update("insert into live_exam_grants (id, student_id, scope, module_id, exam_id, order_id, created_at) "
				+ "values (gen_random_uuid(), ?, 'EXAM', ?, ?, ?, now())", studentId, module, examA, examOrder);

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

		mockMvc.perform(delete("/api/v1/live-exam-modules/" + module).header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/students/" + studentId + "/live-exam-access").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(0));
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
