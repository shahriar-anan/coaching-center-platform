package com.coachingcenter.api.accounting;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Rows for the accounting tests, written straight into the tables (JDBC). Every name carries a random suffix, because the test database
 * is shared and never emptied (README 5.1). Entries are recorded by one Master Admin made by this class.
 */
public class AccTestData {

	private final JdbcTemplate jdbc;

	private UUID creator;

	public AccTestData(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** A Master Admin who records the rows. Made once per instance. */
	public UUID masterAdmin() {
		if (creator == null) {
			creator = UUID.randomUUID();
			jdbc.update("INSERT INTO users (id, phone, email, role, status, created_at, updated_at)"
					+ " VALUES (?, ?, ?, 'MASTER_ADMIN', 'ACTIVE', now(), now())",
					creator, "+8801" + ThreadLocalRandom.current().nextInt(100000000, 1000000000), "acc-test-" + creator + "@example.com");
		}
		return creator;
	}

	/** A new group, not a system group, in the given mode and direction. */
	public UUID group(String mode, String direction) {
		UUID id = UUID.randomUUID();
		jdbc.update("INSERT INTO acc_groups (id, name_en, mode, direction, in_finance, position, created_at, updated_at)"
				+ " VALUES (?, ?, ?, ?, TRUE, 99, now(), now())", id, name("Group"), mode, direction);
		return id;
	}

	/** A new type in a group. A counted type gets the unit "unit". */
	public UUID type(UUID group, String kind) {
		UUID id = UUID.randomUUID();
		jdbc.update("INSERT INTO acc_types (id, group_id, name_en, kind, unit_en, needs_period, needs_description, position,"
				+ " created_at, updated_at) VALUES (?, ?, ?, ?, ?, FALSE, FALSE, 99, now(), now())",
				id, group, name("Type"), kind, "COUNTED".equals(kind) ? "unit" : null);
		return id;
	}

	/**
	 * A new payee in a group. In the Offline instructors group the payee also gets its offline_instructors row, with the same id, as the
	 * migration gives the seeded payees.
	 */
	public UUID payee(UUID group) {
		UUID id = UUID.randomUUID();
		String name = name("Payee");
		jdbc.update("INSERT INTO acc_payees (id, group_id, name, created_by, created_at, updated_at) VALUES (?, ?, ?, ?, now(), now())",
				id, group, name, masterAdmin());
		if (AccIds.GROUP_OFFLINE_INSTRUCTORS.equals(group)) {
			jdbc.update("INSERT INTO offline_instructors (id, name, created_by, created_at, updated_at)"
					+ " VALUES (?, ?, ?, now(), now())", id, name, masterAdmin());
		}
		return id;
	}

	/** A new money account of the given kind. */
	public UUID account(String kind) {
		UUID id = UUID.randomUUID();
		jdbc.update("INSERT INTO acc_accounts (id, name, kind, position, created_at, updated_at) VALUES (?, ?, ?, 99, now(), now())",
				id, name("Account"), kind);
		return id;
	}

	/** A charge of an amount (a negative amount is a deduction charge). Charges name no account. */
	public UUID charge(UUID payee, UUID type, String amount, LocalDate date) {
		return entry("CHARGE", groupOf(payee), payee, type, null, amount, null, null, date);
	}

	/** A counted charge: the amount is quantity times price. */
	public UUID countedCharge(UUID payee, UUID type, String quantity, String unitPrice, LocalDate date) {
		String amount = new java.math.BigDecimal(quantity).multiply(new java.math.BigDecimal(unitPrice)).toPlainString();
		return entry("CHARGE", groupOf(payee), payee, type, null, amount, quantity, unitPrice, date);
	}

	/** A payment to a payee from a money account. */
	public UUID payment(UUID payee, UUID account, String amount, LocalDate date) {
		return entry("PAYMENT", groupOf(payee), payee, null, account, amount, null, null, date);
	}

	/** Spending in a Log group. */
	public UUID spend(UUID group, UUID type, UUID account, String amount, LocalDate date) {
		return entry("SPEND", group, null, type, account, amount, null, null, date);
	}

	/** Income in a Log income group. */
	public UUID income(UUID group, UUID type, UUID account, String amount, LocalDate date) {
		return entry("INCOME", group, null, type, account, amount, null, null, date);
	}

	/** Voids an entry, with a reason. */
	public void voidEntry(UUID entry, String reason) {
		jdbc.update("UPDATE acc_entries SET status = 'VOIDED', voided_by = ?, voided_at = now(), void_reason = ? WHERE id = ?",
				masterAdmin(), reason, entry);
	}

	private UUID groupOf(UUID payee) {
		return jdbc.queryForObject("SELECT group_id FROM acc_payees WHERE id = ?", UUID.class, payee);
	}

	private UUID entry(String kind, UUID group, UUID payee, UUID type, UUID account, String amount, String quantity,
			String unitPrice, LocalDate date) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO acc_entries (id, kind, status, group_id, payee_id, type_id, entry_date, quantity, unit_price, amount,
				  account_id, recorded_by, recorded_at, created_at, updated_at)
				VALUES (?, ?, 'ACTIVE', ?, ?, ?, ?, CAST(? AS NUMERIC), CAST(? AS NUMERIC), CAST(? AS NUMERIC), ?, ?, now(), now(), now())
				""", id, kind, group, payee, type, date, quantity, unitPrice, amount, account, masterAdmin());
		return id;
	}

	private static String name(String prefix) {
		return prefix + " " + UUID.randomUUID();
	}

	/** Sets the description, reference and note of an entry (the search of the list, R1). Null leaves a field empty. */
	public void annotate(UUID entry, String description, String reference, String note) {
		jdbc.update("UPDATE acc_entries SET description = ?, reference = ?, note = ? WHERE id = ?", description, reference, note, entry);
	}

	/** Puts an entry in an offline batch (the Finance tests, R3). */
	public void inBatch(UUID entry, UUID batch) {
		jdbc.update("UPDATE acc_entries SET batch_id = ? WHERE id = ?", batch, entry);
	}

}
