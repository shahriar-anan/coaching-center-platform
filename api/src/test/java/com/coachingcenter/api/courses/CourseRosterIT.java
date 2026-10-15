package com.coachingcenter.api.courses;

import static org.hamcrest.Matchers.hasItem;
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
class CourseRosterIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Test
	void courseRoster() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Admin"));
		UUID courseId = createCourse(master);
		UUID previousId = seed("STUDENT", "Previous Student");
		UUID paidId = seed("STUDENT", "Paid Student");
		UUID waitingId = seed("STUDENT", "Waiting Student");
		UUID admittedId = seed("STUDENT", "Admitted Student");
		String previousPhone = phone(previousId);
		String paidPhone = phone(paidId);
		String waitingPhone = phone(waitingId);

		mockMvc.perform(post("/api/v1/courses/" + courseId + "/admissions").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"studentId\":\"" + previousId + "\"}"))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/courses/" + courseId + "/close").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/courses/" + courseId + "/reopen").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());

		String paid = token(paidId);
		String waiting = token(waitingId);
		UUID paymentId = purchase(paid, courseId, "roster-paid-" + UUID.randomUUID());
		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/verify").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"result\":\"SUCCESS\"}"))
			.andExpect(status().isOk());
		purchase(waiting, courseId, "roster-wait-" + UUID.randomUUID());
		mockMvc.perform(post("/api/v1/courses/" + courseId + "/admissions").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"studentId\":\"" + admittedId + "\"}"))
			.andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/courses/" + courseId + "/enrollments").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").exists())
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.totalPages").value(1))
			.andExpect(jsonPath("$.content[*].fullName", hasItem("Paid Student")))
			.andExpect(jsonPath("$.content[*].fullName", hasItem("Waiting Student")))
			.andExpect(jsonPath("$.content[*].fullName", hasItem("Admitted Student")))
			.andExpect(jsonPath("$.content[?(@.fullName == 'Paid Student')].phone", hasItem(paidPhone)))
			.andExpect(jsonPath("$.content[?(@.fullName == 'Paid Student')].source", hasItem("ONLINE")))
			.andExpect(jsonPath("$.content[?(@.fullName == 'Paid Student')].status", hasItem("ACTIVE")))
			.andExpect(jsonPath("$.content[?(@.fullName == 'Paid Student')].openingNumber", hasItem(2)))
			.andExpect(jsonPath("$.content[?(@.fullName == 'Waiting Student')].status", hasItem("AWAITING_PAYMENT")))
			.andExpect(jsonPath("$.content[?(@.fullName == 'Waiting Student')].phone", hasItem(waitingPhone)))
			.andExpect(jsonPath("$.content[?(@.fullName == 'Admitted Student')].source", hasItem("OFFLINE")))
			.andExpect(jsonPath("$.content[?(@.fullName == 'Admitted Student')].status", hasItem("ACTIVE")))
			.andExpect(jsonPath("$.content[*].fullName", org.hamcrest.Matchers.not(hasItem("Previous Student"))))
			.andExpect(jsonPath("$.content[0].password").doesNotExist())
			.andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());

		mockMvc.perform(get("/api/v1/courses/" + courseId + "/enrollments?opening=1")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].fullName").value("Previous Student"))
			.andExpect(jsonPath("$.content[0].phone").value(previousPhone))
			.andExpect(jsonPath("$.content[0].source").value("OFFLINE"))
			.andExpect(jsonPath("$.content[0].openingNumber").value(1));

		mockMvc.perform(get("/api/v1/courses/" + courseId + "/enrollments").header("Authorization", "Bearer " + paid))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/courses/" + UUID.randomUUID() + "/enrollments")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isNotFound());
	}

	private UUID purchase(String student, UUID courseId, String transactionRef) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/courses/" + courseId + "/purchases").header("Authorization", "Bearer " + student)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"method\":\"BKASH\",\"transactionRef\":\"" + transactionRef + "\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("paymentId").asText());
	}

	private UUID createCourse(String token) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/courses").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"Roster","titleBn":"রোস্টার","contentLanguage":"EN","description":"d","deliveryMode":"ONLINE","accessType":"FREEMIUM","price":100}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		UUID courseId = id(result);
		mockMvc.perform(patch("/api/v1/courses/" + courseId).header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"PUBLISHED\"}"))
			.andExpect(status().isOk());
		return courseId;
	}

	private String phone(UUID userId) {
		return new JdbcTemplate(dataSource).queryForObject("select phone from users where id = ?", String.class, userId);
	}

	private UUID id(MvcResult result) throws Exception {
		JsonNode body = json.readTree(result.getResponse().getContentAsString());
		return UUID.fromString(body.get("id").asText());
	}

	private UUID seed(String role, String name) {
		UUID userId = UUID.randomUUID();
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", userId, uniquePhone(), uniqueEmail(), passwords.encode("password1"), role);
		if ("STUDENT".equals(role)) {
			new JdbcTemplate(dataSource).update(
					"INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, ?, ?)", userId, name,
					"S" + userId.toString().substring(0, 8));
		}
		else {
			new JdbcTemplate(dataSource).update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?)", userId,
					name);
		}
		return userId;
	}

	private String token(UUID userId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"identifier\":\"" + phone(userId) + "\",\"password\":\"password1\"}"))
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
		return "roster-" + UUID.randomUUID() + "@example.com";
	}

}
