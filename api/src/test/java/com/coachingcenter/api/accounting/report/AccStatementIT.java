package com.coachingcenter.api.accounting.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
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
import com.coachingcenter.api.accounting.AccBalances;
import com.coachingcenter.api.accounting.AccTestData;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * A payee's statement (plan 4.6 Statement) on rows made for this class, in 2035 (README 5.1): the opening balance from the entries before
 * the range, the entries in it with the running balance, and the closing balance.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class AccStatementIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private AccBalances balances;

	@Test
	void theStatementRunsFromTheOpeningBalanceThroughTheRangeToTheClosingBalance() throws Exception {
		ReportTestSupport support = new ReportTestSupport(mockMvc, jdbc, passwords, json);
		String master = support.token(support.seedUser("MASTER_ADMIN"));
		AccTestData data = new AccTestData(jdbc);
		UUID group = data.group("PAYEES", "EXPENSE");
		UUID type = data.type(group, "AMOUNT");
		UUID deduction = data.type(group, "DEDUCTION");
		UUID cash = data.account("CASH");
		UUID payee = data.payee(group);

		// Before the range: opening = 500 - 100 = 400.
		data.charge(payee, type, "500.00", LocalDate.parse("2035-02-20"));
		data.payment(payee, cash, "100.00", LocalDate.parse("2035-02-25"));
		// In the range (1 to 31 March, both days counted). The voided one is left out; the charge of 1 April is after it.
		data.charge(payee, type, "200.00", LocalDate.parse("2035-03-01"));
		data.charge(payee, deduction, "-50.00", LocalDate.parse("2035-03-15"));
		UUID voided = data.charge(payee, type, "999.00", LocalDate.parse("2035-03-10"));
		data.voidEntry(voided, "Entered twice");
		UUID same = data.charge(payee, type, "10.00", LocalDate.parse("2035-03-20"));
		data.payment(payee, cash, "5.00", LocalDate.parse("2035-03-20"));
		data.payment(payee, cash, "150.00", LocalDate.parse("2035-03-31"));
		data.charge(payee, type, "77.00", LocalDate.parse("2035-04-01"));

		JsonNode statement = support.ok("/api/v1/accounting/payees/" + payee + "/statement?from=2035-03-01&to=2035-03-31", master);
		assertThat(statement.get("payee").get("id").asText()).isEqualTo(payee.toString());
		assertThat(statement.get("payee").get("balance").asText()).isEqualTo("482.00");
		assertThat(statement.get("from").asText()).isEqualTo("2035-03-01");
		assertThat(statement.get("to").asText()).isEqualTo("2035-03-31");
		assertThat(statement.get("opening").asText()).isEqualTo("400.00");
		assertThat(statement.get("charged").asText()).isEqualTo("160.00");
		assertThat(statement.get("paid").asText()).isEqualTo("155.00");
		assertThat(statement.get("closing").asText()).isEqualTo("405.00");

		// Oldest first by date, then number: the same day's charge comes before its payment, as it was entered first.
		JsonNode lines = statement.get("lines");
		assertThat(lines).hasSize(5);
		assertThat(lines.get(0).get("date").asText()).isEqualTo("2035-03-01");
		assertThat(lines.get(0).get("balanceAfter").asText()).isEqualTo("600.00");
		assertThat(lines.get(1).get("amount").asText()).isEqualTo("-50.00");
		assertThat(lines.get(1).get("balanceAfter").asText()).isEqualTo("550.00");
		assertThat(lines.get(2).get("id").asText()).isEqualTo(same.toString());
		assertThat(lines.get(2).get("balanceAfter").asText()).isEqualTo("560.00");
		assertThat(lines.get(3).get("kind").asText()).isEqualTo("PAYMENT");
		assertThat(lines.get(3).get("balanceAfter").asText()).isEqualTo("555.00");
		assertThat(lines.get(4).get("amount").asText()).isEqualTo("150.00");
		assertThat(lines.get(4).get("balanceAfter").asText()).isEqualTo("405.00");

		// The closing balance is the balance of the payee with the one entry after the range (77) left out.
		assertThat(balances.balance(payee)).isEqualByComparingTo("482");
	}

	@Test
	void theStatementDefaultsToThisMonthAndAnUnknownPayeeIsNotFound() throws Exception {
		ReportTestSupport support = new ReportTestSupport(mockMvc, jdbc, passwords, json);
		String master = support.token(support.seedUser("MASTER_ADMIN"));
		AccTestData data = new AccTestData(jdbc);
		UUID group = data.group("PAYEES", "EXPENSE");
		UUID type = data.type(group, "AMOUNT");
		UUID payee = data.payee(group);
		data.charge(payee, type, "25.00", LocalDate.now(ZoneId.of("Asia/Dhaka")));

		JsonNode statement = support.ok("/api/v1/accounting/payees/" + payee + "/statement", master);
		assertThat(statement.get("from").asText()).isEqualTo(LocalDate.now(ZoneId.of("Asia/Dhaka")).withDayOfMonth(1).toString());
		assertThat(statement.get("lines")).hasSize(1);
		assertThat(statement.get("charged").asText()).isEqualTo("25.00");

		assertThat(support.code("/api/v1/accounting/payees/" + UUID.randomUUID() + "/statement", master)).isEqualTo(404);
		assertThat(support.code("/api/v1/accounting/payees/" + payee + "/statement?from=2035-03-31&to=2035-03-01", master))
			.isEqualTo(400);
	}

	@Test
	void aSystemAdminGetsNoStatement() throws Exception {
		ReportTestSupport support = new ReportTestSupport(mockMvc, jdbc, passwords, json);
		String staff = support.token(support.seedUser("SYSTEM_ADMIN"));
		AccTestData data = new AccTestData(jdbc);
		UUID payee = data.payee(data.group("PAYEES", "EXPENSE"));
		assertThat(support.code("/api/v1/accounting/payees/" + payee + "/statement", staff)).isEqualTo(403);
	}

}
