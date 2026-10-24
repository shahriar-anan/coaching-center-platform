package com.coachingcenter.api.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.coachingcenter.api.PostgresIntegrationTest;

@SpringBootTest(properties = {
		"app.mail.sends-per-email-per-hour=3",
		"app.mail.sends-per-ip-per-hour=10",
		"app.mail.resend-cooldown-seconds=0"
})
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class MailRateLimitIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void passwordResetReturns429AfterThreeSendsToOneEmail() throws Exception {
		String email = "reset-" + java.util.UUID.randomUUID() + "@example.com";
		for (int attempt = 0; attempt < 3; attempt++) {
			forgot(email, "203.0.113.10").andExpect(status().isOk());
		}
		forgot(email, "203.0.113.10").andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("EMAIL_RATE_LIMITED"))
			.andExpect(jsonPath("$.message").value("Too many requests. Try again later."));
	}

	@Test
	void registrationReturns429AfterThreeAttemptsForOneEmail() throws Exception {
		String email = "register-" + java.util.UUID.randomUUID() + "@example.com";
		register(email, "203.0.113.11").andExpect(status().isCreated());
		register(email, "203.0.113.11").andExpect(status().isConflict());
		register(email, "203.0.113.11").andExpect(status().isConflict());
		register(email, "203.0.113.11").andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("EMAIL_RATE_LIMITED"));
	}

	@Test
	void resendReturns429AfterThreeSendsToOneEmail() throws Exception {
		String email = "resend-" + java.util.UUID.randomUUID() + "@example.com";
		for (int attempt = 0; attempt < 3; attempt++) {
			resend(email, "203.0.113.12").andExpect(status().isOk());
		}
		resend(email, "203.0.113.12").andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("EMAIL_RATE_LIMITED"));
	}

	@Test
	void theSameIpIsLimitedAcrossDifferentEmails() throws Exception {
		String ip = "203.0.113.13";
		for (int attempt = 0; attempt < 10; attempt++) {
			forgot("ip-" + attempt + "-" + java.util.UUID.randomUUID() + "@example.com", ip).andExpect(status().isOk());
		}
		forgot("ip-last-" + java.util.UUID.randomUUID() + "@example.com", ip).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("EMAIL_RATE_LIMITED"));
	}

	private ResultActions forgot(String email, String ip) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/forgot-password").header("X-Real-IP", ip)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\"}"));
	}

	private ResultActions resend(String email, String ip) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/verify-email/resend").header("X-Real-IP", ip)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\"}"));
	}

	private ResultActions register(String email, String ip) throws Exception {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return mockMvc.perform(post("/api/v1/auth/register").header("X-Real-IP", ip)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Rate Limit","phone":"+8801%d","email":"%s","password":"password1"}
					""".formatted(suffix, email)));
	}

}
