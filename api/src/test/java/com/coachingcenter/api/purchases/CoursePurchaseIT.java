package com.coachingcenter.api.purchases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class CoursePurchaseIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	private String master;

	private String student;

	private UUID studentId;

	private UUID otherStudent;

	private UUID openCourse;

	private UUID hybridCourse;

	private UUID fullFreeCourse;

	private UUID closedCourse;

	@BeforeEach
	void seed() throws Exception {
		master = token(seed("MASTER_ADMIN"));
		openCourse = createCourse("FREEMIUM", "ONLINE");
		hybridCourse = createCourse("FREEMIUM", "HYBRID");
		fullFreeCourse = createCourse("FULL_FREE", "ONLINE");
		closedCourse = createCourse("FREEMIUM", "ONLINE");
		publish(closedCourse);
		mockMvc.perform(post("/api/v1/courses/" + closedCourse + "/close").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());

		String phone = uniquePhone();
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Buyer","phone":"%s","email":"%s","password":"password1"}
					""".formatted(phone, uniqueEmail())))
			.andExpect(status().isCreated());
		student = tokenFromPhone(phone);
		studentId = new JdbcTemplate(dataSource).queryForObject("select id from users where phone = ?", UUID.class,
				phone);
		otherStudent = seed("STUDENT");
	}

	@Test
	void coursePurchaseFlow() throws Exception {
		MvcResult purchase = mockMvc
			.perform(post("/api/v1/courses/" + openCourse + "/purchases").header("Authorization", "Bearer " + student)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"method":"BKASH","transactionRef":"trx-open-1"}
						"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.enrollmentStatus").value("AWAITING_PAYMENT"))
			.andExpect(jsonPath("$.paymentStatus").value("PENDING"))
			.andExpect(jsonPath("$.paymentNumber").value("01700000001"))
			.andReturn();
		UUID enrollmentId = UUID.fromString(json.readTree(purchase.getResponse().getContentAsString()).get("enrollmentId").asText());

		assertThat(new JdbcTemplate(dataSource).queryForObject("select opening_number from enrollments where id = ?",
				Integer.class, enrollmentId)).isEqualTo(1);
		assertThat(new JdbcTemplate(dataSource).queryForObject("select source from enrollments where id = ?", String.class,
				enrollmentId)).isEqualTo("ONLINE");

		mockMvc.perform(get("/api/v1/me/purchases").header("Authorization", "Bearer " + student))
			.andExpect(jsonPath("$[0].enrollmentId").value(enrollmentId.toString()));

		mockMvc.perform(get("/api/v1/me/purchases").header("Authorization", "Bearer " + token(otherStudent)))
			.andExpect(jsonPath("$.length()").value(0));

		mockMvc
			.perform(post("/api/v1/courses/" + hybridCourse + "/purchases").header("Authorization", "Bearer " + student)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"method":"NAGAD","transactionRef":"trx-hybrid-1","deliveryMode":"OFFLINE"}
						"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.paymentNumber").value("01800000001"));

		assertThat(new JdbcTemplate(dataSource).queryForObject(
				"select delivery_mode from enrollments where course_id = ? and student_id = ?", String.class,
				hybridCourse, studentId)).isEqualTo("OFFLINE");

		mockMvc.perform(post("/api/v1/courses/" + closedCourse + "/purchases").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"method\":\"BKASH\",\"transactionRef\":\"trx-closed\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("COURSE_NOT_OPEN"));

		mockMvc.perform(post("/api/v1/courses/" + fullFreeCourse + "/purchases").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"method\":\"BKASH\",\"transactionRef\":\"trx-free\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("COURSE_IS_FREE"));

		mockMvc.perform(post("/api/v1/courses/" + openCourse + "/purchases").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"method\":\"BKASH\",\"transactionRef\":\"trx-pending-2\"}"))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/courses/" + openCourse + "/purchases").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"method\":\"BKASH\",\"transactionRef\":\"trx-open-1\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DUPLICATE_TRANSACTION_REF"));

		new JdbcTemplate(dataSource).update("""
				INSERT INTO enrollments (id, student_id, course_id, opening_number, delivery_mode, source, status, price_paid, started_at, created_at, updated_at)
				VALUES (?, ?, ?, 1, 'ONLINE', 'ONLINE', 'ACTIVE', 100, now(), now(), now())
				""", UUID.randomUUID(), studentId, openCourse);

		mockMvc.perform(post("/api/v1/courses/" + openCourse + "/purchases").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"method\":\"BKASH\",\"transactionRef\":\"trx-active-block\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_ENROLLED"));
	}

	private UUID createCourse(String accessType, String deliveryMode) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/courses").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "titleEn":"Course-%s",
					  "titleBn":"কোর্স",
					  "contentLanguage":"EN",
					  "description":"d",
					  "deliveryMode":"%s",
					  "accessType":"%s"
					}
					""".formatted(UUID.randomUUID().toString().substring(0, 8), deliveryMode, accessType)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID id = UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
		if ("FREEMIUM".equals(accessType)) {
			publish(id);
		}
		else {
			mockMvc.perform(patch("/api/v1/courses/" + id).header("Authorization", "Bearer " + master)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"PUBLISHED\"}"))
				.andExpect(status().isOk());
		}
		return id;
	}

	private void publish(UUID courseId) throws Exception {
		mockMvc.perform(patch("/api/v1/courses/" + courseId).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":100}"))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/courses/" + courseId).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"PUBLISHED\"}"))
			.andExpect(status().isOk());
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
		return tokenFromPhone(phone);
	}

	private String tokenFromPhone(String phone) throws Exception {
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
		return "purchase-" + UUID.randomUUID() + "@example.com";
	}

}
