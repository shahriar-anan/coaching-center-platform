package com.coachingcenter.api.staffledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * The overview and analytics of the staff pay ledger (staff pay plan, phase 6). The shared test database holds other tests'
 * lines too, so the overview totals are checked as differences from a reading taken before the lines were written. The analytics
 * use months in 2031 that no other test writes to, so their figures are exact.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class StaffPayAnalyticsIT extends PostgresIntegrationTest {

	private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

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
	void theOverviewSplitsWhatIsOwedAndTakesOnlyLiveLinesAndThisMonthsPayments() throws Exception {
		UUID master = seed("MASTER_ADMIN");
		UUID teacher = seed("INSTRUCTOR");
		UUID systemAdmin = seed("SYSTEM_ADMIN");
		String admin = token(master);
		LocalDate today = LocalDate.now(DHAKA);

		JsonNode before = send(HttpMethod.GET, "/api/v1/staff-pay/overview", admin, null, 200);
		BigDecimal owedBefore = money(before.path("owedNow").path("total"));
		BigDecimal overdueBefore = money(before.path("overdue").path("total"));
		BigDecimal paidBefore = money(before.path("paidThisMonth"));
		BigDecimal dueBefore = money(before.path("dueNext30Days").path("amount"));

		// Three sheets published three days ago, and a salary due in ten days.
		marking(teacher, "50", today.minusDays(3));
		marking(teacher, "50", today.minusDays(3));
		marking(teacher, "50", today.minusDays(3));
		salary(systemAdmin, "20000", "2031-05", today.plusDays(10));
		// A voided adjustment is left out; so is a voided payment (below).
		voidedAdjustment(systemAdmin, "7000", today.minusDays(2), master);
		// A payment this month of 60 counts as paid this month; with the one below it covers 100 of the 150 marked.
		payment(teacher, "60", today, "BKASH");
		// A payment dated last month does not count as paid this month.
		payment(teacher, "40", today.minusMonths(1), "BANK");

		JsonNode after = send(HttpMethod.GET, "/api/v1/staff-pay/overview", admin, null, 200);

		assertThat(after.path("asOf").asText()).isEqualTo(today.toString());
		// Marking 150 less payments of 100 (60 this month, 40 last month) leaves 50, all of it overdue.
		assertMoney(money(after.path("owedNow").path("total")).subtract(owedBefore), "20050");
		assertMoney(money(after.path("owedNow").path("salaries")).subtract(money(before.path("owedNow").path("salaries"))),
				"20000");
		assertMoney(money(after.path("owedNow").path("marking")).subtract(money(before.path("owedNow").path("marking"))),
				"50");
		assertMoney(money(after.path("overdue").path("total")).subtract(overdueBefore), "50");
		assertMoney(money(after.path("overdue").path("marking")).subtract(money(before.path("overdue").path("marking"))),
				"50");
		assertMoney(money(after.path("paidThisMonth")).subtract(paidBefore), "60");
		assertMoney(money(after.path("dueNext30Days").path("amount")).subtract(dueBefore), "20000");

		JsonNode owing = after.path("owing");
		JsonNode teacherRow = findRow(owing, teacher);
		assertMoney(teacherRow.path("owedNow").path("total"), "50");
		assertMoney(teacherRow.path("overdue").path("total"), "50");
		assertThat(teacherRow.path("nextDueOn").asText()).isEqualTo(today.minusDays(3).toString());
		JsonNode adminRow = findRow(owing, systemAdmin);
		assertMoney(adminRow.path("owedNow").path("salaries"), "20000");
		assertMoney(adminRow.path("overdue").path("total"), "0");
		assertThat(adminRow.path("nextDueOn").asText()).isEqualTo(today.plusDays(10).toString());
		// Most overdue first: the teacher, who is overdue, comes before the admin, who is not yet.
		assertThat(indexOf(owing, teacher)).isLessThan(indexOf(owing, systemAdmin));

		boolean salaryListed = false;
		for (JsonNode item : after.path("dueNext30Days").path("items")) {
			if (item.path("userId").asText().equals(systemAdmin.toString())) {
				salaryListed = true;
				assertThat(item.path("dueOn").asText()).isEqualTo(today.plusDays(10).toString());
				assertMoney(item.path("amount"), "20000");
			}
		}
		assertThat(salaryListed).isTrue();
	}

	@Test
	void theOverviewLeavesOutAPersonWhoIsNotOwedAndAnAdvanceOwesNothing() throws Exception {
		UUID master = seed("MASTER_ADMIN");
		UUID teacher = seed("INSTRUCTOR");
		LocalDate today = LocalDate.now(DHAKA);
		marking(teacher, "50", today.minusDays(5));
		payment(teacher, "200", today, "NAGAD");

		JsonNode overview = send(HttpMethod.GET, "/api/v1/staff-pay/overview", token(master), null, 200);

		assertThat(findRowOrNull(overview.path("owing"), teacher)).isNull();
	}

	@Test
	void theAnalyticsGiveZeroForEmptyMonthsAndLeaveOutVoidedLines() throws Exception {
		UUID master = seed("MASTER_ADMIN");
		UUID teacher = seed("INSTRUCTOR");
		UUID systemAdmin = seed("SYSTEM_ADMIN");
		marking(teacher, "50", LocalDate.of(2031, 3, 10));
		marking(teacher, "50", LocalDate.of(2031, 3, 11));
		salary(systemAdmin, "20000", "2031-03", LocalDate.of(2031, 4, 5));
		adjustmentOn(systemAdmin, "500", LocalDate.of(2031, 2, 15));
		payment(systemAdmin, "20000", LocalDate.of(2031, 4, 5), "BANK");
		voidedPayment(systemAdmin, "999", LocalDate.of(2031, 4, 6), master);
		payment(teacher, "60", LocalDate.of(2031, 3, 20), "BKASH");

		JsonNode analytics = send(HttpMethod.GET, "/api/v1/staff-pay/analytics?from=2031-02&to=2031-04", token(master), null,
				200);

		assertThat(analytics.path("from").asText()).isEqualTo("2031-02");
		assertThat(analytics.path("to").asText()).isEqualTo("2031-04");
		assertThat(strings(analytics.path("months"))).containsExactly("2031-02", "2031-03", "2031-04");

		JsonNode payroll = analytics.path("payroll");
		assertThat(payroll.size()).isEqualTo(3);
		assertMonth(payroll.get(0), "2031-02", "0", "0", "500", "500", "0");
		assertMonth(payroll.get(1), "2031-03", "20000", "100", "0", "20100", "60");
		assertMonth(payroll.get(2), "2031-04", "0", "0", "0", "0", "20000");

		JsonNode sheets = findByUser(analytics.path("sheets"), teacher);
		assertThat(sheets.path("total").asLong()).isEqualTo(2);
		assertThat(sheets.path("byMonth").size()).isEqualTo(3);
		assertThat(sheets.path("byMonth").get(0).path("sheets").asLong()).isZero();
		assertThat(sheets.path("byMonth").get(1).path("sheets").asLong()).isEqualTo(2);
		assertMoney(sheets.path("byMonth").get(1).path("amount"), "100");
		assertThat(sheets.path("byMonth").get(2).path("sheets").asLong()).isZero();

		JsonNode adminRow = findByUser(analytics.path("people"), systemAdmin);
		assertMoney(adminRow.path("earned"), "20500");
		assertMoney(adminRow.path("paid"), "20000");
		assertMoney(adminRow.path("balance"), "500");
		JsonNode teacherRow = findByUser(analytics.path("people"), teacher);
		assertMoney(teacherRow.path("earned"), "100");
		assertMoney(teacherRow.path("paid"), "60");
		assertMoney(teacherRow.path("balance"), "40");
	}

	@Test
	void theAnalyticsBalanceIsAtTheEndOfTheRangeNotTheStart() throws Exception {
		UUID master = seed("MASTER_ADMIN");
		UUID teacher = seed("INSTRUCTOR");
		marking(teacher, "50", LocalDate.of(2032, 1, 10));
		payment(teacher, "50", LocalDate.of(2032, 2, 1), "ROCKET");
		marking(teacher, "50", LocalDate.of(2032, 3, 1));
		String admin = token(master);

		JsonNode firstTwo = send(HttpMethod.GET, "/api/v1/staff-pay/analytics?from=2032-01&to=2032-02", admin, null, 200);
		JsonNode row = findByUser(firstTwo.path("people"), teacher);
		assertMoney(row.path("earned"), "50");
		assertMoney(row.path("paid"), "50");
		assertMoney(row.path("balance"), "0");

		JsonNode withMarch = send(HttpMethod.GET, "/api/v1/staff-pay/analytics?from=2032-01&to=2032-03", admin, null, 200);
		assertMoney(findByUser(withMarch.path("people"), teacher).path("balance"), "50");
	}

	@Test
	void aRangeDefaultsToTheLastTwelveMonthsAndRefusesBadRanges() throws Exception {
		UUID master = seed("MASTER_ADMIN");
		String admin = token(master);

		JsonNode defaults = send(HttpMethod.GET, "/api/v1/staff-pay/analytics", admin, null, 200);
		assertThat(defaults.path("months").size()).isEqualTo(12);

		JsonNode backwards = send(HttpMethod.GET, "/api/v1/staff-pay/analytics?from=2031-05&to=2031-04", admin, null, 400);
		assertThat(backwards.path("code").asText()).isEqualTo("VALIDATION_FAILED");
		assertThat(backwards.path("fieldErrors").get(0).path("field").asText()).isEqualTo("to");
		send(HttpMethod.GET, "/api/v1/staff-pay/analytics?from=2020-01&to=2031-04", admin, null, 400);
		send(HttpMethod.GET, "/api/v1/staff-pay/analytics?from=2031/04&to=2031-05", admin, null, 400);
		send(HttpMethod.GET, "/api/v1/staff-pay/analytics?from=2031-04&to=not-a-month", admin, null, 400);
	}

	@Test
	void onlyTheMasterAdminSeesTheOverviewAndAnalytics() throws Exception {
		UUID master = seed("MASTER_ADMIN");
		UUID systemAdmin = seed("SYSTEM_ADMIN");
		UUID instructor = seed("INSTRUCTOR");

		send(HttpMethod.GET, "/api/v1/staff-pay/overview", token(systemAdmin), null, 403);
		send(HttpMethod.GET, "/api/v1/staff-pay/overview", token(instructor), null, 403);
		send(HttpMethod.GET, "/api/v1/staff-pay/analytics?from=2031-01&to=2031-02", token(systemAdmin), null, 403);
		send(HttpMethod.GET, "/api/v1/staff-pay/analytics?from=2031-01&to=2031-02", token(instructor), null, 403);
		send(HttpMethod.GET, "/api/v1/staff-pay/overview", token(master), null, 200);
	}

	/* ---------- lines written straight to the ledger ---------- */

	private void marking(UUID user, String amount, LocalDate dueOn) {
		jdbc.update("""
				insert into staff_ledger (id, user_id, kind, amount, due_on, participant_id, exam_id, sheet_rate, created_at)
				values (?, ?, 'MARKING', ?, ?, ?, ?, 50.00, now())
				""", UUID.randomUUID(), user, new BigDecimal(amount), dueOn, UUID.randomUUID(), UUID.randomUUID());
	}

	private void salary(UUID user, String amount, String period, LocalDate dueOn) {
		jdbc.update("""
				insert into staff_ledger (id, user_id, kind, amount, due_on, period, created_at)
				values (?, ?, 'SALARY', ?, ?, ?, now())
				""", UUID.randomUUID(), user, new BigDecimal(amount), dueOn, period);
	}

	private void adjustmentOn(UUID user, String amount, LocalDate dueOn) {
		jdbc.update("""
				insert into staff_ledger (id, user_id, kind, amount, due_on, note, created_at)
				values (?, ?, 'ADJUSTMENT', ?, ?, 'Bonus', now())
				""", UUID.randomUUID(), user, new BigDecimal(amount), dueOn);
	}

	private void voidedAdjustment(UUID user, String amount, LocalDate dueOn, UUID voidedBy) {
		jdbc.update("""
				insert into staff_ledger (id, user_id, kind, amount, due_on, note, created_at, voided_at, voided_by, void_reason)
				values (?, ?, 'ADJUSTMENT', ?, ?, 'Entered twice', now(), now(), ?, 'Entered twice')
				""", UUID.randomUUID(), user, new BigDecimal(amount), dueOn, voidedBy);
	}

	private void payment(UUID user, String amount, LocalDate paidOn, String method) {
		jdbc.update("""
				insert into staff_ledger (id, user_id, kind, amount, due_on, paid_on, method, created_at)
				values (?, ?, 'PAYMENT', ?, ?, ?, ?, now())
				""", UUID.randomUUID(), user, new BigDecimal(amount), paidOn, paidOn, method);
	}

	private void voidedPayment(UUID user, String amount, LocalDate paidOn, UUID voidedBy) {
		jdbc.update("""
				insert into staff_ledger (id, user_id, kind, amount, due_on, paid_on, method, created_at, voided_at, voided_by,
					void_reason)
				values (?, ?, 'PAYMENT', ?, ?, ?, 'BANK', now(), now(), ?, 'Mistake')
				""", UUID.randomUUID(), user, new BigDecimal(amount), paidOn, paidOn, voidedBy);
	}

	/* ---------- reading the answers ---------- */

	private static JsonNode findRow(JsonNode rows, UUID user) {
		JsonNode row = findRowOrNull(rows, user);
		assertThat(row).as("row for " + user).isNotNull();
		return row;
	}

	private static JsonNode findRowOrNull(JsonNode rows, UUID user) {
		for (JsonNode row : rows) {
			if (user.toString().equals(row.path("userId").asText())) {
				return row;
			}
		}
		return null;
	}

	private static JsonNode findByUser(JsonNode rows, UUID user) {
		return findRow(rows, user);
	}

	private static int indexOf(JsonNode rows, UUID user) {
		for (int i = 0; i < rows.size(); i++) {
			if (user.toString().equals(rows.get(i).path("userId").asText())) {
				return i;
			}
		}
		return -1;
	}

	private static java.util.List<String> strings(JsonNode array) {
		java.util.List<String> values = new java.util.ArrayList<>();
		array.forEach(node -> values.add(node.asText()));
		return values;
	}

	private static void assertMonth(JsonNode row, String month, String salary, String marking, String adjustments,
			String earned, String paid) {
		assertThat(row.path("month").asText()).isEqualTo(month);
		assertMoney(row.path("salary"), salary);
		assertMoney(row.path("marking"), marking);
		assertMoney(row.path("adjustments"), adjustments);
		assertMoney(row.path("earned"), earned);
		assertMoney(row.path("paid"), paid);
	}

	private static BigDecimal money(JsonNode node) {
		return new BigDecimal(node.asText());
	}

	private static void assertMoney(JsonNode node, String expected) {
		assertMoney(money(node), expected);
	}

	private static void assertMoney(BigDecimal actual, String expected) {
		assertThat(actual.compareTo(new BigDecimal(expected))).as(actual + " against " + expected).isZero();
	}

	/** Sends one request, expects {@code status}, and returns the body read as JSON (a missing body reads as an empty node). */
	private JsonNode send(HttpMethod method, String path, String token, Object body, int status) throws Exception {
		MockHttpServletRequestBuilder builder = request(method, path).header("Authorization", "Bearer " + token);
		if (body != null) {
			builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
		}
		MvcResult result = mockMvc.perform(builder).andExpect(status().is(status)).andReturn();
		String text = result.getResponse().getContentAsString();
		return text.isBlank() ? json.createObjectNode() : json.readTree(text);
	}

	private UUID seed(String role) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, uniquePhone(), "analytics-" + id + "@example.com", passwords.encode("password1"), role);
		jdbc.update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Analytics Test " + role + "')", id);
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
