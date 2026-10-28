package com.coachingcenter.api.exams;

import java.math.BigDecimal;

/**
 * What a student is shown for an exam or a package. {@code discountEndsAt} is an ISO-8601 instant. {@code moduleOnly} is
 * true when the price buys a package, not one exam.
 */
record PriceView(BigDecimal price, Integer discountPercent, String discountEndsAt, BigDecimal effectivePrice,
		boolean free, boolean moduleOnly) {
}
