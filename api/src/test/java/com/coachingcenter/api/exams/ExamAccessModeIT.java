package com.coachingcenter.api.exams;

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
class ExamAccessModeIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Test
	void accessModeAndCourseLink() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		String system = token(seed("SYSTEM_ADMIN"));
		UUID course = createCourse(master);
		UUID module = createModule(system);

		mockMvc.perform(patch("/api/v1/exam-modules/" + module + "/access").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"accessMode\":\"OPEN\",\"courseId\":null}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessMode").value("OPEN"))
			.andExpect(jsonPath("$.courseId").doesNotExist());

		UUID openWithCourse = createModule(system);
		mockMvc.perform(patch("/api/v1/exam-modules/" + openWithCourse + "/access").header("Authorization",
				"Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"accessMode\":\"OPEN\",\"courseId\":\"" + course + "\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ACCESS_MODE_LINK_MISMATCH"));

		UUID priced = createModule(system);
		mockMvc.perform(patch("/api/v1/exam-modules/" + priced).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":500}"))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/exam-modules/" + priced + "/access").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"accessMode\":\"OPEN\",\"courseId\":null}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PRICE_NOT_ALLOWED"));

		UUID purchaseModule = createModule(system);
		mockMvc.perform(patch("/api/v1/exam-modules/" + purchaseModule).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":250}"))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/exam-modules/" + purchaseModule + "/access").header("Authorization",
				"Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"accessMode\":\"PURCHASE_REQUIRED\",\"courseId\":null}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessMode").value("PURCHASE_REQUIRED"));

		UUID purchaseWithCourse = createModule(system);
		mockMvc.perform(patch("/api/v1/exam-modules/" + purchaseWithCourse).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":100}"))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/exam-modules/" + purchaseWithCourse + "/access").header("Authorization",
				"Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"accessMode\":\"PURCHASE_REQUIRED\",\"courseId\":\"" + course + "\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ACCESS_MODE_LINK_MISMATCH"));

		UUID noPricePurchase = createModule(system);
		mockMvc.perform(patch("/api/v1/exam-modules/" + noPricePurchase + "/access").header("Authorization",
				"Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"accessMode\":\"PURCHASE_REQUIRED\",\"courseId\":null}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PRICE_REQUIRED"));

		UUID enrolledA = createModule(system);
		UUID enrolledB = createModule(system);
		mockMvc.perform(patch("/api/v1/exam-modules/" + enrolledA + "/access").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"accessMode\":\"ENROLLED_ONLY\",\"courseId\":\"" + course + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.courseId").value(course.toString()));
		mockMvc.perform(patch("/api/v1/exam-modules/" + enrolledB + "/access").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"accessMode\":\"ENROLLED_ONLY\",\"courseId\":\"" + course + "\"}"))
			.andExpect(status().isOk());

		mockMvc.perform(patch("/api/v1/exam-modules/" + createModule(system) + "/access").header("Authorization",
				"Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"accessMode\":\"ENROLLED_ONLY\",\"courseId\":null}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ACCESS_MODE_LINK_MISMATCH"));

		mockMvc.perform(patch("/api/v1/exam-modules/" + createModule(system) + "/access").header("Authorization",
				"Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"accessMode\":\"ENROLLED_ONLY\",\"courseId\":\"" + UUID.randomUUID() + "\"}"))
			.andExpect(status().isNotFound());

		mockMvc.perform(patch("/api/v1/exam-modules/" + createModule(system)).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":1}"))
			.andExpect(status().isForbidden());

		UUID saEnrolled = createModule(system);
		mockMvc.perform(patch("/api/v1/exam-modules/" + saEnrolled + "/access").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"accessMode\":\"ENROLLED_ONLY\",\"courseId\":\"" + course + "\"}"))
			.andExpect(status().isOk());

		UUID saPurchase = createModule(system);
		mockMvc.perform(patch("/api/v1/exam-modules/" + saPurchase).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":99}"))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/exam-modules/" + saPurchase + "/access").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"accessMode\":\"PURCHASE_REQUIRED\",\"courseId\":null}"))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/exam-modules/" + enrolledA).header("Authorization", "Bearer " + system))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessMode").value("ENROLLED_ONLY"))
			.andExpect(jsonPath("$.courseId").value(course.toString()));
	}

	private UUID createModule(String token) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/exam-modules").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"Access mod","titleBn":"ম","descriptionEn":"d","descriptionBn":"b"}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID createCourse(String master) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/courses").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "titleEn":"Linked course",
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
		return "exam-access-" + UUID.randomUUID() + "@example.com";
	}

}
