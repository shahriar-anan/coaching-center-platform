package com.coachingcenter.api.accounting.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.coachingcenter.api.PostgresIntegrationTest;

import tools.jackson.databind.ObjectMapper;

/**
 * The ACCOUNTING permission guards every ledger route (contracts section 1): a System Admin gets 403 on each one, before anything else
 * is checked. Every route is called with a body (an empty object where one is read), so a missing body cannot answer first.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccAccessIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	private LedgerTestApi api;

	@BeforeEach
	void setUp() {
		api = new LedgerTestApi(mockMvc, jdbc, passwords, json);
	}

	@Test
	void everyLedgerRouteAnswers403ToASystemAdmin() throws Exception {
		String systemAdmin = api.token(api.user("SYSTEM_ADMIN", "System"));
		String id = UUID.randomUUID().toString();
		String base = "/api/v1/accounting";
		List<String> notRefused = new ArrayList<>();
		String[][] routes = {
				{ "GET", base + "/groups" },
				{ "POST", base + "/groups" },
				{ "PATCH", base + "/groups/" + id },
				{ "POST", base + "/groups/" + id + "/archive" },
				{ "POST", base + "/groups/" + id + "/restore" },
				{ "POST", base + "/groups/order" },
				{ "GET", base + "/groups/" + id + "/types" },
				{ "POST", base + "/groups/" + id + "/types" },
				{ "POST", base + "/groups/" + id + "/types/order" },
				{ "PATCH", base + "/types/" + id },
				{ "POST", base + "/types/" + id + "/archive" },
				{ "POST", base + "/types/" + id + "/restore" },
				{ "GET", base + "/accounts" },
				{ "POST", base + "/accounts" },
				{ "PATCH", base + "/accounts/" + id },
				{ "POST", base + "/accounts/" + id + "/archive" },
				{ "POST", base + "/accounts/" + id + "/restore" },
				{ "POST", base + "/accounts/order" },
				{ "GET", base + "/groups/" + id + "/payees" },
				{ "POST", base + "/groups/" + id + "/payees" },
				{ "GET", base + "/payees" },
				{ "GET", base + "/payees/" + id },
				{ "PATCH", base + "/payees/" + id },
				{ "POST", base + "/payees/" + id + "/archive" },
				{ "POST", base + "/payees/" + id + "/restore" },
				{ "GET", base + "/payees/" + id + "/last-prices" },
				{ "GET", base + "/users/linkable" },
				{ "POST", base + "/entries" },
				{ "POST", base + "/entries/many" },
				{ "GET", base + "/groups/" + id + "/copy-source" },
				{ "GET", base + "/entries/" + id },
		};
		for (String[] route : routes) {
			int status = send(route[0], route[1], systemAdmin);
			if (status != 403) {
				notRefused.add(route[0] + " " + route[1] + " answered " + status);
			}
		}
		assertThat(notRefused).isEmpty();
	}

	private int send(String method, String url, String token) throws Exception {
		var request = switch (method) {
			case "GET" -> org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url);
			case "PATCH" -> org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(url);
			default -> org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url);
		};
		request.header("Authorization", "Bearer " + token).contentType("application/json").content("{}");
		return mockMvc.perform(request).andReturn().getResponse().getStatus();
	}

}
