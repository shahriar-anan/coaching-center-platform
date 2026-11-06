package com.coachingcenter.api.staffpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** The payout number cipher on its own: round trips, binding to the column and the account, keys. No database. */
class PayoutNumberCipherTest {

	private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

	private static final String OTHER_KEY = Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8));

	@Test
	void aNumberOpensToTheSameTextWithTheSameColumnAndAccount() {
		PayoutNumberCipher cipher = new PayoutNumberCipher(KEY, 1);
		UUID account = UUID.randomUUID();
		byte[] sealed = cipher.seal(PayoutNumberCipher.ACCOUNT, account, "1234567890");

		assertThat(cipher.open(PayoutNumberCipher.ACCOUNT, account, sealed)).isEqualTo("1234567890");
		assertThat(new String(sealed, StandardCharsets.UTF_8)).doesNotContain("1234567890");
	}

	@Test
	void theSameNumberSealsDifferentlyEachTime() {
		PayoutNumberCipher cipher = new PayoutNumberCipher(KEY, 1);
		UUID account = UUID.randomUUID();

		assertThat(cipher.seal(PayoutNumberCipher.WALLET, account, "01712345678"))
			.isNotEqualTo(cipher.seal(PayoutNumberCipher.WALLET, account, "01712345678"));
	}

	@Test
	void aSealedValueDoesNotOpenUnderAnotherColumnOrAnotherAccount() {
		PayoutNumberCipher cipher = new PayoutNumberCipher(KEY, 1);
		UUID account = UUID.randomUUID();
		byte[] sealed = cipher.seal(PayoutNumberCipher.ACCOUNT, account, "1234567890");

		assertThatThrownBy(() -> cipher.open(PayoutNumberCipher.ROUTING, account, sealed))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> cipher.open(PayoutNumberCipher.ACCOUNT, UUID.randomUUID(), sealed))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void aDifferentKeyCannotOpenTheNumber() {
		UUID account = UUID.randomUUID();
		byte[] sealed = new PayoutNumberCipher(KEY, 1).seal(PayoutNumberCipher.ACCOUNT, account, "1234567890");

		assertThatThrownBy(() -> new PayoutNumberCipher(OTHER_KEY, 1).open(PayoutNumberCipher.ACCOUNT, account, sealed))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("PAYOUT_ENCRYPTION_KEY");
	}

	@Test
	void withoutAKeyTheCipherIsOffAndSealsNothing() {
		PayoutNumberCipher cipher = new PayoutNumberCipher("", 1);
		UUID account = UUID.randomUUID();

		assertThat(cipher.enabled()).isFalse();
		assertThat(cipher.seal(PayoutNumberCipher.ACCOUNT, account, "1234567890")).isNull();
		assertThat(cipher.open(PayoutNumberCipher.ACCOUNT, account, null)).isNull();
		assertThatThrownBy(() -> cipher.open(PayoutNumberCipher.ACCOUNT, account, new byte[32]))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void aMissingNumberIsNotSealed() {
		assertThat(new PayoutNumberCipher(KEY, 1).seal(PayoutNumberCipher.WALLET, UUID.randomUUID(), null)).isNull();
	}

	@Test
	void theKeyMustBeBase64OfExactlyThirtyTwoBytes() {
		String sixteenBytes = Base64.getEncoder().encodeToString(new byte[16]);

		assertThatThrownBy(() -> new PayoutNumberCipher(sixteenBytes, 1)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("32 bytes");
		assertThatThrownBy(() -> new PayoutNumberCipher("not base64 !!", 1)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("base64");
	}

	@Test
	void aDamagedValueIsRefusedNotShownAsGarbage() {
		PayoutNumberCipher cipher = new PayoutNumberCipher(KEY, 1);
		UUID account = UUID.randomUUID();
		byte[] sealed = cipher.seal(PayoutNumberCipher.ACCOUNT, account, "1234567890");
		sealed[sealed.length - 1] ^= 0x01;

		assertThatThrownBy(() -> cipher.open(PayoutNumberCipher.ACCOUNT, account, sealed))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> cipher.open(PayoutNumberCipher.ACCOUNT, account, new byte[5]))
			.isInstanceOf(IllegalStateException.class);
	}

}
