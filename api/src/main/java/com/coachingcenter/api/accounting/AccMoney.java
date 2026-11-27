package com.coachingcenter.api.accounting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.coachingcenter.api.common.error.FieldErrorDetail;

/**
 * Money and quantities of the ledger (plan 4.4). Money is answered with two decimals ("1250.00"); quantities without trailing zeros
 * ("40", "1.5"). The caps of plan 4.4 (amount, unit price) are checked by AccRules as refusals; this class checks the form of a value.
 */
public final class AccMoney {

	/** The most one entry may hold: ৳1,00,00,000. */
	public static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000.00");

	/** The most one unit price may be: ৳10,00,000. */
	public static final BigDecimal MAX_UNIT_PRICE = new BigDecimal("1000000.00");

	/** The most one quantity may be: 100,000. */
	public static final BigDecimal MAX_QUANTITY = new BigDecimal("100000");

	private AccMoney() {
	}

	/** Quantity times unit price, rounded half up to the paisa (the counted amount). */
	public static BigDecimal counted(BigDecimal quantity, BigDecimal unitPrice) {
		return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
	}

	/**
	 * A money value sent as a JSON string or number. Null or blank gives null. More than 0, with at most 2 decimals; otherwise a field
	 * error.
	 */
	public static BigDecimal parseMoney(Object value, String field) {
		BigDecimal number = number(value, field);
		if (number == null) {
			return null;
		}
		if (number.signum() <= 0) {
			throw AccRefusals.validation(field, "Enter an amount above 0.");
		}
		if (decimals(number) > 2) {
			throw AccRefusals.validation(field, "Use at most 2 decimals.");
		}
		return number.setScale(2, RoundingMode.HALF_UP);
	}

	/**
	 * A quantity sent as a JSON string or number. Null or blank gives null. More than 0, with at most 2 decimals, and at most 100,000.
	 */
	public static BigDecimal parseQuantity(Object value, String field) {
		BigDecimal number = number(value, field);
		if (number == null) {
			return null;
		}
		if (number.signum() <= 0) {
			throw AccRefusals.validation(field, "Enter a quantity above 0.");
		}
		if (decimals(number) > 2) {
			throw AccRefusals.validation(field, "Use at most 2 decimals.");
		}
		if (number.compareTo(MAX_QUANTITY) > 0) {
			throw AccRefusals.validation(field, "Enter a quantity of at most 100,000.");
		}
		return number.setScale(2, RoundingMode.HALF_UP);
	}

	/** Money as the API answers it: two decimals, for example "1250.00". */
	public static String format(BigDecimal money) {
		return money.setScale(2, RoundingMode.HALF_UP).toPlainString();
	}

	/** A quantity as the API answers it: no trailing zeros, for example "40" or "1.5". */
	public static String formatQuantity(BigDecimal quantity) {
		return quantity.stripTrailingZeros().toPlainString();
	}

	private static BigDecimal number(Object value, String field) {
		if (value == null) {
			return null;
		}
		if (value instanceof Number) {
			return new BigDecimal(value.toString());
		}
		String text = value.toString().trim();
		if (text.isEmpty()) {
			return null;
		}
		try {
			return new BigDecimal(text);
		}
		catch (NumberFormatException exception) {
			throw AccRefusals.validation(List.of(new FieldErrorDetail(field, "Enter a number.")));
		}
	}

	/** The count of decimals that matter: trailing zeros do not count. */
	private static int decimals(BigDecimal number) {
		return Math.max(0, number.stripTrailingZeros().scale());
	}

}
