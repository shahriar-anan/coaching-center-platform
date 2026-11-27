package com.coachingcenter.api.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The payment records page after the accounting move (accounting plan 4.8, R3): instructor payments (CLASS and OTHER) are no longer listed
 * or corrected here. Fees, direct enrolment, online purchases and staff pay are unchanged. The database is shared, so the checks look at
 * what the page lists, not at a count.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class PaymentRecordKindsIT extends PostgresIntegrationTest {

	private static final Set<String> KINDS_LISTED = Set.of("FEE", "DIRECT", "ONLINE", "STAFF");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Test
	void theListHasNoInstructorPaymentsAndTheInstructorFilterIsGone() throws Exception {
		String master = token(seedMaster());

		JsonNode rows = json.readTree(mockMvc.perform(get("/api/v1/payment-records").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
		for (JsonNode row : rows) {
			assertThat(KINDS_LISTED).contains(row.get("kind").asText());
		}

		// Asking for the retired kinds gives nothing; an old instructor filter is ignored, not an error.
		for (String kind : List.of("CLASS", "OTHER")) {
			JsonNode none = json.readTree(mockMvc.perform(get("/api/v1/payment-records").param("kind", kind)
				.header("Authorization", "Bearer " + master)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
			assertThat(none).isEmpty();
		}
		mockMvc.perform(get("/api/v1/payment-records?instructorId=" + UUID.randomUUID()).header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
	}

	@Test
	void aCorrectionOfAnInstructorPaymentIsRefused() throws Exception {
		String master = token(seedMaster());
		for (String kind : List.of("CLASS", "OTHER")) {
			mockMvc.perform(post("/api/v1/payment-records/" + kind + "/" + UUID.randomUUID() + "/edits")
				.header("Authorization", "Bearer " + master)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":\"100\",\"reason\":\"Retired\"}"))
				.andExpect(status().isNotFound());
		}
	}

	/** A Master Admin with a password, made for this test. */
	private UUID seedMaster() {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, 'MASTER_ADMIN', 'ACTIVE', now(), now())
				""", id, "+8801" + ThreadLocalRandom.current().nextInt(300000000, 999999999), "records-" + id + "@example.com",
				passwords.encode("password1"));
		jdbc.update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?)", id, "Records Master");
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = jdbc.queryForObject("select phone from users where id = ?", String.class, userId);
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
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

}
