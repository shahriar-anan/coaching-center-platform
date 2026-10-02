package com.coachingcenter.api.users;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import javax.sql.DataSource;

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
import com.coachingcenter.api.auth.CapturingEmailConfig;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class DataIntegrityIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Test
	void softDeletedUsersAreHiddenAndCannotAuthenticate() throws Exception {
		String access = master();
		String phone = uniquePhone();
		String email = uniqueEmail();
		MvcResult created = mockMvc.perform(post("/api/v1/students").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content(account(phone, email)))
			.andExpect(status().isCreated())
			.andReturn();
		String id = json(created, "id");
		String code = json(created, "activationCode");
		mockMvc.perform(post("/api/v1/auth/activate").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"phone":"%s","code":"%s","password":"password1"}
					""".formatted(phone, code)))
			.andExpect(status().isNoContent());
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.delete("/api/v1/students/" + id)
			.header("Authorization", "Bearer " + access)).andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/students?q=" + email).header("Authorization", "Bearer " + access))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[?(@.email == '" + email + "')]").doesNotExist());
		mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"password1"}
					""".formatted(phone)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void softDeletedPhoneAndEmailCanBeReused() throws Exception {
		String access = master();
		String phone = uniquePhone();
		String email = uniqueEmail();
		String id = json(mockMvc.perform(post("/api/v1/students").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content(account(phone, email)))
			.andExpect(status().isCreated())
			.andReturn(), "id");
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.delete("/api/v1/students/" + id)
			.header("Authorization", "Bearer " + access)).andExpect(status().isNoContent());
		mockMvc.perform(post("/api/v1/students").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content(account(phone, email)))
			.andExpect(status().isCreated());
	}

	@Test
	void duplicateActivePhoneOrEmailIsRejected() throws Exception {
		String access = master();
		String phone = uniquePhone();
		String email = uniqueEmail();
		mockMvc.perform(post("/api/v1/students").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content(account(phone, email)))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/students").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content(account(phone, uniqueEmail())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DUPLICATE_PHONE"));
		mockMvc.perform(post("/api/v1/students").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content(account(uniquePhone(), email)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
	}

	private String master() throws Exception {
		String phone = uniquePhone();
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, 'MASTER_ADMIN', 'ACTIVE', now(), now())
				""", UUID.randomUUID(), phone, uniqueEmail(), passwords.encode("password1"));
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"password1"}
					""".formatted(phone)))
			.andExpect(status().isOk())
			.andReturn();
		return json(result, "accessToken");
	}

	private static String json(MvcResult result, String field) throws Exception {
		String body = result.getResponse().getContentAsString();
		String marker = "\"" + field + "\":\"";
		int start = body.indexOf(marker) + marker.length();
		return body.substring(start, body.indexOf('"', start));
	}

	private static String account(String phone, String email) {
		return """
				{"fullName":"Integrity User","phone":"%s","email":"%s"}
				""".formatted(phone, email);
	}

	private static String uniquePhone() {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return "+8801" + suffix;
	}

	private static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

}
