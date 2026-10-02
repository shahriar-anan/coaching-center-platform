package com.coachingcenter.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.PostgresIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class EmailVerificationIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CapturingEmailSender emails;

	@Test
	void verifyEmailConfirmsACodeAndResendIssuesAnother() throws Exception {
		String email = "verify-" + java.util.UUID.randomUUID() + "@example.com";
		String phone = uniquePhone();
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Verify User","phone":"%s","email":"%s","password":"password1"}
					""".formatted(phone, email)))
			.andExpect(status().isCreated());
		String first = emails.latestVerification(email);
		assertThat(first).isNotBlank();

		MvcResult login = mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"password1"}
					""".formatted(phone)))
			.andExpect(status().isOk())
			.andReturn();

		mockMvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
			.content("{\"code\":\"" + first + "\"}"))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + json(login, "accessToken")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.emailVerifiedAt").exists());

		String otherEmail = "resend-" + java.util.UUID.randomUUID() + "@example.com";
		String otherPhone = uniquePhone();
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Resend User","phone":"%s","email":"%s","password":"password1"}
					""".formatted(otherPhone, otherEmail)))
			.andExpect(status().isCreated());
		String original = emails.latestVerification(otherEmail);
		mockMvc.perform(post("/api/v1/auth/verify-email/resend").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + otherEmail + "\"}"))
			.andExpect(status().isOk());
		String resent = emails.latestVerification(otherEmail);
		assertThat(resent).isNotEqualTo(original);
		mockMvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
			.content("{\"code\":\"" + original + "\"}"))
			.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
			.content("{\"code\":\"" + resent + "\"}"))
			.andExpect(status().isNoContent());
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

}
