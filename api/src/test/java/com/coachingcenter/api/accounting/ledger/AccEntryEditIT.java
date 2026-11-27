package com.coachingcenter.api.accounting.ledger;

import static com.coachingcenter.api.accounting.ledger.LedgerTestApi.fieldNames;
import static com.coachingcenter.api.accounting.ledger.LedgerTestApi.suffix;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
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
 * Edit and void (plan 4.5, contracts 3.1): the edit keeps its history and its version, a changed field is the only one recorded, the
 * kind and sign rules, the moved-over class limits, void with its pair, a voided entry is final, and a voided class payment unlocks its
 * class only when no other active class payment points at it. The class tests use the routine's batch and class, so the offline switch
 * is on here, as the offline tests have it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.offline-admission.enabled=true")
class AccEntryEditIT extends PostgresIntegrationTest {

	private static final String ENTRIES = "/api/v1/accounting/entries";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	private LedgerTestApi api;

	private AccTestData data;

	private String master;

	private LocalDate today;

	@BeforeEach
	void setUp() throws Exception {
		api = new LedgerTestApi(mockMvc, jdbc, passwords, json);
		data = new AccTestData(jdbc);
		master = api.token(api.user("MASTER_ADMIN", "Master"));
		today = LocalDate.now(AccDates.DHAKA);
	}

	@Test
	void anEditKeepsItsHistoryAndItsVersionAndRecordsOnlyTheChangedFields() throws Exception {
		UUID payees = group("PAYEES", "EXPENSE");
		UUID payee = data.payee(payees);
		UUID type = data.type(payees, "AMOUNT");
		UUID entry = data.charge(payee, type, "500.00", today);

		JsonNode noReason = api.respond(api.patch(url(entry), master, patch(type, "650", null, 0)), 400);
		assertThat(fieldNames(noReason)).contains("reason");
		JsonNode stale = api.respond(api.patch(url(entry), master, patch(type, "650", "Typo", 7)), 409);
		assertThat(stale.get("code").asText()).isEqualTo("STALE_VERSION");

		JsonNode edited = api.respond(api.patch(url(entry), master, patch(type, "650", "Typo in the bill", 0)), 200);
		assertThat(edited.get("amount").asText()).isEqualTo("650.00");
		assertThat(edited.get("version").asLong()).isEqualTo(1L);
		assertThat(edited.get("correctedAt").isNull()).isFalse();

		JsonNode detail = api.respond(api.get("/api/v1/accounting/entries/" + entry, master), 200);
		assertThat(detail.get("edits").size()).isEqualTo(1);
		JsonNode edit = detail.get("edits").get(0);
		assertThat(edit.get("reason").asText()).isEqualTo("Typo in the bill");
		assertThat(edit.get("byName").asText()).isEqualTo("Master");
		assertThat(edit.get("before").get("amount").asText()).isEqualTo("500.00");
		assertThat(edit.get("after").get("amount").asText()).isEqualTo("650.00");
		assertThat(edit.get("before").has("note")).as("an unchanged field is not recorded").isFalse();

		// The same form again changes nothing: no new history, no new version.
		JsonNode same = api.respond(api.patch(url(entry), master, patch(type, "650", "Again", 1)), 200);
		assertThat(same.get("version").asLong()).isEqualTo(1L);
		assertThat(api.respond(api.get("/api/v1/accounting/entries/" + entry, master), 200).get("edits").size()).isEqualTo(1);

		JsonNode noted = api.respond(api.patch(url(entry), master, patch(type, "650", "Added a note", 1, "Checked with the bill")), 200);
		assertThat(noted.get("note").asText()).isEqualTo("Checked with the bill");
		assertThat(noted.get("version").asLong()).isEqualTo(2L);
	}

	@Test
	void aCountedChargeRecomputesItsAmountFromQuantityAndPrice() throws Exception {
		UUID payees = group("PAYEES", "EXPENSE");
		UUID payee = data.payee(payees);
		UUID counted = data.type(payees, "COUNTED");
		UUID entry = data.countedCharge(payee, counted, "40", "50.00", today);

		JsonNode priced = api.respond(api.patch(url(entry), master, json("typeId", counted, "date", today, "quantity", "40",
				"unitPrice", "60", "reason", "Price rose", "version", 0)), 200);
		assertThat(priced.get("amount").asText()).isEqualTo("2400.00");
		JsonNode detail = api.respond(api.get("/api/v1/accounting/entries/" + entry, master), 200);
		assertThat(detail.get("edits").get(0).get("before").get("unitPrice").asText()).isEqualTo("50.00");
		assertThat(detail.get("edits").get(0).get("after").get("unitPrice").asText()).isEqualTo("60.00");
	}

	@Test
	void theKindIsFixedAndADeductionStaysADeduction() throws Exception {
		UUID payees = group("PAYEES", "EXPENSE");
		UUID payee = data.payee(payees);
		UUID amount = data.type(payees, "AMOUNT");
		UUID otherAmount = data.type(payees, "AMOUNT");
		UUID deduction = data.type(payees, "DEDUCTION");
		UUID fine = data.charge(payee, deduction, "-300.00", today);
		UUID bonus = data.charge(payee, amount, "300.00", today);

		JsonNode refused = api.respond(api.patch(url(fine), master, patch(amount, "300", "Wrong type", 0)), 409);
		assertThat(refused.get("message").asText()).isEqualTo("accounting.errors.type_kind_mismatch");
		JsonNode refusedBack = api.respond(api.patch(url(bonus), master, patch(deduction, "300", "Wrong type", 0)), 409);
		assertThat(refusedBack.get("message").asText()).isEqualTo("accounting.errors.type_kind_mismatch");

		JsonNode moved = api.respond(api.patch(url(bonus), master, patch(otherAmount, "300", "Other bonus", 0)), 200);
		assertThat(moved.get("typeId").asText()).isEqualTo(otherAmount.toString());
		JsonNode still = api.respond(api.patch(url(fine), master, patch(deduction, "300", "Same deduction", 0)), 200);
		assertThat(still.get("amount").asText()).as("a deduction keeps its stored sign").isEqualTo("-300.00");
	}

	@Test
	void aMovedOverClassEntryChangesOnlyItsMoneyDetails() throws Exception {
		UUID payees = group("PAYEES", "EXPENSE");
		UUID payee = data.payee(payees);
		UUID cash = data.account("CASH");
		UUID legacy = legacyPayment(payees, payee, cash, null, "300.00");

		JsonNode changed = api.respond(api.patch(url(legacy), master, json("accountId", cash, "date", today, "amount", "350.00",
				"reference", "NEW-REF", "note", "Fixed the amount", "reason", "Wrong amount", "version", 0)), 200);
		assertThat(changed.get("amount").asText()).isEqualTo("350.00");
		assertThat(changed.get("reference").asText()).isEqualTo("NEW-REF");
		assertThat(changed.get("legacy").asText()).isEqualTo("CLASS");

		JsonNode refused = api.respond(api.patch(url(legacy), master, json("accountId", cash, "date", today, "amount", "350.00",
				"description", "Something else", "reason", "Wrong", "version", 1)), 409);
		assertThat(refused.get("message").asText()).isEqualTo("accounting.errors.legacy_fields");
	}

	@Test
	void aVoidedEntryIsFinal() throws Exception {
		UUID payees = group("PAYEES", "EXPENSE");
		UUID payee = data.payee(payees);
		UUID type = data.type(payees, "AMOUNT");
		UUID entry = data.charge(payee, type, "100.00", today);

		JsonNode noReason = api.respond(api.post(url(entry) + "/void", master, "{}"), 400);
		assertThat(fieldNames(noReason)).contains("reason");

		JsonNode voided = api.respond(api.post(url(entry) + "/void", master, "{\"reason\":\"Entered twice\"}"), 200).get(0);
		assertThat(voided.get("status").asText()).isEqualTo("VOIDED");
		assertThat(voided.get("voidReason").asText()).isEqualTo("Entered twice");
		assertThat(voided.get("voidedByName").asText()).isEqualTo("Master");

		JsonNode again = api.respond(api.post(url(entry) + "/void", master, "{\"reason\":\"Again\"}"), 409);
		assertThat(again.get("message").asText()).isEqualTo("accounting.errors.voided");
		JsonNode editRefused = api.respond(api.patch(url(entry), master, patch(type, "120", "Edit", 1)), 409);
		assertThat(editRefused.get("message").asText()).isEqualTo("accounting.errors.voided");

		JsonNode balance = api.respond(api.get("/api/v1/accounting/payees/" + payee, master), 200);
		assertThat(balance.get("balance").asText()).as("a voided entry leaves the balance").isEqualTo("0.00");
	}

	@Test
	void voidingACharge_alsoVoidsItsPaymentWhenAsked() throws Exception {
		UUID payees = group("PAYEES", "EXPENSE");
		UUID payee = data.payee(payees);
		UUID type = data.type(payees, "AMOUNT");
		UUID cash = data.account("CASH");
		JsonNode both = api.respond(api.post(ENTRIES, master, json("kind", "CHARGE", "groupId", payees, "payeeId", payee, "typeId", type,
				"date", today, "amount", "400", "paidNow", raw(json("accountId", cash)))), 201);
		String charge = both.get(0).get("id").asText();
		String payment = both.get(1).get("id").asText();

		JsonNode pair = api.respond(api.post(url(UUID.fromString(charge)) + "/void", master,
				"{\"reason\":\"Wrong student\",\"alsoVoidPaired\":true}"), 200);
		assertThat(pair.size()).isEqualTo(2);
		assertThat(pair.get(0).get("status").asText()).isEqualTo("VOIDED");
		assertThat(pair.get(1).get("id").asText()).isEqualTo(payment);
		assertThat(pair.get(1).get("status").asText()).isEqualTo("VOIDED");

		// A payment voided alone leaves its charge.
		JsonNode both2 = api.respond(api.post(ENTRIES, master, json("kind", "CHARGE", "groupId", payees, "payeeId", payee, "typeId", type,
				"date", today, "amount", "90", "paidNow", raw(json("accountId", cash)))), 201);
		JsonNode alone = api.respond(api.post(url(UUID.fromString(both2.get(1).get("id").asText())) + "/void", master,
				"{\"reason\":\"Typed twice\"}"), 200);
		assertThat(alone.size()).isEqualTo(1);
		JsonNode detail = api.respond(api.get("/api/v1/accounting/entries/" + both2.get(0).get("id").asText(), master), 200);
		assertThat(detail.get("status").asText()).as("the charge stays active").isEqualTo("ACTIVE");
	}

	@Test
	void voidingAClassPaymentSetsItsClassBackOnlyWhenNoOtherActivePaymentRemains() throws Exception {
		String token = master;
		UUID batch = batch(token);
		UUID classId = insertClass(batch, "PAID");
		UUID payee = data.payee(AccIds.GROUP_OFFLINE_INSTRUCTORS);
		UUID cash = data.account("CASH");
		UUID first = legacyPayment(AccIds.GROUP_OFFLINE_INSTRUCTORS, payee, cash, classId, "200.00");
		UUID second = legacyPayment(AccIds.GROUP_OFFLINE_INSTRUCTORS, payee, cash, classId, "100.00");

		api.respond(api.post(url(first) + "/void", master, "{\"reason\":\"Paid twice\"}"), 200);
		assertThat(classStatus(classId)).as("another active payment still points at the class").isEqualTo("PAID");

		api.respond(api.post(url(second) + "/void", master, "{\"reason\":\"Paid twice\"}"), 200);
		assertThat(classStatus(classId)).as("the class can be edited again").isEqualTo("SCHEDULED");
	}

	@Test
	void anEditInAnArchivedGroupIsAllowedWhenItKeepsItsArchivedTypeButNotAnotherArchivedOne() throws Exception {
		UUID payees = group("PAYEES", "EXPENSE");
		UUID payee = data.payee(payees);
		UUID type = data.type(payees, "AMOUNT");
		UUID other = data.type(payees, "AMOUNT");
		UUID entry = data.charge(payee, type, "80.00", today);
		jdbc.update("update acc_types set archived_at = now() where id = ?", type);
		jdbc.update("update acc_types set archived_at = now() where id = ?", other);
		jdbc.update("update acc_groups set archived_at = now() where id = ?", payees);

		JsonNode kept = api.respond(api.patch(url(entry), master, patch(type, "90", "Kept the old type", 0)), 200);
		assertThat(kept.get("amount").asText()).isEqualTo("90.00");
		JsonNode refused = api.respond(api.patch(url(entry), master, patch(other, "90", "Moved to an archived type", 1)), 409);
		assertThat(refused.get("message").asText()).isEqualTo("accounting.errors.type_archived");
	}

	@Test
	void unknownEntriesAreNotFound() throws Exception {
		UUID unknown = UUID.randomUUID();
		api.respond(api.patch(url(unknown), master, patch(UUID.randomUUID(), "1", "x", 0)), 404);
		api.respond(api.post(url(unknown) + "/void", master, "{\"reason\":\"x\"}"), 404);
	}

	// ---- helpers ----------------------------------------------------------------------------------------------------------

	private UUID group(String mode, String direction) throws Exception {
		JsonNode group = api.respond(api.post("/api/v1/accounting/groups", master,
				json("nameEn", "Edits " + suffix(), "mode", mode, "direction", direction)), 201);
		return UUID.fromString(group.get("id").asText());
	}

	private static String url(UUID entry) {
		return ENTRIES + "/" + entry;
	}

	/** An edit of the form: the type, the date, the amount and the reason, with the version the caller read. */
	private String patch(UUID type, String amount, String reason, long version) {
		return patch(type, amount, reason, version, null);
	}

	private String patch(UUID type, String amount, String reason, long version, String note) {
		return json("typeId", type, "date", today, "amount", amount, "reason", reason, "version", version, "note", note);
	}

	/** A moved-over class payment with its money account, written the way the migration writes one. */
	private UUID legacyPayment(UUID group, UUID payee, UUID account, UUID classId, String amount) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into acc_entries (id, kind, status, group_id, payee_id, entry_date, amount, account_id, legacy_source, legacy_id,
				  legacy_class_id, recorded_by, recorded_at, created_at, updated_at)
				values (?, 'PAYMENT', 'ACTIVE', ?, ?, ?, cast(? as numeric), ?, 'CLASS', ?, ?, ?, now(), now(), now())
				""", id, group, payee, today, amount, account, UUID.randomUUID(), classId, data.masterAdmin());
		return id;
	}

	private UUID batch(String token) throws Exception {
		JsonNode batch = api.respond(api.post("/api/v1/offline-batches", token,
				"{\"nameEn\":\"BJS Edit %s\",\"nameBn\":\"বিজেএস\",\"endsOn\":\"2027-04-30\",\"courseFee\":3000}".formatted(suffix())), 201);
		UUID id = UUID.fromString(batch.get("id").asText());
		api.respond(api.post("/api/v1/offline-batches/" + id + "/publish", token, null), 200);
		return id;
	}

	private UUID insertClass(UUID batch, String status) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into offline_classes (id, batch_id, held_on, subject, kind, instructor_text, status, created_at, updated_at, version)
				values (?, ?, ?, 'Evidence Act', 'LECTURE', 'Test', ?, now(), now(), 0)
				""", id, batch, LocalDate.parse("2026-11-03"), status);
		return id;
	}

	private String classStatus(UUID classId) {
		return jdbc.queryForObject("select status from offline_classes where id = ?", String.class, classId);
	}

	private static String json(Object... pairs) {
		return AccEntryIT.json(pairs);
	}

	private static AccEntryIT.Raw raw(String json) {
		return new AccEntryIT.Raw(json);
	}

}
