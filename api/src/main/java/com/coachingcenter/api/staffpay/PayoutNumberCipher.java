package com.coachingcenter.api.staffpay;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encrypts and decrypts the payout account numbers (staff pay plan, phase 9). AES-256-GCM, a fresh random nonce per value,
 * and associated data that names the table, the column and the account id, so a value cannot be copied onto another row.
 *
 * <p>
 * The key comes from {@code PAYOUT_ENCRYPTION_KEY}: 32 random bytes, base64 encoded. It is never stored, logged or audited.
 * Without a key the cipher is disabled: new writes keep only the plain columns, and a row that is already encrypted cannot be
 * read. {@link PayoutNumberEncryptionGuard} refuses to start in that state.
 *
 * <p>
 * Stored form: nonce (12 bytes) followed by the ciphertext and its 16-byte tag. The {@code key_version} column names the key
 * version that sealed the row.
 */
@Component
public class PayoutNumberCipher {

	static final String TABLE = "staff_payout_accounts";

	static final String ACCOUNT = "account_number";

	static final String ROUTING = "routing_number";

	static final String WALLET = "wallet_number";

	private static final int NONCE_BYTES = 12;

	private static final int KEY_BYTES = 32;

	private static final int TAG_BITS = 128;

	private final SecretKeySpec key;

	private final int keyVersion;

	private final SecureRandom random = new SecureRandom();

	public PayoutNumberCipher(@Value("${app.payout.encryption-key:}") String encodedKey,
			@Value("${app.payout.key-version:1}") int keyVersion) {
		this.key = encodedKey == null || encodedKey.isBlank() ? null : keyOf(encodedKey.trim());
		this.keyVersion = keyVersion;
	}

	/** True when a key is configured, so numbers can be sealed and opened. */
	public boolean enabled() {
		return key != null;
	}

	/** The version of the configured key. Rows sealed with another version are refused, not opened. */
	public int keyVersion() {
		return keyVersion;
	}

	/** The sealed form of one number, or null when there is no number or no key. */
	public byte[] seal(String column, UUID accountId, String plain) {
		if (plain == null || key == null) {
			return null;
		}
		try {
			byte[] nonce = new byte[NONCE_BYTES];
			random.nextBytes(nonce);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
			cipher.updateAAD(associated(column, accountId));
			byte[] sealed = cipher.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			return ByteBuffer.allocate(NONCE_BYTES + sealed.length).put(nonce).put(sealed).array();
		}
		catch (GeneralSecurityException exception) {
			throw new IllegalStateException("A payout number could not be sealed.", exception);
		}
	}

	/** The plain number from its sealed form. The wrong key, another row or a damaged value all fail here, loudly. */
	public String open(String column, UUID accountId, byte[] sealed) {
		if (sealed == null) {
			return null;
		}
		if (key == null) {
			throw new IllegalStateException("PAYOUT_ENCRYPTION_KEY is not set, but payout numbers are encrypted.");
		}
		if (sealed.length <= NONCE_BYTES) {
			throw new IllegalStateException("A payout number is damaged.");
		}
		try {
			ByteBuffer buffer = ByteBuffer.wrap(sealed);
			byte[] nonce = new byte[NONCE_BYTES];
			buffer.get(nonce);
			byte[] body = new byte[buffer.remaining()];
			buffer.get(body);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
			cipher.updateAAD(associated(column, accountId));
			return new String(cipher.doFinal(body), java.nio.charset.StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException exception) {
			throw new IllegalStateException("A payout number could not be opened. Check PAYOUT_ENCRYPTION_KEY.", exception);
		}
	}

	/**
	 * The three numbers of one row as they should be shown: from the sealed columns when they are set, else from the plain
	 * columns (a row that predates the backfill). The row's key version must match the configured one.
	 */
	public PayoutNumbers read(ResultSet rs) throws SQLException {
		UUID id = rs.getObject("id", UUID.class);
		Integer version = rs.getObject("key_version", Integer.class);
		return new PayoutNumbers(
				numberOf(rs, id, version, ACCOUNT, "account_number_enc", "account_number"),
				numberOf(rs, id, version, ROUTING, "routing_number_enc", "routing_number"),
				numberOf(rs, id, version, WALLET, "wallet_number_enc", "wallet_number"));
	}

	private String numberOf(ResultSet rs, UUID id, Integer version, String column, String sealedColumn, String plainColumn)
			throws SQLException {
		byte[] sealed = rs.getBytes(sealedColumn);
		if (sealed == null) {
			return rs.getString(plainColumn);
		}
		if (version == null || version != keyVersion) {
			throw new IllegalStateException("A payout number was sealed with another key version.");
		}
		return open(column, id, sealed);
	}

	private static byte[] associated(String column, UUID accountId) {
		return (TABLE + "|" + column + "|" + accountId).getBytes(java.nio.charset.StandardCharsets.UTF_8);
	}

	private static SecretKeySpec keyOf(String encoded) {
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(encoded);
		}
		catch (IllegalArgumentException exception) {
			throw new IllegalStateException("PAYOUT_ENCRYPTION_KEY must be base64.", exception);
		}
		if (bytes.length != KEY_BYTES) {
			throw new IllegalStateException("PAYOUT_ENCRYPTION_KEY must decode to 32 bytes.");
		}
		return new SecretKeySpec(bytes, "AES");
	}

	/** The three numbers of one account, as the owner and the Master Admin may see them. */
	public record PayoutNumbers(String account, String routing, String wallet) {
	}

}
