package com.coachingcenter.api.offline;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Constants and the matching rule of the monthly fee (plan sections 4.3 and 7). The matching is pure, so the rule is read in one
 * place: a payment is applied to the oldest open due first, and what is left over carries to the next one.
 */
final class OfflineFeeRules {

	static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

	/** The methods a payment can be taken by (as direct payments, C7). */
	static final Set<String> METHODS = Set.of("CASH", "BKASH", "NAGAD", "ROCKET", "BANK");

	/** A reference is 1 to 100 printable ASCII characters, as a direct payment's reference is. */
	static final Pattern REFERENCE = Pattern.compile("^[\\x20-\\x7E]{1,100}$");

	/** The longest a reason or a note may be. */
	static final int MAX_TEXT = 500;

	/** The admission states before onboarding, when no schedule exists yet. */
	static final Set<String> BEFORE_ONBOARDING = Set.of(OfflineAdmission.AWAITING_FORM, OfflineAdmission.SUBMITTED,
			OfflineAdmission.CHANGES_REQUESTED, OfflineAdmission.READY);

	static final String ENTITY_PAYMENT = "OFFLINE_FEE_PAYMENT";

	static final String AUDIT_RECORDED = "OFFLINE_FEE_PAYMENT_RECORDED";

	static final String AUDIT_APPROVED = "OFFLINE_FEE_PAYMENT_APPROVED";

	static final String AUDIT_REJECTED = "OFFLINE_FEE_PAYMENT_REJECTED";

	static final String AUDIT_VOIDED = "OFFLINE_FEE_PAYMENT_VOIDED";

	/** The staff page that lists payments waiting for approval. */
	static final String FEES_PAGE = "/staff/offline-fees";

	/** A due that still owes money: its id, its date, and what is still owed on it. */
	record Open(UUID dueId, LocalDate dueOn, int seq, BigDecimal owed) {
	}

	/** How much of a payment goes to one due. */
	record Share(UUID dueId, BigDecimal amount) {
	}

	/**
	 * Splits a payment over the open dues, oldest first. A due takes as much as it still owes, and the rest carries to the next.
	 * The shares add up to the amount, or to less when everything is already paid.
	 */
	static List<Share> allocate(BigDecimal amount, List<Open> oldestFirst) {
		List<Share> shares = new ArrayList<>();
		BigDecimal left = amount;
		for (Open due : oldestFirst) {
			if (left.signum() <= 0) {
				break;
			}
			if (due.owed().signum() <= 0) {
				continue;
			}
			BigDecimal take = left.min(due.owed());
			shares.add(new Share(due.dueId(), take));
			left = left.subtract(take);
		}
		return shares;
	}

	static BigDecimal total(List<Open> open) {
		BigDecimal total = BigDecimal.ZERO;
		for (Open due : open) {
			total = total.add(due.owed());
		}
		return total;
	}

	/** A money value with two decimals, as the schedule stores it. */
	static BigDecimal money(BigDecimal value) {
		return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
	}

	/** Trimmed text, or null when nothing is left. */
	static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	/** The receipt and the mail name a method in plain words. */
	static String methodLabel(String method) {
		return switch (method) {
			case "CASH" -> "Cash";
			case "BKASH" -> "bKash";
			case "NAGAD" -> "Nagad";
			case "ROCKET" -> "Rocket";
			case "BANK" -> "Bank transfer";
			default -> method;
		};
	}

	private OfflineFeeRules() {
	}

}
