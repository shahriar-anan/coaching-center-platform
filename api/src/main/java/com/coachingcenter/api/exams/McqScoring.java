package com.coachingcenter.api.exams;

import java.math.BigDecimal;

final class McqScoring {

	private McqScoring() {
	}

	/**
	 * A score as the API shows it. A whole mark reads as a whole number, so 12.00 is 12 and the JSON stays what it was
	 * before scores had decimals. A fraction keeps its digits.
	 */
	static BigDecimal plain(BigDecimal score) {
		if (score == null) {
			return null;
		}
		BigDecimal stripped = score.stripTrailingZeros();
		return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
	}

}
