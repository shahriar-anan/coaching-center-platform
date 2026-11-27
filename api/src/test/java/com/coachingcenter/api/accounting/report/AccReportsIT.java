package com.coachingcenter.api.accounting.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
 * The reports (contracts.md, section 3.2; plan 4.6 Reports) on rows made for this class, in 2032 (README 5.1). Owed and monthly totals
 * that span every payee are read as differences; the groups the test makes are checked exactly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class AccReportsIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Test
	void theOwedReportListsEveryNonZeroPayeeWithArchivedOnesMarked() throws Exception {
		ReportTestSupport support = new ReportTestSupport(mockMvc, jdbc, passwords, json);
		String master = support.token(support.seedUser("MASTER_ADMIN"));
		JsonNode before = support.ok("/api/v1/accounting/reports/owed", master);

		AccTestData data = new AccTestData(jdbc);
		UUID first = data.group("PAYEES", "EXPENSE");
		UUID second = data.group("PAYEES", "EXPENSE");
		UUID type = data.type(first, "AMOUNT");
		UUID otherType = data.type(second, "AMOUNT");
		UUID cash = data.account("CASH");
		UUID ana = data.payee(first);
		UUID dan = data.payee(first);
		UUID bina = data.payee(first);
		UUID zero = data.payee(first);
		UUID eve = data.payee(second);
		data.charge(ana, type, "500.00", LocalDate.parse("2032-05-01"));
		data.charge(dan, type, "120.00", LocalDate.parse("2032-05-02"));
		data.charge(bina, type, "100.00", LocalDate.parse("2032-05-03"));
		data.payment(bina, cash, "150.00", LocalDate.parse("2032-05-04"));
		data.charge(zero, type, "40.00", LocalDate.parse("2032-05-05"));
		data.payment(zero, cash, "40.00", LocalDate.parse("2032-05-05"));
		data.charge(eve, otherType, "70.00", LocalDate.parse("2032-05-06"));
		jdbc.update("UPDATE acc_payees SET archived_at = now() WHERE id = ?", dan);

		JsonNode after = support.ok("/api/v1/accounting/reports/owed", master);
		assertThat(new BigDecimal(after.get("total").asText()).subtract(new BigDecimal(before.get("total").asText())))
			.isEqualByComparingTo("690");
		assertThat(new BigDecimal(after.get("advance").asText()).subtract(new BigDecimal(before.get("advance").asText())))
			.isEqualByComparingTo("50");

		JsonNode firstGroup = group(after, first);
		assertThat(firstGroup.get("owed").asText()).isEqualTo("620.00");
		assertThat(firstGroup.get("advance").asText()).isEqualTo("50.00");
		// Balance descending: Ana 500, Dan 120 (archived, marked), Bina -50 (an advance). Zero leaves the list.
		JsonNode payees = firstGroup.get("payees");
		assertThat(payees).hasSize(3);
		assertThat(payees.get(0).get("name").asText()).isEqualTo(name(jdbc, ana));
		assertThat(payees.get(0).get("balance").asText()).isEqualTo("500.00");
		assertThat(payees.get(0).get("archived").asBoolean()).isFalse();
		assertThat(payees.get(1).get("balance").asText()).isEqualTo("120.00");
		assertThat(payees.get(1).get("archived").asBoolean()).isTrue();
		assertThat(payees.get(2).get("balance").asText()).isEqualTo("-50.00");
		assertThat(payees.get(2).get("lastPaymentOn").asText()).isEqualTo("2032-05-04");
		assertThat(payees.get(2).get("lastPaymentAmount").asText()).isEqualTo("150.00");

		JsonNode secondGroup = group(after, second);
		assertThat(secondGroup.get("owed").asText()).isEqualTo("70.00");
		assertThat(secondGroup.get("payees")).hasSize(1);
	}

	@Test
	void theMonthlyReportSumsPaymentsAndCountedSpendingPerMonthAndLeavesOutVoids() throws Exception {
		ReportTestSupport support = new ReportTestSupport(mockMvc, jdbc, passwords, json);
		String master = support.token(support.seedUser("MASTER_ADMIN"));
		String url = "/api/v1/accounting/reports/monthly?from=2032-01&to=2032-03";
		JsonNode before = support.ok(url, master);
		assertThat(before.get("months").toString()).isEqualTo("[\"2032-01\",\"2032-02\",\"2032-03\"]");

		AccTestData data = new AccTestData(jdbc);
		UUID payees = data.group("PAYEES", "EXPENSE");
		UUID payType = data.type(payees, "AMOUNT");
		UUID cash = data.account("CASH");
		UUID person = data.payee(payees);
		data.payment(person, cash, "100.00", LocalDate.parse("2032-01-10"));
		data.payment(person, cash, "50.00", LocalDate.parse("2032-02-05"));
		data.charge(person, payType, "999.00", LocalDate.parse("2032-02-06"));

		UUID spending = data.group("LOG", "EXPENSE");
		UUID spendType = data.type(spending, "AMOUNT");
		data.spend(spending, spendType, cash, "7.00", LocalDate.parse("2032-01-01"));
		data.spend(spending, spendType, cash, "30.00", LocalDate.parse("2032-02-01"));
		data.spend(spending, spendType, cash, "20.00", LocalDate.parse("2032-02-28"));
		UUID voided = data.spend(spending, spendType, cash, "888.00", LocalDate.parse("2032-02-15"));
		data.voidEntry(voided, "Entered twice");
		data.spend(spending, spendType, cash, "7.00", LocalDate.parse("2032-03-31"));

		UUID income = data.group("LOG", "INCOME");
		UUID incomeType = data.type(income, "AMOUNT");
		data.income(income, incomeType, cash, "500.00", LocalDate.parse("2032-03-10"));
		// A day before the range and a day after it are left out.
		data.spend(spending, spendType, cash, "1000.00", LocalDate.parse("2031-12-31"));
		data.spend(spending, spendType, cash, "1000.00", LocalDate.parse("2032-04-01"));

		JsonNode after = support.ok(url, master);
		// Expense and income per month are sums over every group, so they are read as differences.
		assertThat(delta(after, before, "expense", 0)).isEqualByComparingTo("107");
		assertThat(delta(after, before, "expense", 1)).isEqualByComparingTo("100");
		assertThat(delta(after, before, "expense", 2)).isEqualByComparingTo("7");
		assertThat(delta(after, before, "income", 0)).isEqualByComparingTo("0");
		assertThat(delta(after, before, "income", 2)).isEqualByComparingTo("500");

		// Each row is one group that has an active entry in the range: the payees, the spending and the income.
		JsonNode payRow = row(after, payees);
		assertThat(payRow.get("amounts").toString()).isEqualTo("[\"100.00\",\"50.00\",\"0.00\"]");
		assertThat(payRow.get("total").asText()).isEqualTo("150.00");
		assertThat(payRow.get("direction").asText()).isEqualTo("EXPENSE");
		JsonNode spendRow = row(after, spending);
		assertThat(spendRow.get("amounts").toString()).isEqualTo("[\"7.00\",\"50.00\",\"7.00\"]");
		assertThat(spendRow.get("total").asText()).isEqualTo("64.00");
		JsonNode incomeRow = row(after, income);
		assertThat(incomeRow.get("amounts").toString()).isEqualTo("[\"0.00\",\"0.00\",\"500.00\"]");
		assertThat(incomeRow.get("direction").asText()).isEqualTo("INCOME");
	}

	@Test
	void theMonthlyRangeIsCheckedAndTheDefaultIsTwelveMonths() throws Exception {
		ReportTestSupport support = new ReportTestSupport(mockMvc, jdbc, passwords, json);
		String master = support.token(support.seedUser("MASTER_ADMIN"));

		assertThat(support.code("/api/v1/accounting/reports/monthly?from=2032-03&to=2032-01", master)).isEqualTo(400);
		assertThat(support.code("/api/v1/accounting/reports/monthly?from=2029-01&to=2032-01", master)).isEqualTo(400);
		assertThat(support.code("/api/v1/accounting/reports/monthly?from=2032-13&to=2032-01", master)).isEqualTo(400);
		assertThat(support.code("/api/v1/accounting/reports/monthly?from=2029-02&to=2032-01", master)).isEqualTo(200);
		assertThat(support.ok("/api/v1/accounting/reports/monthly", master).get("months")).hasSize(12);
	}

	@Test
	void theGroupSummaryTotalsEachTypeAndPayeeInTheRange() throws Exception {
		ReportTestSupport support = new ReportTestSupport(mockMvc, jdbc, passwords, json);
		String master = support.token(support.seedUser("MASTER_ADMIN"));
		AccTestData data = new AccTestData(jdbc);
		UUID group = data.group("PAYEES", "EXPENSE");
		UUID counted = data.type(group, "COUNTED");
		UUID amount = data.type(group, "AMOUNT");
		UUID deduction = data.type(group, "DEDUCTION");
		UUID cash = data.account("CASH");
		UUID ana = data.payee(group);
		UUID bina = data.payee(group);
		data.countedCharge(ana, counted, "40", "50.00", LocalDate.parse("2032-04-05"));
		data.charge(ana, amount, "300.00", LocalDate.parse("2032-04-06"));
		data.charge(ana, deduction, "-100.00", LocalDate.parse("2032-04-07"));
		data.payment(ana, cash, "1000.00", LocalDate.parse("2032-04-08"));
		data.countedCharge(bina, counted, "1.5", "10.00", LocalDate.parse("2032-04-06"));
		data.charge(bina, amount, "1.00", LocalDate.parse("2032-04-01"));
		data.charge(bina, amount, "1.00", LocalDate.parse("2032-04-30"));
		UUID voided = data.charge(ana, amount, "999.00", LocalDate.parse("2032-04-10"));
		data.voidEntry(voided, "Entered twice");
		data.charge(ana, amount, "77.00", LocalDate.parse("2032-05-01"));

		JsonNode summary = support.ok("/api/v1/accounting/groups/" + group + "/summary?from=2032-04-01&to=2032-04-30", master);
		assertThat(summary.get("from").asText()).isEqualTo("2032-04-01");
		assertThat(summary.get("to").asText()).isEqualTo("2032-04-30");
		assertThat(summary.get("charged").asText()).isEqualTo("2217.00");
		assertThat(summary.get("paid").asText()).isEqualTo("1000.00");
		assertThat(summary.get("spent").asText()).isEqualTo("1000.00");
		assertThat(summary.get("income").asText()).isEqualTo("0.00");

		JsonNode countedRow = typeRow(summary, counted);
		assertThat(countedRow.get("quantity").asText()).isEqualTo("41.5");
		assertThat(countedRow.get("amount").asText()).isEqualTo("2015.00");
		assertThat(countedRow.get("count").asLong()).isEqualTo(2L);
		assertThat(countedRow.get("kind").asText()).isEqualTo("COUNTED");
		JsonNode amountRow = typeRow(summary, amount);
		// The API leaves null fields out of its answers (spring.jackson.default-property-inclusion=non_null).
		assertThat(amountRow.has("quantity")).isFalse();
		assertThat(amountRow.get("amount").asText()).isEqualTo("302.00");
		assertThat(amountRow.get("count").asLong()).isEqualTo(3L);
		JsonNode deductionRow = typeRow(summary, deduction);
		assertThat(deductionRow.has("quantity")).isFalse();
		assertThat(deductionRow.get("amount").asText()).isEqualTo("-100.00");

		JsonNode anaRow = payeeRow(summary, ana);
		assertThat(anaRow.get("charged").asText()).isEqualTo("2200.00");
		assertThat(anaRow.get("paid").asText()).isEqualTo("1000.00");
		JsonNode binaRow = payeeRow(summary, bina);
		assertThat(binaRow.get("charged").asText()).isEqualTo("17.00");
		assertThat(binaRow.get("paid").asText()).isEqualTo("0.00");

		assertThat(support.code("/api/v1/accounting/groups/" + UUID.randomUUID() + "/summary?from=2032-04-01&to=2032-04-30", master))
			.isEqualTo(404);
		assertThat(support.code("/api/v1/accounting/groups/" + group + "/summary?from=2032-04-01", master)).isEqualTo(400);
		assertThat(support.code("/api/v1/accounting/groups/" + group + "/summary?from=2032-04-30&to=2032-04-01", master)).isEqualTo(400);
	}

	/** The difference of one figure between two readings: a month's expense or income, or the total. */
	private static BigDecimal delta(JsonNode after, JsonNode before, String field, int index) {
		return new BigDecimal(after.get(field).get(index).asText()).subtract(new BigDecimal(before.get(field).get(index).asText()));
	}

	private static JsonNode group(JsonNode owed, UUID id) {
		for (JsonNode group : owed.get("groups")) {
			if (group.get("groupId").asText().equals(id.toString())) {
				return group;
			}
		}
		throw new AssertionError("No group " + id);
	}

	private static JsonNode row(JsonNode monthly, UUID group) {
		for (JsonNode row : monthly.get("rows")) {
			if (row.get("groupId").asText().equals(group.toString())) {
				return row;
			}
		}
		throw new AssertionError("No row for group " + group);
	}

	private static JsonNode typeRow(JsonNode summary, UUID type) {
		for (JsonNode row : summary.get("byType")) {
			if (row.get("typeId").asText().equals(type.toString())) {
				return row;
			}
		}
		throw new AssertionError("No type " + type);
	}

	private static JsonNode payeeRow(JsonNode summary, UUID payee) {
		for (JsonNode row : summary.get("byPayee")) {
			if (row.get("payeeId").asText().equals(payee.toString())) {
				return row;
			}
		}
		throw new AssertionError("No payee " + payee);
	}

	private static String name(JdbcTemplate jdbc, UUID payee) {
		return jdbc.queryForObject("select name from acc_payees where id = ?", String.class, payee);
	}

}
