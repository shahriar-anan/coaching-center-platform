package com.coachingcenter.api.staffledger;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Month and window arithmetic for the Master Admin's overview and analytics (staff pay plan, phase 6). Every "today" and
 * every month boundary is a Dhaka date. No database and no clock: callers pass the instant or the date.
 */
public final class StaffPayMonths {

	static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

	/** The analytics range when the caller gives neither month: the twelve months up to and including this one. */
	static final int DEFAULT_SPAN_MONTHS = 12;

	/** The longest analytics range the API answers. */
	static final int MAX_SPAN_MONTHS = 36;

	/** The "due soon" window of the overview: today and the next thirty days. */
	static final int DUE_WINDOW_DAYS = 30;

	private static final Pattern MONTH = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");

	private StaffPayMonths() {
	}

	/** The Dhaka calendar date of an instant. Between 18:00 and midnight UTC the Dhaka date is already the next day. */
	public static LocalDate dhakaDate(Instant instant) {
		return instant.atZone(DHAKA).toLocalDate();
	}

	/** The first day of the Dhaka month that {@code today} falls in. */
	public static LocalDate monthStart(LocalDate today) {
		return today.withDayOfMonth(1);
	}

	/** The first day of the month after {@code today}'s month, so {@code [monthStart, nextMonthStart)} is one month. */
	public static LocalDate nextMonthStart(LocalDate today) {
		return monthStart(today).plusMonths(1);
	}

	/** The last day of the "due soon" window: today plus {@link #DUE_WINDOW_DAYS}, inclusive. */
	public static LocalDate windowEnd(LocalDate today) {
		return today.plusDays(DUE_WINDOW_DAYS);
	}

	/** Parses a month written YYYY-MM. Returns null for anything else, so the caller can answer with a field error. */
	public static YearMonth parse(String value) {
		if (value == null || !MONTH.matcher(value.trim()).matches()) {
			return null;
		}
		return YearMonth.parse(value.trim());
	}

	/** The default range end: the Dhaka month of {@code today}. */
	public static YearMonth defaultTo(LocalDate today) {
		return YearMonth.from(today);
	}

	/** The default range start when the caller gives only the end: {@link #DEFAULT_SPAN_MONTHS} months ending at {@code to}. */
	public static YearMonth defaultFrom(YearMonth to) {
		return to.minusMonths(DEFAULT_SPAN_MONTHS - 1L);
	}

	/** The number of months from {@code from} to {@code to}, both included. Negative when {@code to} is before {@code from}. */
	public static int spanMonths(YearMonth from, YearMonth to) {
		return (int) (to.getYear() * 12L + to.getMonthValue() - (from.getYear() * 12L + from.getMonthValue())) + 1;
	}

	/** Every month from {@code from} to {@code to}, both included, oldest first. Empty when the range is backwards. */
	public static List<YearMonth> range(YearMonth from, YearMonth to) {
		List<YearMonth> months = new ArrayList<>();
		for (YearMonth month = from; !month.isAfter(to); month = month.plusMonths(1)) {
			months.add(month);
		}
		return months;
	}

	/** A month as the API writes it: YYYY-MM. */
	public static String key(YearMonth month) {
		return month.toString();
	}

}
