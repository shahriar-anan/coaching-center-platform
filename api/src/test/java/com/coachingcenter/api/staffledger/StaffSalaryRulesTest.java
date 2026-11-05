package com.coachingcenter.api.staffledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.coachingcenter.api.staffledger.SalaryReminders.Kind;
import com.coachingcenter.api.staffledger.StaffPayCalculator.Line;
import com.coachingcenter.api.staffledger.StaffPayCalculator.Uncovered;

/** The salary catch-up rule, the reminder timing and the uncovered split, with no database. */
class StaffSalaryRulesTest {

	private static final Instant WHEN = Instant.parse("2026-10-01T00:00:00Z");

	@Test
	void aNewSalaryWritesTheFinishedMonthsFromItsStartAndNeverTheMonthInProgress() {
		// Started 1 October, today 1 November: October has finished and is due 5 November.
		assertThat(SalarySchedule.periodsToWrite(LocalDate.of(2026, 10, 1), null, LocalDate.of(2026, 11, 1), Set.of()))
			.containsExactly(YearMonth.of(2026, 10));
		// On 31 October October is still in progress, so nothing is written yet.
		assertThat(SalarySchedule.periodsToWrite(LocalDate.of(2026, 10, 1), null, LocalDate.of(2026, 10, 31), Set.of()))
			.isEmpty();
		// A start later than the last finished month writes nothing.
		assertThat(SalarySchedule.periodsToWrite(LocalDate.of(2026, 12, 1), null, LocalDate.of(2026, 11, 20), Set.of()))
			.isEmpty();
	}

	@Test
	void aMissedRunCatchesUpOnEveryMonthItMissedOldestFirst() {
		// Started August, nothing written for August to October, and the job did not run for three months.
		assertThat(SalarySchedule.periodsToWrite(LocalDate.of(2026, 8, 15), null, LocalDate.of(2026, 11, 3), Set.of()))
			.containsExactly(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10));
		// August is already written, so only the two months after it are added.
		assertThat(SalarySchedule.periodsToWrite(LocalDate.of(2026, 8, 15), null, LocalDate.of(2026, 11, 3),
				Set.of(YearMonth.of(2026, 8))))
			.containsExactly(YearMonth.of(2026, 9), YearMonth.of(2026, 10));
	}

	@Test
	void anEndDateStopsNewMonthsAndTheMonthOfTheEndIsStillWritten() {
		// Ends in September: August and September are written, October is not.
		assertThat(SalarySchedule.periodsToWrite(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30), LocalDate.of(2026, 11, 3),
				Set.of()))
			.containsExactly(YearMonth.of(2026, 8), YearMonth.of(2026, 9));
		// An end date after today still stops at the last finished month.
		assertThat(SalarySchedule.periodsToWrite(LocalDate.of(2026, 10, 1), LocalDate.of(2027, 6, 30), LocalDate.of(2026, 11, 3),
				Set.of()))
			.containsExactly(YearMonth.of(2026, 10));
	}

	@Test
	void theDueDateIsThePayDayOfTheFollowingMonthIncludingPayDay28() {
		assertThat(SalarySchedule.dueOn(YearMonth.of(2026, 10), 5)).isEqualTo(LocalDate.of(2026, 11, 5));
		assertThat(SalarySchedule.dueOn(YearMonth.of(2026, 10), 28)).isEqualTo(LocalDate.of(2026, 11, 28));
		assertThat(SalarySchedule.dueOn(YearMonth.of(2027, 1), 28)).isEqualTo(LocalDate.of(2027, 2, 28));
	}

	@Test
	void dueSoonIsSentInTheWindowBeforeTheDueDateWhileUncovered() {
		LocalDate due = LocalDate.of(2026, 11, 5);
		// Three days ahead: the notice arrives on 2 November, as the plan's example says.
		assertThat(reminder(due, true, LocalDate.of(2026, 11, 1), 3).kind()).isEqualTo(Kind.NONE);
		assertThat(reminder(due, true, LocalDate.of(2026, 11, 2), 3).kind()).isEqualTo(Kind.DUE_SOON);
		assertThat(reminder(due, true, LocalDate.of(2026, 11, 4), 3).kind()).isEqualTo(Kind.DUE_SOON);
		// On the due day itself it is not overdue yet, and no due-soon notice goes out any more.
		assertThat(reminder(due, true, LocalDate.of(2026, 11, 5), 3).kind()).isEqualTo(Kind.NONE);
	}

	@Test
	void zeroDueSoonDaysMeansNoAdvanceNotice() {
		LocalDate due = LocalDate.of(2026, 11, 5);
		assertThat(reminder(due, true, LocalDate.of(2026, 11, 4), 0).kind()).isEqualTo(Kind.NONE);
	}

	@Test
	void aCoveredLineNeverGetsAReminder() {
		LocalDate due = LocalDate.of(2026, 11, 5);
		assertThat(reminder(due, false, LocalDate.of(2026, 11, 2), 3).kind()).isEqualTo(Kind.NONE);
		assertThat(reminder(due, false, LocalDate.of(2026, 11, 6), 3).kind()).isEqualTo(Kind.NONE);
		assertThat(reminder(due, false, LocalDate.of(2026, 12, 6), 3).kind()).isEqualTo(Kind.NONE);
	}

	@Test
	void overdueIsSentTheDayAfterThenWeeklyWhileUnpaid() {
		LocalDate due = LocalDate.of(2026, 11, 5);
		// The due day is not overdue; the day after is, and its block starts that day.
		assertThat(reminder(due, true, LocalDate.of(2026, 11, 5), 3).kind()).isEqualTo(Kind.NONE);
		SalaryReminders.Reminder first = reminder(due, true, LocalDate.of(2026, 11, 6), 3);
		assertThat(first.kind()).isEqualTo(Kind.OVERDUE);
		assertThat(first.weekStart()).isEqualTo(LocalDate.of(2026, 11, 6));
		// Still the same block until the seventh day after the first notice.
		assertThat(reminder(due, true, LocalDate.of(2026, 11, 12), 3).weekStart()).isEqualTo(LocalDate.of(2026, 11, 6));
		// The next notice falls a week later, in the next block.
		assertThat(reminder(due, true, LocalDate.of(2026, 11, 13), 3).weekStart()).isEqualTo(LocalDate.of(2026, 11, 13));
		assertThat(reminder(due, true, LocalDate.of(2026, 11, 20), 3).weekStart()).isEqualTo(LocalDate.of(2026, 11, 20));
	}

	@Test
	void aMissedOverdueDayStillSendsInItsBlockAndEachBlockHasItsOwnWeek() {
		LocalDate due = LocalDate.of(2026, 11, 5);
		// The job was down on 6 and 7 November; it runs again on 9 November and still sends the block's notice.
		SalaryReminders.Reminder late = reminder(due, true, LocalDate.of(2026, 11, 9), 3);
		assertThat(late.kind()).isEqualTo(Kind.OVERDUE);
		assertThat(late.weekStart()).isEqualTo(LocalDate.of(2026, 11, 6));

		assertThat(SalaryReminders.isoWeek(LocalDate.of(2026, 11, 6))).isEqualTo("2026-W45");
		assertThat(SalaryReminders.isoWeek(LocalDate.of(2026, 11, 13))).isEqualTo("2026-W46");
	}

	@Test
	void theUncoveredSplitSettlesTheOldestDueFirstAndKeepsTheRest() {
		Line oldSalary = line(LedgerKind.SALARY, "500", "2020-01-05");
		Line newSalary = line(LedgerKind.SALARY, "300", "2099-01-05");
		Line payment = line(LedgerKind.PAYMENT, "200", "2026-11-01");

		List<Uncovered> items = StaffPayCalculator.uncovered(List.of(newSalary, oldSalary, payment));

		assertThat(items).hasSize(2);
		assertThat(items.get(0).line().id()).isEqualTo(oldSalary.id());
		assertThat(items.get(0).amount()).isEqualByComparingTo("300");
		assertThat(items.get(1).line().id()).isEqualTo(newSalary.id());
		assertThat(items.get(1).amount()).isEqualByComparingTo("300");
	}

	@Test
	void aVoidedPaymentDoesNotCoverAndAPaidLineHasNothingUncovered() {
		Line salary = line(LedgerKind.SALARY, "20000", "2026-11-05");
		Line voided = new Line(UUID.randomUUID(), LedgerKind.PAYMENT, new BigDecimal("20000"), LocalDate.of(2026, 11, 2), WHEN,
				true);
		assertThat(StaffPayCalculator.uncovered(List.of(salary, voided)).get(0).amount()).isEqualByComparingTo("20000");

		Line paid = line(LedgerKind.PAYMENT, "20000", "2026-11-02");
		assertThat(StaffPayCalculator.uncovered(List.of(salary, paid)).get(0).amount()).isEqualByComparingTo("0");
	}

	private static SalaryReminders.Reminder reminder(LocalDate due, boolean uncovered, LocalDate today, int dueSoonDays) {
		return SalaryReminders.reminderFor(due, uncovered, today, dueSoonDays);
	}

	private static Line line(LedgerKind kind, String amount, String dueOn) {
		return new Line(UUID.randomUUID(), kind, new BigDecimal(amount), LocalDate.parse(dueOn), WHEN, false);
	}

}
