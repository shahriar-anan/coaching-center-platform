package com.coachingcenter.api.accounting.ledger;

import static com.coachingcenter.api.accounting.ledger.LedgerTestApi.fieldNames;
import static com.coachingcenter.api.accounting.ledger.LedgerTestApi.suffix;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
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
import org.springframework.test.web.servlet.ResultActions;

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.accounting.AccDates;
import com.coachingcenter.api.accounting.AccIds;
import com.coachingcenter.api.accounting.AccTestData;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Groups (plan 4.1, contracts 3.1): create, names, mode and direction, edit with the version, archive and restore, the list figures, and
 * the order. Each test makes its own groups; the seeded groups are only read, and the Offline instructors group is only refused.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccGroupIT extends PostgresIntegrationTest {

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
	void createsGroupsInTheModesAndRefusesPayeesWithIncome() throws Exception {
		String name = "Printing " + suffix();
		JsonNode payees = api.respond(api.post("/api/v1/accounting/groups", master,
				"{\"nameEn\":\"%s\",\"nameBn\":\"ছাপা খাত\",\"mode\":\"PAYEES\",\"direction\":\"EXPENSE\"}".formatted(name)), 201);
		assertThat(payees.get("nameEn").asText()).isEqualTo(name);
		assertThat(payees.get("nameBn").asText()).isEqualTo("ছাপা খাত");
		assertThat(payees.get("mode").asText()).isEqualTo("PAYEES");
		assertThat(payees.get("inFinance").asBoolean()).as("on when left out").isTrue();
		assertThat(LedgerTestApi.isNull(payees, "systemKey")).isTrue();
		assertThat(payees.get("payeeCount").asInt()).isZero();
		assertThat(payees.get("owed").asText()).isEqualTo("0.00");
		assertThat(payees.get("advance").asText()).isEqualTo("0.00");
		assertThat(payees.get("thisMonth").asText()).isEqualTo("0.00");
		assertThat(payees.get("version").asLong()).isZero();

		JsonNode log = api.respond(api.post("/api/v1/accounting/groups", master,
				"{\"nameEn\":\"Income %s\",\"mode\":\"LOG\",\"direction\":\"INCOME\",\"inFinance\":false}".formatted(suffix())), 201);
		assertThat(log.get("mode").asText()).isEqualTo("LOG");
		assertThat(log.get("inFinance").asBoolean()).isFalse();

		JsonNode refused = api.respond(api.post("/api/v1/accounting/groups", master,
				"{\"nameEn\":\"Income Payees %s\",\"mode\":\"PAYEES\",\"direction\":\"INCOME\"}".formatted(suffix())), 409);
		assertThat(refused.get("code").asText()).isEqualTo("ACC_REFUSED");
		assertThat(refused.get("message").asText()).isEqualTo("accounting.errors.group_mode");
	}

	@Test
	void namesAreUniqueAmongLiveGroupsIgnoringCase() throws Exception {
		String name = "Rent " + suffix();
		JsonNode first = api.respond(api.post("/api/v1/accounting/groups", master,
				"{\"nameEn\":\"%s\",\"mode\":\"LOG\",\"direction\":\"EXPENSE\"}".formatted(name)), 201);

		JsonNode duplicate = api.respond(api.post("/api/v1/accounting/groups", master,
				"{\"nameEn\":\"%s\",\"mode\":\"LOG\",\"direction\":\"EXPENSE\"}".formatted(name.toUpperCase())), 409);
		assertThat(duplicate.get("message").asText()).isEqualTo("accounting.errors.name_taken");

		// A seeded group's name is taken too.
		api.respond(api.post("/api/v1/accounting/groups", master,
				"{\"nameEn\":\"staff\",\"mode\":\"LOG\",\"direction\":\"EXPENSE\"}"), 409);

		// Archived, the name is free again; restoring it then needs the name free.
		String id = first.get("id").asText();
		api.respond(api.post("/api/v1/accounting/groups/" + id + "/archive", master, null), 200);
		api.respond(api.post("/api/v1/accounting/groups", master,
				"{\"nameEn\":\"%s\",\"mode\":\"LOG\",\"direction\":\"EXPENSE\"}".formatted(name)), 201);
		JsonNode restoreRefused = api.respond(api.post("/api/v1/accounting/groups/" + id + "/restore", master, null), 409);
		assertThat(restoreRefused.get("message").asText()).isEqualTo("accounting.errors.name_taken");
	}

	@Test
	void theFieldsAreCheckedAndTheModeAndDirectionAreChoices() throws Exception {
		JsonNode missing = api.respond(api.post("/api/v1/accounting/groups", master,
				"{\"mode\":\"LOG\",\"direction\":\"EXPENSE\"}"), 400);
		assertThat(fieldNames(missing)).contains("nameEn");

		JsonNode badMode = api.respond(api.post("/api/v1/accounting/groups", master,
				"{\"nameEn\":\"X " + suffix() + "\",\"mode\":\"SHOP\",\"direction\":\"EXPENSE\"}"), 400);
		assertThat(fieldNames(badMode)).contains("mode");

		JsonNode longName = api.respond(api.post("/api/v1/accounting/groups", master,
				"{\"nameEn\":\"" + "a".repeat(101) + "\",\"mode\":\"LOG\",\"direction\":\"EXPENSE\"}"), 400);
		assertThat(fieldNames(longName)).contains("nameEn");
	}

	@Test
	void editNeedsTheVersionKeepsTheFieldsLeftOutAndRefusesAStaleOne() throws Exception {
		JsonNode group = api.respond(api.post("/api/v1/accounting/groups", master,
				"{\"nameEn\":\"Old %s\",\"nameBn\":\"পুরোনো\",\"mode\":\"PAYEES\",\"direction\":\"EXPENSE\"}".formatted(suffix())), 201);
		String id = group.get("id").asText();

		JsonNode noVersion = api.respond(api.patch("/api/v1/accounting/groups/" + id, master, "{\"nameEn\":\"New\"}"), 400);
		assertThat(fieldNames(noVersion)).contains("version");

		JsonNode stale = api.respond(api.patch("/api/v1/accounting/groups/" + id, master,
				"{\"nameEn\":\"New\",\"version\":7}"), 409);
		assertThat(stale.get("code").asText()).isEqualTo("STALE_VERSION");

		String renamed = "Renamed " + suffix();
		JsonNode edited = api.respond(api.patch("/api/v1/accounting/groups/" + id, master,
				"{\"nameEn\":\"%s\",\"nameBn\":null,\"inFinance\":false,\"version\":0}".formatted(renamed)), 200);
		assertThat(edited.get("nameEn").asText()).isEqualTo(renamed);
		assertThat(LedgerTestApi.isNull(edited, "nameBn")).as("a null nameBn clears it").isTrue();
		assertThat(edited.get("inFinance").asBoolean()).isFalse();
		assertThat(edited.get("version").asLong()).isEqualTo(1L);
		assertThat(edited.get("mode").asText()).as("the mode never changes").isEqualTo("PAYEES");

		// Leaving out the name keeps it; the version has moved on, so a second edit must send 1.
		JsonNode kept = api.respond(api.patch("/api/v1/accounting/groups/" + id, master, "{\"inFinance\":true,\"version\":1}"), 200);
		assertThat(kept.get("nameEn").asText()).isEqualTo(renamed);
		assertThat(kept.get("inFinance").asBoolean()).isTrue();

		JsonNode taken = api.respond(api.patch("/api/v1/accounting/groups/" + id, master,
				"{\"nameEn\":\"Staff\",\"version\":2}"), 409);
		assertThat(taken.get("message").asText()).isEqualTo("accounting.errors.name_taken");
	}

	@Test
	void theOfflineInstructorsGroupCannotBeArchivedAndOthersCanBeArchivedAndRestored() throws Exception {
		JsonNode locked = api.respond(api.post("/api/v1/accounting/groups/" + AccIds.GROUP_OFFLINE_INSTRUCTORS + "/archive", master,
				null), 409);
		assertThat(locked.get("message").asText()).isEqualTo("accounting.errors.group_locked");

		JsonNode group = api.respond(api.post("/api/v1/accounting/groups", master,
				"{\"nameEn\":\"Hall %s\",\"mode\":\"LOG\",\"direction\":\"EXPENSE\"}".formatted(suffix())), 201);
		String id = group.get("id").asText();

		JsonNode archived = api.respond(api.post("/api/v1/accounting/groups/" + id + "/archive", master, null), 200);
		assertThat(archived.get("archived").asBoolean()).isTrue();
		assertThat(archived.get("version").asLong()).isEqualTo(1L);
		// Archiving again changes nothing.
		JsonNode again = api.respond(api.post("/api/v1/accounting/groups/" + id + "/archive", master, null), 200);
		assertThat(again.get("version").asLong()).isEqualTo(1L);

		assertThat(ids(api.get("/api/v1/accounting/groups", master))).doesNotContain(id);
		assertThat(ids(api.get("/api/v1/accounting/groups?archived=true", master))).contains(id);

		JsonNode restored = api.respond(api.post("/api/v1/accounting/groups/" + id + "/restore", master, null), 200);
		assertThat(restored.get("archived").asBoolean()).isFalse();
		assertThat(ids(api.get("/api/v1/accounting/groups", master))).contains(id);

		// Editing an archived group is refused.
		api.respond(api.post("/api/v1/accounting/groups/" + id + "/archive", master, null), 200);
		JsonNode editArchived = api.respond(api.patch("/api/v1/accounting/groups/" + id, master,
				"{\"nameEn\":\"Hall 2\",\"version\":2}"), 409);
		assertThat(editArchived.get("message").asText()).isEqualTo("accounting.errors.group_archived");
	}

	@Test
	void theFiguresOfAPayeesGroupAndALogGroupCountTheMonthAndTheBalances() throws Exception {
		AccTestData data = new AccTestData(jdbc);
		LocalDate today = LocalDate.now(AccDates.DHAKA);
		UUID payees = data.group("PAYEES", "EXPENSE");
		UUID type = data.type(payees, "AMOUNT");
		UUID cash = data.account("CASH");
		UUID owed = data.payee(payees);
		UUID advance = data.payee(payees);
		data.charge(owed, type, "500.00", today);
		data.payment(owed, cash, "200.00", today);
		data.payment(advance, cash, "100.00", today);

		JsonNode figures = group(payees);
		assertThat(figures.get("payeeCount").asInt()).isEqualTo(2);
		assertThat(figures.get("owed").asText()).isEqualTo("300.00");
		assertThat(figures.get("advance").asText()).isEqualTo("100.00");
		assertThat(figures.get("thisMonth").asText()).as("payments dated this month").isEqualTo("300.00");

		UUID spending = data.group("LOG", "EXPENSE");
		data.spend(spending, data.type(spending, "AMOUNT"), cash, "40.00", today);
		assertThat(group(spending).get("thisMonth").asText()).isEqualTo("40.00");
		assertThat(group(spending).get("owed").asText()).isEqualTo("0.00");

		UUID income = data.group("LOG", "INCOME");
		data.income(income, data.type(income, "AMOUNT"), cash, "70.00", today);
		assertThat(group(income).get("thisMonth").asText()).isEqualTo("70.00");
	}

	@Test
	void theOrderNamesEveryLiveGroupOnceAndTheSwapIsKept() throws Exception {
		JsonNode first = api.respond(api.post("/api/v1/accounting/groups", master,
				"{\"nameEn\":\"Alpha %s\",\"mode\":\"LOG\",\"direction\":\"EXPENSE\"}".formatted(suffix())), 201);
		JsonNode second = api.respond(api.post("/api/v1/accounting/groups", master,
				"{\"nameEn\":\"Beta %s\",\"mode\":\"LOG\",\"direction\":\"EXPENSE\"}".formatted(suffix())), 201);
		List<String> live = ids(api.get("/api/v1/accounting/groups", master));

		JsonNode missing = api.respond(api.post("/api/v1/accounting/groups/order", master,
				"{\"ids\":" + json.writeValueAsString(live.subList(1, live.size())) + "}"), 400);
		assertThat(fieldNames(missing)).contains("ids");

		List<String> doubled = new ArrayList<>(live);
		doubled.add(doubled.get(0));
		api.respond(api.post("/api/v1/accounting/groups/order", master, "{\"ids\":" + json.writeValueAsString(doubled) + "}"), 400);

		// Swap the two new groups; every other row keeps its place.
		List<String> swapped = new ArrayList<>(live);
		int a = swapped.indexOf(first.get("id").asText());
		int b = swapped.indexOf(second.get("id").asText());
		swapped.set(a, second.get("id").asText());
		swapped.set(b, first.get("id").asText());
		JsonNode ordered = api.respond(api.post("/api/v1/accounting/groups/order", master,
				"{\"ids\":" + json.writeValueAsString(swapped) + "}"), 200);
		List<String> after = new ArrayList<>();
		ordered.forEach(item -> after.add(item.get("id").asText()));
		assertThat(after).containsExactlyElementsOf(swapped);
	}

	@Test
	void unknownGroupsAreNotFound() throws Exception {
		api.respond(api.post("/api/v1/accounting/groups/" + UUID.randomUUID() + "/archive", master, null), 404);
		api.respond(api.patch("/api/v1/accounting/groups/" + UUID.randomUUID(), master, "{\"nameEn\":\"X\",\"version\":0}"), 404);
	}

	private JsonNode group(UUID id) throws Exception {
		JsonNode groups = api.respond(api.get("/api/v1/accounting/groups?archived=true", master), 200);
		for (JsonNode group : groups) {
			if (group.get("id").asText().equals(id.toString())) {
				return group;
			}
		}
		throw new AssertionError("group not listed: " + id);
	}

	private List<String> ids(ResultActions actions) throws Exception {
		JsonNode groups = api.respond(actions, 200);
		List<String> found = new ArrayList<>();
		groups.forEach(group -> found.add(group.get("id").asText()));
		return found;
	}

}
