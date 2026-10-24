package com.coachingcenter.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class PasswordChangedIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CapturingEmailSender emails;

	@Test
	void resetAndChangePasswordEachSendOneNoteWithNoCode() throws Exception {
		String phone = uniquePhone();
		String email = uniqueEmail();
		register(phone, email);
		forgot(email);
		String code = emails.latestReset(email);
		mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"code":"%s","password":"newpassword1"}
					""".formatted(code)))
			.andExpect(status().isNoContent());
		assertThat(emails.passwordChangedCount(email)).isEqualTo(1);

		String access = json(login(phone, "newpassword1"), "accessToken");
		mockMvc.perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"currentPassword":"wrong-password","newPassword":"anotherpass1"}
					"""))
			.andExpect(status().isBadRequest());
		assertThat(emails.passwordChangedCount(email)).isEqualTo(1);

		mockMvc.perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"currentPassword":"newpassword1","newPassword":"anotherpass1"}
					"""))
			.andExpect(status().isNoContent());
		assertThat(emails.passwordChangedCount(email)).isEqualTo(2);
	}

	private void register(String phone, String email) throws Exception {
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Changed User","phone":"%s","email":"%s","password":"password1"}
					""".formatted(phone, email)))
			.andExpect(status().isCreated());
	}

	private void forgot(String email) throws Exception {
		mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\"}")).andExpect(status().isOk());
	}

	private MvcResult login(String phone, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"%s"}
					""".formatted(phone, password)))
			.andExpect(status().isOk())
			.andReturn();
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
		return "changed-" + java.util.UUID.randomUUID() + "@example.com";
	}

}
