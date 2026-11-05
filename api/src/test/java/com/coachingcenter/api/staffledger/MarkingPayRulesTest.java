package com.coachingcenter.api.staffledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.coachingcenter.api.staffledger.StaffPayCalculator.Line;

/** The marking pay rules (staff pay plan, phase 4) with no database: the rate, who earns, the reminder maths. */
class MarkingPayRulesTest {

	private static final Instant PUBLISHED = Instant.parse("2026-11-04T10:00:00Z");

	@Test
	void theInstructorsOverrideWinsAndTheDefaultAppliesOtherwise() {
		assertThat(MarkingPayRules.rate(new BigDecimal("60.00"), new BigDecimal("50.00"))).isEqualByComparingTo("60.00");
		assertThat(MarkingPayRules.rate(null, new BigDecimal("50.00"))).isEqualByComparingTo("50.00");
		assertThat(MarkingPayRules.rate(null, new BigDecimal("75.00"))).isEqualByComparingTo("75.00");
	}

	@Test
	void aPublishedWrittenSubmittedSheetMarkedByAnInstructorEarns() {
		assertThat(MarkingPayRules.earnsSheet("INSTRUCTOR", "SUBMITTED", "WRITTEN", PUBLISHED, false)).isTrue();
	}

	@Test
	void aMarkByTheMasterAdminOrASystemAdminEarnsNothing() {
		assertThat(MarkingPayRules.earnsSheet("MASTER_ADMIN", "SUBMITTED", "WRITTEN", PUBLISHED, false)).isFalse();
		assertThat(MarkingPayRules.earnsSheet("SYSTEM_ADMIN", "SUBMITTED", "WRITTEN", PUBLISHED, false)).isFalse();
		assertThat(MarkingPayRules.earnsSheet("STUDENT", "SUBMITTED", "WRITTEN", PUBLISHED, false)).isFalse();
		assertThat(MarkingPayRules.earnsSheet(null, "SUBMITTED", "WRITTEN", PUBLISHED, false)).isFalse();
	}

	@Test
	void anAutomaticZeroIsNotASheet() {
		assertThat(MarkingPayRules.earnsSheet("INSTRUCTOR", "FAILED_TO_SUBMIT", "WRITTEN", PUBLISHED, false)).isFalse();
		assertThat(MarkingPayRules.earnsSheet("INSTRUCTOR", "IN_PROGRESS", "WRITTEN", PUBLISHED, false)).isFalse();
	}

	@Test
	void onlyWrittenExamsEarn() {
		assertThat(MarkingPayRules.earnsSheet("INSTRUCTOR", "SUBMITTED", "MCQ", PUBLISHED, false)).isFalse();
	}

	@Test
	void anUnpublishedOrDeletedSheetDoesNotEarnYet() {
		assertThat(MarkingPayRules.earnsSheet("INSTRUCTOR", "SUBMITTED", "WRITTEN", null, false)).isFalse();
		assertThat(MarkingPayRules.earnsSheet("INSTRUCTOR", "SUBMITTED", "WRITTEN", PUBLISHED, true)).isFalse();
	}

	@Test
	void thePublishDayIsTheDhakaDate() {
		// 00:30 on 5 November in Dhaka is still 4 November in UTC.
		assertThat(MarkingPayRules.publishDay(Instant.parse("2026-11-04T18:30:00Z"))).isEqualTo(LocalDate.of(2026, 11, 5));
		assertThat(MarkingPayRules.publishDay(Instant.parse("2026-11-04T10:00:00Z"))).isEqualTo(LocalDate.of(2026, 11, 4));
	}

	@Test
	void theOldestUnpaidMarkingIsTheFirstMarkingNotCoveredByPayments() {
		List<Line> lines = List.of(
				marking("150", "2026-09-01"),
				marking("100", "2026-10-01"),
				marking("100", "2026-10-15"),
				payment("150"));

		assertThat(MarkingPayRules.oldestUnpaidMarkingDue(lines)).contains(LocalDate.of(2026, 10, 1));
	}

	@Test
	void aPartlyPaidMarkingStillCountsAsUnpaid() {
		List<Line> lines = List.of(marking("100", "2026-10-01"), marking("100", "2026-10-15"), payment("50"));

		assertThat(MarkingPayRules.oldestUnpaidMarkingDue(lines)).contains(LocalDate.of(2026, 10, 1));
	}

	@Test
	void nothingIsUnpaidWhenEveryMarkingIsCovered() {
		List<Line> lines = List.of(marking("100", "2026-10-01"), payment("100"));

		assertThat(MarkingPayRules.oldestUnpaidMarkingDue(lines)).isEmpty();
	}

	@Test
	void aVoidedPaymentDoesNotCoverAMarking() {
		List<Line> lines = List.of(marking("100", "2026-10-01"), new Line(UUID.randomUUID(), LedgerKind.PAYMENT,
				new BigDecimal("100"), LocalDate.of(2026, 10, 2), Instant.parse("2026-10-02T00:00:00Z"), true));

		assertThat(MarkingPayRules.oldestUnpaidMarkingDue(lines)).contains(LocalDate.of(2026, 10, 1));
	}

	@Test
	void aNegativeAdjustmentSettlesTheOldestMarkingFirst() {
		List<Line> lines = List.of(marking("100", "2026-10-01"), marking("100", "2026-10-15"),
				new Line(UUID.randomUUID(), LedgerKind.ADJUSTMENT, new BigDecimal("-100"), LocalDate.of(2026, 10, 20),
						Instant.parse("2026-10-20T00:00:00Z"), false));

		assertThat(MarkingPayRules.oldestUnpaidMarkingDue(lines)).contains(LocalDate.of(2026, 10, 15));
	}

	@Test
	void aReminderGoesOnlyWhenTheOldestUnpaidMarkingIsOlderThanTheDays() {
		LocalDate today = LocalDate.of(2026, 11, 5);

		assertThat(MarkingPayRules.reminderDue(LocalDate.of(2026, 10, 22), today, 14)).isFalse();
		assertThat(MarkingPayRules.reminderDue(LocalDate.of(2026, 10, 21), today, 14)).isTrue();
	}

	@Test
	void theReminderWeekIsTheIsoWeek() {
		assertThat(MarkingPayRules.isoWeek(LocalDate.of(2026, 11, 5))).isEqualTo("2026-W45");
		// 29 December 2025 is already ISO week 1 of 2026.
		assertThat(MarkingPayRules.isoWeek(LocalDate.of(2025, 12, 29))).isEqualTo("2026-W01");
		// Sunday 8 November is still the same ISO week as Thursday 5 November; Monday 9 November starts the next.
		assertThat(MarkingPayRules.isoWeek(LocalDate.of(2026, 11, 8))).isEqualTo("2026-W45");
		assertThat(MarkingPayRules.isoWeek(LocalDate.of(2026, 11, 9))).isEqualTo("2026-W46");
	}

	private static Line marking(String amount, String dueOn) {
		return new Line(UUID.randomUUID(), LedgerKind.MARKING, new BigDecimal(amount), LocalDate.parse(dueOn),
				Instant.parse(dueOn + "T00:00:00Z"), false);
	}

	private static Line payment(String amount) {
		return new Line(UUID.randomUUID(), LedgerKind.PAYMENT, new BigDecimal(amount), LocalDate.of(2026, 11, 1),
				Instant.parse("2026-11-01T00:00:00Z"), false);
	}

}
