package com.coachingcenter.api.staffpay;

/**
 * How a payout account is paid into (staff pay plan, section 3). The names match the table's check constraint. {@code BANK}
 * takes the five bank fields; the three wallets take a mobile wallet number.
 */
public enum PayoutAccountKind {

	BANK,

	BKASH,

	NAGAD,

	ROCKET;

	public boolean isWallet() {
		return this != BANK;
	}

}
