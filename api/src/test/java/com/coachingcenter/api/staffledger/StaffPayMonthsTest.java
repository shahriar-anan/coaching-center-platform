package com.coachingcenter.api.staffledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The month and window arithmetic of the overview and analytics, with no database. Dhaka dates throughout. */
class StaffPayMonthsTest {

	@Test
	void theDhakaDateOfAnInstantIsTheDateInDhakaNotInUtc() {
		// 18:30 UTC on 31 October is 00:30 on 1 November in Dhaka (UTC+6): the new month has begun.
		assertThat(StaffPayMonths.dhakaDate(Instant.parse("2026-10-31T18:30:00Z"))).isEqualTo(LocalDate.of(2026, 11, 1));
		// 17:59 UTC is still 23:59 on 31 October in Dhaka.
		assertThat(StaffPayMonths.dhakaDate(Instant.parse("2026-10-31T17:59:00Z"))).isEqualTo(LocalDate.of(2026, 10, 31));
	}

	@Test
	void theMonthWindowIsFromTheFirstToTheFirstOfTheNextMonth() {
		LocalDate lastDay = LocalDate.of(2026, 11, 30);
		assertThat(StaffPayMonths.monthStart(lastDay)).isEqualTo(LocalDate.of(2026, 11, 1));
		assertThat(StaffPayMonths.nextMonthStart(lastDay)).isEqualTo(LocalDate.of(2026, 12, 1));

		// December rolls into January of the next year.
		LocalDate december = LocalDate.of(2026, 12, 15);
		assertThat(StaffPayMonths.monthStart(december)).isEqualTo(LocalDate.of(2026, 12, 1));
		assertThat(StaffPayMonths.nextMonthStart(december)).isEqualTo(LocalDate.of(2027, 1, 1));
	}

	@Test
	void theDueWindowIsTodayAndThirtyDaysAfterIt() {
		assertThat(StaffPayMonths.windowEnd(LocalDate.of(2026, 11, 5))).isEqualTo(LocalDate.of(2026, 12, 5));
		// Across a month end: 31 January plus thirty days is 2 March in a year that is not a leap year.
		assertThat(StaffPayMonths.windowEnd(LocalDate.of(2027, 1, 31))).isEqualTo(LocalDate.of(2027, 3, 2));
	}

	@Test
	void aMonthIsParsedOnlyInTheYearDashMonthForm() {
		assertThat(StaffPayMonths.parse("2026-11")).isEqualTo(YearMonth.of(2026, 11));
		assertThat(StaffPayMonths.parse(" 2026-01 ")).isEqualTo(YearMonth.of(2026, 1));
		for (String text : List.of("2026-13", "2026-00", "2026-1", "26-11", "2026/11", "2026-11-01", "", "abc")) {
			assertThat(StaffPayMonths.parse(text)).as(text).isNull();
		}
		assertThat(StaffPayMonths.parse(null)).isNull();
	}

	@Test
	void theRangeListsEveryMonthAcrossTheYearEndOldestFirst() {
		List<YearMonth> months = StaffPayMonths.range(YearMonth.of(2026, 11), YearMonth.of(2027, 2));

		assertThat(months).containsExactly(YearMonth.of(2026, 11), YearMonth.of(2026, 12), YearMonth.of(2027, 1),
				YearMonth.of(2027, 2));
		assertThat(months.stream().map(StaffPayMonths::key).toList())
			.containsExactly("2026-11", "2026-12", "2027-01", "2027-02");
	}

	@Test
	void aBackwardsRangeIsEmptyAndASingleMonthIsOneMonth() {
		assertThat(StaffPayMonths.range(YearMonth.of(2026, 11), YearMonth.of(2026, 10))).isEmpty();
		assertThat(StaffPayMonths.range(YearMonth.of(2026, 11), YearMonth.of(2026, 11))).hasSize(1);
	}

	@Test
	void theSpanCountsBothEndMonthsAcrossAYearBoundary() {
		assertThat(StaffPayMonths.spanMonths(YearMonth.of(2026, 11), YearMonth.of(2026, 11))).isEqualTo(1);
		assertThat(StaffPayMonths.spanMonths(YearMonth.of(2025, 12), YearMonth.of(2026, 11))).isEqualTo(12);
		assertThat(StaffPayMonths.spanMonths(YearMonth.of(2026, 11), YearMonth.of(2026, 10))).isZero();
	}

	@Test
	void theDefaultRangeEndsThisMonthAndCoversTwelveMonths() {
		YearMonth to = StaffPayMonths.defaultTo(LocalDate.of(2026, 11, 5));

		assertThat(to).isEqualTo(YearMonth.of(2026, 11));
		assertThat(StaffPayMonths.defaultFrom(to)).isEqualTo(YearMonth.of(2025, 12));
		assertThat(StaffPayMonths.spanMonths(StaffPayMonths.defaultFrom(to), to)).isEqualTo(StaffPayMonths.DEFAULT_SPAN_MONTHS);
	}

}
