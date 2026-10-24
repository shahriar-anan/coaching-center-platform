package com.coachingcenter.api.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.PostgresIntegrationTest;

import javax.sql.DataSource;

@SpringBootTest(properties = {
		"spring.profiles.active=staging",
		"MAIL_TEST_ENABLED=true",
		"MAIL_HOST=smtp-relay.brevo.com",
		"MAIL_PORT=587",
		"MAIL_USERNAME=mail-test-user",
		"MAIL_PASSWORD=mail-test-not-a-real-key",
		"MAIL_FROM_ADDRESS=no-reply@symamlaw.com"
})
@AutoConfigureMockMvc
class MailTestEnabledIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@MockitoBean
	private SmtpEmailSender mail;

	@Test
	void aRecipientOtherThanTheSignedInAdminIsRejected() throws Exception {
		String email = "admin-" + java.util.UUID.randomUUID() + "@example.com";
		String access = adminAccess(email);
		when(mail.sendPasswordResetTest(email)).thenReturn(new SmtpEmailSender.DeliveryResult(true, "accepted"));

		mockMvc.perform(post("/api/v1/admin/mail-test").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"to\":\"other@example.com\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		verify(mail, never()).sendPasswordResetTest(anyString());

		mockMvc.perform(post("/api/v1/admin/mail-test").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"to\":\"" + email + "\"}"))
			.andExpect(status().isOk());
		verify(mail).sendPasswordResetTest(email);
	}

	@Test
	void theSixthTestWithinFifteenMinutesReturns429() throws Exception {
		String email = "limit-" + java.util.UUID.randomUUID() + "@example.com";
		String access = adminAccess(email);
		when(mail.sendPasswordResetTest(email)).thenReturn(new SmtpEmailSender.DeliveryResult(true, "accepted"));
		String body = "{\"to\":\"" + email + "\"}";
		for (int attempt = 0; attempt < 5; attempt++) {
			mockMvc.perform(post("/api/v1/admin/mail-test").header("Authorization", "Bearer " + access)
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk());
		}
		mockMvc.perform(post("/api/v1/admin/mail-test").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("MAIL_TEST_LIMITED"));
	}

	private String adminAccess(String email) throws Exception {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		String phone = "+8801" + suffix;
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, 'MASTER_ADMIN', 'ACTIVE', now(), now())
				""", java.util.UUID.randomUUID(), phone, email, passwords.encode("password1"));
		MvcResult login = mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"password1"}
					""".formatted(phone)))
			.andExpect(status().isOk())
			.andReturn();
		String body = login.getResponse().getContentAsString();
		String marker = "\"accessToken\":\"";
		int start = body.indexOf(marker) + marker.length();
		return body.substring(start, body.indexOf('"', start));
	}

}
