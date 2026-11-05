package com.coachingcenter.api.staffledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The balance and the overdue split for one person's ledger (staff pay plan, section 3). No database and no clock: the
 * caller passes the lines and today's date in Dhaka.
 *
 * <p>
 * Balance = live MARKING + SALARY + ADJUSTMENT amounts minus live PAYMENT amounts. A voided line is left out.
 *
 * <p>
 * Overdue is worked out oldest first. Payments settle the oldest dues first; a negative adjustment counts as money already
 * settled, so it settles the oldest dues too. Whatever is still uncovered from a due whose date is before today is overdue.
 */
public final class StaffPayCalculator {

	private static final BigDecimal ZERO = BigDecimal.ZERO;

	private StaffPayCalculator() {
	}

	/** One money line as the calculation reads it. {@code voided} lines are kept for the list but count for nothing. */
	public record Line(UUID id, LedgerKind kind, BigDecimal amount, LocalDate dueOn, Instant createdAt, boolean voided) {
	}

	/**
	 * {@code charged} is the live dues and adjustments; {@code paid} the live payments; {@code balance} is charged minus paid
	 * (negative when the person has been paid in advance). {@code owedNow} is the balance, never below zero; {@code overdue}
	 * is the part of {@code owedNow} whose due date has passed.
	 */
	public record Totals(BigDecimal charged, BigDecimal paid, BigDecimal balance, BigDecimal owedNow, BigDecimal overdue) {
	}

	public static Totals totals(List<Line> lines, LocalDate today) {
		Tally tally = tally(lines);
		BigDecimal balance = tally.charged().subtract(tally.paid());
		BigDecimal owedNow = balance.max(ZERO);
		BigDecimal overdue = ZERO;
		for (Uncovered item : allocate(tally.dues(), tally.paid().add(tally.advances()))) {
			if (item.amount().signum() > 0 && item.line().dueOn().isBefore(today)) {
				overdue = overdue.add(item.amount());
			}
		}
		return new Totals(scale(tally.charged()), scale(tally.paid()), scale(balance), scale(owedNow), scale(overdue));
	}

	/**
	 * What is still uncovered on each live due line (MARKING, SALARY and a positive ADJUSTMENT), oldest first, after the
	 * payments and the negative adjustments have settled the oldest dues. Zero means the line is settled. The salary job and
	 * the salary months read this; {@link #totals} uses the same rule.
	 */
	public static List<Uncovered> uncovered(List<Line> lines) {
		Tally tally = tally(lines);
		return allocate(tally.dues(), tally.paid().add(tally.advances()));
	}

	/** One due line and the amount still uncovered on it. */
	public record Uncovered(Line line, BigDecimal amount) {
	}

	/** The live lines summed: charged, paid, advances (negative adjustments as a positive amount) and the due lines. */
	private record Tally(BigDecimal charged, BigDecimal paid, BigDecimal advances, List<Line> dues) {
	}

	private static Tally tally(List<Line> lines) {
		BigDecimal charged = ZERO;
		BigDecimal paid = ZERO;
		BigDecimal advances = ZERO;
		List<Line> dues = new ArrayList<>();
		for (Line line : lines) {
			if (line.voided()) {
				continue;
			}
			switch (line.kind()) {
				case PAYMENT -> paid = paid.add(line.amount());
				case MARKING, SALARY -> {
					charged = charged.add(line.amount());
					dues.add(line);
				}
				case ADJUSTMENT -> {
					charged = charged.add(line.amount());
					if (line.amount().signum() > 0) {
						dues.add(line);
					}
					else {
						advances = advances.add(line.amount().negate());
					}
				}
			}
		}
		return new Tally(charged, paid, advances, dues);
	}

	/** Credit settles the dues oldest first; returns what is left on each due, in that order. */
	private static List<Uncovered> allocate(List<Line> dues, BigDecimal credit) {
		List<Line> sorted = new ArrayList<>(dues);
		sorted.sort(Comparator.comparing(Line::dueOn).thenComparing(Line::createdAt).thenComparing(Line::id));
		List<Uncovered> result = new ArrayList<>();
		BigDecimal left = credit;
		for (Line due : sorted) {
			BigDecimal applied = left.min(due.amount());
			left = left.subtract(applied);
			result.add(new Uncovered(due, due.amount().subtract(applied)));
		}
		return result;
	}

	/** Money is kept to two places. */
	static BigDecimal scale(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP);
	}

}
