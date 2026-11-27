package com.coachingcenter.api.accounting.ledger;

import static com.coachingcenter.api.accounting.ledger.LedgerTestApi.fieldNames;
import static com.coachingcenter.api.accounting.ledger.LedgerTestApi.suffix;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
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
import com.coachingcenter.api.accounting.AccTestData;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Entries (plan 4.4, contracts 3.1): the kinds in the right and wrong groups, the counted maths, the deduction sign, the month and
 * description types, dates, "paid now", several lines together, copy last month, and the refusals of archived and unknown things. Each
 * test makes its own groups, payees, types and accounts; figures are read per payee or per group.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccEntryIT extends PostgresIntegrationTest {

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
	void eachKindIsSavedOnlyInTheGroupModeItFits() throws Exception {
		UUID payees = group("PAYEES", "EXPENSE");
		UUID expense = group("LOG", "EXPENSE");
		UUID income = group("LOG", "INCOME");
		UUID cash = data.account("CASH");
		UUID payee = data.payee(payees);
		UUID charge = data.type(payees, "AMOUNT");
		UUID spend = data.type(expense, "AMOUNT");
		UUID received = data.type(income, "AMOUNT");

		JsonNode owed = saved(entry(json("kind", "CHARGE", "groupId", payees, "payeeId", payee, "typeId", charge, "date", today,
				"amount", "500")));
		assertThat(owed.get("kind").asText()).isEqualTo("CHARGE");
		assertThat(owed.get("amount").asText()).isEqualTo("500.00");
		assertThat(owed.get("payeeName").asText()).isNotBlank();
		assertThat(owed.get("status").asText()).isEqualTo("ACTIVE");
		assertThat(owed.get("no").asLong()).isPositive();
		assertThat(owed.get("recordedByName").asText()).isEqualTo("Master");

		JsonNode paid = saved(entry(json("kind", "PAYMENT", "groupId", payees, "payeeId", payee, "accountId", cash, "date", today,
				"amount", "200", "reference", "bKash 8H7")));
		assertThat(paid.get("amount").asText()).isEqualTo("200.00");
		assertThat(paid.get("accountName").asText()).startsWith("Account ");
		assertThat(paid.get("reference").asText()).isEqualTo("bKash 8H7");
		assertThat(LedgerTestApi.isNull(paid, "typeId")).as("a payment names no type").isTrue();

		JsonNode out = saved(entry(json("kind", "SPEND", "groupId", expense, "typeId", spend, "accountId", cash, "date", today,
				"amount", 40.5, "counterparty", "Agora")));
		assertThat(out.get("amount").asText()).isEqualTo("40.50");
		assertThat(out.get("counterparty").asText()).isEqualTo("Agora");
		assertThat(out.get("direction").asText()).isEqualTo("EXPENSE");

		JsonNode in = saved(entry(json("kind", "INCOME", "groupId", income, "typeId", received, "accountId", cash, "date", today,
				"amount", "70")));
		assertThat(in.get("direction").asText()).isEqualTo("INCOME");

		assertThat(refusal(entry(json("kind", "CHARGE", "groupId", expense, "payeeId", payee, "typeId", spend, "date", today,
				"amount", "5")), 409)).isEqualTo("group_mode");
		assertThat(refusal(entry(json("kind", "SPEND", "groupId", payees, "typeId", charge, "accountId", cash, "date", today,
				"amount", "5")), 409)).isEqualTo("group_mode");
		assertThat(refusal(entry(json("kind", "INCOME", "groupId", expense, "typeId", spend, "accountId", cash, "date", today,
				"amount", "5")), 409)).isEqualTo("group_mode");
		assertThat(refusal(entry(json("kind", "PAYMENT", "groupId", expense, "payeeId", payee, "accountId", cash, "date", today,
				"amount", "5")), 409)).isEqualTo("group_mode");

		// The balance is the charge less the payment; the spending and income stay out of it.
		JsonNode balance = api.respond(api.get("/api/v1/accounting/payees/" + payee, master), 200);
		assertThat(balance.get("balance").asText()).isEqualTo("300.00");
	}

	@Test
	void theCountedAmountIsQuantityTimesPriceRoundedHalfUp() throws Exception {
		UUID payees = group("PAYEES", "EXPENSE");
		UUID payee = data.payee(payees);
		UUID counted = data.type(payees, "COUNTED");

		JsonNode lecture = saved(entry(charge(payees, payee, counted, "40", "50.00", null)));
		assertThat(lecture.get("amount").asText()).isEqualTo("2000.00");
		assertThat(lecture.get("quantity").asText()).isEqualTo("40");
		assertThat(lecture.get("unitPrice").asText()).isEqualTo("50.00");
		assertThat(lecture.get("unitEn").asText()).isEqualTo("unit");

		JsonNode half = saved(entry(charge(payees, payee, counted, "1.5", "999.99", null)));
		assertThat(half.get("amount").asText()).as("1.5 x 999.99 = 1499.985, half up").isEqualTo("1499.99");
		assertThat(half.get("quantity").asText()).isEqualTo("1.5");

		// Numbers are accepted as well as strings; an amount typed for a counted type is ignored.
		JsonNode numeric = saved(entry(json("kind", "CHARGE", "groupId", payees, "payeeId", payee, "typeId", counted, "date", today,
				"quantity", 12, "unitPrice", 1000, "amount", "1")));
		assertThat(numeric.get("amount").asText()).isEqualTo("12000.00");

		JsonNode threeDecimals = api.respond(entry(charge(payees, payee, counted, "3", "33.335", null)),
				400);
		assertThat(fieldNames(threeDecimals)).contains("unitPrice");
		JsonNode zero = api.respond(entry(charge(payees, payee, counted, "0", "10", null)), 400);
		assertThat(fieldNames(zero)).contains("quantity");
		assertThat(refusal(entry(charge(payees, payee, counted, null, "10", null)), 409)).isEqualTo("quantity_required");
		assertThat(refusal(entry(charge(payees, payee, counted, "3", null, null)), 409)).isEqualTo("price_required");
	}

	@Test
	void aDeductionIsStoredNegativeAndLowersTheBalance() throws Exception {
		UUID payees = group("PAYEES", "EXPENSE");
		UUID payee = data.payee(payees);
		UUID amount = data.type(payees, "AMOUNT");
		UUID deduction = data.type(payees, "DEDUCTION");
		UUID cash = data.account("CASH");
		data.charge(payee, amount, "1000.00", today);

		JsonNode fine = saved(entry(json("kind", "CHARGE", "groupId", payees, "payeeId", payee, "typeId", deduction, "date", today,
				"amount", "500")));
		assertThat(fine.get("amount").asText()).isEqualTo("-500.00");
		assertThat(fine.get("typeKind").asText()).isEqualTo("DEDUCTION");
		JsonNode balance = api.respond(api.get("/api/v1/accounting/payees/" + payee, master), 200);
		assertThat(balance.get("balance").asText()).isEqualTo("500.00");

		assertThat(refusal(entry(json("kind", "CHARGE", "groupId", payees, "payeeId", payee, "typeId", deduction, "date", today,
				"amount", "100", "paidNow", raw(json("accountId", cash)))), 409)).isEqualTo("paid_now_deduction");
	}

	@Test
	void aMonthAndADescriptionAreAskedForWhereTheTypeNeedsThem() throws Exception {
		UUID payees = group("PAYEES", "EXPENSE");
		UUID payee = data.payee(payees);
		UUID monthly = data.type(payees, "AMOUNT");
		jdbc.update("update acc_types set needs_period = true where id = ?", monthly);
		UUID other = data.type(payees, "AMOUNT");
		jdbc.update("update acc_types set needs_description = true where id = ?", other);

		assertThat(refusal(entry(json("kind", "CHARGE", "groupId", payees, "payeeId", payee, "typeId", monthly, "date", today,
				"amount", "300")), 409)).isEqualTo("period_required");
		JsonNode badMonth = api.respond(entry(json("kind", "CHARGE", "groupId", payees, "payeeId", payee,
				"typeId", monthly, "date", today, "period", "2026-13", "amount", "300")), 400);
		assertThat(fieldNames(badMonth)).contains("period");
		JsonNode salary = saved(entry(json("kind", "CHARGE", "groupId", payees, "payeeId", payee, "typeId", monthly, "date", today,
				"period", "2026-11", "amount", "300")));
		assertThat(salary.get("period").asText()).isEqualTo("2026-11");

		assertThat(refusal(entry(json("kind", "CHARGE", "groupId", payees, "payeeId", payee, "typeId", other, "date", today,
				"amount", "300")), 409)).isEqualTo("description_required");
		saved(entry(json("kind", "CHARGE", "groupId", payees, "payeeId", payee, "typeId", other, "date", today, "amount", "300",
				"description", "Printing the mock sheets")));
	}

	@Test
	void aFutureDayIsRefusedAndAPastDayIsAccepted() throws Exception {
		UUID payees = group("PAYEES", "EXPENSE");
		UUID payee = data.payee(payees);
		UUID type = data.type(payees, "AMOUNT");

		assertThat(refusal(entry(json("kind", "CHARGE", "groupId", payees, "payeeId", payee, "typeId", type, "date",
				today.plusDays(1), "amount", "1")), 409)).isEqualTo("future_date");
		JsonNode past = saved(entry(json("kind", "CHARGE", "groupId", payees, "payeeId", payee, "typeId", type, "date", "2020-03-05",
				"amount", "1")));
		assertThat(past.get("date").asText()).isEqualTo("2020-03-05");
		JsonNode notADay = api.respond(entry(json("kind", "CHARGE", "groupId", payees, "payeeId", payee,
				"typeId", type, "date", "2031-02-30", "amount", "1")), 400);
		assertThat(fieldNames(notADay)).contains("date");
	}

	@Test
	void paidNowPaysInFullOrInPartAndNotMoreThanTheCharge() throws Exception {
		UUID payees = group("PAYEES", "EXPENSE");
		UUID payee = data.payee(payees);
		UUID type = data.type(payees, "AMOUNT");
		UUID cash = data.account("CASH");

		JsonNode both = api.respond(entry(json("kind", "CHARGE", "groupId", payees, "payeeId", payee,
				"typeId", type, "date", today, "amount", "500", "paidNow", raw(json("accountId", cash, "reference", "TX1")))), 201);
		assertThat(both.size()).isEqualTo(2);
		JsonNode charge = both.get(0);
		JsonNode payment = both.get(1);
		assertThat(charge.get("kind").asText()).isEqualTo("CHARGE");
		assertThat(payment.get("kind").asText()).isEqualTo("PAYMENT");
		assertThat(payment.get("amount").asText()).as("the payment starts as the charge").isEqualTo("500.00");
		assertThat(payment.get("reference").asText()).isEqualTo("TX1");
		assertThat(payment.get("pairedEntryId").asText()).isEqualTo(charge.get("id").asText());

		JsonNode detail = api.respond(api.get("/api/v1/accounting/entries/" + charge.get("id").asText(), master), 200);
		assertThat(detail.get("kind").asText()).as("the entry's fields sit at the top level").isEqualTo("CHARGE");
		assertThat(detail.get("amount").asText()).isEqualTo("500.00");
		assertThat(detail.get("paired").get("id").asText()).isEqualTo(payment.get("id").asText());
		assertThat(detail.get("edits").isEmpty()).isTrue();
		JsonNode fromPayment = api.respond(api.get("/api/v1/accounting/entries/" + payment.get("id").asText(), master), 200);
		assertThat(fromPayment.get("paired").get("id").asText()).isEqualTo(charge.get("id").asText());

		JsonNode part = api.respond(entry(json("kind", "CHARGE", "groupId", payees, "payeeId", payee,
				"typeId", type, "date", today, "amount", "800", "paidNow", raw(json("accountId", cash, "amount", "300.00")))), 201);
		assertThat(part.get(1).get("amount").asText()).isEqualTo("300.00");

		assertThat(refusal(entry(json("kind", "CHARGE", "groupId", payees, "payeeId", payee, "typeId", type, "date", today,
				"amount", "100", "paidNow", raw(json("accountId", cash, "amount", "900")))), 409)).isEqualTo("paid_now_too_much");
		JsonNode notCharge = api.respond(entry(json("kind", "PAYMENT", "groupId", payees, "payeeId", payee,
				"accountId", cash, "date", today, "amount", "1", "paidNow", raw(json("accountId", cash)))), 400);
		assertThat(fieldNames(notCharge)).contains("paidNow");
	}

	@Test
	void severalLinesAreSavedTogetherOrNotAtAll() throws Exception {
		UUID payees = group("PAYEES", "EXPENSE");
		UUID first = data.payee(payees);
		UUID second = data.payee(payees);
		UUID type = data.type(payees, "AMOUNT");
		int before = count(payees);

		JsonNode bad = api.respond(api.post(ENTRIES + "/many", master, many(line(payees, first, type, "100"),
				line(payees, second, type, "0"))), 400);
		assertThat(fieldNames(bad)).contains("entries[1].amount");
		assertThat(count(payees)).as("one bad line saves nothing").isEqualTo(before);

		JsonNode saved = api.respond(api.post(ENTRIES + "/many", master, many(line(payees, first, type, "100"),
				line(payees, second, type, "250"))), 201);
		assertThat(saved.size()).isEqualTo(2);
		assertThat(count(payees)).isEqualTo(before + 2);
		assertThat(auditRows("ACC_ENTRIES_CREATED", saved.get(0).get("id").asText())).isEqualTo(1);

		assertThat(refusal(api.post(ENTRIES + "/many", master, "{\"entries\":[]}"), 400)).isEqualTo("entries");
	}

	@Test
	void severalLinesHaveAtMostTwoHundred() throws Exception {
		UUID payees = group("PAYEES", "EXPENSE");
		UUID payee = data.payee(payees);
		UUID type = data.type(payees, "AMOUNT");
		List<String> lines = new ArrayList<>();
		for (int i = 0; i < 201; i++) {
			lines.add(line(payees, payee, type, "1"));
		}
		assertThat(refusal(api.post(ENTRIES + "/many", master, "{\"entries\":[" + String.join(",", lines) + "]}"), 409))
			.isEqualTo("too_many_lines");
	}

	@Test
	void copyLastMonthOffersTheChargesAndSpendingWithTheMonthMovedOn() throws Exception {
		UUID payees = group("PAYEES", "EXPENSE");
		UUID expense = group("LOG", "EXPENSE");
		UUID income = group("LOG", "INCOME");
		UUID cash = data.account("CASH");
		UUID payee = data.payee(payees);
		UUID counted = data.type(payees, "COUNTED");
		jdbc.update("update acc_types set needs_period = true where id = ?", counted);
		UUID food = data.type(expense, "AMOUNT");
		UUID received = data.type(income, "AMOUNT");
		LocalDate lastMonth = YearMonth.from(today).minusMonths(1).atDay(3);
		String lastPeriod = YearMonth.from(today).minusMonths(1).toString();
		String thisPeriod = YearMonth.from(today).toString();

		UUID lectures = data.countedCharge(payee, counted, "40", "50.00", lastMonth);
		jdbc.update("update acc_entries set period = ? where id = ?", lastPeriod, lectures);
		data.payment(payee, cash, "100.00", lastMonth);
		UUID meal = data.spend(expense, food, cash, "120.00", lastMonth);
		data.income(income, received, cash, "70.00", lastMonth);

		JsonNode drafts = api.respond(api.get("/api/v1/accounting/groups/" + payees + "/copy-source", master), 200);
		assertThat(drafts.size()).as("payments are never copied").isEqualTo(1);
		JsonNode draft = drafts.get(0);
		assertThat(draft.get("sourceEntryId").asText()).isEqualTo(lectures.toString());
		assertThat(draft.get("date").asText()).isEqualTo(today.toString());
		assertThat(draft.get("period").asText()).isEqualTo(thisPeriod);
		assertThat(draft.get("quantity").asText()).isEqualTo("40");
		assertThat(draft.get("unitPrice").asText()).isEqualTo("50.00");
		assertThat(draft.get("amount").asText()).isEqualTo("2000.00");
		assertThat(draft.get("alreadyThisMonth").asBoolean()).isFalse();

		JsonNode spend = api.respond(api.get("/api/v1/accounting/groups/" + expense + "/copy-source", master), 200);
		assertThat(spend.size()).isEqualTo(1);
		assertThat(spend.get(0).get("sourceEntryId").asText()).isEqualTo(meal.toString());
		assertThat(spend.get(0).get("amount").asText()).isEqualTo("120.00");

		assertThat(api.respond(api.get("/api/v1/accounting/groups/" + income + "/copy-source", master), 200).size()).isZero();

		// This month's lecture already there: the same draft starts unticked.
		UUID todays = data.countedCharge(payee, counted, "40", "50.00", today);
		jdbc.update("update acc_entries set period = ? where id = ?", thisPeriod, todays);
		JsonNode again = api.respond(api.get("/api/v1/accounting/groups/" + payees + "/copy-source", master), 200);
		assertThat(again.get(0).get("alreadyThisMonth").asBoolean()).isTrue();

		assertThat(refusal(api.get("/api/v1/accounting/groups/" + payees + "/copy-source?month=2026-13", master), 400))
			.isEqualTo("month");
	}

	@Test
	void unknownIdsAreNotFoundAndArchivedThingsAreRefusedForNewEntries() throws Exception {
		UUID payees = group("PAYEES", "EXPENSE");
		UUID payee = data.payee(payees);
		UUID type = data.type(payees, "AMOUNT");
		UUID cash = data.account("CASH");
		UUID unknown = UUID.randomUUID();

		api.respond(entry(charge(unknown, payee, type, null, null, "1")), 404);
		api.respond(entry(charge(payees, unknown, type, null, null, "1")), 404);
		api.respond(entry(charge(payees, payee, unknown, null, null, "1")), 404);
		api.respond(entry(json("kind", "PAYMENT", "groupId", payees, "payeeId", payee, "accountId", unknown,
				"date", today, "amount", "1")), 404);
		api.respond(entry(json("kind", "SPEND", "groupId", payees, "typeId", type, "accountId", cash,
				"batchId", UUID.randomUUID(), "date", today, "amount", "1")), 404);
		api.respond(api.get("/api/v1/accounting/entries/" + UUID.randomUUID(), master), 404);

		jdbc.update("update acc_accounts set archived_at = now() where id = ?", cash);
		assertThat(refusal(entry(json("kind", "PAYMENT", "groupId", payees, "payeeId", payee, "accountId", cash, "date", today,
				"amount", "1")), 409)).isEqualTo("account_archived");

		jdbc.update("update acc_types set archived_at = now() where id = ?", type);
		assertThat(refusal(entry(charge(payees, payee, type, null, null, "1")), 409)).isEqualTo("type_archived");

		UUID live = data.type(payees, "AMOUNT");
		jdbc.update("update acc_groups set archived_at = now() where id = ?", payees);
		assertThat(refusal(entry(charge(payees, payee, live, null, null, "1")), 409)).isEqualTo("group_archived");
	}

	@Test
	void anEntryIsAuditedOnceWhenItIsSaved() throws Exception {
		UUID payees = group("PAYEES", "EXPENSE");
		UUID payee = data.payee(payees);
		UUID type = data.type(payees, "AMOUNT");
		JsonNode saved = saved(entry(charge(payees, payee, type, null, null, "10")));
		assertThat(auditRows("ACC_ENTRY_CREATED", saved.get("id").asText())).isEqualTo(1);
	}

	// ---- helpers ----------------------------------------------------------------------------------------------------------

	private UUID group(String mode, String direction) throws Exception {
		JsonNode group = api.respond(api.post("/api/v1/accounting/groups", master,
				json("nameEn", "Entries " + suffix(), "mode", mode, "direction", direction)), 201);
		return UUID.fromString(group.get("id").asText());
	}

	/** A charge body: quantity and unit price for a counted type, or a typed amount. A null field is left out. */
	private String charge(UUID group, UUID payee, UUID type, String quantity, String price, String amount) {
		return json("kind", "CHARGE", "groupId", group, "payeeId", payee, "typeId", type, "date", today, "quantity", quantity,
				"unitPrice", price, "amount", amount);
	}

	private String line(UUID group, UUID payee, UUID type, String amount) {
		return json("kind", "CHARGE", "groupId", group, "payeeId", payee, "typeId", type, "date", today, "amount", amount);
	}

	private String many(String... lines) {
		return "{\"entries\":[" + String.join(",", lines) + "]}";
	}

	private ResultActions entry(String body) throws Exception {
		return api.post(ENTRIES, master, body);
	}

	/**
	 * A JSON object from name and value pairs. A null value is left out; a number or a boolean is written bare; a {@link Raw} value is
	 * written as it is (a nested object); anything else is a quoted string.
	 */
	static String json(Object... pairs) {
		StringBuilder out = new StringBuilder("{");
		for (int i = 0; i < pairs.length; i += 2) {
			Object value = pairs[i + 1];
			if (value == null) {
				continue;
			}
			if (out.length() > 1) {
				out.append(',');
			}
			out.append('"').append(pairs[i]).append("\":");
			if (value instanceof Raw raw) {
				out.append(raw.json());
			}
			else if (value instanceof Number || value instanceof Boolean) {
				out.append(value);
			}
			else {
				out.append('"').append(value).append('"');
			}
		}
		return out.append('}').toString();
	}

	/** A value written into a JSON object as it is, for a nested object. */
	record Raw(String json) {
	}

	private static Raw raw(String json) {
		return new Raw(json);
	}

	/** The saved rows of a 201: the first is the entry (or the charge). */
	private JsonNode saved(ResultActions actions) throws Exception {
		return api.respond(actions, 201).get(0);
	}

	/** The refusal key of a 409, or the field names of a 400 joined with commas, for the test to compare. */
	private String refusal(ResultActions actions, int status) throws Exception {
		JsonNode body = api.respond(actions, status);
		if (status == 409) {
			return body.get("message").asText().replace("accounting.errors.", "");
		}
		return String.join(",", fieldNames(body));
	}

	private int count(UUID group) {
		return jdbc.queryForObject("select count(*) from acc_entries where group_id = ?", Integer.class, group);
	}

	private int auditRows(String action, String entityId) {
		return jdbc.queryForObject("select count(*) from audit_log where action = ? and entity_id = ?", Integer.class, action,
				UUID.fromString(entityId));
	}

}
