package com.coachingcenter.api.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.PostgresIntegrationTest;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class TokenIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private JwtService jwt;

	@Test
	void refreshRotatesAndReuseRevokesTheFamily() throws Exception {
		String phone = uniquePhone();
		register(phone, phone.substring(1) + "@example.com");
		MvcResult login = loginMobile(phone);
		String firstRefresh = json(login, "refreshToken");

		MvcResult rotated = mockMvc.perform(post("/api/v1/auth/refresh").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"refreshToken\":\"" + firstRefresh + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").exists())
			.andExpect(jsonPath("$.refreshToken").exists())
			.andReturn();
		String secondRefresh = json(rotated, "refreshToken");

		mockMvc.perform(post("/api/v1/auth/refresh").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"refreshToken\":\"" + firstRefresh + "\"}"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/v1/auth/refresh").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"refreshToken\":\"" + secondRefresh + "\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void expiredAndMalformedTokensAreRejected() throws Exception {
		mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer not-a-token")).andExpect(status().isUnauthorized());
		String expired = jwt.issue(UUID.randomUUID(), Role.STUDENT, Instant.now().minusSeconds(60));
		mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + expired)).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/auth/refresh").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"refreshToken\":\"missing\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void logoutRevokesTheRefreshToken() throws Exception {
		String phone = uniquePhone();
		register(phone, phone.substring(1) + "@example.com");
		String refresh = json(loginMobile(phone), "refreshToken");
		String access = json(loginMobile(phone), "accessToken");

		mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + access)
			.header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"refreshToken\":\"" + refresh + "\"}"))
			.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/v1/auth/refresh").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"refreshToken\":\"" + refresh + "\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void refreshDeliveryDependsOnClientType() throws Exception {
		String phone = uniquePhone();
		register(phone, phone.substring(1) + "@example.com");
		String body = """
				{"identifier":"%s","password":"password1"}
				""".formatted(phone);

		mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "web")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.refreshToken").doesNotExist())
			.andExpect(cookie().exists(AuthController.REFRESH_COOKIE))
			.andExpect(cookie().httpOnly(AuthController.REFRESH_COOKIE, true))
			.andExpect(cookie().secure(AuthController.REFRESH_COOKIE, true))
			.andExpect(cookie().path(AuthController.REFRESH_COOKIE, "/api/v1/auth"))
			.andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("SameSite=Strict")));

		mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.refreshToken").exists())
			.andExpect(cookie().doesNotExist(AuthController.REFRESH_COOKIE));
	}

	@Test
	void inactiveUserCannotRefresh() throws Exception {
		String phone = uniquePhone();
		String email = phone.substring(1) + "@example.com";
		register(phone, email);
		MvcResult login = loginMobile(phone);
		jdbc.update("UPDATE users SET status = 'INACTIVE' WHERE email = ?", email);
		mockMvc.perform(post("/api/v1/auth/refresh").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"refreshToken\":\"" + json(login, "refreshToken") + "\"}"))
			.andExpect(status().isUnauthorized());
	}

	private void register(String phone, String email) throws Exception {
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Token User","phone":"%s","email":"%s","password":"password1"}
					""".formatted(phone, email)))
			.andExpect(status().isCreated());
	}

	private MvcResult loginMobile(String phone) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"password1"}
					""".formatted(phone)))
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

	@SuppressWarnings("unused")
	private Cookie ignored() {
		return null;
	}

}
