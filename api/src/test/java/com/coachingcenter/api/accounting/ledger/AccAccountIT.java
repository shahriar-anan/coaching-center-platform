package com.coachingcenter.api.accounting.ledger;

import static com.coachingcenter.api.accounting.ledger.LedgerTestApi.fieldNames;
import static com.coachingcenter.api.accounting.ledger.LedgerTestApi.suffix;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Money accounts (plan 5.2, contracts 3.1): create, names, kinds, edit with the version, archive and restore, and the order. The seeded
 * accounts (Cash box, bKash, Nagad, Rocket, Bank) are only read.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccAccountIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	private LedgerTestApi api;

	private String master;

	@BeforeEach
	void setUp() throws Exception {
		api = new LedgerTestApi(mockMvc, jdbc, passwords, json);
		master = api.token(api.user("MASTER_ADMIN", "Master"));
	}

	@Test
	void createsAnAccountWithAKindAndANote() throws Exception {
		String name = "Savings " + suffix();
		JsonNode account = api.respond(api.post("/api/v1/accounting/accounts", master,
				"{\"name\":\"%s\",\"kind\":\"BANK\",\"note\":\"Branch cheques\"}".formatted(name)), 201);
		assertThat(account.get("name").asText()).isEqualTo(name);
		assertThat(account.get("kind").asText()).isEqualTo("BANK");
		assertThat(account.get("note").asText()).isEqualTo("Branch cheques");
		assertThat(account.get("archived").asBoolean()).isFalse();
		assertThat(account.get("version").asLong()).isZero();

		JsonNode badKind = api.respond(api.post("/api/v1/accounting/accounts", master,
				"{\"name\":\"Other %s\",\"kind\":\"CRYPTO\"}".formatted(suffix())), 400);
		assertThat(fieldNames(badKind)).contains("kind");

		JsonNode noName = api.respond(api.post("/api/v1/accounting/accounts", master, "{\"kind\":\"CASH\"}"), 400);
		assertThat(fieldNames(noName)).contains("name");
	}

	@Test
	void namesAreUniqueAmongLiveAccountsIgnoringCase() throws Exception {
		api.respond(api.post("/api/v1/accounting/accounts", master, "{\"name\":\"BKASH\",\"kind\":\"BKASH\"}"), 409);
		String name = "Wallet " + suffix();
		JsonNode wallet = api.respond(api.post("/api/v1/accounting/accounts", master,
				"{\"name\":\"%s\",\"kind\":\"CASH\"}".formatted(name)), 201);
		api.respond(api.post("/api/v1/accounting/accounts", master, "{\"name\":\"%s\",\"kind\":\"CASH\"}".formatted(name.toUpperCase())), 409);

		String id = wallet.get("id").asText();
		api.respond(api.post("/api/v1/accounting/accounts/" + id + "/archive", master, null), 200);
		api.respond(api.post("/api/v1/accounting/accounts", master, "{\"name\":\"%s\",\"kind\":\"CASH\"}".formatted(name)), 201);
		JsonNode restoreRefused = api.respond(api.post("/api/v1/accounting/accounts/" + id + "/restore", master, null), 409);
		assertThat(restoreRefused.get("message").asText()).isEqualTo("accounting.errors.name_taken");
	}

	@Test
	void editNeedsTheVersionAndRefusesAStaleOne() throws Exception {
		String id = api.respond(api.post("/api/v1/accounting/accounts", master,
				"{\"name\":\"Card %s\",\"kind\":\"CASH\"}".formatted(suffix())), 201).get("id").asText();

		JsonNode noVersion = api.respond(api.patch("/api/v1/accounting/accounts/" + id, master, "{\"kind\":\"BANK\"}"), 400);
		assertThat(fieldNames(noVersion)).contains("version");

		JsonNode stale = api.respond(api.patch("/api/v1/accounting/accounts/" + id, master, "{\"kind\":\"BANK\",\"version\":4}"), 409);
		assertThat(stale.get("code").asText()).isEqualTo("STALE_VERSION");

		JsonNode edited = api.respond(api.patch("/api/v1/accounting/accounts/" + id, master,
				"{\"kind\":\"BANK\",\"note\":null,\"version\":0}"), 200);
		assertThat(edited.get("kind").asText()).isEqualTo("BANK");
		assertThat(LedgerTestApi.isNull(edited, "note")).isTrue();
		assertThat(edited.get("version").asLong()).isEqualTo(1L);
		assertThat(edited.get("name").asText()).as("a name left out is kept").startsWith("Card ");

		JsonNode taken = api.respond(api.patch("/api/v1/accounting/accounts/" + id, master, "{\"name\":\"nagad\",\"version\":1}"), 409);
		assertThat(taken.get("message").asText()).isEqualTo("accounting.errors.name_taken");
	}

	@Test
	void anArchivedAccountIsHiddenCannotBeEditedAndCanBeRestored() throws Exception {
		String id = api.respond(api.post("/api/v1/accounting/accounts", master,
				"{\"name\":\"Old %s\",\"kind\":\"ROCKET\"}".formatted(suffix())), 201).get("id").asText();
		JsonNode archived = api.respond(api.post("/api/v1/accounting/accounts/" + id + "/archive", master, null), 200);
		assertThat(archived.get("archived").asBoolean()).isTrue();

		assertThat(ids(false)).doesNotContain(id);
		assertThat(ids(true)).contains(id);

		JsonNode edit = api.respond(api.patch("/api/v1/accounting/accounts/" + id, master, "{\"kind\":\"BANK\",\"version\":1}"), 409);
		assertThat(edit.get("message").asText()).isEqualTo("accounting.errors.account_archived");

		JsonNode restored = api.respond(api.post("/api/v1/accounting/accounts/" + id + "/restore", master, null), 200);
		assertThat(restored.get("archived").asBoolean()).isFalse();
		assertThat(ids(false)).contains(id);
	}

	@Test
	void theOrderNamesEveryLiveAccountOnce() throws Exception {
		List<String> live = ids(false);
		JsonNode missing = api.respond(api.post("/api/v1/accounting/accounts/order", master,
				"{\"ids\":" + json.writeValueAsString(live.subList(1, live.size())) + "}"), 400);
		assertThat(fieldNames(missing)).contains("ids");

		List<String> reversed = new ArrayList<>(live);
		Collections.reverse(reversed);
		JsonNode ordered = api.respond(api.post("/api/v1/accounting/accounts/order", master,
				"{\"ids\":" + json.writeValueAsString(reversed) + "}"), 200);
		List<String> after = new ArrayList<>();
		ordered.forEach(account -> after.add(account.get("id").asText()));
		assertThat(after).containsExactlyElementsOf(reversed);

		// Put the order back as it was, so the seeded accounts keep their places.
		api.respond(api.post("/api/v1/accounting/accounts/order", master, "{\"ids\":" + json.writeValueAsString(live) + "}"), 200);
	}

	@Test
	void unknownAccountsAreNotFound() throws Exception {
		api.respond(api.patch("/api/v1/accounting/accounts/" + UUID.randomUUID(), master, "{\"kind\":\"BANK\",\"version\":0}"), 404);
		api.respond(api.post("/api/v1/accounting/accounts/" + UUID.randomUUID() + "/archive", master, null), 404);
	}

	private List<String> ids(boolean archived) throws Exception {
		JsonNode accounts = api.respond(api.get("/api/v1/accounting/accounts?archived=" + archived, master), 200);
		List<String> found = new ArrayList<>();
		accounts.forEach(account -> found.add(account.get("id").asText()));
		return found;
	}

}
