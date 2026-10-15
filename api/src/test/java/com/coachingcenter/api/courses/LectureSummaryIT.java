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

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class LectureSummaryIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Test
	void lectureSummary() throws Exception {
		String manage = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID courseId = createCourse(master);
		UUID sectionId = createNode(manage, courseId, "{\"nodeType\":\"SECTION\",\"title\":\"Section\"}");
		UUID lectureId = createNode(manage, courseId, "{\"nodeType\":\"LECTURE\",\"title\":\"Lecture\"}");

		mockMvc.perform(patch("/api/v1/courses/" + courseId + "/nodes/" + lectureId).header("Authorization",
				"Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"description\":\"What this lecture covers\",\"durationMinutes\":40}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.description").value("What this lecture covers"))
			.andExpect(jsonPath("$.durationMinutes").value(40));

		mockMvc.perform(patch("/api/v1/courses/" + courseId + "/nodes/" + lectureId).header("Authorization",
				"Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"durationMinutes\":0}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		mockMvc.perform(get("/api/v1/courses/" + courseId + "/nodes").header("Authorization", "Bearer " + manage))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '%s')].durationMinutes".formatted(lectureId), hasItem(40)))
			.andExpect(jsonPath("$[?(@.id == '%s')].description".formatted(lectureId), hasItem("What this lecture covers")));

		mockMvc.perform(patch("/api/v1/courses/" + courseId + "/nodes/" + sectionId).header("Authorization",
				"Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"description\":\"Not allowed\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		publish(master, courseId);
		String student = token(seed("STUDENT"));
		mockMvc.perform(get("/api/v1/catalog/courses/" + courseId).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.contentTree[?(@.id == '%s')].durationMinutes".formatted(lectureId), hasItem(40)))
			.andExpect(jsonPath("$.contentTree[0].videoRef").doesNotExist())
			.andExpect(jsonPath("$.contentTree[0].notes").doesNotExist());

		mockMvc.perform(get("/api/v1/catalog/courses/" + courseId + "/lectures/" + lectureId)
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ENROLLMENT_REQUIRED"))
			.andExpect(jsonPath("$.description").doesNotExist())
			.andExpect(jsonPath("$.durationMinutes").doesNotExist());

		mockMvc.perform(patch("/api/v1/courses/" + courseId + "/nodes/" + lectureId).header("Authorization",
				"Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"free\":true}"))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/catalog/courses/" + courseId + "/lectures/" + lectureId)
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.description").value("What this lecture covers"))
			.andExpect(jsonPath("$.durationMinutes").value(40));

		mockMvc.perform(patch("/api/v1/courses/" + courseId + "/nodes/" + lectureId).header("Authorization",
				"Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"description\":null,\"durationMinutes\":null}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.description").doesNotExist())
			.andExpect(jsonPath("$.durationMinutes").doesNotExist());
	}

	private void publish(String master, UUID courseId) throws Exception {
		mockMvc.perform(patch("/api/v1/courses/" + courseId).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":100,\"status\":\"PUBLISHED\"}"))
			.andExpect(status().isOk());
	}

	private UUID createCourse(String token) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/courses").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"Summary","titleBn":"সারাংশ","contentLanguage":"EN","description":"d","deliveryMode":"ONLINE","accessType":"FREEMIUM"}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		return id(result);
	}

	private UUID createNode(String token, UUID courseId, String body) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/courses/" + courseId + "/nodes").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated())
			.andReturn();
		return id(result);
	}

	private UUID id(MvcResult result) throws Exception {
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID seed(String role) {
		UUID userId = UUID.randomUUID();
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", userId, uniquePhone(), uniqueEmail(), passwords.encode("password1"), role);
		if ("STUDENT".equals(role)) {
			new JdbcTemplate(dataSource).update(
					"INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, 'Seed', ?)", userId,
					"S" + userId.toString().substring(0, 8));
		}
		else {
			new JdbcTemplate(dataSource).update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Admin')",
					userId);
		}
		return userId;
	}

	private String token(UUID userId) throws Exception {
		String phone = new JdbcTemplate(dataSource).queryForObject("select phone from users where id = ?", String.class,
				userId);
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"identifier\":\"" + phone + "\",\"password\":\"password1\"}"))
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
		return "summary-" + UUID.randomUUID() + "@example.com";
	}

}
