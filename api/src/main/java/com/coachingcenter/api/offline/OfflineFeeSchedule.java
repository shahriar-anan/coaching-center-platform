package com.coachingcenter.api.offline;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * The instalments of an admission's course fee and the monthly due dates they fall on (plan 4.3, course-fee-plan.md), and the times an
 * access cap ends (plan 4.4, side effect 18). Dates are Asia/Dhaka
 * calendar dates. A due falls on the same day of each month; a day a month does not have (the 31st in February) falls on that
 * month's last day, and each month is worked out from the first due date, so one short month does not move later months.
 */
final class OfflineFeeSchedule {

	static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

	private OfflineFeeSchedule() {
	}

	/**
	 * Every due date from {@code firstDueOn} up to and including the last one on or before {@code courseEndsOn}, in order. The
	 * first entry is {@code firstDueOn} itself. Empty when the first due is after the course end.
	 */
	static List<LocalDate> dueDates(LocalDate firstDueOn, LocalDate courseEndsOn) {
		List<LocalDate> dates = new ArrayList<>();
		YearMonth first = YearMonth.from(firstDueOn);
		int day = firstDueOn.getDayOfMonth();
		for (int month = 0;; month++) {
			YearMonth current = first.plusMonths(month);
			LocalDate due = current.atDay(Math.min(day, current.lengthOfMonth()));
			if (due.isAfter(courseEndsOn)) {
				return dates;
			}
			dates.add(due);
		}
	}

	/** The most instalments a course fee may be paid in (course-fee-plan.md section 7). */
	static final int MAX_INSTALLMENTS = 24;

	/**
	 * The date of instalment {@code seq} (1 first): the first due date plus {@code seq - 1} months, on the same day, or the month's last
	 * day when the month is shorter.
	 */
	static LocalDate installmentDate(LocalDate firstDueOn, int seq) {
		YearMonth month = YearMonth.from(firstDueOn).plusMonths(seq - 1L);
		return month.atDay(Math.min(firstDueOn.getDayOfMonth(), month.lengthOfMonth()));
	}

	/** The dates of {@code count} monthly instalments from the first due date, in order. */
	static List<LocalDate> installmentDates(LocalDate firstDueOn, int count) {
		List<LocalDate> dates = new ArrayList<>(count);
		for (int seq = 1; seq <= count; seq++) {
			dates.add(installmentDate(firstDueOn, seq));
		}
		return dates;
	}

	/**
	 * Splits {@code total} into {@code count} instalments (decision C3): whole taka for each but the last, which takes the rest, paisa
	 * included. 7,000 in 6 is 1,166 five times, then 1,170. A count below one gives an empty list.
	 */
	static List<BigDecimal> split(BigDecimal total, int count) {
		List<BigDecimal> amounts = new ArrayList<>(Math.max(count, 0));
		if (count < 1) {
			return amounts;
		}
		BigDecimal each = total.divide(BigDecimal.valueOf(count), 0, RoundingMode.FLOOR).setScale(2);
		for (int index = 0; index < count - 1; index++) {
			amounts.add(each);
		}
		amounts.add(total.subtract(each.multiply(BigDecimal.valueOf(count - 1L))).setScale(2, RoundingMode.HALF_UP));
		return amounts;
	}

	/**
	 * The number of monthly instalments that fit from the first due date to the course end, at least one and at most
	 * {@link #MAX_INSTALLMENTS}. The default when the admin has not chosen one.
	 */
	static int fittingInstallments(LocalDate firstDueOn, LocalDate courseEndsOn) {
		if (firstDueOn == null || courseEndsOn == null) {
			return 1;
		}
		return Math.max(1, Math.min(MAX_INSTALLMENTS, dueDates(firstDueOn, courseEndsOn).size()));
	}

	/** The end of a course-end date in Dhaka: the start of the next day. An access cap set here lasts through the whole day. */
	static Instant endOfDay(LocalDate endsOn) {
		return endsOn.plusDays(1).atStartOfDay(DHAKA).toInstant();
	}

	/** Today's date in Dhaka, from the application clock. */
	static LocalDate today(Instant now) {
		return now.atZone(DHAKA).toLocalDate();
	}

}
