package com.coachingcenter.api.staffledger;

/**
 * The text a payment keeps about the account it was paid to (staff pay plan, section 2.7): "bKash 017••••4821" or
 * "Islami Bank ••••1234". Only the last four digits of a number are shown. The full number never leaves the payout account
 * row, and the snapshot is never written to a notification or to the audit log.
 */
public final class AccountSnapshot {

	static final String HIDDEN = "••••";

	static final int MAX = 200;

	private static final int MAX_BANK_NAME = 80;

	private AccountSnapshot() {
	}

	/**
	 * The snapshot for a payout account of {@code kind} (BANK, BKASH, NAGAD or ROCKET). A bank uses its name and the last four
	 * digits of its account number; a wallet uses its label and its number masked in the middle.
	 */
	public static String of(String kind, String bankName, String accountNumber, String walletNumber) {
		String text;
		if ("BANK".equals(kind)) {
			String name = bankName == null || bankName.isBlank() ? "Bank" : bankName.trim();
			if (name.length() > MAX_BANK_NAME) {
				name = name.substring(0, MAX_BANK_NAME);
			}
			text = name + " " + HIDDEN + lastFour(accountNumber);
		}
		else {
			text = label(kind) + " " + maskWallet(walletNumber);
		}
		return text.length() > MAX ? text.substring(0, MAX) : text;
	}

	/** The wallet's label as the staff see it. */
	static String label(String kind) {
		return switch (kind == null ? "" : kind) {
			case "BKASH" -> "bKash";
			case "NAGAD" -> "Nagad";
			case "ROCKET" -> "Rocket";
			default -> "Wallet";
		};
	}

	/** A wallet number with its first three and last four digits kept: "017••••4821". Short numbers show nothing but the dots. */
	static String maskWallet(String number) {
		String digits = number == null ? "" : number.trim();
		if (digits.length() < 8) {
			return HIDDEN;
		}
		return digits.substring(0, 3) + HIDDEN + digits.substring(digits.length() - 4);
	}

	/** The last four characters of a number, or nothing when the number is too short to show any of it. */
	static String lastFour(String number) {
		String value = number == null ? "" : number.trim();
		return value.length() < 5 ? "" : value.substring(value.length() - 4);
	}

}
