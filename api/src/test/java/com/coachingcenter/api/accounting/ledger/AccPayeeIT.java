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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.accounting.AccDates;
import com.coachingcenter.api.accounting.AccIds;
import com.coachingcenter.api.accounting.AccTestData;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Payees (plan 4.3, contracts 3.1): create, names and aliases, the Offline instructors mirror (same id, the routine picker reads it),
 * archive and restore, the figures, the linked user, last prices and the picker. Each test makes its own group or payees; the seeded
 * Offline instructors group is written only with names no one else uses.
 */
@SpringBootTest
@AutoConfigureMockMvc
// The routine picker (/api/v1/offline-instructors) is behind the offline switch, which the tests turn on as the offline tests do.
@TestPropertySource(properties = "app.offline-admission.enabled=true")
class AccPayeeIT extends PostgresIntegrationTest {

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
	void createsAPayeeInAPayeesGroupAndItsRecordShowsTheFigures() throws Exception {
		AccTestData data = new AccTestData(jdbc);
		UUID group = payeesGroup();
		UUID type = data.type(group, "COUNTED");
		UUID cash = data.account("CASH");
		LocalDate today = LocalDate.now(AccDates.DHAKA);
		LocalDate yesterday = today.minusDays(1);

		JsonNode created = api.respond(api.post("/api/v1/accounting/groups/" + group + "/payees", master,
				"{\"name\":\"Rahim %s\",\"phone\":\"01711000111\",\"note\":\"bKash, pays on Fridays\"}".formatted(suffix())), 201);
		UUID payee = UUID.fromString(created.get("id").asText());
		assertThat(created.get("balance").asText()).isEqualTo("0.00");
		assertThat(created.get("phone").asText()).isEqualTo("01711000111");
		assertThat(created.get("note").asText()).isEqualTo("bKash, pays on Fridays");
		assertThat(created.get("aliases").isEmpty()).as("aliases count only for the Offline instructors group").isTrue();
		assertThat(created.get("totalCharged").asText()).isEqualTo("0.00");
		assertThat(created.get("paymentsCount").asInt()).isZero();
		assertThat(created.get("version").asLong()).isZero();

		data.countedCharge(payee, type, "40", "50.00", yesterday);
		data.countedCharge(payee, type, "10", "55.00", today);
		data.payment(payee, cash, "300.00", today);

		JsonNode detail = api.respond(api.get("/api/v1/accounting/payees/" + payee, master), 200);
		assertThat(detail.get("balance").asText()).isEqualTo("2250.00");
		assertThat(detail.get("totalCharged").asText()).isEqualTo("2550.00");
		assertThat(detail.get("totalPaid").asText()).isEqualTo("300.00");
		assertThat(detail.get("paymentsCount").asInt()).isEqualTo(1);
		assertThat(detail.get("paidThisMonth").asText()).isEqualTo("300.00");
		assertThat(detail.get("lastPaymentOn").asText()).isEqualTo(today.toString());
		assertThat(detail.get("lastPaymentAmount").asText()).isEqualTo("300.00");
		assertThat(detail.get("groupNameEn").asText()).isEqualTo(group(group).get("nameEn").asText());

		JsonNode list = api.respond(api.get("/api/v1/accounting/groups/" + group + "/payees", master), 200);
		assertThat(list.get(0).get("id").asText()).isEqualTo(payee.toString());
		assertThat(list.get(0).get("balance").asText()).isEqualTo("2250.00");
	}

	@Test
	void theLastPricesAreTheNewestActiveChargeOfEachType() throws Exception {
		AccTestData data = new AccTestData(jdbc);
		UUID group = payeesGroup();
		UUID lecture = data.type(group, "COUNTED");
		UUID sheets = data.type(group, "COUNTED");
		UUID payee = data.payee(group);
		LocalDate today = LocalDate.now(AccDates.DHAKA);
		data.countedCharge(payee, lecture, "12", "1000.00", today.minusDays(3));
		data.countedCharge(payee, lecture, "2", "1200.00", today.minusDays(1));
		UUID voided = data.countedCharge(payee, lecture, "1", "9999.00", today);
		data.voidEntry(voided, "Typed by mistake");
		data.countedCharge(payee, sheets, "40", "50.00", today);

		JsonNode prices = api.respond(api.get("/api/v1/accounting/payees/" + payee + "/last-prices", master), 200);
		assertThat(prices.size()).isEqualTo(2);
		JsonNode lectureLast = find(prices, "typeId", lecture);
		assertThat(lectureLast.get("unitPrice").asText()).isEqualTo("1200.00");
		assertThat(lectureLast.get("quantity").asText()).isEqualTo("2");
		assertThat(lectureLast.get("date").asText()).isEqualTo(today.minusDays(1).toString());
		JsonNode sheetLast = find(prices, "typeId", sheets);
		assertThat(sheetLast.get("quantity").asText()).isEqualTo("40");
		assertThat(sheetLast.get("amount").asText()).isEqualTo("2000.00");
	}

	@Test
	void theOfflineInstructorsPayeeIsTheRoutineInstructorAndTheAliasesAreKept() throws Exception {
		String name = "Tuhin " + suffix();
		String alias = "Tuhin S " + suffix();
		JsonNode created = api.respond(api.post("/api/v1/accounting/groups/" + AccIds.GROUP_OFFLINE_INSTRUCTORS + "/payees", master,
				"{\"name\":\"%s\",\"aliases\":[\"%s\", \"%s\", \" \"]}".formatted(name, alias, alias.toLowerCase())), 201);
		String id = created.get("id").asText();
		assertThat(strings(created.get("aliases"))).containsExactly(alias);

		// The routine picker reads the same record.
		JsonNode picker = api.respond(api.get("/api/v1/offline-instructors?q=" + name, master), 200);
		assertThat(picker.toString()).contains(id);
		assertThat(jdbc.queryForObject("select name from offline_instructors where id = ?", String.class, UUID.fromString(id)))
			.isEqualTo(name);

		String renamed = "Tuhin Renamed " + suffix();
		JsonNode edited = api.respond(api.patch("/api/v1/accounting/payees/" + id, master,
				"{\"name\":\"%s\",\"phone\":\"01822000111\",\"aliases\":[\"%s\"],\"version\":0}".formatted(renamed, alias)), 200);
		assertThat(edited.get("name").asText()).isEqualTo(renamed);
		assertThat(jdbc.queryForObject("select name from offline_instructors where id = ?", String.class, UUID.fromString(id)))
			.isEqualTo(renamed);
		assertThat(jdbc.queryForObject("select phone from offline_instructors where id = ?", String.class, UUID.fromString(id)))
			.isEqualTo("01822000111");

		// Archive takes the instructor out of the picker; restore brings it back.
		api.respond(api.post("/api/v1/accounting/payees/" + id + "/archive", master, null), 200);
		assertThat(jdbc.queryForObject("select archived_at is not null from offline_instructors where id = ?", Boolean.class,
				UUID.fromString(id))).isTrue();
		JsonNode live = api.respond(api.get("/api/v1/offline-instructors?q=" + renamed, master), 200);
		assertThat(live.toString()).doesNotContain(id);
		api.respond(api.post("/api/v1/accounting/payees/" + id + "/restore", master, null), 200);
		assertThat(jdbc.queryForObject("select archived_at is not null from offline_instructors where id = ?", Boolean.class,
				UUID.fromString(id))).isFalse();
	}

	@Test
	void anAliasOrNameBelongsToOneLiveInstructorOnly() throws Exception {
		String alias = "Milon " + suffix();
		api.respond(api.post("/api/v1/accounting/groups/" + AccIds.GROUP_OFFLINE_INSTRUCTORS + "/payees", master,
				"{\"name\":\"Milon Holder %s\",\"aliases\":[\"%s\"]}".formatted(suffix(), alias)), 201);

		JsonNode clash = api.respond(api.post("/api/v1/accounting/groups/" + AccIds.GROUP_OFFLINE_INSTRUCTORS + "/payees", master,
				"{\"name\":\"Someone %s\",\"aliases\":[\"%s\"]}".formatted(suffix(), alias.toUpperCase())), 409);
		assertThat(clash.get("message").asText()).isEqualTo("accounting.errors.alias_taken");

		String other = "Other " + suffix();
		String otherId = api.respond(api.post("/api/v1/accounting/groups/" + AccIds.GROUP_OFFLINE_INSTRUCTORS + "/payees", master,
				"{\"name\":\"%s\"}".formatted(other)), 201).get("id").asText();
		JsonNode renameClash = api.respond(api.patch("/api/v1/accounting/payees/" + otherId, master,
				"{\"aliases\":[\"%s\"],\"version\":0}".formatted(alias)), 409);
		assertThat(renameClash.get("message").asText()).isEqualTo("accounting.errors.alias_taken");

		// A name that is another instructor's name is refused as the same clash.
		JsonNode nameClash = api.respond(api.post("/api/v1/accounting/groups/" + AccIds.GROUP_OFFLINE_INSTRUCTORS + "/payees", master,
				"{\"name\":\"%s\"}".formatted(other.toLowerCase())), 409);
		assertThat(nameClash.get("message").asText()).isEqualTo("accounting.errors.name_taken");
	}

	@Test
	void namesAreUniqueInTheGroupAndRestoreRefusesAClash() throws Exception {
		UUID group = payeesGroup();
		String name = "Karim " + suffix();
		String id = api.respond(api.post("/api/v1/accounting/groups/" + group + "/payees", master,
				"{\"name\":\"%s\"}".formatted(name)), 201).get("id").asText();
		JsonNode duplicate = api.respond(api.post("/api/v1/accounting/groups/" + group + "/payees", master,
				"{\"name\":\"%s\"}".formatted(name.toUpperCase())), 409);
		assertThat(duplicate.get("message").asText()).isEqualTo("accounting.errors.name_taken");

		// The same name in another group is fine.
		api.respond(api.post("/api/v1/accounting/groups/" + payeesGroup() + "/payees", master,
				"{\"name\":\"%s\"}".formatted(name)), 201);

		api.respond(api.post("/api/v1/accounting/payees/" + id + "/archive", master, null), 200);
		api.respond(api.post("/api/v1/accounting/groups/" + group + "/payees", master,
				"{\"name\":\"%s\"}".formatted(name)), 201);
		JsonNode restoreRefused = api.respond(api.post("/api/v1/accounting/payees/" + id + "/restore", master, null), 409);
		assertThat(restoreRefused.get("message").asText()).isEqualTo("accounting.errors.name_taken");
	}

	@Test
	void archivedPayeesLeaveTheListButStayChargeableAndStillCountInTheOwedFigure() throws Exception {
		AccTestData data = new AccTestData(jdbc);
		UUID group = payeesGroup();
		UUID type = data.type(group, "AMOUNT");
		String id = api.respond(api.post("/api/v1/accounting/groups/" + group + "/payees", master,
				"{\"name\":\"Old %s\"}".formatted(suffix())), 201).get("id").asText();
		data.charge(UUID.fromString(id), type, "700.00", LocalDate.now(AccDates.DHAKA));

		JsonNode archived = api.respond(api.post("/api/v1/accounting/payees/" + id + "/archive", master, null), 200);
		assertThat(archived.get("archived").asBoolean()).isTrue();
		assertThat(archived.get("balance").asText()).as("an archived payee keeps its balance").isEqualTo("700.00");

		JsonNode hidden = api.respond(api.get("/api/v1/accounting/groups/" + group + "/payees", master), 200);
		assertThat(hidden.toString()).doesNotContain(id);
		JsonNode shown = api.respond(api.get("/api/v1/accounting/groups/" + group + "/payees?archived=true", master), 200);
		assertThat(shown.toString()).contains(id);
		JsonNode groupFigures = api.respond(api.get("/api/v1/accounting/groups?archived=true", master), 200);
		JsonNode thisGroup = null;
		for (JsonNode item : groupFigures) {
			if (item.get("id").asText().equals(group.toString())) {
				thisGroup = item;
			}
		}
		assertThat(thisGroup).isNotNull();
		assertThat(thisGroup.get("owed").asText()).isEqualTo("700.00");
		assertThat(thisGroup.get("payeeCount").asInt()).as("live payees only").isZero();

		// The picker finds live payees only.
		JsonNode picked = api.respond(api.get("/api/v1/accounting/payees?q=Old%20", master), 200);
		assertThat(picked.toString()).doesNotContain(id);
	}

	@Test
	void aLinkedUserIsAStaffOrInstructorAndIsLinkedToOnePayeeOnly() throws Exception {
		String name = "Staff Link " + suffix();
		UUID staff = api.user("SYSTEM_ADMIN", name);
		UUID admin = api.user("MASTER_ADMIN", "Admin " + suffix());
		UUID group = payeesGroup();
		String first = api.respond(api.post("/api/v1/accounting/groups/" + group + "/payees", master,
				"{\"name\":\"Linked %s\",\"userId\":\"%s\"}".formatted(suffix(), staff)), 201).get("id").asText();

		JsonNode linked = api.respond(api.get("/api/v1/accounting/payees/" + first, master), 200);
		assertThat(linked.get("userId").asText()).isEqualTo(staff.toString());
		assertThat(linked.get("userName").asText()).isEqualTo(name);
		assertThat(linked.get("staffPayBalance").asText()).as("no staff pay rows: a zero balance").isEqualTo("0.00");

		JsonNode users = api.respond(api.get("/api/v1/accounting/users/linkable?q=" + name, master), 200);
		assertThat(users.toString()).contains(staff.toString());

		JsonNode second = api.respond(api.post("/api/v1/accounting/groups/" + group + "/payees", master,
				"{\"name\":\"Second %s\",\"userId\":\"%s\"}".formatted(suffix(), staff)), 409);
		assertThat(second.get("message").asText()).isEqualTo("accounting.errors.user_linked");

		JsonNode notLinkable = api.respond(api.post("/api/v1/accounting/groups/" + group + "/payees", master,
				"{\"name\":\"Admin %s\",\"userId\":\"%s\"}".formatted(suffix(), admin)), 409);
		assertThat(notLinkable.get("message").asText()).isEqualTo("accounting.errors.user_not_linkable");

		JsonNode unlinked = api.respond(api.patch("/api/v1/accounting/payees/" + first, master, "{\"userId\":null,\"version\":0}"), 200);
		assertThat(LedgerTestApi.isNull(unlinked, "userId")).isTrue();
		assertThat(LedgerTestApi.isNull(unlinked, "staffPayBalance")).isTrue();
	}

	@Test
	void editNeedsTheVersionAndNamesStayUnique() throws Exception {
		UUID group = payeesGroup();
		String id = api.respond(api.post("/api/v1/accounting/groups/" + group + "/payees", master,
				"{\"name\":\"Edit %s\",\"phone\":\"01733000111\"}".formatted(suffix())), 201).get("id").asText();
		String taken = api.respond(api.post("/api/v1/accounting/groups/" + group + "/payees", master,
				"{\"name\":\"Taken %s\"}".formatted(suffix())), 201).get("name").asText();

		JsonNode noVersion = api.respond(api.patch("/api/v1/accounting/payees/" + id, master, "{\"phone\":null}"), 400);
		assertThat(fieldNames(noVersion)).contains("version");
		JsonNode stale = api.respond(api.patch("/api/v1/accounting/payees/" + id, master, "{\"phone\":null,\"version\":3}"), 409);
		assertThat(stale.get("code").asText()).isEqualTo("STALE_VERSION");

		JsonNode nameClash = api.respond(api.patch("/api/v1/accounting/payees/" + id, master,
				"{\"name\":\"%s\",\"version\":0}".formatted(taken.toLowerCase())), 409);
		assertThat(nameClash.get("message").asText()).isEqualTo("accounting.errors.name_taken");

		JsonNode edited = api.respond(api.patch("/api/v1/accounting/payees/" + id, master, "{\"phone\":null,\"version\":0}"), 200);
		assertThat(LedgerTestApi.isNull(edited, "phone")).isTrue();
		assertThat(edited.get("name").asText()).as("a name left out is kept").startsWith("Edit ");
		assertThat(edited.get("version").asLong()).isEqualTo(1L);
	}

	@Test
	void aPayeeCannotBeMadeInALogGroupOrAnArchivedOne() throws Exception {
		String logGroup = api.respond(api.post("/api/v1/accounting/groups", master,
				"{\"nameEn\":\"Log %s\",\"mode\":\"LOG\",\"direction\":\"EXPENSE\"}".formatted(suffix())), 201).get("id").asText();
		JsonNode refused = api.respond(api.post("/api/v1/accounting/groups/" + logGroup + "/payees", master,
				"{\"name\":\"Nobody\"}"), 409);
		assertThat(refused.get("message").asText()).isEqualTo("accounting.errors.group_mode");

		UUID group = payeesGroup();
		api.respond(api.post("/api/v1/accounting/groups/" + group + "/archive", master, null), 200);
		JsonNode archivedGroup = api.respond(api.post("/api/v1/accounting/groups/" + group + "/payees", master,
				"{\"name\":\"Nobody\"}"), 409);
		assertThat(archivedGroup.get("message").asText()).isEqualTo("accounting.errors.group_archived");
	}

	@Test
	void thePickerNeedsAQueryAndUnknownIdsAreNotFound() throws Exception {
		JsonNode missing = api.respond(api.get("/api/v1/accounting/payees", master), 400);
		assertThat(fieldNames(missing)).contains("q");
		JsonNode badLimit = api.respond(api.get("/api/v1/accounting/payees?q=a&limit=99", master), 400);
		assertThat(fieldNames(badLimit)).contains("limit");

		String phone = "0199" + String.format("%06d", Math.abs(suffix().hashCode()) % 1000000);
		UUID group = payeesGroup();
		api.respond(api.post("/api/v1/accounting/groups/" + group + "/payees", master,
				"{\"name\":\"Phone Person %s\",\"phone\":\"%s\"}".formatted(suffix(), phone)), 201);
		JsonNode found = api.respond(api.get("/api/v1/accounting/payees?q=" + phone, master), 200);
		assertThat(found.size()).isGreaterThanOrEqualTo(1);

		api.respond(api.get("/api/v1/accounting/payees/" + UUID.randomUUID(), master), 404);
		api.respond(api.get("/api/v1/accounting/groups/" + UUID.randomUUID() + "/payees", master), 404);
		api.respond(api.get("/api/v1/accounting/payees/" + UUID.randomUUID() + "/last-prices", master), 404);
	}

	private UUID payeesGroup() throws Exception {
		JsonNode group = api.respond(api.post("/api/v1/accounting/groups", master,
				"{\"nameEn\":\"Printers %s\",\"mode\":\"PAYEES\",\"direction\":\"EXPENSE\"}".formatted(suffix())), 201);
		return UUID.fromString(group.get("id").asText());
	}

	private JsonNode group(UUID id) throws Exception {
		JsonNode groups = api.respond(api.get("/api/v1/accounting/groups?archived=true", master), 200);
		for (JsonNode item : groups) {
			if (item.get("id").asText().equals(id.toString())) {
				return item;
			}
		}
		throw new AssertionError("group not listed: " + id);
	}

	private static JsonNode find(JsonNode items, String field, UUID value) {
		for (JsonNode item : items) {
			if (item.get(field).asText().equals(value.toString())) {
				return item;
			}
		}
		throw new AssertionError("not listed: " + value);
	}

	private static List<String> strings(JsonNode array) {
		List<String> found = new ArrayList<>();
		array.forEach(item -> found.add(item.asText()));
		return found;
	}

}
