package com.coachingcenter.api.staffledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.coachingcenter.api.staffledger.StaffPayCalculator.Line;

/**
 * The part of each due that is still unpaid, line by line (staff pay plan, section 3). It applies the same oldest-first rule
 * as {@link StaffPayCalculator}: payments and negative adjustments settle the oldest dues first. The overview needs the
 * breakdown by kind, which the totals do not give. The sum of the uncovered amounts equals the calculator's owed-now figure,
 * and the sum of those whose due date has passed equals its overdue figure; a test holds both.
 */
public final class StaffPayOpenDues {

	private StaffPayOpenDues() {
	}

	/** One due with an unpaid part. {@code uncovered} is the part still owed; it is above zero. */
	public record OpenDue(LedgerKind kind, LocalDate dueOn, BigDecimal uncovered) {
	}

	/**
	 * The uncovered part of every due of one person. Voided lines are left out. Dues settled in full are not listed.
	 * The list is oldest first.
	 */
	public static List<OpenDue> of(List<Line> lines) {
		BigDecimal paid = BigDecimal.ZERO;
		BigDecimal advances = BigDecimal.ZERO;
		List<Line> dues = new ArrayList<>();
		for (Line line : lines) {
			if (line.voided()) {
				continue;
			}
			switch (line.kind()) {
				case PAYMENT -> paid = paid.add(line.amount());
				case MARKING, SALARY -> dues.add(line);
				case ADJUSTMENT -> {
					if (line.amount().signum() > 0) {
						dues.add(line);
					}
					else {
						advances = advances.add(line.amount().negate());
					}
				}
			}
		}
		dues.sort(Comparator.comparing(Line::dueOn).thenComparing(Line::createdAt).thenComparing(Line::id));
		BigDecimal credit = paid.add(advances);
		List<OpenDue> open = new ArrayList<>();
		for (Line due : dues) {
			BigDecimal applied = credit.min(due.amount());
			credit = credit.subtract(applied);
			BigDecimal uncovered = due.amount().subtract(applied);
			if (uncovered.signum() > 0) {
				open.add(new OpenDue(due.kind(), due.dueOn(), uncovered.setScale(2, RoundingMode.HALF_UP)));
			}
		}
		return open;
	}

	/** The sum of the uncovered parts, whatever their kind. */
	public static BigDecimal total(List<OpenDue> open) {
		return open.stream().map(OpenDue::uncovered).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	/** The sum of the uncovered parts whose due date is before {@code today}: the overdue part. */
	public static BigDecimal overdue(List<OpenDue> open, LocalDate today) {
		return open.stream()
			.filter(due -> due.dueOn().isBefore(today))
			.map(OpenDue::uncovered)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	/** The sum of the uncovered parts of one kind. */
	public static BigDecimal ofKind(List<OpenDue> open, LedgerKind kind) {
		return open.stream()
			.filter(due -> due.kind() == kind)
			.map(OpenDue::uncovered)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

}
