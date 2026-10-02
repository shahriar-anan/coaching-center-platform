package com.coachingcenter.api.auth;

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

import com.coachingcenter.api.PostgresIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class AuthLoginIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Test
	void loginSucceedsWithPhoneOrEmail() throws Exception {
		String phone = uniquePhone();
		register("Phone User", phone, emailOf(phone));

		mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"password1"}
					""".formatted(phone)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").exists());

		mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"password1"}
					""".formatted(emailOf(phone))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").exists());
	}

	@Test
	void wrongPasswordIsGeneric() throws Exception {
		String phone = uniquePhone();
		register("Wrong User", phone, emailOf(phone));
		mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"missing@example.com","password":"password1"}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
			.andExpect(jsonPath("$.message").value("Invalid credentials."));

		mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"not-the-password"}
					""".formatted(emailOf(phone))))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
			.andExpect(jsonPath("$.message").value("Invalid credentials."));
	}

	@Test
	void inactiveAndPendingUsersCannotLogIn() throws Exception {
		String inactivePhone = uniquePhone();
		String pendingPhone = uniquePhone();
		insertUser(inactivePhone, emailOf(inactivePhone), "INACTIVE", passwords.encode("password1"));
		insertUser(pendingPhone, emailOf(pendingPhone), "PENDING_ACTIVATION", null);

		mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"password1"}
					""".formatted(emailOf(inactivePhone))))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Invalid credentials."));

		mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"password1"}
					""".formatted(emailOf(pendingPhone))))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Invalid credentials."));
	}

	@Test
	void repeatedFailuresLockTheIdentifier() throws Exception {
		String phone = uniquePhone();
		register("Locked User", phone, emailOf(phone));
		String body = """
				{"identifier":"%s","password":"wrong-password"}
				""".formatted(emailOf(phone));
		for (int attempt = 0; attempt < 3; attempt++) {
			mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body)).andExpect(status().isUnauthorized());
		}
		mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));
	}

	private static String uniquePhone() {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return "+8801" + suffix;
	}

	private static String emailOf(String phone) {
		return phone.substring(1) + "@example.com";
	}

	private void register(String name, String phone, String email) throws Exception {
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"%s","phone":"%s","email":"%s","password":"password1"}
					""".formatted(name, phone, email)))
			.andExpect(status().isCreated());
	}

	private void insertUser(String phone, String email, String status, String passwordHash) {
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, 'STUDENT', ?, now(), now())
				""", UUID.randomUUID(), phone, email, passwordHash, status);
	}

}
