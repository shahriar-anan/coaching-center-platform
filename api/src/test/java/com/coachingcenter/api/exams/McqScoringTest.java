package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class McqScoringTest {

	private static final UUID A = UUID.randomUUID();

	private static final UUID B = UUID.randomUUID();

	private static final UUID C = UUID.randomUUID();

	private static final UUID X = UUID.randomUUID();

	private static final BigDecimal QUARTER = new BigDecimal("0.25");

	@Test
	void aSingleAnswerExactMatchEarnsTheMarksAndAWrongChoiceLosesTheNegativeMark() {
		assertThat(McqScoring.questionScore(2, Set.of(A), Set.of(A), QUARTER)).isEqualByComparingTo("2");
		assertThat(McqScoring.questionScore(2, Set.of(A), Set.of(X), QUARTER)).isEqualByComparingTo("-0.25");
	}

	@Test
	void aBlankScoresZeroWhateverTheNegativeMark() {
		assertThat(McqScoring.questionScore(1, Set.of(A), Set.of(), QUARTER)).isEqualByComparingTo("0");
		assertThat(McqScoring.questionScore(1, Set.of(A), Set.of(), null)).isEqualByComparingTo("0");
	}

	@Test
	void aWrongChoiceWithNoNegativeMarkingScoresZero() {
		assertThat(McqScoring.questionScore(1, Set.of(A), Set.of(X), null)).isEqualByComparingTo("0");
		assertThat(McqScoring.questionScore(1, Set.of(A), Set.of(X), BigDecimal.ZERO)).isEqualByComparingTo("0");
	}

	@Test
	void aMultiAnswerEarnsMarksOnlyForAnExactMatch() {
		Set<UUID> correct = Set.of(A, B);

		assertThat(McqScoring.questionScore(3, correct, Set.of(A, B), QUARTER)).isEqualByComparingTo("3");
		assertThat(McqScoring.questionScore(3, correct, Set.of(A), QUARTER)).isEqualByComparingTo("-0.25");
		assertThat(McqScoring.questionScore(3, correct, Set.of(A, B, C), QUARTER)).isEqualByComparingTo("-0.25");
		assertThat(McqScoring.questionScore(3, correct, Set.of(), QUARTER)).isEqualByComparingTo("0");
	}

	@Test
	void aTotalCanBeNegativeAndAMissingChoiceScoresAsBlank() {
		List<McqScoring.QuestionKey> key = List.of(
				new McqScoring.QuestionKey(UUID.randomUUID(), 1, Set.of(A)),
				new McqScoring.QuestionKey(UUID.randomUUID(), 1, Set.of(B)));
		UUID first = key.get(0).questionId();

		assertThat(McqScoring.total(key, Map.of(first, Set.of(X)), QUARTER)).isEqualByComparingTo("-0.25");
		assertThat(McqScoring.total(key, Map.of(first, Set.of(A)), QUARTER)).isEqualByComparingTo("1");
		assertThat(McqScoring.total(key, Map.of(), QUARTER)).isEqualByComparingTo("0");
	}

	@Test
	void theMaximumIsTheSumOfTheMarks() {
		List<McqScoring.QuestionKey> key = List.of(
				new McqScoring.QuestionKey(UUID.randomUUID(), 2, Set.of(A)),
				new McqScoring.QuestionKey(UUID.randomUUID(), 3, Set.of(B)));

		assertThat(McqScoring.maximum(key)).isEqualTo(5);
	}

	@Test
	void aScoreAtThePassPercentPasses() {
		assertThat(McqScoring.passed(new BigDecimal("40"), 100, 40)).isTrue();
		assertThat(McqScoring.passed(new BigDecimal("39.99"), 100, 40)).isFalse();
		assertThat(McqScoring.passed(new BigDecimal("-1"), 100, null)).isFalse();
	}

	@Test
	void aMissingPassPercentIsNoBar() {
		assertThat(McqScoring.passed(BigDecimal.ZERO, 10, null)).isTrue();
	}

	@Test
	void plainShowsAWholeMarkAsAWholeNumberAndKeepsAFraction() {
		assertThat(McqScoring.plain(new BigDecimal("12.00")).toPlainString()).isEqualTo("12");
		assertThat(McqScoring.plain(new BigDecimal("10.00")).toPlainString()).isEqualTo("10");
		assertThat(McqScoring.plain(new BigDecimal("12.50")).toPlainString()).isEqualTo("12.5");
		assertThat(McqScoring.plain(null)).isNull();
	}

}
