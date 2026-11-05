package com.coachingcenter.api.staffledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.coachingcenter.api.staffledger.StaffPayCalculator.Line;
import com.coachingcenter.api.staffledger.StaffPayCalculator.Totals;

/** The balance and the oldest-first overdue split, with no database. */
class StaffPayCalculatorTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

	private static final Instant EARLY = Instant.parse("2026-10-01T00:00:00Z");

	private static final Instant LATE = Instant.parse("2026-10-02T00:00:00Z");

	@Test
	void theBalanceIsDuesAndAdjustmentsMinusPaymentsAndVoidedLinesAreLeftOut() {
		List<Line> lines = List.of(
				line(LedgerKind.SALARY, "20000", "2026-11-05", false),
				line(LedgerKind.MARKING, "150", "2026-11-03", false),
				line(LedgerKind.ADJUSTMENT, "500", "2026-11-04", false),
				line(LedgerKind.PAYMENT, "10000", "2026-11-05", false),
				line(LedgerKind.PAYMENT, "999", "2026-11-05", true),
				line(LedgerKind.ADJUSTMENT, "700", "2026-11-04", true));

		Totals totals = StaffPayCalculator.totals(lines, TODAY);

		assertThat(totals.charged()).isEqualByComparingTo("20650.00");
		assertThat(totals.paid()).isEqualByComparingTo("10000.00");
		assertThat(totals.balance()).isEqualByComparingTo("10650.00");
		assertThat(totals.owedNow()).isEqualByComparingTo("10650.00");
		assertThat(totals.overdue()).isEqualByComparingTo("0.00");
	}

	@Test
	void aPartialPaymentSettlesTheOldestDueFirstAndTheRestIsOverdue() {
		List<Line> lines = List.of(
				line(LedgerKind.SALARY, "500", "2020-01-05", false),
				line(LedgerKind.SALARY, "300", "2099-01-05", false),
				line(LedgerKind.PAYMENT, "200", "2026-11-01", false));

		Totals totals = StaffPayCalculator.totals(lines, TODAY);

		assertThat(totals.balance()).isEqualByComparingTo("600.00");
		assertThat(totals.owedNow()).isEqualByComparingTo("600.00");
		// The 2020 due had 500 and 200 settled it, so 300 is left and its date has passed. The 2099 due is not yet overdue.
		assertThat(totals.overdue()).isEqualByComparingTo("300.00");
	}

	@Test
	void aDueOnTodayIsNotOverdueYetAndAPassedDueIsWhenNotCovered() {
		assertThat(StaffPayCalculator.totals(List.of(line(LedgerKind.SALARY, "100", "2026-11-05", false)), TODAY).overdue())
			.isEqualByComparingTo("0.00");
		assertThat(StaffPayCalculator.totals(List.of(line(LedgerKind.SALARY, "100", "2026-11-04", false)), TODAY).overdue())
			.isEqualByComparingTo("100.00");
	}

	@Test
	void aPaymentCoveringEverythingLeavesNothingOwedAndAnAdvanceIsNegativeBalance() {
		List<Line> covered = List.of(line(LedgerKind.SALARY, "500", "2020-01-05", false),
				line(LedgerKind.PAYMENT, "500", "2026-11-01", false));
		Totals settled = StaffPayCalculator.totals(covered, TODAY);
		assertThat(settled.balance()).isEqualByComparingTo("0.00");
		assertThat(settled.overdue()).isEqualByComparingTo("0.00");

		List<Line> advance = List.of(line(LedgerKind.SALARY, "500", "2026-11-05", false),
				line(LedgerKind.PAYMENT, "800", "2026-11-01", false));
		Totals ahead = StaffPayCalculator.totals(advance, TODAY);
		assertThat(ahead.balance()).isEqualByComparingTo("-300.00");
		assertThat(ahead.owedNow()).isEqualByComparingTo("0.00");
		assertThat(ahead.overdue()).isEqualByComparingTo("0.00");
	}

	@Test
	void aNegativeAdjustmentSettlesTheOldestDueLikeMoneyPaid() {
		List<Line> lines = List.of(
				line(LedgerKind.SALARY, "1000", "2020-01-05", false),
				line(LedgerKind.ADJUSTMENT, "-400", "2026-11-01", false));

		Totals totals = StaffPayCalculator.totals(lines, TODAY);

		assertThat(totals.charged()).isEqualByComparingTo("600.00");
		assertThat(totals.balance()).isEqualByComparingTo("600.00");
		assertThat(totals.overdue()).isEqualByComparingTo("600.00");
	}

	@Test
	void theSameDueDateIsSettledInCreationOrder() {
		Line first = new Line(UUID.randomUUID(), LedgerKind.SALARY, new BigDecimal("300"), LocalDate.of(2020, 1, 5), EARLY,
				false);
		Line second = new Line(UUID.randomUUID(), LedgerKind.SALARY, new BigDecimal("300"), LocalDate.of(2020, 1, 5), LATE,
				false);
		Line payment = new Line(UUID.randomUUID(), LedgerKind.PAYMENT, new BigDecimal("300"), TODAY, LATE, false);

		Totals totals = StaffPayCalculator.totals(List.of(second, first, payment), TODAY);

		// The payment settles the earlier-created 300 first, so the later one is the overdue part.
		assertThat(totals.overdue()).isEqualByComparingTo("300.00");
	}

	@Test
	void noLinesMeansZeroEverywhere() {
		Totals totals = StaffPayCalculator.totals(List.of(), TODAY);
		assertThat(totals.balance()).isEqualByComparingTo("0.00");
		assertThat(totals.charged()).isEqualByComparingTo("0.00");
		assertThat(totals.overdue()).isEqualByComparingTo("0.00");
	}

	private static Line line(LedgerKind kind, String amount, String dueOn, boolean voided) {
		return new Line(UUID.randomUUID(), kind, new BigDecimal(amount), LocalDate.parse(dueOn), EARLY, voided);
	}

}
