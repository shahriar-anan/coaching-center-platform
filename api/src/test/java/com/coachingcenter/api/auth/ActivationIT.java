package com.coachingcenter.api.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.coachingcenter.api.PostgresIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class ActivationIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private SecretHasher hasher;

	@Test
	void validCodeActivatesTheAccount() throws Exception {
		String phone = uniquePhone();
		seed(phone, "CODE1234", Instant.now().plusSeconds(3600), null, 0);
		mockMvc.perform(post("/api/v1/auth/activate").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"phone":"%s","code":"CODE1234","password":"password1"}
					""".formatted(phone)))
			.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"password1"}
					""".formatted(phone)))
			.andExpect(status().isOk());
	}

	@Test
	void expiredUsedWrongAndExhaustedCodesAreRejected() throws Exception {
		String expiredPhone = uniquePhone();
		seed(expiredPhone, "EXPIRED1", Instant.now().minusSeconds(60), null, 0);
		activate(expiredPhone, "EXPIRED1").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CODE_EXPIRED"));

		String usedPhone = uniquePhone();
		seed(usedPhone, "USEDCODE", Instant.now().plusSeconds(3600), Instant.now(), 0);
		activate(usedPhone, "USEDCODE").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CODE_ALREADY_USED"));

		String wrongPhone = uniquePhone();
		seed(wrongPhone, "RIGHTCOD", Instant.now().plusSeconds(3600), null, 0);
		activate(wrongPhone, "WRONGCOD").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CODE"));

		String cappedPhone = uniquePhone();
		seed(cappedPhone, "CAPPED01", Instant.now().plusSeconds(3600), null, 3);
		activate(cappedPhone, "CAPPED01").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CODE_ATTEMPTS_EXCEEDED"));
	}

	private org.springframework.test.web.servlet.ResultActions activate(String phone, String code) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/activate").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"phone":"%s","code":"%s","password":"password1"}
					""".formatted(phone, code)));
	}

	private void seed(String phone, String code, Instant expiresAt, Instant usedAt, int failedAttempts) {
		UUID userId = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO users (id, phone, email, role, status, created_at, updated_at)
				VALUES (?, ?, ?, 'STUDENT', 'PENDING_ACTIVATION', now(), now())
				""", userId, phone, userId + "@example.com");
		jdbc.update("""
				INSERT INTO one_time_codes (id, user_id, purpose, code_hash, expires_at, used_at, failed_attempts, created_at)
				VALUES (?, ?, 'ACTIVATION', ?, ?, ?, ?, now())
				""", UUID.randomUUID(), userId, hasher.sha256(code), java.sql.Timestamp.from(expiresAt),
				usedAt == null ? null : java.sql.Timestamp.from(usedAt), failedAttempts);
	}

	private static String uniquePhone() {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return "+8801" + suffix;
	}

}
