package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** AG-03: GET /api/v1/system-status is Master Admin only, and it returns check names and yes or no, never a value. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class SystemStatusIT extends PostgresIntegrationTest {

	private static final Set<String> CHECK_NAMES = Set.of("checkout.configured", "checkout.profile", "mail.configured",
			"google.configured", "mail.hour-before.off", "auth.dev-codes.off", "seed.off");

	/** Values the test environment sets. None may reach the response. */
	private static final String[] SECRETS = { "test-only-jwt-secret-not-a-real-credential", "bootstrap-password-1",
			"master-bootstrap@example.com", "+8801700000001" };

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Test
	void masterAdminReadsTheCheckNamesAndYesOrNoOnly() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		MvcResult result = mockMvc.perform(get("/api/v1/system-status").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.checks").isArray())
			.andReturn();
		String body = result.getResponse().getContentAsString();
		JsonNode checks = json.readTree(body).get("checks");
		assertThat(checks).hasSize(CHECK_NAMES.size());
		for (JsonNode check : checks) {
			assertThat(CHECK_NAMES).contains(check.get("name").asText());
			assertThat(check.get("ok").isBoolean()).isTrue();
			assertThat(check.size()).isEqualTo(3);
		}
		for (String secret : SECRETS) {
			assertThat(body).doesNotContain(secret);
		}
	}

	@Test
	void systemAdminInstructorAndStudentGetForbidden() throws Exception {
		String system = token(seed("SYSTEM_ADMIN"));
		String instructor = token(seed("INSTRUCTOR"));
		String student = token(seed("STUDENT"));
		for (String token : new String[] { system, instructor, student }) {
			mockMvc.perform(get("/api/v1/system-status").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		}
	}

	private UUID seed(String role) {
		UUID id = UUID.randomUUID();
		String phone = "+8801" + ThreadLocalRandom.current().nextInt(100000000, 999999999);
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		jdbc.update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, phone, "status-" + id + "@example.com", passwords.encode("password1"), role);
		if ("STUDENT".equals(role)) {
			jdbc.update("INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, ?, ?)", id, "Status",
					"T" + id.toString().substring(0, 8));
		}
		else {
			jdbc.update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?)", id, "Status");
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

}
