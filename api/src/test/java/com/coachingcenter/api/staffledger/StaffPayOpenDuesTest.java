package com.coachingcenter.api.staffledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.coachingcenter.api.staffledger.StaffPayCalculator.Line;
import com.coachingcenter.api.staffledger.StaffPayOpenDues.OpenDue;

/** The uncovered part of each due, and the overdue figure it gives, with no database. */
class StaffPayOpenDuesTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

	private static final Instant EARLY = Instant.parse("2026-10-01T00:00:00Z");

	private static final Instant LATE = Instant.parse("2026-10-02T00:00:00Z");

	@Test
	void theOverdueFigureIsTheUncoveredPartOfDuesWhoseDateHasPassed() {
		List<Line> lines = List.of(
				line(LedgerKind.SALARY, "500", "2020-01-05", EARLY, false),
				line(LedgerKind.SALARY, "300", "2099-01-05", LATE, false),
				line(LedgerKind.PAYMENT, "200", "2026-11-01", LATE, false));

		List<OpenDue> open = StaffPayOpenDues.of(lines);

		// The 2020 due had 500 and 200 settled it, so 300 is open and overdue. The 2099 due is open but not overdue.
		assertThat(StaffPayOpenDues.total(open)).isEqualByComparingTo("600.00");
		assertThat(StaffPayOpenDues.overdue(open, TODAY)).isEqualByComparingTo("300.00");
		assertThat(open).hasSize(2);
		assertThat(open.get(0).dueOn()).isEqualTo(LocalDate.of(2020, 1, 5));
		assertThat(open.get(0).uncovered()).isEqualByComparingTo("300.00");
	}

	@Test
	void aDueOnTodayIsNotOverdueAndAFullyPaidDueIsNotListed() {
		assertThat(StaffPayOpenDues.overdue(StaffPayOpenDues.of(List.of(line(LedgerKind.SALARY, "100", "2026-11-05", EARLY,
				false))), TODAY)).isEqualByComparingTo("0.00");
		assertThat(StaffPayOpenDues.overdue(StaffPayOpenDues.of(List.of(line(LedgerKind.SALARY, "100", "2026-11-04", EARLY,
				false))), TODAY)).isEqualByComparingTo("100.00");

		List<Line> covered = List.of(line(LedgerKind.MARKING, "150", "2020-01-05", EARLY, false),
				line(LedgerKind.PAYMENT, "150", "2026-11-01", LATE, false));
		assertThat(StaffPayOpenDues.of(covered)).isEmpty();
	}

	@Test
	void voidedLinesCountForNothing() {
		List<Line> lines = List.of(
				line(LedgerKind.SALARY, "500", "2020-01-05", EARLY, false),
				line(LedgerKind.PAYMENT, "500", "2026-11-01", LATE, true));

		assertThat(StaffPayOpenDues.total(StaffPayOpenDues.of(lines))).isEqualByComparingTo("500.00");
	}

	@Test
	void aNegativeAdjustmentSettlesTheOldestDueLikeAPayment() {
		List<Line> lines = List.of(
				line(LedgerKind.MARKING, "200", "2020-01-05", EARLY, false),
				line(LedgerKind.MARKING, "200", "2020-02-05", LATE, false),
				line(LedgerKind.ADJUSTMENT, "-150", "2026-11-01", LATE, false));

		List<OpenDue> open = StaffPayOpenDues.of(lines);

		assertThat(StaffPayOpenDues.total(open)).isEqualByComparingTo("250.00");
		assertThat(open.get(0).uncovered()).isEqualByComparingTo("50.00");
		assertThat(open.get(0).kind()).isEqualTo(LedgerKind.MARKING);
	}

	@Test
	void theKindSplitAddsUpToTheWholeAndKeepsEachKindApart() {
		// The salary is the oldest due, so the payment settles it first and the marking and the adjustment stay whole.
		List<Line> lines = List.of(
				line(LedgerKind.SALARY, "20000", "2026-10-05", EARLY, false),
				line(LedgerKind.MARKING, "150", "2026-11-03", EARLY, false),
				line(LedgerKind.ADJUSTMENT, "500", "2026-11-04", EARLY, false),
				line(LedgerKind.PAYMENT, "10000", "2026-11-05", EARLY, false));

		List<OpenDue> open = StaffPayOpenDues.of(lines);

		assertThat(StaffPayOpenDues.ofKind(open, LedgerKind.SALARY)).isEqualByComparingTo("10000.00");
		assertThat(StaffPayOpenDues.ofKind(open, LedgerKind.MARKING)).isEqualByComparingTo("150.00");
		assertThat(StaffPayOpenDues.ofKind(open, LedgerKind.ADJUSTMENT)).isEqualByComparingTo("500.00");
		assertThat(StaffPayOpenDues.total(open)).isEqualByComparingTo("10650.00");
	}

	@Test
	void theOpenDuesAgreeWithTheCalculatorOnBalanceAndOverdueForManyShapes() {
		List<List<Line>> people = List.of(
				List.of(line(LedgerKind.SALARY, "500", "2020-01-05", EARLY, false),
						line(LedgerKind.SALARY, "300", "2099-01-05", LATE, false),
						line(LedgerKind.PAYMENT, "200", "2026-11-01", LATE, false)),
				List.of(line(LedgerKind.SALARY, "500", "2026-11-05", EARLY, false),
						line(LedgerKind.PAYMENT, "800", "2026-11-01", LATE, false)),
				List.of(line(LedgerKind.MARKING, "50", "2026-10-30", EARLY, false),
						line(LedgerKind.ADJUSTMENT, "-300", "2026-11-02", LATE, false),
						line(LedgerKind.PAYMENT, "100", "2026-11-03", LATE, true)),
				List.of(line(LedgerKind.ADJUSTMENT, "-40", "2026-11-01", EARLY, false)));

		for (List<Line> lines : people) {
			StaffPayCalculator.Totals totals = StaffPayCalculator.totals(lines, TODAY);
			List<OpenDue> open = StaffPayOpenDues.of(lines);

			assertThat(StaffPayOpenDues.total(open)).as(lines.toString()).isEqualByComparingTo(totals.owedNow());
			assertThat(StaffPayOpenDues.overdue(open, TODAY)).as(lines.toString()).isEqualByComparingTo(totals.overdue());
		}
	}

	private static Line line(LedgerKind kind, String amount, String dueOn, Instant createdAt, boolean voided) {
		return new Line(UUID.randomUUID(), kind, new BigDecimal(amount), LocalDate.parse(dueOn), createdAt, voided);
	}

}
