package com.coachingcenter.api.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.coachingcenter.api.PostgresIntegrationTest;

/**
 * The balance helpers (contracts.md, section 4) on rows made for this class. Each test makes its own groups and payees, so only its own
 * figures are read. Dates are in 2033, the year this class uses (README 5.1).
 */
@SpringBootTest
class AccBalancesIT extends PostgresIntegrationTest {

	private static final LocalDate MARCH_10 = LocalDate.parse("2033-03-10");

	private static final LocalDate MARCH_11 = LocalDate.parse("2033-03-11");

	private static final LocalDate MARCH_12 = LocalDate.parse("2033-03-12");

	private static final LocalDate MARCH_13 = LocalDate.parse("2033-03-13");

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private AccBalances balances;

	@Test
	void aPayeesBalanceIsChargesLessPaymentsAndVoidedEntriesLeaveIt() {
		AccTestData data = new AccTestData(jdbc);
		UUID group = data.group("PAYEES", "EXPENSE");
		UUID type = data.type(group, "AMOUNT");
		UUID cash = data.account("CASH");
		UUID owed = data.payee(group);
		UUID advance = data.payee(group);

		data.charge(owed, type, "100.00", MARCH_10);
		data.charge(owed, type, "-20.00", MARCH_10);
		data.payment(owed, cash, "30.00", MARCH_11);
		data.payment(advance, cash, "10.00", MARCH_11);
		UUID voided = data.charge(advance, type, "999.00", MARCH_10);
		data.voidEntry(voided, "Entered by mistake");

		assertThat(balances.balance(owed)).isEqualByComparingTo("50");
		assertThat(balances.balance(advance)).isEqualByComparingTo("-10");
		assertThat(balances.balances(group)).containsEntry(owed, new java.math.BigDecimal("50.00"))
				.containsEntry(advance, new java.math.BigDecimal("-10.00"));
		AccBalances.Owed totals = balances.owedAndAdvance(group);
		assertThat(totals.owed()).isEqualByComparingTo("50");
		assertThat(totals.advance()).isEqualByComparingTo("10");
		assertThat(balances.paidInRange(owed, MARCH_10, MARCH_11)).isEqualByComparingTo("30");
		assertThat(balances.paidInRange(owed, LocalDate.parse("2033-04-01"), LocalDate.parse("2033-04-30")))
				.isEqualByComparingTo("0");
	}

	@Test
	void theRangeTotalsCountChargesPaymentsSpendingAndIncome() {
		AccTestData data = new AccTestData(jdbc);
		UUID payees = data.group("PAYEES", "EXPENSE");
		UUID payeeType = data.type(payees, "AMOUNT");
		UUID cash = data.account("CASH");
		UUID person = data.payee(payees);
		data.charge(person, payeeType, "100.00", MARCH_10);
		data.charge(person, payeeType, "-20.00", MARCH_10);
		data.payment(person, cash, "40.00", MARCH_11);

		UUID log = data.group("LOG", "EXPENSE");
		UUID logType = data.type(log, "AMOUNT");
		data.spend(log, logType, cash, "25.00", MARCH_12);

		UUID income = data.group("LOG", "INCOME");
		UUID incomeType = data.type(income, "AMOUNT");
		data.income(income, incomeType, cash, "60.00", MARCH_13);

		AccBalances.Totals payeeTotals = balances.totals(MARCH_10, MARCH_13, payees);
		assertThat(payeeTotals.charged()).isEqualByComparingTo("80");
		assertThat(payeeTotals.paid()).isEqualByComparingTo("40");
		assertThat(payeeTotals.spent()).isEqualByComparingTo("40");
		assertThat(payeeTotals.income()).isEqualByComparingTo("0");

		AccBalances.Totals spendTotals = balances.totals(MARCH_10, MARCH_13, log);
		assertThat(spendTotals.spent()).isEqualByComparingTo("25");
		assertThat(spendTotals.income()).isEqualByComparingTo("0");

		AccBalances.Totals incomeTotals = balances.totals(MARCH_10, MARCH_13, income);
		assertThat(incomeTotals.income()).isEqualByComparingTo("60");
		assertThat(incomeTotals.spent()).isEqualByComparingTo("0");
	}

}
