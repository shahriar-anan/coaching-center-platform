package com.coachingcenter.api.staffpay;

import java.util.Locale;

import org.springframework.http.HttpStatus;

import com.coachingcenter.api.auth.PhoneNumbers;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.staffpay.PayoutAccountService.PayoutAccountRequest;

/**
 * The rules for one payout account (staff pay plan, phase 2): what a bank or wallet entry must hold, and how a number is
 * shown in a list. No database and no clock, so the rules are checked on their own.
 */
final class PayoutAccountRules {

	/** A person keeps at most this many live payout accounts. */
	static final int MAX_ACCOUNTS = 5;

	/** The mask a list shows: the last four digits of the number. The owner's edit form shows the full number. */
	private static final String MASK_PREFIX = "•••• ";

	private static final int NAME_MIN = 2;

	private static final int NAME_MAX = 120;

	private static final int ACCOUNT_MIN = 6;

	private static final int ACCOUNT_MAX = 20;

	private static final int ROUTING_LENGTH = 9;

	private PayoutAccountRules() {
	}

	/** The checked, stored form of one entry. Fields the kind does not use are null. */
	record Fields(PayoutAccountKind kind, String accountName, String accountNumber, String bankName, String branch,
			String routingNumber, String walletNumber) {
	}

	/**
	 * Checks an entry and returns its stored form. A bank entry needs all five bank fields and no wallet; a wallet needs a
	 * Bangladesh mobile number (the same rule as a phone number) and no bank fields.
	 */
	static Fields validate(PayoutAccountRequest request) {
		if (request == null) {
			throw invalid("Request validation failed.");
		}
		PayoutAccountKind kind = kind(request.kind());
		if (kind.isWallet()) {
			return new Fields(kind, null, null, null, null, null, wallet(request.walletNumber()));
		}
		return new Fields(kind, text(request.accountName(), NAME_MIN, NAME_MAX, "Account name is required."),
				digits(request.accountNumber(), ACCOUNT_MIN, ACCOUNT_MAX, "Account number must be 6 to 20 digits."),
				text(request.bankName(), NAME_MIN, NAME_MAX, "Bank name is required."),
				text(request.branch(), NAME_MIN, NAME_MAX, "Branch is required."),
				digits(request.routingNumber(), ROUTING_LENGTH, ROUTING_LENGTH, "Routing number must be 9 digits."), null);
	}

	/** The account type, in any letter case. An unknown or missing type is refused. */
	static PayoutAccountKind kind(String raw) {
		if (raw == null || raw.isBlank()) {
			throw invalid("Account type is required.");
		}
		try {
			return PayoutAccountKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException unknown) {
			throw invalid("Account type is not known.");
		}
	}

	/** A wallet number in the Bangladesh mobile form, stored as +8801XXXXXXXXX. */
	static String wallet(String raw) {
		if (raw == null || raw.isBlank()) {
			throw invalid("Wallet number is required.");
		}
		try {
			return PhoneNumbers.normalize(raw);
		}
		catch (ApiException notMobile) {
			throw invalid("Wallet number is invalid.");
		}
	}

	/** The masked form for a list: the last four digits behind a fixed prefix. A null number shows as the prefix alone. */
	static String mask(String number) {
		if (number == null || number.isEmpty()) {
			return MASK_PREFIX.trim();
		}
		String last = number.length() <= 4 ? number : number.substring(number.length() - 4);
		return MASK_PREFIX + last;
	}

	private static String text(String raw, int min, int max, String message) {
		String trimmed = raw == null ? "" : raw.trim();
		if (trimmed.length() < min || trimmed.length() > max) {
			throw invalid(message);
		}
		return trimmed;
	}

	/** Spaces and dashes are dropped; what is left must be ASCII digits within the length. */
	private static String digits(String raw, int min, int max, String message) {
		String compact = raw == null ? "" : raw.replaceAll("[\s-]", "");
		if (compact.length() < min || compact.length() > max || !compact.matches("[0-9]+")) {
			throw invalid(message);
		}
		return compact;
	}

	private static ApiException invalid(String message) {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, message);
	}

}
