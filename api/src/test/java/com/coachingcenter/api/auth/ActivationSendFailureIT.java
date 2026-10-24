package com.coachingcenter.api.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.PostgresIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ActivationSendFailureIT.ThrowingMail.class)
class ActivationSendFailureIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void aFailedActivationSendDoesNotRollBackTheAccount() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/students").header("Authorization", "Bearer " + masterAccess())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Mail Down","phone":"%s","email":"%s"}
					""".formatted(uniquePhone(), uniqueEmail())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.activationCode").isNotEmpty())
			.andExpect(jsonPath("$.status").value("PENDING_ACTIVATION"))
			.andReturn();
		created.getResponse().getContentAsString();
	}

	private String masterAccess() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"+8801700000001","password":"bootstrap-password-1"}
					"""))
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
		return "down-" + java.util.UUID.randomUUID() + "@example.com";
	}

	@TestConfiguration
	static class ThrowingMail {

		@Bean
		@Primary
		EmailSender emailSender() {
			return new EmailSender() {
				@Override
				public void sendEmailVerification(String to, String code) {
				}

				@Override
				public void sendPasswordReset(String to, String code) {
				}

				@Override
				public void sendPasswordChanged(String to) {
				}

				@Override
				public void sendActivation(String to, String code) {
					throw new IllegalStateException("smtp down");
				}
			};
		}

	}

}
