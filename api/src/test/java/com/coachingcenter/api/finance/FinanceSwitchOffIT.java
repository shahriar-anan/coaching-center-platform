package com.coachingcenter.api.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import org.springframework.context.annotation.Import;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;

/**
 * With the offline switch off (plan 9, side effect 6), the finance reports leave out the offline sources: they are not in the source list,
 * a request that names them gets nothing for them, and a batch filter (which only offline sources have) gets zero. The online sources stay.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
@TestPropertySource(properties = "app.offline-admission.enabled=false")
class FinanceSwitchOffIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Test
	void theOfflineSourcesAreAbsentAndTheirTotalsAreZeroWhileTheSwitchIsOff() throws Exception {
		String master = token(seedMaster());

		JsonNode summary = respond(get("/api/v1/finance/summary?from=2100-01-01&to=2100-01-31").header("Authorization", "Bearer " + master));
		for (JsonNode source : summary.get("sources")) {
			assertThat(source.get("source").asText()).doesNotStartWith("OFFLINE_");
		}

		JsonNode named = respond(get("/api/v1/finance/summary?from=2100-01-01&to=2100-01-31&sources=OFFLINE_FEES,OFFLINE_INSTRUCTORS")
			.header("Authorization", "Bearer " + master));
		assertThat(named.get("income").decimalValue().signum()).isZero();
		assertThat(named.get("expense").decimalValue().signum()).isZero();
		assertThat(named.get("sources")).isEmpty();

		JsonNode batched = respond(get("/api/v1/finance/summary?from=2100-01-01&to=2100-01-31&batchId=" + UUID.randomUUID())
			.header("Authorization", "Bearer " + master));
		assertThat(batched.get("income").decimalValue().signum()).isZero();
		assertThat(batched.get("expense").decimalValue().signum()).isZero();
	}

	private UUID seedMaster() {
		UUID id = UUID.randomUUID();
		jdbc().update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, 'MASTER_ADMIN', 'ACTIVE', now(), now())
				""", id, "+8801" + (300000000 + (int) (Math.random() * 600000000)), "finance-off-" + id + "@example.com",
				passwords.encode("password1"));
		jdbc().update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?)", id, "Switch Off Master");
		return id;
	}

	private JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}

	private JsonNode respond(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
		MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	private String token(UUID userId) throws Exception {
		String phone = jdbc().queryForObject("select phone from users where id = ?", String.class, userId);
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

}
