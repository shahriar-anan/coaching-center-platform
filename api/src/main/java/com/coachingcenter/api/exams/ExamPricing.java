package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;

/** Price, percent discount (with an optional end) and the price a student pays at an instant (NR-01, B5 to B7). */
final class ExamPricing {

	private static final int MAX_DISCOUNT = 99;

	private ExamPricing() {
	}

	/**
	 * The price after an active discount, rounded half-up to two places. A discount is active before its end, and with no
	 * end it is always active. No price gives no price.
	 */
	static BigDecimal effective(BigDecimal price, Integer discountPercent, Instant discountEndsAt, Instant now) {
		if (price == null) {
			return null;
		}
		if (!discountActive(discountPercent, discountEndsAt, now)) {
			return price;
		}
		return price.multiply(BigDecimal.valueOf(100 - discountPercent)).divide(BigDecimal.valueOf(100), 2,
				RoundingMode.HALF_UP);
	}

	static PriceView view(LiveExam exam, Instant now, boolean moduleOnly) {
		return view(exam.getPrice(), exam.isFree(), exam.getDiscountPercent(), exam.getDiscountEndsAt(), now, moduleOnly);
	}

	/** A package's price. Its view is always {@code moduleOnly}: the package is what is bought. */
	static PriceView view(LiveExamModule module, Instant now) {
		return view(module.getPrice(), false, module.getDiscountPercent(), module.getDiscountEndsAt(), now, true);
	}

	/**
	 * A discount end that is set or changed must not be in the past. An end that is unchanged may stay, even after it has
	 * run out, so saving something else on an old discount is not refused.
	 */
	static void requireNotPast(Instant next, Instant previous, Instant now) {
		if (next != null && !next.equals(previous) && next.isBefore(now)) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.",
					List.of(new FieldErrorDetail("discountEndsAt", "The discount end cannot be in the past.")));
		}
	}

	/**
	 * Rejects a price that no order could carry: a free item with a price or a discount, a price that is not positive, a
	 * discount outside 1 to 99, or a discount end with no discount.
	 */
	static void validate(BigDecimal price, boolean free, Integer discountPercent, Instant discountEndsAt) {
		List<FieldErrorDetail> errors = new ArrayList<>();
		if (free && (price != null || discountPercent != null)) {
			errors.add(new FieldErrorDetail("price", "A free exam has no price or discount."));
		}
		if (price != null && price.signum() <= 0) {
			errors.add(new FieldErrorDetail("price", "The price must be more than zero."));
		}
		if (discountPercent != null && (discountPercent < 1 || discountPercent > MAX_DISCOUNT)) {
			errors.add(new FieldErrorDetail("discountPercent", "The discount must be between 1 and 99 percent."));
		}
		if (discountEndsAt != null && discountPercent == null) {
			errors.add(new FieldErrorDetail("discountEndsAt", "A discount end needs a discount."));
		}
		if (!errors.isEmpty()) {
			// ApiException carries one details object; the list of field errors is that object (see f-done).
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.",
					errors);
		}
	}

	private static PriceView view(BigDecimal price, boolean free, Integer discountPercent, Instant discountEndsAt,
			Instant now, boolean moduleOnly) {
		return new PriceView(price, discountPercent, discountEndsAt == null ? null : discountEndsAt.toString(),
				free ? null : effective(price, discountPercent, discountEndsAt, now), free, moduleOnly);
	}

	private static boolean discountActive(Integer discountPercent, Instant discountEndsAt, Instant now) {
		return discountPercent != null && (discountEndsAt == null || now.isBefore(discountEndsAt));
	}

}
