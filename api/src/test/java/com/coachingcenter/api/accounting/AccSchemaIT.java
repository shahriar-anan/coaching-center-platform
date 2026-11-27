package com.coachingcenter.api.accounting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.coachingcenter.api.PostgresIntegrationTest;

/**
 * The accounting schema (V107_1): the seed with its fixed ids, and each check and partial unique index of plan 5.2. Every name carries a
 * random suffix, because the test database is shared and never emptied (README 5.1).
 */
@SpringBootTest
class AccSchemaIT extends PostgresIntegrationTest {

	private static final UUID GROUP_OFFLINE = UUID.fromString("a0000000-0000-4000-8000-000000000001");

	private static final UUID GROUP_PERSONAL = UUID.fromString("a0000000-0000-4000-8000-000000000004");

	private static final UUID TYPE_LECTURE = UUID.fromString("a0000000-0000-4000-8000-000000000111");

	private static final UUID TYPE_BONUS = UUID.fromString("a0000000-0000-4000-8000-000000000115");

	private static final UUID TYPE_FOOD = UUID.fromString("a0000000-0000-4000-8000-000000000141");

	private static final UUID ACCOUNT_CASH = UUID.fromString("a0000000-0000-4000-8000-000000000201");

	private static final String GROUP_INSERT = "INSERT INTO acc_groups (id, name_en, name_bn, mode, direction, system_key, in_finance,"
			+ " position, created_at, updated_at) VALUES (?, ?, NULL, ?, ?, ?, TRUE, 99, now(), now())";

	/** Arguments: id, group, name, kind, unit (English). */
	private static final String TYPE_INSERT = "INSERT INTO acc_types (id, group_id, name_en, name_bn, kind, unit_en, unit_bn,"
			+ " needs_period, needs_description, position, created_at, updated_at)"
			+ " VALUES (?, ?, ?, NULL, ?, ?, NULL, FALSE, FALSE, 99, now(), now())";

	private static final String PAYEE_INSERT = "INSERT INTO acc_payees (id, group_id, name, user_id, created_by, created_at, updated_at)"
			+ " VALUES (?, ?, ?, ?, ?, now(), now())";

	private static final String ACCOUNT_INSERT = "INSERT INTO acc_accounts (id, name, kind, position, created_at, updated_at)"
			+ " VALUES (?, ?, ?, 99, now(), now())";

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void theSeedIsPresentWithItsFixedIds() {
		assertEquals(5, count("select count(*) from acc_groups where id in ("
				+ "'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000002',"
				+ "'a0000000-0000-4000-8000-000000000003', 'a0000000-0000-4000-8000-000000000004',"
				+ "'a0000000-0000-4000-8000-000000000005')"));
		assertEquals(30, count("select count(*) from acc_types where id::text like 'a0000000-0000-4000-8000-0000000001%'"));
		assertEquals(5, count("select count(*) from acc_accounts where kind in ('CASH', 'BKASH', 'NAGAD', 'ROCKET', 'BANK')"
				+ " and id::text like 'a0000000-0000-4000-8000-00000000020%'"));
		assertEquals("Offline instructors", jdbc.queryForObject("select name_en from acc_groups where id = ?", String.class,
				GROUP_OFFLINE));
		assertEquals("OFFLINE_INSTRUCTORS", jdbc.queryForObject("select system_key from acc_groups where id = ?", String.class,
				GROUP_OFFLINE));
		assertEquals("Lecture", jdbc.queryForObject("select name_en from acc_types where id = ?", String.class, TYPE_LECTURE));
		assertEquals("COUNTED", jdbc.queryForObject("select kind from acc_types where id = ?", String.class, TYPE_LECTURE));
		assertEquals("class", jdbc.queryForObject("select unit_en from acc_types where id = ?", String.class, TYPE_LECTURE));
		assertEquals("Cash box", jdbc.queryForObject("select name from acc_accounts where id = ?", String.class, ACCOUNT_CASH));
	}

	@Test
	void groupsRefuseBadRows() {
		String name = name("Group");
		assertRefused(GROUP_INSERT, UUID.randomUUID(), " ", "PAYEES", "EXPENSE", null);
		assertRefused(GROUP_INSERT, UUID.randomUUID(), name, "CASH", "EXPENSE", null);
		assertRefused(GROUP_INSERT, UUID.randomUUID(), name, "PAYEES", "OUT", null);
		assertRefused(GROUP_INSERT, UUID.randomUUID(), name, "PAYEES", "EXPENSE", "BOGUS");
		assertRefused(GROUP_INSERT, UUID.randomUUID(), name, "PAYEES", "INCOME", null);
		assertRefused(GROUP_INSERT, UUID.randomUUID(), "Offline instructors " + name, "PAYEES", "EXPENSE", "OFFLINE_INSTRUCTORS");
		assertRefused(GROUP_INSERT, UUID.randomUUID(), "Offline instructors", "PAYEES", "EXPENSE", null);
	}

	@Test
	void aGroupNameIsUniqueAmongLiveGroupsAndCanBeReusedAfterArchive() {
		String name = name("Printing group");
		UUID first = UUID.randomUUID();
		jdbc.update(GROUP_INSERT, first, name, "LOG", "EXPENSE", null);
		assertRefused(GROUP_INSERT, UUID.randomUUID(), name.toUpperCase(), "LOG", "EXPENSE", null);
		jdbc.update("update acc_groups set archived_at = now() where id = ?", first);
		jdbc.update(GROUP_INSERT, UUID.randomUUID(), name.toUpperCase(), "LOG", "EXPENSE", null);
		assertEquals(2, count("select count(*) from acc_groups where lower(name_en) = lower('" + name + "')"));
	}

	@Test
	void typesRefuseBadRows() {
		UUID group = newGroup("PAYEES", "EXPENSE");
		assertRefused(TYPE_INSERT, UUID.randomUUID(), group, " ", "AMOUNT", null);
		assertRefused(TYPE_INSERT, UUID.randomUUID(), group, name("Type"), "PERCENT", null);
		assertRefused(TYPE_INSERT, UUID.randomUUID(), group, name("Type"), "COUNTED", null);
	}

	@Test
	void aTypeNameIsUniqueInItsGroupAndCanBeReusedAfterArchive() {
		UUID group = newGroup("PAYEES", "EXPENSE");
		UUID first = UUID.randomUUID();
		String name = name("Type");
		jdbc.update(TYPE_INSERT, first, group, name, "AMOUNT", null);
		assertRefused(TYPE_INSERT, UUID.randomUUID(), group, name.toLowerCase(), "AMOUNT", null);
		jdbc.update(TYPE_INSERT, UUID.randomUUID(), newGroup("LOG", "EXPENSE"), name, "AMOUNT", null);
		jdbc.update("update acc_types set archived_at = now() where id = ?", first);
		jdbc.update(TYPE_INSERT, UUID.randomUUID(), group, name.toUpperCase(), "AMOUNT", null);
		assertEquals(2, count("select count(*) from acc_types where group_id = '" + group + "'"));
	}

	@Test
	void payeesRefuseBadRows() {
		UUID group = newGroup("PAYEES", "EXPENSE");
		UUID user = seedUser("INSTRUCTOR");
		UUID creator = seedUser("MASTER_ADMIN");
		assertRefused(PAYEE_INSERT, UUID.randomUUID(), group, " ", null, creator);
		assertRefused(PAYEE_INSERT, UUID.randomUUID(), group, name("Payee"), null, null);
		assertRefused(PAYEE_INSERT, UUID.randomUUID(), group, name("Payee"), UUID.randomUUID(), creator);
		assertEquals(1, jdbc.update(PAYEE_INSERT, UUID.randomUUID(), group, name("Payee"), user, creator));
	}

	@Test
	void aPayeeNameIsUniqueInItsGroupAndALiveUserIsLinkedToOnePayeeOnly() {
		UUID group = newGroup("PAYEES", "EXPENSE");
		UUID creator = seedUser("MASTER_ADMIN");
		UUID user = seedUser("INSTRUCTOR");
		String name = name("Payee");
		UUID first = UUID.randomUUID();
		jdbc.update(PAYEE_INSERT, first, group, name, user, creator);
		assertRefused(PAYEE_INSERT, UUID.randomUUID(), group, name.toUpperCase(), null, creator);
		assertRefused(PAYEE_INSERT, UUID.randomUUID(), group, name("Payee"), user, creator);
		jdbc.update("update acc_payees set archived_at = now() where id = ?", first);
		jdbc.update(PAYEE_INSERT, UUID.randomUUID(), group, name.toUpperCase(), user, creator);
		assertEquals(2, count("select count(*) from acc_payees where group_id = '" + group + "'"));
	}

	@Test
	void accountsRefuseBadRows() {
		assertRefused(ACCOUNT_INSERT, UUID.randomUUID(), " ", "CASH");
		assertRefused(ACCOUNT_INSERT, UUID.randomUUID(), name("Account"), "CHEQUE");
		assertRefused(ACCOUNT_INSERT, UUID.randomUUID(), "Cash box", "CASH");
	}

	@Test
	void anAccountNameIsUniqueWhileLiveAndCanBeReusedAfterArchive() {
		String name = name("Account");
		UUID first = UUID.randomUUID();
		jdbc.update(ACCOUNT_INSERT, first, name, "BANK");
		assertRefused(ACCOUNT_INSERT, UUID.randomUUID(), name.toUpperCase(), "BANK");
		jdbc.update("update acc_accounts set archived_at = now() where id = ?", first);
		jdbc.update(ACCOUNT_INSERT, UUID.randomUUID(), name.toUpperCase(), "BANK");
		assertEquals(2, count("select count(*) from acc_accounts where lower(name) = lower('" + name + "')"));
	}

	@Test
	void entriesRefuseAnUnknownKindOrStatus() {
		assertRefusedEntry("SALE", "ACTIVE", null, null, null, "10.00", null, null, null, null, null);
		assertRefusedEntry("SPEND", "DRAFT", null, TYPE_FOOD, ACCOUNT_CASH, "10.00", null, null, null, null, null);
		assertRefusedEntry("SPEND", "ACTIVE", null, TYPE_FOOD, ACCOUNT_CASH, "10.00", null, null, null, "PARTNER", UUID.randomUUID());
	}

	@Test
	void aChargeNeedsAPayeeAndNoAccount() {
		UUID payee = seedPayee();
		assertRefusedEntry("CHARGE", "ACTIVE", null, TYPE_LECTURE, null, "100.00", null, null, null, null, null);
		assertRefusedEntry("CHARGE", "ACTIVE", payee, TYPE_LECTURE, ACCOUNT_CASH, "100.00", null, null, null, null, null);
	}

	@Test
	void aPaymentNeedsAPayeeAndAnAccountAndNoType() {
		UUID payee = seedPayee();
		assertRefusedEntry("PAYMENT", "ACTIVE", null, null, ACCOUNT_CASH, "100.00", null, null, null, null, null);
		assertRefusedEntry("PAYMENT", "ACTIVE", payee, TYPE_LECTURE, ACCOUNT_CASH, "100.00", null, null, null, null, null);
		assertRefusedEntry("PAYMENT", "ACTIVE", payee, null, null, "100.00", null, null, null, null, null);
	}

	@Test
	void aSpendingNeedsAnAccountAndATypeAndNoPayee() {
		UUID payee = seedPayee();
		assertRefusedEntry("SPEND", "ACTIVE", payee, TYPE_FOOD, ACCOUNT_CASH, "10.00", null, null, null, null, null);
		assertRefusedEntry("SPEND", "ACTIVE", null, null, ACCOUNT_CASH, "10.00", null, null, null, null, null);
		assertRefusedEntry("SPEND", "ACTIVE", null, TYPE_FOOD, null, "10.00", null, null, null, null, null);
	}

	@Test
	void anAmountIsNeverZeroAndOnlyChargesMayBeNegative() {
		UUID payee = seedPayee();
		assertRefusedEntry("CHARGE", "ACTIVE", payee, TYPE_LECTURE, null, "0.00", null, null, null, null, null);
		assertRefusedEntry("PAYMENT", "ACTIVE", payee, null, ACCOUNT_CASH, "-5.00", null, null, null, null, null);
		assertEquals(1, insertEntry("CHARGE", payee, TYPE_BONUS, null, "-500.00", null, null, null, "ACTIVE", null, null));
	}

	@Test
	void quantityAndUnitPriceArePositiveAndAPriceNeedsAQuantity() {
		UUID payee = seedPayee();
		assertRefusedEntry("CHARGE", "ACTIVE", payee, TYPE_LECTURE, null, "100.00", "0", null, null, null, null);
		assertRefusedEntry("CHARGE", "ACTIVE", payee, TYPE_LECTURE, null, "100.00", "2", "0.00", null, null, null);
		assertRefusedEntry("CHARGE", "ACTIVE", payee, TYPE_LECTURE, null, "100.00", null, "10.00", null, null, null);
	}

	@Test
	void aPeriodIsAYearAndAMonth() {
		UUID payee = seedPayee();
		assertRefusedEntry("CHARGE", "ACTIVE", payee, TYPE_LECTURE, null, "100.00", null, null, "2026-13", null, null);
		assertEquals(1, insertEntry("CHARGE", payee, TYPE_LECTURE, null, "100.00", null, null, "2026-11", "ACTIVE", null, null));
	}

	@Test
	void aVoidedEntryHasItsVoidTimeAndReasonAndNoOtherEntryDoes() {
		UUID payee = seedPayee();
		UUID recorder = seedUser("MASTER_ADMIN");
		assertRefused("INSERT INTO acc_entries (id, kind, status, group_id, payee_id, type_id, entry_date, amount, recorded_by,"
				+ " recorded_at, created_at, updated_at) VALUES (?, 'CHARGE', 'VOIDED', ?, ?, ?, DATE '2026-11-10', 100.00, ?,"
				+ " now(), now(), now())", UUID.randomUUID(), GROUP_OFFLINE, payee, TYPE_LECTURE, recorder);
		assertRefused("INSERT INTO acc_entries (id, kind, status, group_id, payee_id, type_id, entry_date, amount, recorded_by,"
				+ " recorded_at, voided_at, created_at, updated_at) VALUES (?, 'CHARGE', 'ACTIVE', ?, ?, ?, DATE '2026-11-10', 100.00,"
				+ " ?, now(), now(), now(), now())", UUID.randomUUID(), GROUP_OFFLINE, payee, TYPE_LECTURE, recorder);
		assertRefused("INSERT INTO acc_entries (id, kind, status, group_id, payee_id, type_id, entry_date, amount, recorded_by,"
				+ " recorded_at, voided_at, created_at, updated_at) VALUES (?, 'CHARGE', 'VOIDED', ?, ?, ?, DATE '2026-11-10', 100.00,"
				+ " ?, now(), now(), now(), now())", UUID.randomUUID(), GROUP_OFFLINE, payee, TYPE_LECTURE, recorder);
	}

	@Test
	void aMovedEntryIsUniqueByItsOldTableAndId() {
		UUID payee = seedPayee();
		UUID old = UUID.randomUUID();
		assertEquals(1, insertEntry("PAYMENT", payee, null, ACCOUNT_CASH, "100.00", null, null, null, "ACTIVE", "OTHER", old));
		assertRefusedEntry("PAYMENT", "ACTIVE", payee, null, ACCOUNT_CASH, "100.00", null, null, null, "OTHER", old);
		assertEquals(1, insertEntry("PAYMENT", payee, null, ACCOUNT_CASH, "100.00", null, null, null, "ACTIVE", "CLASS", old));
		assertEquals(1, insertEntry("CHARGE", payee, TYPE_LECTURE, null, "100.00", null, null, null, "ACTIVE", null, null));
		assertEquals(1, insertEntry("CHARGE", payee, TYPE_LECTURE, null, "100.00", null, null, null, "ACTIVE", null, null));
	}

	@Test
	void everyEntryGetsANumberFromTheSequence() {
		UUID payee = seedPayee();
		insertEntry("PAYMENT", payee, null, ACCOUNT_CASH, "10.00", null, null, null, "ACTIVE", null, null);
		insertEntry("PAYMENT", payee, null, ACCOUNT_CASH, "10.00", null, null, null, "ACTIVE", null, null);
		assertEquals(0, count("select count(*) from (select no from acc_entries group by no having count(*) > 1) d"));
	}

	@Test
	void anEditKeepsARequiredReason() {
		UUID payee = seedPayee();
		UUID entry = UUID.randomUUID();
		UUID editor = seedUser("MASTER_ADMIN");
		jdbc.update("INSERT INTO acc_entries (id, kind, status, group_id, payee_id, type_id, entry_date, amount, recorded_by,"
				+ " recorded_at, created_at, updated_at) VALUES (?, 'CHARGE', 'ACTIVE', ?, ?, ?, DATE '2026-11-10', 100.00, ?,"
				+ " now(), now(), now())", entry, GROUP_OFFLINE, payee, TYPE_LECTURE, editor);
		assertRefused("INSERT INTO acc_entry_edits (id, entry_id, before_values, after_values, reason, edited_by, edited_at,"
				+ " created_at, updated_at) VALUES (?, ?, '{}', '{}', ' ', ?, now(), now(), now())", UUID.randomUUID(), entry, editor);
	}

	private void assertRefused(String sql, Object... args) {
		assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(sql, args));
	}

	private void assertRefusedEntry(String kind, String status, UUID payee, UUID type, UUID account, String amount, String quantity,
			String unitPrice, String period, String legacySource, UUID legacyId) {
		assertThrows(DataIntegrityViolationException.class,
				() -> insertEntry(kind, payee, type, account, amount, quantity, unitPrice, period, status, legacySource, legacyId));
	}

	private int insertEntry(String kind, UUID payee, UUID type, UUID account, String amount, String quantity, String unitPrice,
			String period, String status, String legacySource, UUID legacyId) {
		UUID recorder = seedUser("MASTER_ADMIN");
		return jdbc.update("""
				INSERT INTO acc_entries (id, kind, status, group_id, payee_id, type_id, entry_date, period, quantity, unit_price,
				  amount, account_id, legacy_source, legacy_id, recorded_by, recorded_at, voided_at, void_reason, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, DATE '2026-11-10', ?, CAST(? AS NUMERIC), CAST(? AS NUMERIC), CAST(? AS NUMERIC), ?, ?, ?,
				  ?, now(), CASE WHEN ? = 'VOIDED' THEN now() END, CASE WHEN ? = 'VOIDED' THEN 'Test void' END, now(), now())
				""", UUID.randomUUID(), kind, status, GROUP_OFFLINE, payee, type, period, quantity, unitPrice, amount, account,
				legacySource, legacyId, recorder, status, status);
	}

	private UUID newGroup(String mode, String direction) {
		UUID id = UUID.randomUUID();
		jdbc.update(GROUP_INSERT, id, name("Group"), mode, direction, null);
		return id;
	}

	/** A new group with a random name, for the type and payee tests. */

	private UUID seedPayee() {
		UUID id = UUID.randomUUID();
		jdbc.update(PAYEE_INSERT, id, GROUP_OFFLINE, name("Payee"), null, seedUser("MASTER_ADMIN"));
		return id;
	}

	private UUID seedUser(String role) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO users (id, phone, email, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, "+8801" + ThreadLocalRandom.current().nextInt(100000000, 1000000000),
				"acc-" + id + "@example.com", role);
		return id;
	}

	private int count(String sql) {
		return jdbc.queryForObject(sql, Integer.class);
	}

	private static String name(String prefix) {
		return prefix + " " + UUID.randomUUID();
	}

}
