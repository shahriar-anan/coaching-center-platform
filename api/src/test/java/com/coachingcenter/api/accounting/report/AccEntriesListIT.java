package com.coachingcenter.api.accounting.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.accounting.AccTestData;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The entries list and its CSV (contracts.md, section 3.2) on rows made for this class, in March 2034 (the year only this class uses,
 * README 5.1). Every check narrows to one group the test made, so the figures are exact.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class AccEntriesListIT extends PostgresIntegrationTest {

	private static final LocalDate MARCH_1 = LocalDate.parse("2034-03-01");

	private static final LocalDate MARCH_2 = LocalDate.parse("2034-03-02");

	private static final LocalDate MARCH_3 = LocalDate.parse("2034-03-03");

	private static final LocalDate MARCH_4 = LocalDate.parse("2034-03-04");

	private static final LocalDate MARCH_5 = LocalDate.parse("2034-03-05");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	/** The rows of one test: a group with two payees, and five entries (the fifth is voided). */
	private record Rows(UUID group, UUID ana, UUID bina, UUID lecture, UUID fine, UUID bonus, UUID payment, UUID voided, UUID cash,
			UUID amountType, UUID deductionType) {
	}

	@Test
	void theListPagesAndTheTotalsLeaveVoidedEntriesOut() throws Exception {
		ReportTestSupport support = new ReportTestSupport(mockMvc, jdbc, passwords, json);
		String master = support.token(support.seedUser("MASTER_ADMIN"));
		Rows rows = rows();

		// Newest first by default; five rows in all, the voided one included; the totals leave it out.
		JsonNode first = support.ok("/api/v1/accounting/entries?groupId=" + rows.group() + "&size=2&page=0", master);
		assertThat(first.get("total").asLong()).isEqualTo(5L);
		assertThat(first.get("items")).hasSize(2);
		assertThat(id(first, 0)).isEqualTo(rows.voided().toString());
		assertThat(id(first, 1)).isEqualTo(rows.payment().toString());
		assertThat(first.get("items").get(0).get("status").asText()).isEqualTo("VOIDED");
		assertThat(first.get("items").get(0).get("voidReason").asText()).isEqualTo("Entered twice");
		assertThat(totals(first)).containsExactly("1800.00", "1000.00", "1000.00", "0.00");

		JsonNode second = support.ok("/api/v1/accounting/entries?groupId=" + rows.group() + "&size=2&page=1", master);
		assertThat(id(second, 0)).isEqualTo(rows.bonus().toString());
		assertThat(id(second, 1)).isEqualTo(rows.fine().toString());
		JsonNode third = support.ok("/api/v1/accounting/entries?groupId=" + rows.group() + "&size=2&page=2", master);
		assertThat(third.get("items")).hasSize(1);
		assertThat(id(third, 0)).isEqualTo(rows.lecture().toString());
		assertThat(totals(third)).containsExactly("1800.00", "1000.00", "1000.00", "0.00");

		// Every money figure is a string with two decimals, and the quantity has no trailing zeros.
		JsonNode lecture = third.get("items").get(0);
		assertThat(lecture.get("amount").asText()).isEqualTo("2000.00");
		assertThat(lecture.get("quantity").asText()).isEqualTo("40");
		assertThat(lecture.get("unitPrice").asText()).isEqualTo("50.00");
		assertThat(lecture.get("date").asText()).isEqualTo("2034-03-01");
		assertThat(lecture.get("unitEn").asText()).isEqualTo("unit");
	}

	@Test
	void eachFilterNarrowsTheRowsAndTheTotals() throws Exception {
		ReportTestSupport support = new ReportTestSupport(mockMvc, jdbc, passwords, json);
		String master = support.token(support.seedUser("MASTER_ADMIN"));
		Rows rows = rows();
		String base = "/api/v1/accounting/entries?groupId=" + rows.group();

		JsonNode payments = support.ok(base + "&kind=PAYMENT", master);
		assertThat(payments.get("total").asLong()).isEqualTo(1L);
		assertThat(id(payments, 0)).isEqualTo(rows.payment().toString());

		JsonNode charges = support.ok(base + "&kind=CHARGE", master);
		assertThat(charges.get("total").asLong()).isEqualTo(4L);
		assertThat(totals(charges)).containsExactly("1800.00", "0.00", "0.00", "0.00");

		JsonNode voided = support.ok(base + "&status=VOIDED", master);
		assertThat(voided.get("total").asLong()).isEqualTo(1L);
		assertThat(totals(voided)).containsExactly("0.00", "0.00", "0.00", "0.00");

		JsonNode anas = support.ok(base + "&payeeId=" + rows.ana(), master);
		assertThat(anas.get("total").asLong()).isEqualTo(3L);
		assertThat(support.ok(base + "&typeId=" + rows.deductionType(), master).get("total").asLong()).isEqualTo(1L);
		assertThat(support.ok(base + "&accountId=" + rows.cash(), master).get("total").asLong()).isEqualTo(1L);
		assertThat(support.ok(base + "&from=2034-03-03&to=2034-03-04", master).get("total").asLong()).isEqualTo(2L);
		assertThat(support.ok(base + "&from=2034-03-05&to=2034-03-05", master).get("total").asLong()).isEqualTo(1L);
		assertThat(support.ok(base + "&batchId=" + UUID.randomUUID(), master).get("total").asLong()).isZero();
	}

	@Test
	void theSearchFindsTextAndTheNumberOfAnEntry() throws Exception {
		ReportTestSupport support = new ReportTestSupport(mockMvc, jdbc, passwords, json);
		String master = support.token(support.seedUser("MASTER_ADMIN"));
		Rows rows = rows();
		String base = "/api/v1/accounting/entries?groupId=" + rows.group();
		String tag = "Tag" + UUID.randomUUID().toString().substring(0, 8);
		new AccTestData(jdbc).annotate(rows.lecture(), "Question paper " + tag, null, null);
		new AccTestData(jdbc).annotate(rows.payment(), null, "bKash " + tag, "Paid at the desk");

		JsonNode byText = support.ok(base + "&q=" + encode(tag.toUpperCase()), master);
		assertThat(byText.get("total").asLong()).isEqualTo(2L);

		Long number = jdbc.queryForObject("select no from acc_entries where id = ?", Long.class, rows.payment());
		JsonNode byNumber = support.okParam(base, "q", "#" + number, master);
		assertThat(byNumber.get("total").asLong()).isEqualTo(1L);
		assertThat(id(byNumber, 0)).isEqualTo(rows.payment().toString());
		JsonNode byPlainNumber = support.okParam(base, "q", String.valueOf(number), master);
		assertThat(id(byPlainNumber, 0)).isEqualTo(rows.payment().toString());

		JsonNode byPayee = support.ok(base + "&q=" + encode("Payee"), master);
		assertThat(byPayee.get("total").asLong()).isEqualTo(5L);
	}

	@Test
	void theSortsOrderTheRowsAndTiesFallBackToTheNewestNumber() throws Exception {
		ReportTestSupport support = new ReportTestSupport(mockMvc, jdbc, passwords, json);
		String master = support.token(support.seedUser("MASTER_ADMIN"));
		Rows rows = rows();
		String base = "/api/v1/accounting/entries?groupId=" + rows.group();

		// Smallest first, then largest first: -500, 300, 999, 1000, 2000.
		JsonNode smallest = support.ok(base + "&sort=amount&size=200", master);
		assertThat(id(smallest, 0)).isEqualTo(rows.fine().toString());
		assertThat(id(smallest, 1)).isEqualTo(rows.bonus().toString());
		assertThat(id(smallest, 4)).isEqualTo(rows.lecture().toString());
		JsonNode largest = support.ok(base + "&sort=-amount&size=200", master);
		assertThat(id(largest, 0)).isEqualTo(rows.lecture().toString());
		assertThat(largest.get("items").get(0).get("amount").asText()).isEqualTo("2000.00");

		// By number: the oldest number first, or the newest first.
		JsonNode oldest = support.ok(base + "&sort=no&size=200", master);
		assertThat(id(oldest, 0)).isEqualTo(rows.lecture().toString());
		JsonNode newest = support.ok(base + "&sort=-no&size=200", master);
		assertThat(id(newest, 0)).isEqualTo(rows.voided().toString());

		// By date, ascending: the lecture (1 March) first, the voided charge (5 March) last.
		JsonNode dates = support.ok(base + "&sort=date&size=200", master);
		assertThat(id(dates, 0)).isEqualTo(rows.lecture().toString());
		assertThat(id(dates, 4)).isEqualTo(rows.voided().toString());
	}

	@Test
	void theCsvHasABomAHeaderAndEveryRowWithBanglaNames() throws Exception {
		ReportTestSupport support = new ReportTestSupport(mockMvc, jdbc, passwords, json);
		String master = support.token(support.seedUser("MASTER_ADMIN"));
		Rows rows = rows();
		jdbc.update("UPDATE acc_payees SET name = ? WHERE id = ?", "রহিম মিয়া", rows.ana());

		var result = support.raw("/api/v1/accounting/entries.csv?groupId=" + rows.group() + "&from=2034-03-01&to=2034-03-31", master);
		assertThat(result.getResponse().getContentType()).startsWith("text/csv");
		assertThat(result.getResponse().getHeader("Content-Disposition")).contains("accounting-2034-03-01-2034-03-31.csv");
		byte[] bytes = result.getResponse().getContentAsByteArray();
		assertThat(bytes[0] & 0xFF).isEqualTo(0xEF);
		assertThat(bytes[1] & 0xFF).isEqualTo(0xBB);
		assertThat(bytes[2] & 0xFF).isEqualTo(0xBF);
		String text = new String(bytes, StandardCharsets.UTF_8).substring(1);
		assertThat(text).startsWith("No,Date,Kind,Status,Group,Payee,Type,Month,Quantity,Unit,Unit price,Amount,Account,Reference,"
				+ "Paid to,Batch,Exam,Description,Note,Recorded by,Recorded at,Voided reason\r\n");
		assertThat(text.lines().count()).isEqualTo(6L);
		assertThat(text).contains("2034-03,40,unit,50.00,2000.00");
		assertThat(text).contains("রহিম মিয়া");
		assertThat(text).contains("VOIDED");
	}

	@Test
	void aBadValueIsAFieldErrorAndADateRangeTheWrongWayRoundIsRefused() throws Exception {
		ReportTestSupport support = new ReportTestSupport(mockMvc, jdbc, passwords, json);
		String master = support.token(support.seedUser("MASTER_ADMIN"));
		Rows rows = rows();
		String base = "/api/v1/accounting/entries?groupId=" + rows.group();

		assertThat(support.code(base + "&kind=NOPE", master)).isEqualTo(400);
		assertThat(support.code(base + "&sort=bogus", master)).isEqualTo(400);
		assertThat(support.code(base + "&payeeId=not-an-id", master)).isEqualTo(400);
		assertThat(support.code(base + "&from=2034-03-05&to=2034-03-01", master)).isEqualTo(400);
		assertThat(support.code(base + "&from=31-03-2034", master)).isEqualTo(400);
	}

	@Test
	void aSystemAdminGetsNothingOnTheListOrTheCsv() throws Exception {
		ReportTestSupport support = new ReportTestSupport(mockMvc, jdbc, passwords, json);
		String staff = support.token(support.seedUser("SYSTEM_ADMIN"));
		mockMvc.perform(get("/api/v1/accounting/entries").header("Authorization", "Bearer " + staff)).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/accounting/entries.csv").header("Authorization", "Bearer " + staff))
			.andExpect(status().isForbidden());
	}

	/** A group with two payees and five entries in March 2034: a counted lecture (2000), a deduction (-500), a bonus (300), a payment
	 * (1000) and a voided charge (999). */
	private Rows rows() {
		AccTestData data = new AccTestData(jdbc);
		UUID group = data.group("PAYEES", "EXPENSE");
		UUID counted = data.type(group, "COUNTED");
		UUID deduction = data.type(group, "DEDUCTION");
		UUID amount = data.type(group, "AMOUNT");
		UUID cash = data.account("CASH");
		UUID ana = data.payee(group);
		UUID bina = data.payee(group);
		UUID lecture = data.countedCharge(ana, counted, "40", "50.00", MARCH_1);
		UUID fine = data.charge(ana, deduction, "-500.00", MARCH_2);
		UUID bonus = data.charge(bina, amount, "300.00", MARCH_3);
		UUID payment = data.payment(ana, cash, "1000.00", MARCH_4);
		UUID voided = data.charge(bina, amount, "999.00", MARCH_5);
		data.voidEntry(voided, "Entered twice");
		data.annotate(lecture, "Lecture for March", null, null);
		return new Rows(group, ana, bina, lecture, fine, bonus, payment, voided, cash, amount, deduction);
	}

	private static String id(JsonNode page, int index) {
		return page.get("items").get(index).get("id").asText();
	}

	private static java.util.List<String> totals(JsonNode page) {
		JsonNode totals = page.get("totals");
		return java.util.List.of(totals.get("charged").asText(), totals.get("paid").asText(), totals.get("spent").asText(),
				totals.get("income").asText());
	}

	private static String encode(String text) {
		return URLEncoder.encode(text, StandardCharsets.UTF_8);
	}

}
