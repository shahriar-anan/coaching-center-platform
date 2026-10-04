package com.coachingcenter.api.purchases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
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

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class OfflineAdmissionIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	private String master;

	private String system;

	private UUID courseId;

	private UUID studentA;

	private UUID studentB;

	@BeforeEach
	void seed() throws Exception {
		master = token(seed("MASTER_ADMIN"));
		system = token(seed("SYSTEM_ADMIN"));
		courseId = createCourse();
		studentA = seed("STUDENT");
		studentB = seed("STUDENT");
	}

	@Test
	void offlineAdmissionFlow() throws Exception {
		mockMvc.perform(post("/api/v1/courses/" + courseId + "/admissions").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"studentId":"%s"}
					""".formatted(studentA)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.source").value("OFFLINE"));

		mockMvc.perform(post("/api/v1/courses/" + courseId + "/admissions").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"studentId":"%s"}
					""".formatted(studentB)))
			.andExpect(status().isCreated());

		assertThat(new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from payments where student_id = ? and payable_type = 'COURSE_ENROLLMENT'", Integer.class,
				studentA)).isZero();

		mockMvc.perform(post("/api/v1/courses/" + courseId + "/close").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/courses/" + courseId + "/admissions").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"studentId":"%s"}
					""".formatted(studentA)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("COURSE_NOT_OPEN"));

		UUID freeCourse = createFullFreeCourse();
		mockMvc.perform(post("/api/v1/courses/" + freeCourse + "/admissions").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"studentId":"%s"}
					""".formatted(studentA)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("COURSE_IS_FREE"));

		mockMvc.perform(post("/api/v1/courses/" + courseId + "/reopen").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());

		Integer openingBefore = new JdbcTemplate(dataSource).queryForObject(
				"select opening_number from enrollments where student_id = ? and course_id = ? and source = 'OFFLINE' order by created_at limit 1",
				Integer.class, studentA, courseId);

		mockMvc.perform(post("/api/v1/courses/" + courseId + "/admissions").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"studentId":"%s"}
					""".formatted(studentA)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.openingNumber").value(2));

		mockMvc.perform(post("/api/v1/courses/" + courseId + "/admissions").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"studentId":"%s"}
					""".formatted(studentA)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_ENROLLED"));

		Integer openingAfter = new JdbcTemplate(dataSource).queryForObject(
				"select opening_number from enrollments where student_id = ? and course_id = ? and source = 'OFFLINE' order by created_at limit 1",
				Integer.class, studentA, courseId);
		assertThat(openingAfter).isEqualTo(openingBefore);
	}

	private UUID createCourse() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/courses").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "titleEn":"AdmitCourse",
					  "titleBn":"কোর্স",
					  "contentLanguage":"EN",
					  "description":"d",
					  "deliveryMode":"ONLINE",
					  "accessType":"FREEMIUM"
					}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		UUID id = UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(patch("/api/v1/courses/" + id).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":100,\"status\":\"PUBLISHED\"}"))
			.andExpect(status().isOk());
		return id;
	}

	private UUID createFullFreeCourse() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/courses").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "titleEn":"FreeCourse",
					  "titleBn":"ফ্রি",
					  "contentLanguage":"EN",
					  "description":"d",
					  "deliveryMode":"ONLINE",
					  "accessType":"FULL_FREE"
					}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		UUID id = UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(patch("/api/v1/courses/" + id).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"PUBLISHED\"}"))
			.andExpect(status().isOk());
		return id;
	}

	private UUID seed(String role) {
		UUID id = UUID.randomUUID();
		String phone = uniquePhone();
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, phone, uniqueEmail(), passwords.encode("password1"), role);
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

	private static String uniqueEmail() {
		return "admit-" + UUID.randomUUID() + "@example.com";
	}

}
