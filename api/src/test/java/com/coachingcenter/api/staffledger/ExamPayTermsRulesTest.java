package com.coachingcenter.api.staffledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/** The pay-per-exam rules (staff pay plan, phase 8, decision P18) with no database: the rate order and the rate's shape. */
class ExamPayTermsRulesTest {

	private static final BigDecimal DEFAULT = new BigDecimal("50.00");

	private static final BigDecimal INSTRUCTOR = new BigDecimal("60.00");

	private static final BigDecimal EXAM = new BigDecimal("70.00");

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Test
	void theExamsOwnRateBeatsTheInstructorsAndTheDefault() {
		assertThat(ExamPayTermsService.effectiveRate(EXAM, INSTRUCTOR, DEFAULT)).isEqualByComparingTo("70.00");
	}

	@Test
	void theInstructorsRateBeatsTheDefaultWhenTheExamHasNoRateOfItsOwn() {
		assertThat(ExamPayTermsService.effectiveRate(null, INSTRUCTOR, DEFAULT)).isEqualByComparingTo("60.00");
	}

	@Test
	void theDefaultIsUsedWhenNeitherTheExamNorTheInstructorHasARate() {
		assertThat(ExamPayTermsService.effectiveRate(null, null, DEFAULT)).isEqualByComparingTo("50.00");
	}

	@Test
	void aRateIsAPositiveNumberWithAtMostTwoDecimalsAndTenDigitsBeforeThePoint() throws Exception {
		assertThat(ExamPayTermsService.rateOf(JSON.readTree("60"))).isEqualByComparingTo("60.00");
		assertThat(ExamPayTermsService.rateOf(JSON.readTree("12.5"))).isEqualByComparingTo("12.50");
		assertThat(ExamPayTermsService.rateOf(JSON.readTree("12.50"))).isEqualByComparingTo("12.50");
		assertThat(ExamPayTermsService.rateOf(JSON.readTree("1234567890"))).isEqualByComparingTo("1234567890.00");
	}

	@Test
	void aRateThatBreaksTheRulesIsRefused() throws Exception {
		assertThat(ExamPayTermsService.rateOf(JSON.readTree("0"))).isNull();
		assertThat(ExamPayTermsService.rateOf(JSON.readTree("-5"))).isNull();
		assertThat(ExamPayTermsService.rateOf(JSON.readTree("50.005"))).isNull();
		assertThat(ExamPayTermsService.rateOf(JSON.readTree("12345678901"))).isNull();
		assertThat(ExamPayTermsService.rateOf(JSON.readTree("\"60\""))).isNull();
		assertThat(ExamPayTermsService.rateOf(JSON.readTree("null"))).isNull();
		assertThat(ExamPayTermsService.rateOf(null)).isNull();
	}

}
