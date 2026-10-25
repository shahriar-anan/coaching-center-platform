package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, LiveGrantAdminIT.ClockConfig.class })
class LiveGrantAdminIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Test
	void masterAndSystemAdminGrantAModuleOrAnExamByHandAndTheRowIsAudited() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Granted Student");
		UUID module = module(system, "By hand");
		UUID exam = exam(system, module);

		MvcResult moduleGrant = mockMvc.perform(post("/api/v1/live-exam-grants")
			.header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(student, "MODULE", module, null, "Paid by cash at the office")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.source").value("MANUAL"))
			.andExpect(jsonPath("$.moduleId").value(module.toString()))
			.andExpect(jsonPath("$.examId").doesNotExist())
			.andExpect(jsonPath("$.reason").value("Paid by cash at the office"))
			.andExpect(jsonPath("$.grantedBy").isNotEmpty())
			.andReturn();
		UUID grant = UUID.fromString(json.readTree(moduleGrant.getResponse().getContentAsString()).get("id").asText());
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		assertThat(jdbc.queryForObject("select source from live_exam_grants where id = ?", String.class, grant)).isEqualTo("MANUAL");
		assertThat(jdbc.queryForObject("select order_id from live_exam_grants where id = ?", UUID.class, grant)).isNull();
		assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'ACCESS_GRANTED' and entity_id = ?",
				Integer.class, grant)).isEqualTo(1);

		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(student, "EXAM", null, exam, "Retake for a sick day")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.scope").value("EXAM"))
			.andExpect(jsonPath("$.examId").value(exam.toString()))
			.andExpect(jsonPath("$.moduleId").value(module.toString()));

		mockMvc.perform(get("/api/v1/students/" + student + "/live-exam-access").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.content[0].via").value("MANUAL"))
			.andExpect(jsonPath("$.content[1].via").value("MANUAL"));
	}

	@Test
	void instructorsAndStudentsCannotGrant() throws Exception {
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Refused Student");
		UUID module = module(system, "Refused");
		String instructor = token(seed("INSTRUCTOR", "Teacher"));
		String learner = token(student);
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + instructor)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(student, "MODULE", module, null, "Trying")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + learner)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(student, "MODULE", module, null, "Trying")))
			.andExpect(status().isForbidden());
	}

	@Test
	void aSecondActiveGrantOnTheSameTargetIsRefused() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID student = seed("STUDENT", "Twice Student");
		UUID module = module(master, "Twice");
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(student, "MODULE", module, null, "First")))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(student, "MODULE", module, null, "Second")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("GRANT_ALREADY_ACTIVE"));
	}

	@Test
	void unknownStudentsAndTargetsAreNotFoundAndBadInputIsRefused() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID student = seed("STUDENT", "Missing Targets");
		UUID teacher = seed("INSTRUCTOR", "Not A Student");
		UUID module = module(master, "Missing targets");
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(UUID.randomUUID(), "MODULE", module, null, "Unknown student")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(teacher, "MODULE", module, null, "Not a student")))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(student, "MODULE", UUID.randomUUID(), null, "Unknown module")))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(student, "ALL", module, null, "Bad scope")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(student, "MODULE", module, null, "   ")))
			.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(student, "MODULE", module, null, "a".repeat(501))))
			.andExpect(status().isBadRequest());
	}

	@Test
	void aRevokedGrantStopsAccessAndANewGrantRestoresIt() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Revoked Student");
		String learner = token(student);
		UUID module = module(system, "Revoke");
		publish(system, module);
		mockMvc.perform(get("/api/v1/live-exam-modules/" + module).header("Authorization", "Bearer " + learner))
			.andExpect(status().isNotFound());

		MvcResult first = mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(student, "MODULE", module, null, "First grant")))
			.andExpect(status().isCreated())
			.andReturn();
		UUID grant = UUID.fromString(json.readTree(first.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(get("/api/v1/live-exam-modules/" + module).header("Authorization", "Bearer " + learner))
			.andExpect(status().isOk());

		mockMvc.perform(delete("/api/v1/live-exam-grants/" + grant).param("reason", "Entered by mistake")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.revokedAt").isNotEmpty())
			.andExpect(jsonPath("$.revokedBy").isNotEmpty())
			.andExpect(jsonPath("$.revokeReason").value("Entered by mistake"));
		mockMvc.perform(get("/api/v1/live-exam-modules/" + module).header("Authorization", "Bearer " + learner))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/students/" + student + "/live-exam-access").header("Authorization", "Bearer " + master))
			.andExpect(jsonPath("$.totalElements").value(0));
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'ACCESS_REVOKED' and entity_id = ?",
				Integer.class, grant)).isEqualTo(1);

		mockMvc.perform(delete("/api/v1/live-exam-grants/" + grant).param("reason", "Again")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/v1/live-exam-grants/" + grant).header("Authorization", "Bearer " + master))
			.andExpect(status().isBadRequest());
		mockMvc.perform(delete("/api/v1/live-exam-grants/" + grant).param("reason", "Trying")
			.header("Authorization", "Bearer " + system))
			.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(student, "MODULE", module, null, "Granted again")))
			.andExpect(status().isCreated());
		mockMvc.perform(get("/api/v1/live-exam-modules/" + module).header("Authorization", "Bearer " + learner))
			.andExpect(status().isOk());
	}

	private void publish(String token, UUID module) throws Exception {
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/final-save").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/publish").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
	}

	private UUID module(String token, String title) throws Exception {
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

	private UUID exam(String token, UUID module) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/exams")
			.header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "examType":"MCQ","paperLanguage":"BN","titleEn":"Grant quiz","titleBn":"কুইজ",
					  "descriptionEn":"d","descriptionBn":"বি","rulesEn":"English rules","rulesBn":"নিয়ম",
					  "liveAt":"2026-10-20T12:00:00+06:00","durationMinutes":40,"passPercent":50
					}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
	}

	private static String body(UUID student, String scope, UUID module, UUID exam, String reason) {
		return """
				{"studentId":"%s","scope":"%s","moduleId":%s,"examId":%s,"reason":"%s"}
				""".formatted(student, scope, quoted(module), quoted(exam), reason);
	}

	private static String quoted(UUID id) {
		return id == null ? "null" : "\"" + id + "\"";
	}

	private UUID seed(String role, String name) {
		UUID id = UUID.randomUUID();
		String phone = "+8801" + java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, phone, "grant-" + id + "@example.com", passwords.encode("password1"), role);
		if ("STUDENT".equals(role)) {
			new JdbcTemplate(dataSource).update(
					"INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, ?, ?)", id, name,
					"G" + id.toString().substring(0, 8));
		}
		else {
			new JdbcTemplate(dataSource).update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?)", id, name);
		}
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = new JdbcTemplate(dataSource).queryForObject("select phone from users where id = ?", String.class, userId);
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

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(Instant.parse("2026-10-20T04:00:00Z"));
		}

	}

}
