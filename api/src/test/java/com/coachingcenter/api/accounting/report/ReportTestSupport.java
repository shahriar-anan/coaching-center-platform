package com.coachingcenter.api.accounting.report;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Sign-in and requests for the report tests: a user with a password is made and logged in as the apps do, and a GET is answered with its
 * status or its JSON body. Used only by the tests of this package.
 */
final class ReportTestSupport {

	private final MockMvc mockMvc;

	private final JdbcTemplate jdbc;

	private final PasswordEncoder passwords;

	private final ObjectMapper json;

	ReportTestSupport(MockMvc mockMvc, JdbcTemplate jdbc, PasswordEncoder passwords, ObjectMapper json) {
		this.mockMvc = mockMvc;
		this.jdbc = jdbc;
		this.passwords = passwords;
		this.json = json;
	}

	/** A new user of the role, with a password and a name, ready to sign in. */
	UUID seedUser(String role) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, "+8801" + ThreadLocalRandom.current().nextInt(300000000, 999999999), "report-" + id + "@example.com",
				passwords.encode("password1"), role);
		jdbc.update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?)", id, "Report " + role);
		return id;
	}

	/** The access token of a user made with {@link #seedUser}. */
	String token(UUID userId) throws Exception {
		String phone = jdbc.queryForObject("select phone from users where id = ?", String.class, userId);
		MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"password1"}
					""".formatted(phone)))
			.andExpect(status().isOk())
			.andReturn();
		String body = result.getResponse().getContentAsString();
		String marker = "\"accessToken\":\"";
		int start = body.indexOf(marker) + marker.length();
		return body.substring(start, body.indexOf('"', start));
	}

	/** The HTTP status of a GET. */
	int code(String url, String token) throws Exception {
		return mockMvc.perform(get(url).header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus();
	}

	/** A GET answered with 200 and its JSON body. */
	JsonNode ok(String url, String token) throws Exception {
		MvcResult result = mockMvc.perform(get(url).header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	/**
	 * A GET answered with 200 and its JSON body, with one parameter given as a value rather than in the URL (a "#" in the value would be
	 * encoded twice by a URL template).
	 */
	JsonNode okParam(String url, String name, String value, String token) throws Exception {
		MvcResult result = mockMvc.perform(get(url).param(name, value).header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	/** A GET answered with 200 and its body as text (the CSV). */
	MvcResult raw(String url, String token) throws Exception {
		return mockMvc.perform(get(url).header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn();
	}

}
