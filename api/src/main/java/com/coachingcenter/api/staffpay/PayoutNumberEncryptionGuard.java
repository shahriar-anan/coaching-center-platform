package com.coachingcenter.api.staffpay;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Refuses to start when the stored payout numbers cannot be read with the configured key (staff pay plan, phase 9): a sealed
 * row exists and no key is set, or a row was sealed with another key version. Starting anyway would show empty numbers, or
 * would write new numbers the old key cannot read. Runs before the backfill.
 */
@Component
@Order(1)
public class PayoutNumberEncryptionGuard implements ApplicationRunner {

	private final JdbcTemplate jdbc;

	private final PayoutNumberCipher cipher;

	public PayoutNumberEncryptionGuard(JdbcTemplate jdbc, PayoutNumberCipher cipher) {
		this.jdbc = jdbc;
		this.cipher = cipher;
	}

	@Override
	public void run(ApplicationArguments args) {
		Long sealed = jdbc.queryForObject("""
				select count(*) from staff_payout_accounts
				where account_number_enc is not null or routing_number_enc is not null or wallet_number_enc is not null
				""", Long.class);
		if (!cipher.enabled()) {
			if (sealed != null && sealed > 0) {
				throw new IllegalStateException("PAYOUT_ENCRYPTION_KEY is not set, but " + sealed
						+ " payout account row(s) hold encrypted numbers. Set the key before starting.");
			}
			return;
		}
		Long otherVersion = jdbc.queryForObject("""
				select count(*) from staff_payout_accounts where key_version is not null and key_version <> ?
				""", Long.class, cipher.keyVersion());
		if (otherVersion != null && otherVersion > 0) {
			throw new IllegalStateException("Payout account rows were sealed with another key version than "
					+ cipher.keyVersion() + ". Set PAYOUT_KEY_VERSION to the version that sealed them.");
		}
	}

}
