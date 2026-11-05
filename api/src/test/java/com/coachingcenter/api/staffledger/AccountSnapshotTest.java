package com.coachingcenter.api.staffledger;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The masked text a payment keeps about its account: the last four digits only, never the full number. */
class AccountSnapshotTest {

	@Test
	void aWalletShowsItsLabelTheFirstThreeAndTheLastFourDigits() {
		assertThat(AccountSnapshot.of("BKASH", null, null, "01712344821")).isEqualTo("bKash 017••••4821");
		assertThat(AccountSnapshot.of("NAGAD", null, null, "01912345678")).isEqualTo("Nagad 019••••5678");
		assertThat(AccountSnapshot.of("ROCKET", null, null, "01812345000")).isEqualTo("Rocket 018••••5000");
	}

	@Test
	void aBankShowsItsNameAndTheLastFourDigitsOfTheAccount() {
		assertThat(AccountSnapshot.of("BANK", "Islami Bank", "2041001234567", null)).isEqualTo("Islami Bank ••••4567");
	}

	@Test
	void aShortNumberShowsNoDigitsAtAll() {
		assertThat(AccountSnapshot.of("BKASH", null, null, "0171")).isEqualTo("bKash ••••");
		assertThat(AccountSnapshot.of("BANK", "Sonali", "1234", null)).isEqualTo("Sonali ••••");
	}

	@Test
	void theFullNumberNeverAppears() {
		String text = AccountSnapshot.of("BKASH", null, null, "01712344821");
		assertThat(text).doesNotContain("01712344821").doesNotContain("1712344");
	}

	@Test
	void aLongBankNameIsCutSoTheSnapshotFitsItsColumn() {
		String text = AccountSnapshot.of("BANK", "B".repeat(300), "2041001234567", null);
		assertThat(text.length()).isLessThanOrEqualTo(AccountSnapshot.MAX);
		assertThat(text).endsWith("••••4567");
	}

}
