package com.coachingcenter.api.accounting.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.accounting.AccTestData;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The home page (contracts.md, section 3.2 and 4; plan 4.6 Home). The clock is fixed on 15 March 2031 in Dhaka, the year only this class
 * uses (README 5.1). Every figure is read before and after the rows are added and compared as a difference, so the rows of other runs
 * do not matter.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, AccDashboardIT.ClockConfig.class })
class AccDashboardIT extends PostgresIntegrationTest {

	private static final Instant NOW = Instant.parse("2031-03-15T04:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private MutableClock clock;

	@BeforeEach
	void fixTheClock() {
		clock.set(NOW);
	}

	@Test
	void theFiguresAreTheSumsOfTheLedgerAndEveryLiveGroupIsListed() throws Exception {
		ReportTestSupport support = new ReportTestSupport(mockMvc, jdbc, passwords, json);
		String master = support.token(support.seedUser("MASTER_ADMIN"));
		JsonNode before = support.ok("/api/v1/accounting/dashboard", master);
		assertThat(before.get("today").asText()).isEqualTo("2031-03-15");

		AccTestData data = new AccTestData(jdbc);
		UUID owedGroup = data.group("PAYEES", "EXPENSE");
		UUID counted = data.type(owedGroup, "COUNTED");
		UUID deduction = data.type(owedGroup, "DEDUCTION");
		UUID amount = data.type(owedGroup, "AMOUNT");
		UUID cash = data.account("CASH");
		UUID ana = data.payee(owedGroup);
		UUID bina = data.payee(owedGroup);
		data.countedCharge(ana, counted, "10", "100.00", LocalDate.parse("2031-03-02"));
		data.charge(ana, deduction, "-200.00", LocalDate.parse("2031-03-03"));
		data.payment(ana, cash, "300.00", LocalDate.parse("2031-03-06"));
		data.charge(bina, amount, "100.00", LocalDate.parse("2031-03-04"));
		data.payment(bina, cash, "150.00", LocalDate.parse("2031-03-05"));

		// A Log expense group that does not count in Finance: its spending still counts on the home page.
		UUID spending = data.group("LOG", "EXPENSE");
		UUID spendType = data.type(spending, "AMOUNT");
		jdbc.update("UPDATE acc_groups SET in_finance = FALSE WHERE id = ?", spending);
		data.spend(spending, spendType, cash, "75.00", LocalDate.parse("2031-03-07"));
		UUID voided = data.spend(spending, spendType, cash, "999.00", LocalDate.parse("2031-03-09"));
		data.voidEntry(voided, "Entered twice");
		data.spend(spending, spendType, cash, "60.00", LocalDate.parse("2031-02-10"));
		data.spend(spending, spendType, cash, "33.00", LocalDate.parse("2030-04-15"));
		data.spend(spending, spendType, cash, "44.00", LocalDate.parse("2030-03-15"));
		data.spend(spending, spendType, cash, "5.00", LocalDate.parse("2030-03-16"));

		UUID income = data.group("LOG", "INCOME");
		UUID incomeType = data.type(income, "AMOUNT");
		UUID received = data.income(income, incomeType, cash, "400.00", LocalDate.parse("2031-03-08"));

		JsonNode after = support.ok("/api/v1/accounting/dashboard", master);

		// Owed: Ana 1000 - 200 - 300 = 500; Bina 100 - 150 = an advance of 50.
		assertThat(delta(after, before, "owed")).isEqualByComparingTo("500");
		assertThat(delta(after, before, "advance")).isEqualByComparingTo("50");
		// Spent this month: the two payments and the spending (the voided 999 is left out, the group off in Finance counts).
		assertThat(delta(after, before, "spentThisMonth")).isEqualByComparingTo("525");
		assertThat(delta(after, before, "spentLastMonth")).isEqualByComparingTo("60");
		assertThat(delta(after, before, "incomeThisMonth")).isEqualByComparingTo("400");

		// The twelve months end this month, oldest first; a month outside them is not listed.
		JsonNode months = after.get("months");
		assertThat(months).hasSize(12);
		assertThat(months.get(0).get("month").asText()).isEqualTo("2030-04");
		assertThat(months.get(11).get("month").asText()).isEqualTo("2031-03");
		assertThat(monthDelta(after, before, "2030-04", "expense")).isEqualByComparingTo("33");
		assertThat(monthDelta(after, before, "2031-02", "expense")).isEqualByComparingTo("60");
		assertThat(monthDelta(after, before, "2031-03", "expense")).isEqualByComparingTo("525");
		assertThat(monthDelta(after, before, "2031-03", "income")).isEqualByComparingTo("400");

		// Each live group: owed for the payees, this month for the log.
		JsonNode owed = group(after, owedGroup);
		assertThat(owed.get("owed").asText()).isEqualTo("500.00");
		assertThat(owed.get("thisMonth").asText()).isEqualTo("450.00");
		assertThat(owed.get("mode").asText()).isEqualTo("PAYEES");
		JsonNode log = group(after, spending);
		assertThat(log.get("owed").asText()).isEqualTo("0.00");
		assertThat(log.get("thisMonth").asText()).isEqualTo("75.00");
		assertThat(group(after, income).get("thisMonth").asText()).isEqualTo("400.00");

		// The newest entries come first by the time they were recorded: the income was the last row made.
		assertThat(after.get("latest").get(0).get("id").asText()).isEqualTo(received.toString());
		assertThat(after.get("latest")).hasSizeLessThanOrEqualTo(10);

		// The most owed: sorted, every row owed, at most ten.
		JsonNode top = after.get("topOwed");
		assertThat(top).hasSizeLessThanOrEqualTo(10);
		BigDecimal previous = null;
		for (JsonNode row : top) {
			BigDecimal balance = new BigDecimal(row.get("balance").asText());
			assertThat(balance).isPositive();
			if (previous != null) {
				assertThat(balance).isLessThanOrEqualTo(previous);
			}
			previous = balance;
		}
	}

	@Test
	void aSystemAdminGetsNothingOnTheHomePage() throws Exception {
		ReportTestSupport support = new ReportTestSupport(mockMvc, jdbc, passwords, json);
		String staff = support.token(support.seedUser("SYSTEM_ADMIN"));
		assertThat(support.code("/api/v1/accounting/dashboard", staff)).isEqualTo(403);
	}

	private static BigDecimal delta(JsonNode after, JsonNode before, String field) {
		return new BigDecimal(after.get(field).asText()).subtract(new BigDecimal(before.get(field).asText()));
	}

	private static BigDecimal monthDelta(JsonNode after, JsonNode before, String month, String field) {
		return new BigDecimal(monthOf(after, month).get(field).asText()).subtract(new BigDecimal(monthOf(before, month).get(field).asText()));
	}

	private static JsonNode monthOf(JsonNode dashboard, String month) {
		for (JsonNode row : dashboard.get("months")) {
			if (row.get("month").asText().equals(month)) {
				return row;
			}
		}
		throw new AssertionError("No month " + month);
	}

	private static JsonNode group(JsonNode dashboard, UUID id) {
		for (JsonNode row : dashboard.get("groups")) {
			if (row.get("groupId").asText().equals(id.toString())) {
				return row;
			}
		}
		throw new AssertionError("No group " + id);
	}

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(NOW);
		}

	}

}
