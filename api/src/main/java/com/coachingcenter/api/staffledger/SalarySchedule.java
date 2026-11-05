package com.coachingcenter.api.staffledger;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Salary period and due-date maths (staff pay plan, decision P7). A month's salary falls due on the pay day of the following
 * month: pay day 5 makes November's salary due 5 December. No pro-rating: a period counts in full when its month is in range.
 */
public final class SalarySchedule {

	/** A safety bound on the search: about a hundred years of months. */
	private static final int MAX_MONTHS = 1200;

	private SalarySchedule() {
	}

	/** The day a period's salary falls due. Pay day 28 exists in every month, so this never throws for 1 to 28. */
	public static LocalDate dueOn(YearMonth period, int payDay) {
		return period.plusMonths(1).atDay(payDay);
	}

	/**
	 * The first due date that is on or after {@code today}, for a salary that runs from the month of {@code start} to the
	 * month of {@code end} (open-ended when {@code end} is null). Returns null when no period is left.
	 */
	public static LocalDate nextDue(LocalDate start, LocalDate end, int payDay, LocalDate today) {
		YearMonth first = YearMonth.from(start);
		YearMonth last = end == null ? null : YearMonth.from(end);
		for (int i = 0; i < MAX_MONTHS; i++) {
			YearMonth period = first.plusMonths(i);
			if (last != null && period.isAfter(last)) {
				return null;
			}
			LocalDate due = dueOn(period, payDay);
			if (!due.isBefore(today)) {
				return due;
			}
		}
		return null;
	}

	/**
	 * The periods that still need a SALARY line, oldest first: each month from the month of {@code start} up to the last
	 * finished month (the month before {@code today}), or up to the month of {@code end} when that comes first, minus the
	 * months already in {@code written}. A run that was missed or late therefore catches up on every month it missed.
	 */
	public static List<YearMonth> periodsToWrite(LocalDate start, LocalDate end, LocalDate today, Set<YearMonth> written) {
		YearMonth last = YearMonth.from(today).minusMonths(1);
		if (end != null && YearMonth.from(end).isBefore(last)) {
			last = YearMonth.from(end);
		}
		YearMonth first = YearMonth.from(start);
		List<YearMonth> periods = new ArrayList<>();
		for (int i = 0; i < MAX_MONTHS; i++) {
			YearMonth period = first.plusMonths(i);
			if (period.isAfter(last)) {
				break;
			}
			if (!written.contains(period)) {
				periods.add(period);
			}
		}
		return periods;
	}

}
