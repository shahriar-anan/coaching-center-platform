package com.coachingcenter.api.offline;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * The monthly due dates of an admission and the times an access cap ends (plan 4.3, 4.4, side effect 18). Dates are Asia/Dhaka
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

	/** The end of a course-end date in Dhaka: the start of the next day. An access cap set here lasts through the whole day. */
	static Instant endOfDay(LocalDate endsOn) {
		return endsOn.plusDays(1).atStartOfDay(DHAKA).toInstant();
	}

	/** Today's date in Dhaka, from the application clock. */
	static LocalDate today(Instant now) {
		return now.atZone(DHAKA).toLocalDate();
	}

}
