package com.coachingcenter.api.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The offline admission feature switch while it is ON (staging): the routes answer, the public status says enabled, and a student
 * create with {@code offlineAdmission} makes the admission row.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
@TestPropertySource(properties = "app.offline-admission.enabled=true")
class OfflineFeatureSwitchOnIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Test
	void offlineRoutesAnswerForTheMasterAdmin() throws Exception {
		String masterToken = token(seed("MASTER_ADMIN", "Master"));

		mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/offline-admissions")
			.header("Authorization", "Bearer " + masterToken))
			.andExpect(status().isOk());
	}

	@Test
	void publicStatusSaysEnabled() throws Exception {
		MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/public/offline-admission/status"))
			.andExpect(status().isOk())
			.andReturn();
		assertThat(json.readTree(result.getResponse().getContentAsString()).get("enabled").asBoolean()).isTrue();
	}

	@Test
	void studentCreateWithOfflineAdmissionMakesTheAdmissionRow() throws Exception {
		String masterToken = token(seed("MASTER_ADMIN", "Master"));
		String phone = "017" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
		String email = "switch-on-" + UUID.randomUUID() + "@example.com";

		MvcResult created = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/students")
			.header("Authorization", "Bearer " + masterToken)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Switched On","phone":"%s","email":"%s","offlineAdmission":true}
					""".formatted(phone, email)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID studentId = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());

		Integer admissions = jdbc().queryForObject("select count(*) from offline_admissions where student_id = ?", Integer.class,
				studentId);
		assertThat(admissions).isEqualTo(1);
	}

	private JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}

	private UUID seed(String role, String name) {
		UUID id = UUID.randomUUID();
		jdbc().update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, "+8801" + ThreadLocalRandom.current().nextInt(300000000, 999999999), "offline-switch-on-" + id + "@example.com",
				passwords.encode("password1"), role);
		if ("STUDENT".equals(role)) {
			jdbc().update("INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, ?, ?)", id, name,
					"S" + id.toString().substring(0, 8));
		}
		else {
			jdbc().update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?)", id, name);
		}
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = jdbc().queryForObject("select phone from users where id = ?", String.class, userId);
		MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"password1"}
					""".formatted(phone)))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
	}

}
