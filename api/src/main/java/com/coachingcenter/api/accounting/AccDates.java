package com.coachingcenter.api.accounting;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * Days and months of the ledger. Every day is a Dhaka day (plan 4.4); instants are UTC. The clock is the application's Clock bean.
 */
public final class AccDates {

	public static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

	private AccDates() {
	}

	/** Today in Dhaka. */
	public static LocalDate today(Clock clock) {
		return LocalDate.now(clock.withZone(DHAKA));
	}

	/** A day sent as YYYY-MM-DD. Blank or unreadable is a field error. */
	public static LocalDate parseDay(String text, String field) {
		if (text == null || text.isBlank()) {
			throw AccRefusals.validation(field, "Enter the date.");
		}
		try {
			return LocalDate.parse(text.trim());
		}
		catch (DateTimeException exception) {
			throw AccRefusals.validation(field, "Enter the date as YYYY-MM-DD.");
		}
	}

	/** A month sent as YYYY-MM, for example 2026-11. Blank or unreadable is a field error. */
	public static YearMonth parseMonth(String text, String field) {
		if (text == null || text.isBlank()) {
			throw AccRefusals.validation(field, "Choose the month.");
		}
		try {
			return YearMonth.parse(text.trim());
		}
		catch (DateTimeException exception) {
			throw AccRefusals.validation(field, "Choose the month as YYYY-MM.");
		}
	}

	/** The month of a day as YYYY-MM. */
	public static String monthOf(LocalDate day) {
		return YearMonth.from(day).toString();
	}

	/** A day after today is refused (plan 4.4: a future day is refused). */
	public static void notFuture(LocalDate day, LocalDate today) {
		if (day.isAfter(today)) {
			throw AccRefusals.refuse("future_date");
		}
	}

}
