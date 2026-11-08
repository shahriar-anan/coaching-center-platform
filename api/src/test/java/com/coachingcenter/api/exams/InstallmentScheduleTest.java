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

/** The schedule rules and receipt figures of a direct enrolment's payment plan (plan section 4). No database. */
class InstallmentScheduleTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);

	private static BigDecimal taka(String value) {
		return new BigDecimal(value);
	}

	private static List<InstallmentSchedule.Row> rows(String... amountAndDate) {
		List<InstallmentSchedule.Row> rows = new ArrayList<>();
		for (int i = 0; i < amountAndDate.length; i += 2) {
			rows.add(new InstallmentSchedule.Row(i / 2 + 1, taka(amountAndDate[i]), LocalDate.parse(amountAndDate[i + 1])));
		}
		return rows;
	}

	@Test
	void aThreeInstallmentPlanPasses() {
		List<InstallmentSchedule.Row> rows = rows("2100", "2026-11-08", "2100", "2026-12-08", "2100", "2027-01-08");
		InstallmentSchedule.validate(taka("6300"), taka("2100"), TODAY, rows, TODAY);
	}

	@Test
	void aFullPaymentIsOneRowThatCoversTheTotal() {
		List<InstallmentSchedule.Row> rows = rows("6000", "2026-11-08");
		InstallmentSchedule.validate(taka("6000"), taka("6000"), TODAY, rows, TODAY);
	}

	@Test
	void rowsThatDoNotAddUpToTheTotalAreRefused() {
		List<InstallmentSchedule.Row> rows = rows("2100", "2026-11-08", "2100.00", "2026-12-08", "2099.99", "2027-01-08");
		assertRefused(taka("6300"), taka("2100"), TODAY, rows, TODAY);
	}

	@Test
	void rowOneMustBeThePaymentOfToday() {
		List<InstallmentSchedule.Row> wrongAmount = rows("2000", "2026-11-08", "2100", "2026-12-08", "2200", "2027-01-08");
		assertRefused(taka("6300"), taka("2100"), TODAY, wrongAmount, TODAY);
		List<InstallmentSchedule.Row> wrongDate = rows("2100", "2026-11-09", "2100", "2026-12-09", "2100", "2027-01-09");
		assertRefused(taka("6300"), taka("2100"), TODAY, wrongDate, TODAY);
	}

	@Test
	void aSingleRowMustCoverTheWholeTotal() {
		assertRefused(taka("6000"), taka("2000"), TODAY, rows("2000", "2026-11-08"), TODAY);
	}

	@Test
	void aPlanWithInstallmentsCannotTakeTheWholeTotalToday() {
		List<InstallmentSchedule.Row> rows = rows("6000", "2026-11-08", "0.00", "2026-12-08");
		assertRefused(taka("6000"), taka("6000"), TODAY, rows, TODAY);
	}

	@Test
	void aBackdatedPaymentIsAcceptedAndAFuturePaymentIsRefused() {
		LocalDate lastWeek = LocalDate.of(2026, 11, 1);
		InstallmentSchedule.validate(taka("6300"), taka("2100"), lastWeek,
				rows("2100", "2026-11-01", "2100", "2026-12-08", "2100", "2027-01-08"), TODAY);
		assertRefused(taka("6300"), taka("2100"), LocalDate.of(2026, 11, 9),
				rows("2100", "2026-11-09", "2100", "2026-12-09", "2100", "2027-01-09"), TODAY);
	}

	@Test
	void laterRowsMustBeInOrderAndNotInThePast() {
		assertRefused(taka("6300"), taka("2100"), TODAY,
				rows("2100", "2026-11-08", "2100", "2026-12-08", "2100", "2026-12-08"), TODAY);
		assertRefused(taka("6300"), taka("2100"), TODAY,
				rows("2100", "2026-11-08", "2100", "2026-11-01", "2100", "2027-01-08"), TODAY);
	}

	@Test
	void moreThan24RowsAreRefusedAndTheLimitIsAccepted() {
		List<InstallmentSchedule.Row> twentyFive = new ArrayList<>();
		for (int i = 1; i <= 25; i++) {
			twentyFive.add(new InstallmentSchedule.Row(i, taka("10"), TODAY.plusMonths(i - 1)));
		}
		assertRefused(taka("250"), taka("10"), TODAY, twentyFive, TODAY);
		List<InstallmentSchedule.Row> twentyFour = twentyFive.subList(0, 24);
		InstallmentSchedule.validate(taka("240"), taka("10"), TODAY, twentyFour, TODAY);
	}

	@Test
	void moreThanTwoDecimalsIsRefusedNotRounded() {
		assertRefused(taka("6300.001"), taka("2100"), TODAY, rows("2100", "2026-11-08", "4200.001", "2026-12-08"), TODAY);
		assertThat(InstallmentSchedule.isMoney(taka("2100.50"))).isTrue();
		assertThat(InstallmentSchedule.isMoney(taka("2100.500"))).isTrue();
		assertThat(InstallmentSchedule.isMoney(taka("2100.505"))).isFalse();
	}

	@Test
	void theFirstPaymentTouchesRowOneAndLeavesTheNextRowDue() {
		List<InstallmentSchedule.Row> rows = rows("2100", "2026-11-08", "2100", "2026-12-08", "2100", "2027-01-08");
		InstallmentSchedule.Position position = InstallmentSchedule.position(rows, taka("6300"), BigDecimal.ZERO, taka("2100"));
		assertThat(position.paidToDate()).isEqualByComparingTo("2100");
		assertThat(position.dueAfter()).isEqualByComparingTo("4200");
		assertThat(position.installmentNo()).isEqualTo(1);
		assertThat(position.installmentCount()).isEqualTo(3);
		assertThat(position.nextSeq()).isEqualTo(2);
		assertThat(position.nextDueOn()).isEqualTo(LocalDate.of(2026, 12, 8));
		assertThat(position.nextDueAmount()).isEqualByComparingTo("2100");
	}

	@Test
	void aPartPaymentLeavesTheRestOfTheRowDue() {
		List<InstallmentSchedule.Row> rows = rows("1000", "2026-11-08", "1000", "2026-12-08", "1000", "2027-01-08");
		InstallmentSchedule.Position position = InstallmentSchedule.position(rows, taka("3000"), BigDecimal.ZERO, taka("400"));
		assertThat(position.nextSeq()).isEqualTo(1);
		assertThat(position.nextDueAmount()).isEqualByComparingTo("600");
		assertThat(position.dueAfter()).isEqualByComparingTo("2600");
	}

	@Test
	void aPaymentCanClearTwoRowsAndTheNextUncoveredRowIsTheThird() {
		List<InstallmentSchedule.Row> rows = rows("1000", "2026-11-08", "1000", "2026-12-08", "1000", "2027-01-08");
		InstallmentSchedule.Position position = InstallmentSchedule.position(rows, taka("3000"), BigDecimal.ZERO, taka("2000"));
		assertThat(position.nextSeq()).isEqualTo(3);
		assertThat(position.nextDueAmount()).isEqualByComparingTo("1000");
	}

	@Test
	void theFinalPaymentLeavesNothingDue() {
		List<InstallmentSchedule.Row> rows = rows("1000", "2026-11-08", "1000", "2026-12-08");
		InstallmentSchedule.Position position = InstallmentSchedule.position(rows, taka("2000"), taka("1000"), taka("1000"));
		assertThat(position.dueAfter()).isEqualByComparingTo("0");
		assertThat(position.nextSeq()).isNull();
		assertThat(position.nextDueOn()).isNull();
		assertThat(position.nextDueAmount()).isNull();
		assertThat(position.installmentNo()).isEqualTo(2);
	}

	private static void assertRefused(BigDecimal total, BigDecimal amount, LocalDate paidOn,
			List<InstallmentSchedule.Row> rows, LocalDate today) {
		assertThatThrownBy(() -> InstallmentSchedule.validate(total, amount, paidOn, rows, today))
			.isInstanceOfSatisfying(ApiException.class, exception ->
					assertThat(exception.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
	}

}
