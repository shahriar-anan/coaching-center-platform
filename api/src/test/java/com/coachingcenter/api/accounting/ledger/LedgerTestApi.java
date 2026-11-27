package com.coachingcenter.api.accounting.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Requests and users for the ledger tests. A user with a password is made in the test database and signed in through the login route,
 * so the token carries the real role. Every name the tests make carries a random suffix (README 5.1).
 */
class LedgerTestApi {

	private final MockMvc mockMvc;

	private final JdbcTemplate jdbc;

	private final PasswordEncoder passwords;

	private final ObjectMapper json;

	LedgerTestApi(MockMvc mockMvc, JdbcTemplate jdbc, PasswordEncoder passwords, ObjectMapper json) {
		this.mockMvc = mockMvc;
		this.jdbc = jdbc;
		this.passwords = passwords;
		this.json = json;
	}

	/** A user with the role, a name, and the password "password1". */
	UUID user(String role, String name) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, "+8801" + ThreadLocalRandom.current().nextInt(300000000, 999999999), "ledger-" + id + "@example.com",
				passwords.encode("password1"), role);
		jdbc.update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?)", id, name);
		return id;
	}

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

	ResultActions get(String url, String token) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.get(url).header("Authorization", "Bearer " + token));
	}

	ResultActions post(String url, String token, String body) throws Exception {
		return send(MockMvcRequestBuilders.post(url), token, body);
	}

	ResultActions patch(String url, String token, String body) throws Exception {
		return send(MockMvcRequestBuilders.patch(url), token, body);
	}

	private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token,
			String body) throws Exception {
		request.header("Authorization", "Bearer " + token);
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request);
	}

	/** A request answered with the expected status; returns its JSON body (an empty object when there is none). */
	JsonNode respond(ResultActions actions, int expected) throws Exception {
		MvcResult result = actions.andReturn();
		String text = result.getResponse().getContentAsString();
		assertThat(result.getResponse().getStatus()).as("status for %s", text).isEqualTo(expected);
		return text.isBlank() ? json.createObjectNode() : json.readTree(text);
	}

	/** The API leaves null fields out of its answers (non_null), so a null field is absent or null. */
	static boolean isNull(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull();
	}

	/** The field names a 400 names. */
	static Set<String> fieldNames(JsonNode refusal) {
		Set<String> fields = new HashSet<>();
		refusal.get("fieldErrors").forEach(error -> fields.add(error.get("field").asText()));
		return fields;
	}

	static String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

}
