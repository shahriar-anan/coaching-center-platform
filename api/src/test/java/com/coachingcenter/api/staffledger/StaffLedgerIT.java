package com.coachingcenter.api.staffledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The staff pay ledger (staff pay plan, phase 3): the balance on both sides of a payment, partial and advance payments, voids,
 * the oldest-first overdue split, the account snapshot staying fixed, the settings and defaults rules, and who may call what.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class StaffLedgerIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	private JdbcTemplate jdbc;

	@BeforeEach
	void setUp() {
		jdbc = new JdbcTemplate(dataSource);
	}

	@Test
	void aPaymentMovesTheBalanceOnBothSidesAndAnAdvanceNeedsItsOwnTick() throws Exception {
		UUID master = seed("MASTER_ADMIN");
		UUID person = seed("INSTRUCTOR");
		String admin = token(master);

		send(HttpMethod.POST, "/api/v1/staff-pay/people/" + person + "/adjustments", admin,
				Map.of("amount", 1000, "note", "Bonus for the exam"), 201);
		assertMoney(detail(person, admin).path("summary").path("balance"), "1000");

		JsonNode first = send(HttpMethod.POST, "/api/v1/staff-pay/people/" + person + "/payments", admin,
				Map.of("amount", 400, "method", "BANK", "reference", "CHQ-1", "note", "First part"), 201);
		assertThat(first.path("kind").asText()).isEqualTo("PAYMENT");
		JsonNode afterFirst = detail(person, admin);
		assertMoney(afterFirst.path("summary").path("paid"), "400");
		assertMoney(afterFirst.path("summary").path("balance"), "600");

		JsonNode refused = send(HttpMethod.POST, "/api/v1/staff-pay/people/" + person + "/payments", admin,
				Map.of("amount", 700, "method", "BANK"), 409);
		assertThat(refused.path("code").asText()).isEqualTo("PAYMENT_EXCEEDS_BALANCE");

		JsonNode advance = send(HttpMethod.POST, "/api/v1/staff-pay/people/" + person + "/payments", admin,
				Map.of("amount", 700, "method", "BANK", "allowAdvance", true), 201);
		assertMoney(detail(person, admin).path("summary").path("balance"), "-100");

		JsonNode voided = send(HttpMethod.POST, "/api/v1/staff-pay/ledger/" + advance.path("id").asText() + "/void", admin,
				Map.of("reason", "Entered twice"), 200);
		assertThat(voided.path("voided").asBoolean()).isTrue();
		assertThat(voided.path("voidReason").asText()).isEqualTo("Entered twice");
		assertMoney(detail(person, admin).path("summary").path("balance"), "600");

		send(HttpMethod.POST, "/api/v1/staff-pay/ledger/" + advance.path("id").asText() + "/void", admin,
				Map.of("reason", "Again"), 409);

		assertThat(count("select count(*) from staff_notifications where recipient_id = ? and kind = 'PAYMENT_RECORDED'",
				person)).isEqualTo(2);
		assertThat(count("select count(*) from staff_notifications where recipient_id = ? and kind = 'PAYMENT_VOIDED'",
				person)).isEqualTo(1);
		assertThat(count("select count(*) from audit_log where action = 'STAFF_PAYMENT_RECORD' and actor_user_id = ?",
				master)).isGreaterThanOrEqualTo(2);
	}

	@Test
	void theOverdueMoneyIsWhatIsLeftOfTheOldestDuesAfterPayments() throws Exception {
		UUID master = seed("MASTER_ADMIN");
		UUID person = seed("SYSTEM_ADMIN");
		jdbc.update("""
				insert into staff_ledger (id, user_id, kind, amount, due_on, period, created_at)
				values (?, ?, 'SALARY', 500, '2020-01-05', '2019-12', now())
				""", UUID.randomUUID(), person);
		jdbc.update("""
				insert into staff_ledger (id, user_id, kind, amount, due_on, period, created_at)
				values (?, ?, 'SALARY', 300, '2099-01-05', '2098-12', now())
				""", UUID.randomUUID(), person);
		String admin = token(master);

		send(HttpMethod.POST, "/api/v1/staff-pay/people/" + person + "/payments", admin,
				Map.of("amount", 200, "method", "NAGAD"), 201);

		JsonNode summary = detail(person, admin).path("summary");
		assertMoney(summary.path("balance"), "600");
		assertMoney(summary.path("owedNow"), "600");
		assertMoney(summary.path("overdue"), "300");
	}

	@Test
	void aSalaryOrMarkingLineCannotBeVoidedAndAWrongOneIsOffsetByAnAdjustment() throws Exception {
		UUID master = seed("MASTER_ADMIN");
		UUID person = seed("INSTRUCTOR");
		UUID line = UUID.randomUUID();
		jdbc.update("""
				insert into staff_ledger (id, user_id, kind, amount, due_on, period, created_at)
				values (?, ?, 'SALARY', 500, '2026-10-05', '2026-09', now())
				""", line, person);
		String admin = token(master);

		JsonNode refused = send(HttpMethod.POST, "/api/v1/staff-pay/ledger/" + line + "/void", admin,
				Map.of("reason", "Wrong"), 409);
		assertThat(refused.path("code").asText()).isEqualTo("LEDGER_NOT_VOIDABLE");

		send(HttpMethod.POST, "/api/v1/staff-pay/people/" + person + "/adjustments", admin,
				Map.of("amount", -500, "note", "Offsets the wrong salary line"), 201);
		assertMoney(detail(person, admin).path("summary").path("balance"), "0");
	}

	@Test
	void theRightPeopleSeeTheRightRoutesAndNobodyWritesAnotherPersonsAccounts() throws Exception {
		UUID master = seed("MASTER_ADMIN");
		UUID sysAdmin = seed("SYSTEM_ADMIN");
		UUID instructor = seed("INSTRUCTOR");
		String masterToken = token(master);
		String sysAdminToken = token(sysAdmin);
		String instructorToken = token(instructor);

		send(HttpMethod.GET, "/api/v1/staff-pay/people", sysAdminToken, null, 403);
		send(HttpMethod.GET, "/api/v1/staff-pay/people/" + instructor, sysAdminToken, null, 403);
		send(HttpMethod.GET, "/api/v1/staff-pay/defaults", instructorToken, null, 403);
		send(HttpMethod.GET, "/api/v1/me/pay/summary", masterToken, null, 403);
		send(HttpMethod.GET, "/api/v1/me/pay/summary", sysAdminToken, null, 200);
		send(HttpMethod.GET, "/api/v1/me/pay/ledger", instructorToken, null, 200);
		send(HttpMethod.GET, "/api/v1/staff-pay/people", masterToken, null, 200);
		send(HttpMethod.GET, "/api/v1/staff-pay/people/" + master, masterToken, null, 404);

		// No route writes a payout account for someone else: the path exists to read only.
		mockMvc.perform(request(HttpMethod.POST, "/api/v1/staff-pay/people/" + instructor + "/payout-accounts")
			.header("Authorization", "Bearer " + masterToken)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"kind\":\"BKASH\",\"walletNumber\":\"01712344821\"}")).andExpect(status().is4xxClientError());
	}

	@Test
	void theAccountSnapshotStaysAfterThePersonEditsTheAccount() throws Exception {
		UUID master = seed("MASTER_ADMIN");
		UUID instructor = seed("INSTRUCTOR");
		UUID account = UUID.randomUUID();
		jdbc.update("""
				insert into staff_payout_accounts (id, user_id, kind, wallet_number, preferred, created_at, updated_at)
				values (?, ?, 'BKASH', '01712344821', true, now(), now())
				""", account, instructor);
		String admin = token(master);
		send(HttpMethod.POST, "/api/v1/staff-pay/people/" + instructor + "/adjustments", admin,
				Map.of("amount", 500, "note", "Marking"), 201);

		send(HttpMethod.POST, "/api/v1/staff-pay/people/" + instructor + "/payments", admin,
				Map.of("amount", 200, "method", "BANK", "accountId", account.toString()), 400);

		JsonNode paid = send(HttpMethod.POST, "/api/v1/staff-pay/people/" + instructor + "/payments", admin,
				Map.of("amount", 200, "method", "BKASH", "accountId", account.toString(), "reference", "TX-9"), 201);
		assertThat(paid.path("accountSnapshot").asText()).isEqualTo("bKash 017••••4821");

		jdbc.update("update staff_payout_accounts set wallet_number = '01999999999' where id = ?", account);

		JsonNode ledger = send(HttpMethod.GET, "/api/v1/staff-pay/people/" + instructor + "/ledger", admin, null, 200);
		boolean found = false;
		for (JsonNode row : ledger.path("content")) {
			if ("PAYMENT".equals(row.path("kind").asText())) {
				found = true;
				assertThat(row.path("accountSnapshot").asText()).isEqualTo("bKash 017••••4821");
			}
		}
		assertThat(found).isTrue();
		assertThat(count("select count(*) from staff_notifications where recipient_id = ? and payload::text like '%4821%'",
				instructor)).isZero();
		assertThat(count("select count(*) from staff_notifications where recipient_id = ? and payload::text like '%1712344%'",
				instructor)).isZero();
	}

	@Test
	void theSettingsRefuseTheWrongRolesFieldsAndAPersonSeesTheirOwnPay() throws Exception {
		UUID master = seed("MASTER_ADMIN");
		UUID sysAdmin = seed("SYSTEM_ADMIN");
		UUID instructor = seed("INSTRUCTOR");
		String admin = token(master);

		send(HttpMethod.PUT, "/api/v1/staff-pay/people/" + sysAdmin + "/settings", admin, Map.of("sheetRate", 60), 400);
		send(HttpMethod.PUT, "/api/v1/staff-pay/people/" + instructor + "/settings", admin,
				Map.of("monthlySalary", 20000, "payDay", 5, "salaryStart", "2026-10-01"), 400);

		JsonNode salary = send(HttpMethod.PUT, "/api/v1/staff-pay/people/" + sysAdmin + "/settings", admin,
				Map.of("monthlySalary", 20000, "payDay", 5, "salaryStart", "2026-10-01"), 200);
		assertMoney(salary.path("monthlySalary"), "20000");
		// Only an instructor has a sheet rate; the API omits the field for anyone else (non_null inclusion).
		assertThat(salary.path("effectiveSheetRate").isMissingNode() || salary.path("effectiveSheetRate").isNull()).isTrue();
		assertThat(detail(sysAdmin, admin).path("nextPayDate").asText()).isNotBlank();

		JsonNode rate = send(HttpMethod.PUT, "/api/v1/staff-pay/people/" + instructor + "/settings", admin,
				Map.of("sheetRate", 60), 200);
		assertMoney(rate.path("effectiveSheetRate"), "60");

		JsonNode defaults = send(HttpMethod.GET, "/api/v1/staff-pay/defaults", admin, null, 200);
		JsonNode cleared = send(HttpMethod.PUT, "/api/v1/staff-pay/people/" + instructor + "/settings", admin, Map.of(), 200);
		assertMoney(cleared.path("effectiveSheetRate"), defaults.path("sheetRate").asText());

		JsonNode own = send(HttpMethod.GET, "/api/v1/me/pay/summary", token(sysAdmin), null, 200);
		assertThat(own.path("role").asText()).isEqualTo("SYSTEM_ADMIN");
		assertMoney(own.path("monthlySalary"), "20000");
	}

	@Test
	void theDefaultsAreChangedByTheMasterAdminOnlyAndRejectNonsense() throws Exception {
		UUID master = seed("MASTER_ADMIN");
		UUID sysAdmin = seed("SYSTEM_ADMIN");
		String admin = token(master);
		JsonNode before = send(HttpMethod.GET, "/api/v1/staff-pay/defaults", admin, null, 200);

		send(HttpMethod.PUT, "/api/v1/staff-pay/defaults", token(sysAdmin),
				Map.of("sheetRate", 60, "unpaidReminderDays", 10, "salaryDueSoonDays", 2), 403);
		send(HttpMethod.PUT, "/api/v1/staff-pay/defaults", admin,
				Map.of("sheetRate", 0, "unpaidReminderDays", 0, "salaryDueSoonDays", 2), 400);

		JsonNode changed = send(HttpMethod.PUT, "/api/v1/staff-pay/defaults", admin,
				Map.of("sheetRate", 60, "unpaidReminderDays", 10, "salaryDueSoonDays", 2), 200);
		assertMoney(changed.path("sheetRate"), "60");
		assertThat(changed.path("unpaidReminderDays").asInt()).isEqualTo(10);

		// Put the defaults back as they were.
		send(HttpMethod.PUT, "/api/v1/staff-pay/defaults", admin,
				Map.of("sheetRate", before.path("sheetRate").asText(), "unpaidReminderDays",
						before.path("unpaidReminderDays").asInt(), "salaryDueSoonDays",
						before.path("salaryDueSoonDays").asInt()),
				200);
	}

	@Test
	void aPersonReadsTheirOwnPaymentsAndNoticesAndAnAdjustmentNeedsANote() throws Exception {
		UUID master = seed("MASTER_ADMIN");
		UUID instructor = seed("INSTRUCTOR");
		String admin = token(master);
		send(HttpMethod.POST, "/api/v1/staff-pay/people/" + instructor + "/adjustments", admin, Map.of("amount", 100), 400);
		send(HttpMethod.POST, "/api/v1/staff-pay/people/" + instructor + "/adjustments", admin,
				Map.of("amount", 100, "note", "Marking bonus"), 201);
		send(HttpMethod.POST, "/api/v1/staff-pay/people/" + instructor + "/payments", admin,
				Map.of("amount", 60, "method", "ROCKET", "reference", "R-1"), 201);

		String token = token(instructor);
		JsonNode summary = send(HttpMethod.GET, "/api/v1/me/pay/summary", token, null, 200);
		assertMoney(summary.path("summary").path("balance"), "40");
		assertMoney(summary.path("summary").path("paid"), "60");
		JsonNode ledger = send(HttpMethod.GET, "/api/v1/me/pay/ledger", token, null, 200);
		assertThat(ledger.path("totalElements").asLong()).isEqualTo(2);
		JsonNode notices = send(HttpMethod.GET, "/api/v1/me/staff-notifications", token, null, 200);
		assertThat(notices.path("content").toString()).contains("PAYMENT_RECORDED");
	}

	private JsonNode detail(UUID person, String token) throws Exception {
		return send(HttpMethod.GET, "/api/v1/staff-pay/people/" + person, token, null, 200);
	}

	private static void assertMoney(JsonNode node, String expected) {
		assertThat(new BigDecimal(node.asText()).compareTo(new BigDecimal(expected))).as(node.toString()).isZero();
	}

	/** Sends one request, expects {@code status}, and returns the body read as JSON (a missing body reads as an empty node). */
	private JsonNode send(HttpMethod method, String path, String token, Object body, int status) throws Exception {
		MockHttpServletRequestBuilder builder = request(method, path).header("Authorization", "Bearer " + token);
		if (body != null) {
			builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
		}
		else if (method == HttpMethod.POST || method == HttpMethod.PUT) {
			builder.contentType(MediaType.APPLICATION_JSON).content("{}");
		}
		MvcResult result = mockMvc.perform(builder).andExpect(status().is(status)).andReturn();
		String text = result.getResponse().getContentAsString();
		return text.isBlank() ? json.createObjectNode() : json.readTree(text);
	}

	private long count(String sql, Object... args) {
		Long value = jdbc.queryForObject(sql, Long.class, args);
		return value == null ? 0 : value;
	}

	private UUID seed(String role) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, uniquePhone(), "pay-" + id + "@example.com", passwords.encode("password1"), role);
		jdbc.update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Pay Test')", id);
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = jdbc.queryForObject("select phone from users where id = ?", String.class, userId);
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(Map.of("identifier", phone, "password", "password1"))))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
	}

	private static String uniquePhone() {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return "+8801" + suffix;
	}

}
