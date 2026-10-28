package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * MCQ marks (NR-01, R8 and P-a). Pure: the caller supplies the key and the choices. A multi-answer question earns its marks
 * only for an exact match; a wrong, non-empty choice loses the negative mark; a blank scores zero. A negative total is allowed.
 */
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

	/** One question's mark: {@code marks} for an exact match, {@code -negativeMarks} for a wrong choice, zero for a blank. */
	static BigDecimal questionScore(int marks, Set<UUID> correct, Set<UUID> chosen, BigDecimal negativeMarks) {
		if (chosen.isEmpty()) {
			return BigDecimal.ZERO;
		}
		if (chosen.equals(correct)) {
			return BigDecimal.valueOf(marks);
		}
		return negativeMarks == null ? BigDecimal.ZERO : negativeMarks.negate();
	}

	/** The paper's total. {@code chosenByQuestion} may leave out a question, which then scores as blank. */
	static BigDecimal total(List<QuestionKey> key, Map<UUID, Set<UUID>> chosenByQuestion, BigDecimal negativeMarks) {
		BigDecimal total = BigDecimal.ZERO;
		for (QuestionKey question : key) {
			total = total.add(questionScore(question.marks(), question.correctOptionIds(),
					chosenByQuestion.getOrDefault(question.questionId(), Set.of()), negativeMarks));
		}
		return total;
	}

	/** The highest mark the paper can give: the sum of its marks. */
	static int maximum(List<QuestionKey> key) {
		int sum = 0;
		for (QuestionKey question : key) {
			sum += question.marks();
		}
		return sum;
	}

	/** Pass when {@code score / maximum} reaches the pass percent. A missing pass percent means no bar, as it always has. */
	static boolean passed(BigDecimal score, int maximum, Integer passPercent) {
		int percent = passPercent == null ? 0 : passPercent;
		return score.multiply(BigDecimal.valueOf(100)).compareTo(BigDecimal.valueOf((long) percent * maximum)) >= 0;
	}

	record QuestionKey(UUID questionId, int marks, Set<UUID> correctOptionIds) {
	}

}
