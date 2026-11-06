package com.coachingcenter.api.staffpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;
import com.coachingcenter.api.staffpay.PayoutAccountService.PayoutAccountRequest;
import com.coachingcenter.api.staffpay.PayoutAccountService.PayoutAccountView;

/**
 * Phase 9: numbers are sealed on write, a row stored before the key is sealed by the backfill and keeps its value, and the
 * startup guard refuses a start that could not read the sealed numbers. Runs against the shared test database.
 */
@SpringBootTest
@Import(CapturingEmailConfig.class)
class PayoutEncryptionIT extends PostgresIntegrationTest {

	private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

	private static final String BANK_NUMBER = "1234567890";

	private static final String ROUTING = "200262123";

	@DynamicPropertySource
	static void payoutKey(DynamicPropertyRegistry registry) {
		registry.add("app.payout.encryption-key", () -> KEY);
		registry.add("app.payout.key-version", () -> "1");
	}

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PayoutAccountService accounts;

	@Autowired
	private PayoutNumberBackfill backfill;

	@Autowired
	private PasswordEncoder passwords;

	@Test
	void aNewNumberIsSealedAndStillReadsInFull() {
		UUID owner = seed("INSTRUCTOR");

		PayoutAccountView added = accounts.add(owner, bank("Rahim Uddin", BANK_NUMBER));

		assertThat(added.accountNumber()).isEqualTo(BANK_NUMBER);
		Object[] row = jdbc.queryForObject("""
				select account_number, account_number_enc, key_version from staff_payout_accounts where id = ?
				""", (rs, n) -> new Object[] { rs.getString(1), rs.getBytes(2), rs.getObject(3) }, added.id());
		assertThat(row[0]).isEqualTo(BANK_NUMBER);
		assertThat((byte[]) row[1]).isNotNull();
		assertThat(new String((byte[]) row[1], java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain(BANK_NUMBER);
		assertThat(((Number) row[2]).intValue()).isEqualTo(1);
		assertThat(accounts.list(owner)).extracting(PayoutAccountView::accountNumber).containsExactly(BANK_NUMBER);
	}

	@Test
	void aNumberStoredBeforeTheKeyIsSealedByTheBackfillAndKeepsItsValue() {
		UUID owner = seed("SYSTEM_ADMIN");
		UUID account = UUID.randomUUID();
		jdbc.update("""
				insert into staff_payout_accounts (id, user_id, kind, account_name, account_number, bank_name, branch,
				  routing_number, preferred, created_at, updated_at)
				values (?, ?, 'BANK', 'Before Key', ?, 'Sonali Bank', 'Dhaka', ?, true, now(), now())
				""", account, owner, BANK_NUMBER, ROUTING);

		while (backfill.sealBatch() > 0) {
			// Each call seals up to one batch and verifies it; the loop ends when nothing is left to seal.
		}

		Object[] row = jdbc.queryForObject("""
				select account_number, account_number_enc, routing_number, key_version from staff_payout_accounts where id = ?
				""", (rs, n) -> new Object[] { rs.getString(1), rs.getBytes(2), rs.getString(3), rs.getObject(4) }, account);
		assertThat(row[0]).isEqualTo(BANK_NUMBER);
		assertThat(row[2]).isEqualTo(ROUTING);
		assertThat((byte[]) row[1]).isNotNull();
		assertThat(((Number) row[3]).intValue()).isEqualTo(1);
		assertThat(accounts.list(owner)).extracting(PayoutAccountView::accountNumber).contains(BANK_NUMBER);
		assertThat(backfill.sealBatch()).isZero();
	}

	@Test
	void theGuardRefusesToStartWithoutTheKeyWhenSealedNumbersExist() {
		UUID owner = seed("INSTRUCTOR");
		accounts.add(owner, bank("Guard Test", BANK_NUMBER));

		PayoutNumberEncryptionGuard noKey = new PayoutNumberEncryptionGuard(jdbc, new PayoutNumberCipher("", 1));
		assertThatThrownBy(() -> noKey.run(null)).hasMessageContaining("PAYOUT_ENCRYPTION_KEY is not set");

		PayoutNumberEncryptionGuard otherVersion = new PayoutNumberEncryptionGuard(jdbc, new PayoutNumberCipher(KEY, 2));
		assertThatThrownBy(() -> otherVersion.run(null)).hasMessageContaining("another key version");

		PayoutNumberEncryptionGuard matching = new PayoutNumberEncryptionGuard(jdbc, new PayoutNumberCipher(KEY, 1));
		assertThatCode(() -> matching.run(null)).doesNotThrowAnyException();
	}

	private PayoutAccountRequest bank(String name, String number) {
		return new PayoutAccountRequest("BANK", name, number, "Sonali Bank", "Dhaka", ROUTING, null);
	}

	private UUID seed(String role) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, "+8801" + (100000000 + Math.abs(id.hashCode()) % 899999999), "seal-" + id + "@example.com",
				passwords.encode("password1"), role);
		jdbc.update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?)", id, "Seal " + role);
		return id;
	}

}
