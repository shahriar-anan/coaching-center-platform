package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;

class ExamPricingTest {

	private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");

	private static final Instant DISCOUNT_END = Instant.parse("2026-11-01T00:00:00Z");

	@Test
	void withoutADiscountTheEffectivePriceIsThePrice() {
		assertThat(ExamPricing.effective(new BigDecimal("199"), null, null, NOW)).isEqualByComparingTo("199");
	}

	@Test
	void aDiscountWithNoEndIsAlwaysActiveAndRoundsHalfUpToTwoPlaces() {
		assertThat(ExamPricing.effective(new BigDecimal("199"), 33, null, NOW)).isEqualByComparingTo("133.33");
		assertThat(ExamPricing.effective(new BigDecimal("199"), 33, null, NOW)).hasToString("133.33");
	}

	@Test
	void aDiscountIsActiveBeforeItsEndAndInactiveAtIt() {
		assertThat(ExamPricing.effective(new BigDecimal("200"), 50, DISCOUNT_END, DISCOUNT_END.minusSeconds(1)))
			.isEqualByComparingTo("100.00");
		assertThat(ExamPricing.effective(new BigDecimal("200"), 50, DISCOUNT_END, DISCOUNT_END))
			.isEqualByComparingTo("200");
	}

	@Test
	void noPriceGivesNoPrice() {
		assertThat(ExamPricing.effective(null, 33, null, NOW)).isNull();
	}

	@Test
	void aFreeExamShowsNoEffectivePrice() {
		LiveExam exam = new LiveExam();
		exam.setFree(true);

		PriceView view = ExamPricing.view(exam, NOW, false);

		assertThat(view.free()).isTrue();
		assertThat(view.effectivePrice()).isNull();
		assertThat(view.moduleOnly()).isFalse();
	}

	@Test
	void aPackageViewIsModuleOnlyAndShowsTheDiscountedPrice() {
		LiveExamModule module = new LiveExamModule();
		module.setPrice(new BigDecimal("300"));
		module.setDiscountPercent(10);
		module.setDiscountEndsAt(DISCOUNT_END);

		PriceView view = ExamPricing.view(module, NOW);

		assertThat(view.moduleOnly()).isTrue();
		assertThat(view.price()).isEqualByComparingTo("300");
		assertThat(view.effectivePrice()).isEqualByComparingTo("270.00");
		assertThat(view.discountEndsAt()).isEqualTo(DISCOUNT_END.toString());
	}

	@Test
	void validateRefusesAPriceNoOrderCanCarry() {
		assertInvalidFields(() -> ExamPricing.validate(new BigDecimal("100"), true, null, null), "price");
		assertInvalidFields(() -> ExamPricing.validate(BigDecimal.ZERO, false, null, null), "price");
		assertInvalidFields(() -> ExamPricing.validate(new BigDecimal("100"), false, 0, null), "discountPercent");
		assertInvalidFields(() -> ExamPricing.validate(new BigDecimal("100"), false, 100, null), "discountPercent");
		assertInvalidFields(() -> ExamPricing.validate(new BigDecimal("100"), false, null, DISCOUNT_END), "discountEndsAt");
	}

	@Test
	void validateAcceptsAFreeExamAndAPricedOneWithAValidDiscount() {
		assertThatCode(() -> ExamPricing.validate(null, true, null, null)).doesNotThrowAnyException();
		assertThatCode(() -> ExamPricing.validate(new BigDecimal("199"), false, 33, DISCOUNT_END))
			.doesNotThrowAnyException();
	}

	private static void assertInvalidFields(Runnable action, String field) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, exception -> {
			assertThat(exception.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
			assertThat((List<?>) exception.details()).extracting(detail -> ((FieldErrorDetail) detail).field())
				.contains(field);
		});
	}

}
