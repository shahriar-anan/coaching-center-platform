package com.coachingcenter.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.PostgresIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class PasswordResetAudienceIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CapturingEmailSender emails;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private SecretHasher hasher;

	@Test
	void forgotPasswordMailsOnlyAnActiveUserWhoIsNotTheMasterAdmin() throws Exception {
		String activeEmail = uniqueEmail();
		register(uniquePhone(), activeEmail);
		String pendingEmail = uniqueEmail();
		createStudent(pendingEmail);
		String inactiveEmail = uniqueEmail();
		register(uniquePhone(), inactiveEmail);
		jdbc.update("update users set status = 'INACTIVE' where email = ?", inactiveEmail);
		String missing = "missing-" + uniqueEmail();

		MvcResult active = forgot(activeEmail);
		MvcResult pending = forgot(pendingEmail);
		MvcResult inactive = forgot(inactiveEmail);
		MvcResult unknown = forgot(missing);
		MvcResult master = forgot("master-bootstrap@example.com");

		String sentence = active.getResponse().getContentAsString();
		assertThat(pending.getResponse().getContentAsString()).isEqualTo(sentence);
		assertThat(inactive.getResponse().getContentAsString()).isEqualTo(sentence);
		assertThat(unknown.getResponse().getContentAsString()).isEqualTo(sentence);
		assertThat(master.getResponse().getContentAsString()).isEqualTo(sentence);
		assertThat(emails.latestReset(activeEmail)).isNotBlank();
		assertThat(emails.latestReset(pendingEmail)).isNull();
		assertThat(emails.latestReset(inactiveEmail)).isNull();
		assertThat(emails.latestReset(missing)).isNull();
		assertThat(emails.latestReset("master-bootstrap@example.com")).isNull();
	}

	@Test
	void resetOfAMasterAdminCodeIsRefused() throws Exception {
		UUID masterId = jdbc.queryForObject("select id from users where email = ?", UUID.class, "master-bootstrap@example.com");
		jdbc.update("""
				insert into one_time_codes (id, user_id, purpose, code_hash, expires_at, used_at, failed_attempts, created_at)
				values (?, ?, 'PASSWORD_RESET', ?, ?, null, 0, now())
				""", UUID.randomUUID(), masterId, hasher.sha256("MASTER01"), java.sql.Timestamp.from(Instant.now().plusSeconds(600)));
		mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"code":"MASTER01","password":"newpassword1"}
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MASTER_ADMIN_PROTECTED"));
		mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"+8801700000001","password":"bootstrap-password-1"}
					"""))
			.andExpect(status().isOk());
		assertThat(emails.passwordChangedCount("master-bootstrap@example.com")).isZero();
	}

	private void createStudent(String email) throws Exception {
		mockMvc.perform(post("/api/v1/students").header("Authorization", "Bearer " + masterAccess())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Pending Student","phone":"%s","email":"%s"}
					""".formatted(uniquePhone(), email)))
			.andExpect(status().isCreated());
	}

	private String masterAccess() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"+8801700000001","password":"bootstrap-password-1"}
					"""))
			.andExpect(status().isOk())
			.andReturn();
		return json(result, "accessToken");
	}

	private void register(String phone, String email) throws Exception {
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Reset User","phone":"%s","email":"%s","password":"password1"}
					""".formatted(phone, email)))
			.andExpect(status().isCreated());
	}

	private MvcResult forgot(String email) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\"}")).andExpect(status().isOk()).andReturn();
	}

	private static String json(MvcResult result, String field) throws Exception {
		String body = result.getResponse().getContentAsString();
		String marker = "\"" + field + "\":\"";
		int start = body.indexOf(marker) + marker.length();
		return body.substring(start, body.indexOf('"', start));
	}

	private static String uniquePhone() {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return "+8801" + suffix;
	}

	private static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

}
