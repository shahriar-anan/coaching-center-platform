package com.coachingcenter.api.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/** The amount maths and the form of money and quantity values (plan 4.4). */
class AccMoneyTest {

	@Test
	void countedIsQuantityTimesPriceRoundedHalfUpToThePaisa() {
		assertThat(AccMoney.counted(new BigDecimal("0.5"), new BigDecimal("0.05"))).isEqualByComparingTo("0.03");
		assertThat(AccMoney.counted(new BigDecimal("12"), new BigDecimal("1000"))).isEqualByComparingTo("12000.00");
		assertThat(AccMoney.counted(new BigDecimal("40"), new BigDecimal("50")).scale()).isEqualTo(2);
	}

	@Test
	void moneyIsReadFromAStringOrANumberWithTwoDecimals() {
		assertThat(AccMoney.parseMoney("1250", "amount").toPlainString()).isEqualTo("1250.00");
		assertThat(AccMoney.parseMoney(12.5, "amount").toPlainString()).isEqualTo("12.50");
		assertThat(AccMoney.parseMoney(" 1250.10 ", "amount").toPlainString()).isEqualTo("1250.10");
		assertThat(AccMoney.parseMoney("", "amount")).isNull();
		assertThat(AccMoney.parseMoney(null, "amount")).isNull();
	}

	@Test
	void moneyRefusesZeroNegativeMoreThanTwoDecimalsAndWords() {
		assertFieldError(() -> AccMoney.parseMoney("0", "amount"), "amount");
		assertFieldError(() -> AccMoney.parseMoney("-5", "amount"), "amount");
		assertFieldError(() -> AccMoney.parseMoney("1.005", "amount"), "amount");
		assertFieldError(() -> AccMoney.parseMoney("abc", "amount"), "amount");
	}

	@Test
	void quantityIsAboveZeroWithAtMostTwoDecimalsAndAtMost100000() {
		assertThat(AccMoney.parseQuantity("0.5", "quantity").stripTrailingZeros().toPlainString()).isEqualTo("0.5");
		assertThat(AccMoney.parseQuantity("100000", "quantity").toPlainString()).isEqualTo("100000.00");
		assertFieldError(() -> AccMoney.parseQuantity("100001", "quantity"), "quantity");
		assertFieldError(() -> AccMoney.parseQuantity("0", "quantity"), "quantity");
		assertFieldError(() -> AccMoney.parseQuantity("1.555", "quantity"), "quantity");
	}

	@Test
	void theAnswerForMoneyHasTwoDecimalsAndForQuantitiesNone() {
		assertThat(AccMoney.format(new BigDecimal("1250"))).isEqualTo("1250.00");
		assertThat(AccMoney.format(new BigDecimal("-500"))).isEqualTo("-500.00");
		assertThat(AccMoney.formatQuantity(new BigDecimal("40.00"))).isEqualTo("40");
		assertThat(AccMoney.formatQuantity(new BigDecimal("1.50"))).isEqualTo("1.5");
		assertThat(AccMoney.formatQuantity(new BigDecimal("100"))).isEqualTo("100");
	}

	private static void assertFieldError(ThrowableAssert.ThrowingCallable call, String field) {
		assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, exception -> {
			assertThat(exception.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
			assertThat(exception.status().value()).isEqualTo(400);
			assertThat(exception.details().toString()).contains("field=" + field);
		});
	}

}
