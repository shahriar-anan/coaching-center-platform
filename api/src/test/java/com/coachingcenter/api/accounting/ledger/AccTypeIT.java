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

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.accounting.AccDates;
import com.coachingcenter.api.accounting.AccIds;
import com.coachingcenter.api.accounting.AccTestData;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Types (plan 4.2, contracts 3.1): the kinds and their rules, names within a group, the kind fixed once an entry uses the type, archive
 * and restore, and the order. Each test makes its own group; the seeded types are only read.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccTypeIT extends PostgresIntegrationTest {

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
	void createsEachKindAndNeedsAUnitForACountedType() throws Exception {
		UUID payees = group("PAYEES", "EXPENSE");
		JsonNode counted = api.respond(api.post("/api/v1/accounting/groups/" + payees + "/types", master,
				"{\"nameEn\":\"Lecture %s\",\"nameBn\":\"ক্লাস\",\"kind\":\"COUNTED\",\"unitEn\":\"class\",\"unitBn\":\"ক্লাস\"}"
					.formatted(suffix())), 201);
		assertThat(counted.get("kind").asText()).isEqualTo("COUNTED");
		assertThat(counted.get("unitEn").asText()).isEqualTo("class");
		assertThat(counted.get("unitBn").asText()).isEqualTo("ক্লাস");
		assertThat(counted.get("used").asBoolean()).isFalse();
		assertThat(counted.get("version").asLong()).isZero();

		JsonNode noUnit = api.respond(api.post("/api/v1/accounting/groups/" + payees + "/types", master,
				"{\"nameEn\":\"Exam %s\",\"kind\":\"COUNTED\"}".formatted(suffix())), 400);
		assertThat(fieldNames(noUnit)).contains("unitEn");

		JsonNode month = api.respond(api.post("/api/v1/accounting/groups/" + payees + "/types", master,
				"{\"nameEn\":\"Rent %s\",\"kind\":\"AMOUNT\",\"needsPeriod\":true,\"unitEn\":\"ignored\"}".formatted(suffix())), 201);
		assertThat(month.get("needsPeriod").asBoolean()).isTrue();
		assertThat(month.get("needsDescription").asBoolean()).isFalse();
		assertThat(LedgerTestApi.isNull(month, "unitEn")).as("a unit is kept for a counted type only").isTrue();

		api.respond(api.post("/api/v1/accounting/groups/" + payees + "/types", master,
				"{\"nameEn\":\"Fine %s\",\"kind\":\"DEDUCTION\"}".formatted(suffix())), 201);

		UUID log = group("LOG", "EXPENSE");
		JsonNode deduction = api.respond(api.post("/api/v1/accounting/groups/" + log + "/types", master,
				"{\"nameEn\":\"Fine %s\",\"kind\":\"DEDUCTION\"}".formatted(suffix())), 409);
		assertThat(deduction.get("message").asText()).isEqualTo("accounting.errors.group_mode");
	}

	@Test
	void namesAreUniqueWithinAGroupAndArchivedNamesAreFree() throws Exception {
		UUID group = group("LOG", "EXPENSE");
		String name = "Food " + suffix();
		JsonNode food = api.respond(api.post("/api/v1/accounting/groups/" + group + "/types", master,
				"{\"nameEn\":\"%s\",\"kind\":\"AMOUNT\"}".formatted(name)), 201);
		JsonNode duplicate = api.respond(api.post("/api/v1/accounting/groups/" + group + "/types", master,
				"{\"nameEn\":\"%s\",\"kind\":\"AMOUNT\"}".formatted(name.toLowerCase())), 409);
		assertThat(duplicate.get("message").asText()).isEqualTo("accounting.errors.name_taken");

		// The same name in another group is fine.
		UUID other = group("LOG", "EXPENSE");
		api.respond(api.post("/api/v1/accounting/groups/" + other + "/types", master,
				"{\"nameEn\":\"%s\",\"kind\":\"AMOUNT\"}".formatted(name)), 201);

		String id = food.get("id").asText();
		JsonNode archived = api.respond(api.post("/api/v1/accounting/types/" + id + "/archive", master, null), 200);
		assertThat(archived.get("archived").asBoolean()).isTrue();
		api.respond(api.post("/api/v1/accounting/groups/" + group + "/types", master,
				"{\"nameEn\":\"%s\",\"kind\":\"AMOUNT\"}".formatted(name)), 201);
		JsonNode restoreRefused = api.respond(api.post("/api/v1/accounting/types/" + id + "/restore", master, null), 409);
		assertThat(restoreRefused.get("message").asText()).isEqualTo("accounting.errors.name_taken");
	}

	@Test
	void theKindIsFixedOnceAnEntryUsesTheTypeButTheNameCanChange() throws Exception {
		UUID payees = group("PAYEES", "EXPENSE");
		AccTestData data = new AccTestData(jdbc);
		UUID usedType = data.type(payees, "AMOUNT");
		data.charge(data.payee(payees), usedType, "100.00", LocalDate.now(AccDates.DHAKA));

		JsonNode refused = api.respond(api.patch("/api/v1/accounting/types/" + usedType, master,
				"{\"kind\":\"DEDUCTION\",\"version\":0}"), 409);
		assertThat(refused.get("message").asText()).isEqualTo("accounting.errors.type_kind_used");

		JsonNode renamed = api.respond(api.patch("/api/v1/accounting/types/" + usedType, master,
				"{\"nameEn\":\"Bonus %s\",\"kind\":\"AMOUNT\",\"needsDescription\":true,\"version\":0}".formatted(suffix())), 200);
		assertThat(renamed.get("needsDescription").asBoolean()).isTrue();
		assertThat(renamed.get("used").asBoolean()).isTrue();
		assertThat(renamed.get("version").asLong()).isEqualTo(1L);

		UUID freeType = data.type(payees, "AMOUNT");
		JsonNode changed = api.respond(api.patch("/api/v1/accounting/types/" + freeType, master,
				"{\"kind\":\"COUNTED\",\"unitEn\":\"sheet\",\"version\":0}"), 200);
		assertThat(changed.get("kind").asText()).isEqualTo("COUNTED");
		assertThat(changed.get("unitEn").asText()).isEqualTo("sheet");

		JsonNode stale = api.respond(api.patch("/api/v1/accounting/types/" + freeType, master, "{\"nameEn\":\"X\",\"version\":0}"), 409);
		assertThat(stale.get("code").asText()).isEqualTo("STALE_VERSION");

		JsonNode noVersion = api.respond(api.patch("/api/v1/accounting/types/" + freeType, master, "{\"nameEn\":\"X\"}"), 400);
		assertThat(fieldNames(noVersion)).contains("version");

		JsonNode noUnit = api.respond(api.patch("/api/v1/accounting/types/" + freeType, master,
				"{\"unitEn\":null,\"version\":1}"), 400);
		assertThat(fieldNames(noUnit)).contains("unitEn");
	}

	@Test
	void anArchivedTypeCannotBeEditedAndTheListHidesIt() throws Exception {
		UUID group = group("LOG", "EXPENSE");
		JsonNode type = api.respond(api.post("/api/v1/accounting/groups/" + group + "/types", master,
				"{\"nameEn\":\"Travel %s\",\"kind\":\"AMOUNT\"}".formatted(suffix())), 201);
		String id = type.get("id").asText();
		api.respond(api.post("/api/v1/accounting/types/" + id + "/archive", master, null), 200);

		JsonNode archivedEdit = api.respond(api.patch("/api/v1/accounting/types/" + id, master, "{\"nameEn\":\"New\",\"version\":1}"), 409);
		assertThat(archivedEdit.get("message").asText()).isEqualTo("accounting.errors.type_archived");

		assertThat(typeIds(group, false)).doesNotContain(id);
		assertThat(typeIds(group, true)).contains(id);
	}

	@Test
	void theSeededOfflineInstructorTypesAreListedInOrder() throws Exception {
		List<String> names = new ArrayList<>();
		api.respond(api.get("/api/v1/accounting/groups/" + AccIds.GROUP_OFFLINE_INSTRUCTORS + "/types", master), 200)
			.forEach(type -> names.add(type.get("nameEn").asText()));
		assertThat(names).containsSubsequence("Lecture", "Exam", "Answer sheet evaluation", "Question making", "Bonus", "Deduction",
				"Other");
	}

	@Test
	void theOrderNamesEveryLiveTypeOnce() throws Exception {
		UUID group = group("LOG", "EXPENSE");
		List<String> made = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			made.add(api.respond(api.post("/api/v1/accounting/groups/" + group + "/types", master,
					"{\"nameEn\":\"Item %s\",\"kind\":\"AMOUNT\"}".formatted(suffix())), 201).get("id").asText());
		}
		List<String> reversed = new ArrayList<>(made);
		java.util.Collections.reverse(reversed);

		JsonNode missing = api.respond(api.post("/api/v1/accounting/groups/" + group + "/types/order", master,
				"{\"ids\":" + json.writeValueAsString(made.subList(0, 2)) + "}"), 400);
		assertThat(fieldNames(missing)).contains("ids");

		JsonNode ordered = api.respond(api.post("/api/v1/accounting/groups/" + group + "/types/order", master,
				"{\"ids\":" + json.writeValueAsString(reversed) + "}"), 200);
		List<String> after = new ArrayList<>();
		ordered.forEach(type -> after.add(type.get("id").asText()));
		assertThat(after).containsExactlyElementsOf(reversed);
		assertThat(typeIds(group, false)).containsExactlyElementsOf(reversed);
	}

	@Test
	void anUnknownGroupIsNotFound() throws Exception {
		api.respond(api.get("/api/v1/accounting/groups/" + UUID.randomUUID() + "/types", master), 404);
		api.respond(api.post("/api/v1/accounting/groups/" + UUID.randomUUID() + "/types", master,
				"{\"nameEn\":\"X\",\"kind\":\"AMOUNT\"}"), 404);
	}

	private UUID group(String mode, String direction) throws Exception {
		JsonNode group = api.respond(api.post("/api/v1/accounting/groups", master,
				"{\"nameEn\":\"Group %s\",\"mode\":\"%s\",\"direction\":\"%s\"}".formatted(suffix(), mode, direction)), 201);
		return UUID.fromString(group.get("id").asText());
	}

	private List<String> typeIds(UUID group, boolean archived) throws Exception {
		List<String> found = new ArrayList<>();
		api.respond(api.get("/api/v1/accounting/groups/" + group + "/types?archived=" + archived, master), 200)
			.forEach(type -> found.add(type.get("id").asText()));
		return found;
	}

}
