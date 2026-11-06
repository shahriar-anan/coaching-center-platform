package com.coachingcenter.api.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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

/**
 * The admin's student details show when the student signed up: {@code createdAt} on {@code GET /api/v1/students/{id}} is the
 * account's creation time. A student sees their own; staff see any student.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class StudentSignupDateIT extends PostgresIntegrationTest {

	private static final Instant SIGNED_UP = Instant.parse("2026-09-01T06:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Test
	void theAdminSeesWhenAStudentSignedUp() throws Exception {
		UUID student = seed("STUDENT", SIGNED_UP);
		String master = token(seed("MASTER_ADMIN", Instant.parse("2026-08-01T00:00:00Z")));

		mockMvc.perform(get("/api/v1/students/" + student).header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.createdAt").value(org.hamcrest.Matchers.startsWith("2026-09-01T06:00:00")));
	}

	@Test
	void aStudentSeesTheirOwnSignUpTime() throws Exception {
		UUID student = seed("STUDENT", SIGNED_UP);
		String token = token(student);

		mockMvc.perform(get("/api/v1/students/" + student).header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.createdAt").value(org.hamcrest.Matchers.startsWith("2026-09-01T06:00:00")));
	}

	private UUID seed(String role, Instant createdAt) {
		UUID id = UUID.randomUUID();
		String phone = "+8801" + ThreadLocalRandom.current().nextInt(100000000, 999999999);
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		jdbc.update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
				""", id, phone, "signup-date-" + id + "@example.com", passwords.encode("password1"), role,
				java.sql.Timestamp.from(createdAt), java.sql.Timestamp.from(createdAt));
		if (role.endsWith("ADMIN")) {
			jdbc.update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Admin')", id);
		}
		else {
			jdbc.update("INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, 'Student', ?)", id,
					"SIGN" + id.toString().substring(0, 8));
		}
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = new JdbcTemplate(dataSource).queryForObject("select phone from users where id = ?", String.class,
				userId);
		MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"password1"}
					""".formatted(phone)))
			.andExpect(status().isOk())
			.andReturn();
		String body = result.getResponse().getContentAsString();
		String marker = "\"accessToken\":\"";
		int start = body.indexOf(marker) + marker.length();
		assertThat(start).isGreaterThan(marker.length() - 1);
		return body.substring(start, body.indexOf('"', start));
	}

}
