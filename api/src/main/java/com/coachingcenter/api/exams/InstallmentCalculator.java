package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Where a payment plan stands today (plan section 4): how much of each row is paid, what is still due and when, and the
 * plan's status. Nothing here is stored. Payments are applied to rows in order, so a row is covered once the running total
 * of payments reaches the running total of rows. No database and no clock: the caller passes today in Dhaka.
 */
public final class InstallmentCalculator {

	/** A plan whose next installment falls within this many days is due soon (plan section 11, item 11). */
	static final int DUE_SOON_DAYS = 3;

	/** A row with nothing paid on it yet. */
	static final String ROW_UPCOMING = "UPCOMING";

	/** The oldest row not covered in full, due today or later, with nothing paid on it. */
	static final String ROW_DUE = "DUE";

	/** A row partly paid, due today or later. */
	static final String ROW_PART_PAID = "PART_PAID";

	/** A row not covered in full whose date has passed. */
	static final String ROW_OVERDUE = "OVERDUE";

	/** A row fully covered. */
	static final String ROW_PAID = "PAID";

	static final String ACTIVE = "ACTIVE";

	static final String DUE_SOON = "DUE_SOON";

	static final String OVERDUE = "OVERDUE";

	static final String COMPLETED = "COMPLETED";

	static final String ACCESS_REVOKED = "ACCESS_REVOKED";

	private InstallmentCalculator() {
	}

	/** One row with what has been paid on it. {@code uncovered} is what is still owed on it. */
	record RowState(int seq, BigDecimal amount, LocalDate dueOn, BigDecimal paid, BigDecimal uncovered, String state) {
	}

	/**
	 * The plan's standing. {@code nextSeq}, {@code nextDueOn} and {@code nextDueAmount} describe the oldest row not covered in
	 * full, and are null once nothing is due. {@code status} is COMPLETED when nothing is due, otherwise ACCESS_REVOKED when the
	 * grant is revoked, otherwise OVERDUE, DUE_SOON or ACTIVE by the next date.
	 */
	record Standing(BigDecimal paid, BigDecimal due, Integer nextSeq, LocalDate nextDueOn, BigDecimal nextDueAmount,
			String status, List<RowState> rows) {
	}

	/**
	 * The standing of a plan. {@code paid} is the total of its payments that are not voided. {@code revoked} is true when the
	 * grant behind the plan has been revoked.
	 */
	static Standing standing(List<InstallmentSchedule.Row> rows, BigDecimal agreedTotal, BigDecimal paid, boolean revoked,
			LocalDate today) {
		BigDecimal due = agreedTotal.subtract(paid);
		Integer nextSeq = null;
		LocalDate nextDueOn = null;
		BigDecimal nextDueAmount = null;
		BigDecimal start = BigDecimal.ZERO;
		List<BigDecimal[]> spread = new ArrayList<>();
		for (InstallmentSchedule.Row row : rows) {
			BigDecimal onRow = clamp(paid.subtract(start), row.amount());
			BigDecimal uncovered = row.amount().subtract(onRow);
			spread.add(new BigDecimal[] { onRow, uncovered });
			if (nextSeq == null && uncovered.signum() > 0) {
				nextSeq = row.seq();
				nextDueOn = row.dueOn();
				nextDueAmount = uncovered;
			}
			start = start.add(row.amount());
		}
		List<RowState> states = new ArrayList<>();
		for (int i = 0; i < rows.size(); i++) {
			InstallmentSchedule.Row row = rows.get(i);
			BigDecimal onRow = spread.get(i)[0];
			BigDecimal uncovered = spread.get(i)[1];
			states.add(new RowState(row.seq(), row.amount(), row.dueOn(), onRow, uncovered,
					rowState(row, onRow, uncovered, nextSeq, today)));
		}
		return new Standing(paid, due, nextSeq, nextDueOn, nextDueAmount, status(due, revoked, nextDueOn, today), states);
	}

	private static String rowState(InstallmentSchedule.Row row, BigDecimal onRow, BigDecimal uncovered, Integer nextSeq,
			LocalDate today) {
		if (uncovered.signum() == 0) {
			return ROW_PAID;
		}
		if (row.dueOn().isBefore(today)) {
			return ROW_OVERDUE;
		}
		if (onRow.signum() > 0) {
			return ROW_PART_PAID;
		}
		return nextSeq != null && nextSeq == row.seq() ? ROW_DUE : ROW_UPCOMING;
	}

	private static String status(BigDecimal due, boolean revoked, LocalDate nextDueOn, LocalDate today) {
		if (due.signum() <= 0 || nextDueOn == null) {
			return COMPLETED;
		}
		if (revoked) {
			return ACCESS_REVOKED;
		}
		if (nextDueOn.isBefore(today)) {
			return OVERDUE;
		}
		if (!nextDueOn.isAfter(today.plusDays(DUE_SOON_DAYS))) {
			return DUE_SOON;
		}
		return ACTIVE;
	}

	/** The part of {@code amount} that a payment total reaches, never below zero and never above the row. */
	private static BigDecimal clamp(BigDecimal reached, BigDecimal amount) {
		if (reached.signum() < 0) {
			return BigDecimal.ZERO;
		}
		return reached.compareTo(amount) > 0 ? amount : reached;
	}

}
