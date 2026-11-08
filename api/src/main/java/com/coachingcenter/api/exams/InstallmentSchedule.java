package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;

/**
 * The schedule of a direct enrolment's payment plan, and the figures a receipt shows (plan section 4). No database and no
 * clock: the caller passes today in Dhaka. Row 1 is the payment taken on the day; the rest are installments still to come.
 *
 * <p>
 * A payment covers rows in order. A row is covered once the running total of payments reaches the running total of rows.
 */
final class InstallmentSchedule {

	static final int MAX_ROWS = 24;

	private static final BigDecimal ZERO = BigDecimal.ZERO;

	private InstallmentSchedule() {
	}

	/** One row of the schedule. {@code seq} starts at 1. */
	record Row(int seq, BigDecimal amount, LocalDate dueOn) {
	}

	/**
	 * The figures a payment's receipt shows, as they stand after that payment. {@code nextSeq}, {@code nextDueOn} and
	 * {@code nextDueAmount} are null once nothing is left to pay. {@code installmentNo} is the first row the payment touches.
	 */
	record Position(BigDecimal paidToDate, BigDecimal dueAfter, Integer nextSeq, LocalDate nextDueOn,
			BigDecimal nextDueAmount, int installmentNo, int installmentCount) {
	}

	/**
	 * Checks a plan before anything is saved. Every problem found is listed, so the form can show them together.
	 *
	 * @throws ApiException with {@link ErrorCode#VALIDATION_FAILED} when the plan breaks a rule
	 */
	static void validate(BigDecimal agreedTotal, BigDecimal amount, LocalDate paidOn, List<Row> rows, LocalDate today) {
		List<FieldErrorDetail> errors = new ArrayList<>();
		if (!isMoney(agreedTotal) || agreedTotal.signum() <= 0) {
			errors.add(new FieldErrorDetail("agreedTotal", "The total must be more than zero, with at most two decimals."));
		}
		if (!isMoney(amount) || amount.signum() <= 0) {
			errors.add(new FieldErrorDetail("amount", "The amount paid must be more than zero, with at most two decimals."));
		}
		if (paidOn == null || paidOn.isAfter(today)) {
			errors.add(new FieldErrorDetail("paidOn", "The payment date cannot be in the future."));
		}
		if (rows == null || rows.isEmpty() || rows.size() > MAX_ROWS) {
			errors.add(new FieldErrorDetail("schedule", "A plan has between 1 and " + MAX_ROWS + " rows."));
			throwIfAny(errors);
			return;
		}
		BigDecimal sum = ZERO;
		LocalDate previous = null;
		for (int i = 0; i < rows.size(); i++) {
			Row row = rows.get(i);
			if (!isMoney(row.amount()) || row.amount().signum() <= 0) {
				errors.add(new FieldErrorDetail("schedule", "Row " + (i + 1) + " must be more than zero, with at most two decimals."));
			}
			else {
				sum = sum.add(row.amount());
			}
			if (row.dueOn() == null) {
				errors.add(new FieldErrorDetail("schedule", "Row " + (i + 1) + " needs a date."));
				continue;
			}
			if (previous != null && !row.dueOn().isAfter(previous)) {
				errors.add(new FieldErrorDetail("schedule", "Each row's date must be later than the one before it."));
			}
			if (i > 0 && row.dueOn().isBefore(today)) {
				errors.add(new FieldErrorDetail("schedule", "An installment cannot fall due before today."));
			}
			previous = row.dueOn();
		}
		if (amount != null && isMoney(amount) && agreedTotal != null && isMoney(agreedTotal)) {
			Row first = rows.get(0);
			if (first.amount() == null || first.amount().compareTo(amount) != 0) {
				errors.add(new FieldErrorDetail("schedule", "Row 1 must be the amount paid today."));
			}
			if (paidOn != null && !paidOn.equals(first.dueOn())) {
				errors.add(new FieldErrorDetail("schedule", "Row 1 must be dated on the payment date."));
			}
			if (amount.compareTo(agreedTotal) > 0) {
				errors.add(new FieldErrorDetail("amount", "The amount paid cannot be more than the total."));
			}
			if (sum.compareTo(agreedTotal) != 0) {
				errors.add(new FieldErrorDetail("schedule", "The rows must add up to the total."));
			}
			if (rows.size() == 1 && amount.compareTo(agreedTotal) != 0) {
				errors.add(new FieldErrorDetail("amount", "A single payment must cover the whole total."));
			}
			if (rows.size() > 1 && amount.compareTo(agreedTotal) >= 0) {
				errors.add(new FieldErrorDetail("amount", "An installment plan must leave something to pay later."));
			}
		}
		throwIfAny(errors);
	}

	/**
	 * The receipt figures after a payment of {@code payment} on top of {@code paidBefore}. Expects a plan that already passed
	 * {@link #validate}.
	 */
	static Position position(List<Row> rows, BigDecimal agreedTotal, BigDecimal paidBefore, BigDecimal payment) {
		BigDecimal paidAfter = paidBefore.add(payment);
		BigDecimal cumulative = ZERO;
		Integer touched = null;
		Row next = null;
		BigDecimal nextAmount = null;
		for (Row row : rows) {
			BigDecimal start = cumulative;
			cumulative = cumulative.add(row.amount());
			if (touched == null && cumulative.compareTo(paidBefore) > 0) {
				touched = row.seq();
			}
			if (next == null && cumulative.compareTo(paidAfter) > 0) {
				next = row;
				nextAmount = cumulative.subtract(paidAfter.max(start));
			}
		}
		return new Position(paidAfter, agreedTotal.subtract(paidAfter),
				next == null ? null : next.seq(), next == null ? null : next.dueOn(), nextAmount,
				touched == null ? rows.size() : touched, rows.size());
	}

	/** How many rows, counted from the first, the payments already cover in full. Covered rows are always a prefix. */
	static int coveredCount(List<Row> rows, BigDecimal paid) {
		int count = 0;
		BigDecimal cumulative = ZERO;
		for (Row row : rows) {
			cumulative = cumulative.add(row.amount());
			if (cumulative.compareTo(paid) > 0) {
				break;
			}
			count++;
		}
		return count;
	}

	/**
	 * Checks a schedule change (plan section 4). {@code rows} is the whole schedule after the change. Its first {@code frozen}
	 * rows are the ones the payments already cover and must not move; the rows after them are the unpaid ones, which must be
	 * dated today or later. Every problem found is listed.
	 *
	 * @throws ApiException with {@link ErrorCode#VALIDATION_FAILED} when the change breaks a rule
	 */
	static void validateChange(BigDecimal agreedTotal, List<Row> rows, int frozen, LocalDate today) {
		List<FieldErrorDetail> errors = new ArrayList<>();
		if (rows == null || rows.size() > MAX_ROWS || rows.size() <= frozen) {
			errors.add(new FieldErrorDetail("schedule", "After the paid rows, a plan needs between 1 and " + MAX_ROWS
					+ " rows in all, with at least one row still to pay."));
			throwIfAny(errors);
			return;
		}
		BigDecimal sum = ZERO;
		LocalDate previous = null;
		for (int i = 0; i < rows.size(); i++) {
			Row row = rows.get(i);
			if (!isMoney(row.amount()) || row.amount().signum() <= 0) {
				errors.add(new FieldErrorDetail("schedule", "Row " + (i + 1) + " must be more than zero, with at most two decimals."));
			}
			else {
				sum = sum.add(row.amount());
			}
			if (row.dueOn() == null) {
				errors.add(new FieldErrorDetail("schedule", "Row " + (i + 1) + " needs a date."));
				continue;
			}
			if (previous != null && !row.dueOn().isAfter(previous)) {
				errors.add(new FieldErrorDetail("schedule", "Each row's date must be later than the one before it."));
			}
			if (i >= frozen && row.dueOn().isBefore(today)) {
				errors.add(new FieldErrorDetail("schedule", "An installment cannot fall due before today."));
			}
			previous = row.dueOn();
		}
		if (agreedTotal != null && sum.compareTo(agreedTotal) != 0) {
			errors.add(new FieldErrorDetail("schedule", "The rows must add up to the total."));
		}
		throwIfAny(errors);
	}

	/** Whole taka with at most two decimals. A value with more decimals is refused, never rounded. */
	static boolean isMoney(BigDecimal value) {
		return value != null && value.stripTrailingZeros().scale() <= 2;
	}

	private static void throwIfAny(List<FieldErrorDetail> errors) {
		if (!errors.isEmpty()) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.", errors);
		}
	}

}
