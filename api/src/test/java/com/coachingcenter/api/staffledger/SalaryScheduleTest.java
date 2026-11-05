package com.coachingcenter.api.staffledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;

/** The salary period and due-date maths (decision P7): a month falls due on the pay day of the next month. */
class SalaryScheduleTest {

	@Test
	void aMonthFallsDueOnThePayDayOfTheFollowingMonth() {
		assertThat(SalarySchedule.dueOn(YearMonth.of(2026, 11), 5)).isEqualTo(LocalDate.of(2026, 12, 5));
		assertThat(SalarySchedule.dueOn(YearMonth.of(2026, 12), 5)).isEqualTo(LocalDate.of(2027, 1, 5));
	}

	@Test
	void payDay28ExistsInEveryMonthSoFebruaryWorks() {
		assertThat(SalarySchedule.dueOn(YearMonth.of(2027, 1), 28)).isEqualTo(LocalDate.of(2027, 2, 28));
		assertThat(SalarySchedule.dueOn(YearMonth.of(2027, 2), 28)).isEqualTo(LocalDate.of(2027, 3, 28));
		assertThat(SalarySchedule.dueOn(YearMonth.of(2028, 1), 28)).isEqualTo(LocalDate.of(2028, 2, 28));
	}

	@Test
	void theNextDueIsTheFirstOnOrAfterToday() {
		// October's salary is due 5 November; on 5 November it is still the next one.
		assertThat(SalarySchedule.nextDue(LocalDate.of(2026, 10, 1), null, 5, LocalDate.of(2026, 11, 5)))
			.isEqualTo(LocalDate.of(2026, 11, 5));
		assertThat(SalarySchedule.nextDue(LocalDate.of(2026, 10, 1), null, 5, LocalDate.of(2026, 11, 6)))
			.isEqualTo(LocalDate.of(2026, 12, 5));
	}

	@Test
	void aPartialStartMonthCountsInFullAndAnEndDateStopsTheSchedule() {
		// Starts mid-October: the October period still counts, due 5 November.
		assertThat(SalarySchedule.nextDue(LocalDate.of(2026, 10, 15), null, 5, LocalDate.of(2026, 10, 20)))
			.isEqualTo(LocalDate.of(2026, 11, 5));
		// Ends in October: October is the last period, due 5 November. After that there is nothing.
		assertThat(SalarySchedule.nextDue(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 5, LocalDate.of(2026, 11, 6)))
			.isNull();
	}

	@Test
	void aStartOnPayDayStillFindsItsFirstDue() {
		assertThat(SalarySchedule.nextDue(LocalDate.of(2026, 11, 1), null, 28, LocalDate.of(2026, 11, 1)))
			.isEqualTo(LocalDate.of(2026, 12, 28));
	}

}
