package com.coachingcenter.api.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.PostgresIntegrationTest;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class SessionProbeIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void visitorWithoutCookieIsReportedAsNoSession() throws Exception {
		mockMvc.perform(get("/api/v1/auth/session"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.hasSession").value(false))
			.andExpect(jsonPath("$.accessToken").doesNotExist())
			.andExpect(jsonPath("$.refreshToken").doesNotExist())
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}

	@Test
	void webLoginCookieIsReportedAsSessionWithoutBeingUsed() throws Exception {
		String phone = uniquePhone();
		register(phone, phone.substring(1) + "@example.com");
		MvcResult login = mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "web")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"password1"}
					""".formatted(phone)))
			.andExpect(status().isOk())
			.andReturn();
		Cookie refresh = login.getResponse().getCookie(AuthController.REFRESH_COOKIE);

		mockMvc.perform(get("/api/v1/auth/session").cookie(refresh))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.hasSession").value(true))
			.andExpect(jsonPath("$.accessToken").doesNotExist())
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

		// The probe does not rotate or revoke the cookie, so the same cookie still refreshes.
		mockMvc.perform(post("/api/v1/auth/refresh").header("X-Client-Type", "web").cookie(refresh)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").exists());
	}

	@Test
	void unknownCookieIsStillOnlyReportedByPresence() throws Exception {
		// The probe never looks the value up, so an unknown or used cookie reads as present;
		// the refresh call is what ends such a session.
		mockMvc.perform(get("/api/v1/auth/session").cookie(new Cookie(AuthController.REFRESH_COOKIE, "not-a-real-token")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.hasSession").value(true));

		mockMvc.perform(get("/api/v1/auth/session").cookie(new Cookie(AuthController.REFRESH_COOKIE, "")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.hasSession").value(false));
	}

	private void register(String phone, String email) throws Exception {
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Probe User","phone":"%s","email":"%s","password":"password1"}
					""".formatted(phone, email)))
			.andExpect(status().isCreated());
	}

	private static String uniquePhone() {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return "+8801" + suffix;
	}

}
