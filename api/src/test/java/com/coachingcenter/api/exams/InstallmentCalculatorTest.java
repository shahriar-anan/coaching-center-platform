package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/** Coverage, status and the schedule-change rules of a payment plan (plan sections 4 and 10.1). No database. Written, not run. */
class InstallmentCalculatorTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);

	private static BigDecimal taka(String value) {
		return new BigDecimal(value);
	}

	private static List<InstallmentSchedule.Row> threeRows() {
		List<InstallmentSchedule.Row> rows = new ArrayList<>();
		rows.add(new InstallmentSchedule.Row(1, taka("2100"), LocalDate.of(2026, 11, 8)));
		rows.add(new InstallmentSchedule.Row(2, taka("2100"), LocalDate.of(2026, 12, 8)));
		rows.add(new InstallmentSchedule.Row(3, taka("2100"), LocalDate.of(2027, 1, 8)));
		return rows;
	}

	@Test
	void anExactPaymentCoversOneRowAndNamesTheNext() {
		InstallmentCalculator.Standing standing = InstallmentCalculator.standing(threeRows(), taka("6300"), taka("2100"), false,
				TODAY);
		assertThat(standing.due()).isEqualByComparingTo("4200");
		assertThat(standing.nextSeq()).isEqualTo(2);
		assertThat(standing.nextDueOn()).isEqualTo(LocalDate.of(2026, 12, 8));
		assertThat(standing.nextDueAmount()).isEqualByComparingTo("2100");
		assertThat(standing.rows().get(0).state()).isEqualTo(InstallmentCalculator.ROW_PAID);
		assertThat(standing.rows().get(1).state()).isEqualTo(InstallmentCalculator.ROW_DUE);
	}

	@Test
	void aPartPaymentLeavesTheRestDueOnThatRow() {
		InstallmentCalculator.Standing standing = InstallmentCalculator.standing(threeRows(), taka("6300"), taka("2500"), false,
				TODAY);
		assertThat(standing.rows().get(1).paid()).isEqualByComparingTo("400");
		assertThat(standing.rows().get(1).uncovered()).isEqualByComparingTo("1700");
		assertThat(standing.nextSeq()).isEqualTo(2);
		assertThat(standing.nextDueAmount()).isEqualByComparingTo("1700");
		assertThat(standing.rows().get(1).state()).isEqualTo(InstallmentCalculator.ROW_PART_PAID);
	}

	@Test
	void aPaymentThatOverrunsCarriesIntoTheNextRow() {
		InstallmentCalculator.Standing standing = InstallmentCalculator.standing(threeRows(), taka("6300"), taka("3000"), false,
				TODAY);
		assertThat(standing.rows().get(0).state()).isEqualTo(InstallmentCalculator.ROW_PAID);
		assertThat(standing.rows().get(1).uncovered()).isEqualByComparingTo("1200");
		assertThat(standing.rows().get(2).paid()).isEqualByComparingTo("0");
	}

	@Test
	void theFinalPaymentCompletesThePlan() {
		InstallmentCalculator.Standing standing = InstallmentCalculator.standing(threeRows(), taka("6300"), taka("6300"), false,
				TODAY);
		assertThat(standing.due()).isEqualByComparingTo("0");
		assertThat(standing.status()).isEqualTo(InstallmentCalculator.COMPLETED);
		assertThat(standing.nextDueOn()).isNull();
		assertThat(standing.nextDueAmount()).isNull();
	}

	@Test
	void aRowPastItsDateWithMoneyOwedIsOverdue() {
		InstallmentCalculator.Standing standing = InstallmentCalculator.standing(threeRows(), taka("6300"), taka("2100"), false,
				LocalDate.of(2026, 12, 9));
		assertThat(standing.status()).isEqualTo(InstallmentCalculator.OVERDUE);
		assertThat(standing.rows().get(1).state()).isEqualTo(InstallmentCalculator.ROW_OVERDUE);
	}

	@Test
	void aNextDateWithinThreeDaysIsDueSoonAndOnTheDayItIsStillDue() {
		InstallmentCalculator.Standing soon = InstallmentCalculator.standing(threeRows(), taka("6300"), taka("2100"), false,
				LocalDate.of(2026, 12, 5));
		assertThat(soon.status()).isEqualTo(InstallmentCalculator.DUE_SOON);
		InstallmentCalculator.Standing onTheDay = InstallmentCalculator.standing(threeRows(), taka("6300"), taka("2100"), false,
				LocalDate.of(2026, 12, 8));
		assertThat(onTheDay.status()).isEqualTo(InstallmentCalculator.DUE_SOON);
		assertThat(onTheDay.rows().get(1).state()).isEqualTo(InstallmentCalculator.ROW_DUE);
	}

	@Test
	void aRevokedPlanWithMoneyOwedReadsAccessRevoked() {
		InstallmentCalculator.Standing standing = InstallmentCalculator.standing(threeRows(), taka("6300"), taka("2100"), true,
				TODAY);
		assertThat(standing.status()).isEqualTo(InstallmentCalculator.ACCESS_REVOKED);
	}

	@Test
	void coveredCountCountsOnlyRowsFullyReached() {
		assertThat(InstallmentSchedule.coveredCount(threeRows(), taka("0"))).isZero();
		assertThat(InstallmentSchedule.coveredCount(threeRows(), taka("2100"))).isEqualTo(1);
		assertThat(InstallmentSchedule.coveredCount(threeRows(), taka("2599.99"))).isEqualTo(1);
		assertThat(InstallmentSchedule.coveredCount(threeRows(), taka("4200"))).isEqualTo(2);
	}

	@Test
	void aScheduleChangeKeepsThePaidRowAndAddsUpToWhatIsLeft() {
		List<InstallmentSchedule.Row> proposed = new ArrayList<>();
		proposed.add(threeRows().get(0));
		proposed.add(new InstallmentSchedule.Row(2, taka("1000"), LocalDate.of(2026, 12, 20)));
		proposed.add(new InstallmentSchedule.Row(3, taka("3200"), LocalDate.of(2027, 2, 20)));
		InstallmentSchedule.validateChange(taka("6300"), proposed, 1, TODAY);
	}

	@Test
	void aScheduleChangeThatNoLongerAddsUpIsRefused() {
		List<InstallmentSchedule.Row> proposed = new ArrayList<>();
		proposed.add(threeRows().get(0));
		proposed.add(new InstallmentSchedule.Row(2, taka("1000"), LocalDate.of(2026, 12, 20)));
		proposed.add(new InstallmentSchedule.Row(3, taka("3199.99"), LocalDate.of(2027, 2, 20)));
		assertThatThrownBy(() -> InstallmentSchedule.validateChange(taka("6300"), proposed, 1, TODAY))
			.isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.code())
				.isEqualTo(ErrorCode.VALIDATION_FAILED));
	}

	@Test
	void aNewRowDatedInThePastIsRefused() {
		List<InstallmentSchedule.Row> proposed = new ArrayList<>();
		proposed.add(threeRows().get(0));
		proposed.add(new InstallmentSchedule.Row(2, taka("4200"), LocalDate.of(2026, 11, 7)));
		assertThatThrownBy(() -> InstallmentSchedule.validateChange(taka("6300"), proposed, 1, TODAY))
			.isInstanceOf(ApiException.class);
	}

}
