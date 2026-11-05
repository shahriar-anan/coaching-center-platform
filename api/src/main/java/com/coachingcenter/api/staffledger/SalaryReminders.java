package com.coachingcenter.api.staffledger;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;

/**
 * The reminder timing for one salary line (staff pay plan, phase 5). No database and no clock: the caller passes the line's due
 * date, whether anything is still uncovered on it, today in Dhaka and the due-soon days from the defaults.
 *
 * <p>
 * Due soon: from {@code dueSoonDays} before the due date up to the day before it, while anything is uncovered. Zero days means
 * no advance notice. Overdue: from the day after the due date, then once a week while it stays uncovered. The overdue notice
 * for each week is keyed to the first day of that week's block (the day after the due date, plus whole weeks), so a run that
 * was missed still sends it on its next run inside the block, and each block has its own ISO week.
 */
public final class SalaryReminders {

	private SalaryReminders() {
	}

	public enum Kind {
		NONE,
		DUE_SOON,
		OVERDUE
	}

	/** The reminder for today. {@code weekStart} is set for OVERDUE only: the first day of the week's block. */
	public record Reminder(Kind kind, LocalDate weekStart) {
	}

	private static final Reminder NOTHING = new Reminder(Kind.NONE, null);

	public static Reminder reminderFor(LocalDate dueOn, boolean uncovered, LocalDate today, int dueSoonDays) {
		if (!uncovered) {
			return NOTHING;
		}
		if (today.isBefore(dueOn)) {
			boolean inWindow = !today.isBefore(dueOn.minusDays(dueSoonDays));
			return inWindow ? new Reminder(Kind.DUE_SOON, null) : NOTHING;
		}
		if (today.isAfter(dueOn)) {
			long daysPast = ChronoUnit.DAYS.between(dueOn, today);
			long week = (daysPast - 1) / 7;
			return new Reminder(Kind.OVERDUE, dueOn.plusDays(1 + 7 * week));
		}
		return NOTHING;
	}

	/** The ISO week of a day, as {@code 2026-W45}. Used in an overdue notice's dedupe key. */
	public static String isoWeek(LocalDate day) {
		return "%04d-W%02d".formatted(day.get(IsoFields.WEEK_BASED_YEAR), day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
	}

}
