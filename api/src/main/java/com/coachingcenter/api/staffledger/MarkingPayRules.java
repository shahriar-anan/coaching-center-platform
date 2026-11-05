package com.coachingcenter.api.staffledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.coachingcenter.api.staffledger.StaffPayCalculator.Line;

/**
 * The rules for instructor marking pay (staff pay plan, section 5, phase 4), with no database and no clock. The earnings job
 * and the marking views both use these, so a sheet is judged the same way everywhere.
 *
 * <ul>
 * <li>P1: a sheet is one written, submitted script. An automatic zero (FAILED_TO_SUBMIT) is not a sheet. A retake is its own
 * participant row, so it earns its own line.</li>
 * <li>P2: a sheet earns once. The ledger keeps one MARKING line per participant.</li>
 * <li>P3: the marker whose mark was first published earns it, and only an INSTRUCTOR earns. A mark by the Master Admin or a
 * System Admin earns nothing.</li>
 * <li>P4: the rate is the instructor's override when there is one, else the default. The line keeps the rate it was earned at.</li>
 * </ul>
 */
public final class MarkingPayRules {

	static final String INSTRUCTOR = "INSTRUCTOR";

	static final String SUBMITTED = "SUBMITTED";

	static final String WRITTEN = "WRITTEN";

	/** The day a publish counts for. Money dates are Dhaka dates, as the rest of the ledger. */
	static final ZoneId DHAKA = StaffLedgerService.DHAKA;

	private MarkingPayRules() {
	}

	/** The rate for one sheet: the instructor's override when set, else the default. */
	public static BigDecimal rate(BigDecimal override, BigDecimal defaultRate) {
		return override != null ? override : defaultRate;
	}

	/**
	 * Whether a published sheet earns a line (P1 to P3). The evaluator's role decides it: only an INSTRUCTOR earns.
	 *
	 * @param evaluatorRole the role of {@code evaluated_by}, or null when nobody marked it
	 * @param status the participant's status
	 * @param examType the exam's type
	 * @param publishedAt when the mark was published, or null when it is not
	 * @param deleted whether the participant row is soft deleted
	 */
	public static boolean earnsSheet(String evaluatorRole, String status, String examType, Instant publishedAt, boolean deleted) {
		return INSTRUCTOR.equals(evaluatorRole)
				&& SUBMITTED.equals(status)
				&& WRITTEN.equals(examType)
				&& publishedAt != null
				&& !deleted;
	}

	/** The Dhaka date a publish falls on. */
	public static LocalDate publishDay(Instant publishedAt) {
		return publishedAt.atZone(DHAKA).toLocalDate();
	}

	/**
	 * The oldest marking that is not yet covered by a payment, or empty when every marking is paid. Payments and negative
	 * adjustments settle the oldest dues first, the same order {@link StaffPayCalculator} uses. Voided lines are left out.
	 */
	public static Optional<LocalDate> oldestUnpaidMarkingDue(List<Line> lines) {
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
		for (Line due : dues) {
			BigDecimal applied = credit.min(due.amount());
			credit = credit.subtract(applied);
			boolean uncovered = due.amount().subtract(applied).signum() > 0;
			if (uncovered && due.kind() == LedgerKind.MARKING) {
				return Optional.of(due.dueOn());
			}
		}
		return Optional.empty();
	}

	/** True when the oldest unpaid marking is older than {@code reminderDays} on {@code today}. */
	public static boolean reminderDue(LocalDate oldestDue, LocalDate today, int reminderDays) {
		return ChronoUnit.DAYS.between(oldestDue, today) > reminderDays;
	}

	/** The ISO week a reminder is keyed to, for example "2026-W45". A reminder goes at most once in each week. */
	public static String isoWeek(LocalDate day) {
		return "%d-W%02d".formatted(day.get(IsoFields.WEEK_BASED_YEAR), day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
	}

}
