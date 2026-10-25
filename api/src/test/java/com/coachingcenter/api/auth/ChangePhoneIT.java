package com.coachingcenter.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.web.servlet.ResultActions;

import com.coachingcenter.api.PostgresIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class ChangePhoneIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CapturingEmailSender emails;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	private JdbcTemplate jdbc;

	@BeforeEach
	void setUp() {
		jdbc = new JdbcTemplate(dataSource);
	}

	@Test
	void studentChangesThePhoneWithTheCurrentPassword() throws Exception {
		String phone = uniquePhone();
		String email = uniqueEmail();
		register(phone, email);
		String access = access(phone, "password1");
		String newPhone = uniquePhone();
		changePhone(access, "password1", newPhone)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.phone").value(newPhone))
			.andExpect(jsonPath("$.email").value(email));
		UUID userId = userIdByEmail(email);
		assertThat(phoneOf(userId)).isEqualTo(newPhone);
		assertThat(phoneAuditCount(userId)).isEqualTo(1);
		assertThat(emails.phoneChangedCount(email)).isEqualTo(1);
	}

	@Test
	void masterAdminChangesThePhoneAndTheOldPhoneNoLongerSignsIn() throws Exception {
		String phone = uniquePhone();
		String email = uniqueEmail();
		jdbc.update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, 'MASTER_ADMIN', 'ACTIVE', now(), now())
				""", UUID.randomUUID(), phone, email, passwords.encode("password1"));
		String newPhone = uniquePhone();
		changePhone(access(phone, "password1"), "password1", newPhone).andExpect(status().isOk())
			.andExpect(jsonPath("$.phone").value(newPhone))
			.andExpect(jsonPath("$.role").value("MASTER_ADMIN"));
		login(newPhone, "password1").andExpect(status().isOk());
		login(phone, "password1").andExpect(status().isUnauthorized());
		assertThat(phoneAuditCount(userIdByEmail(email))).isEqualTo(1);
	}

	@Test
	void wrongCurrentPasswordLeavesThePhoneAlone() throws Exception {
		String phone = uniquePhone();
		String email = uniqueEmail();
		register(phone, email);
		changePhone(access(phone, "password1"), "wrong-password", uniquePhone())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));
		UUID userId = userIdByEmail(email);
		assertThat(phoneOf(userId)).isEqualTo(phone);
		assertThat(phoneAuditCount(userId)).isZero();
		assertThat(emails.phoneChangedCount(email)).isZero();
	}

	@Test
	void aPhoneUsedByAnotherAccountConflicts() throws Exception {
		String taken = uniquePhone();
		register(taken, uniqueEmail());
		String phone = uniquePhone();
		String email = uniqueEmail();
		register(phone, email);
		changePhone(access(phone, "password1"), "password1", taken)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DUPLICATE_PHONE"));
		assertThat(phoneOf(userIdByEmail(email))).isEqualTo(phone);
	}

	@Test
	void aBadPhoneFormatIsRejected() throws Exception {
		String phone = uniquePhone();
		String email = uniqueEmail();
		register(phone, email);
		String access = access(phone, "password1");
		for (String bad : new String[] { "+88017123", "+8802123456789", "12345" }) {
			changePhone(access, "password1", bad).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		}
		assertThat(phoneOf(userIdByEmail(email))).isEqualTo(phone);
	}

	@Test
	void theLocalFormIsNormalized() throws Exception {
		String phone = uniquePhone();
		String email = uniqueEmail();
		register(phone, email);
		String newPhone = uniquePhone();
		changePhone(access(phone, "password1"), "password1", "0" + newPhone.substring(4))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.phone").value(newPhone));
		assertThat(phoneOf(userIdByEmail(email))).isEqualTo(newPhone);
	}

	@Test
	void theSameNumberChangesNothing() throws Exception {
		String phone = uniquePhone();
		String email = uniqueEmail();
		register(phone, email);
		changePhone(access(phone, "password1"), "password1", phone).andExpect(status().isOk())
			.andExpect(jsonPath("$.phone").value(phone));
		assertThat(phoneAuditCount(userIdByEmail(email))).isZero();
		assertThat(emails.phoneChangedCount(email)).isZero();
	}

	@Test
	void anAccountWithNoPasswordIsRefused() throws Exception {
		String phone = uniquePhone();
		String email = uniqueEmail();
		register(phone, email);
		String access = access(phone, "password1");
		UUID userId = userIdByEmail(email);
		jdbc.update("UPDATE users SET password_hash = NULL WHERE id = ?", userId);
		changePhone(access, "password1", uniquePhone()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));
		assertThat(phoneOf(userId)).isEqualTo(phone);
	}

	@Test
	void noTokenIsUnauthenticated() throws Exception {
		mockMvc.perform(post("/api/v1/auth/change-phone").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"currentPassword":"password1","phone":"%s"}
					""".formatted(uniquePhone())))
			.andExpect(status().isUnauthorized());
	}

	private ResultActions changePhone(String access, String currentPassword, String phone) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/change-phone").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"currentPassword":"%s","phone":"%s"}
					""".formatted(currentPassword, phone)));
	}

	private void register(String phone, String email) throws Exception {
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Phone User","phone":"%s","email":"%s","password":"password1"}
					""".formatted(phone, email)))
			.andExpect(status().isCreated());
	}

	private ResultActions login(String identifier, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"%s"}
					""".formatted(identifier, password)));
	}

	private String access(String identifier, String password) throws Exception {
		MvcResult result = login(identifier, password).andExpect(status().isOk()).andReturn();
		String body = result.getResponse().getContentAsString();
		String marker = "\"accessToken\":\"";
		int start = body.indexOf(marker) + marker.length();
		return body.substring(start, body.indexOf('"', start));
	}

	private UUID userIdByEmail(String email) {
		return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
	}

	private String phoneOf(UUID userId) {
		return jdbc.queryForObject("SELECT phone FROM users WHERE id = ?", String.class, userId);
	}

	private int phoneAuditCount(UUID userId) {
		Integer count = jdbc.queryForObject(
				"SELECT count(*) FROM audit_log WHERE action = 'PHONE_CHANGED' AND entity_type = 'USER' AND entity_id = ?",
				Integer.class, userId);
		return count == null ? 0 : count;
	}

	private static String uniquePhone() {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return "+8801" + suffix;
	}

	private static String uniqueEmail() {
		return "phone-" + UUID.randomUUID() + "@example.com";
	}

}
