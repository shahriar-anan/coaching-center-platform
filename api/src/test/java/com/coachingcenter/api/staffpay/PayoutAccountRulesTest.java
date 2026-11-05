package com.coachingcenter.api.staffpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.PermissionCatalog;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.staffpay.PayoutAccountService.PayoutAccountRequest;

/** The payout account rules and the owner-only grant, checked without a database. */
class PayoutAccountRulesTest {

	private static final String BANK_KIND = "BANK";

	@Test
	void aBankEntryIsTrimmedAndTheSpacesAndDashesInItsNumbersAreDropped() {
		PayoutAccountRules.Fields fields = PayoutAccountRules.validate(bank(" Rahim Uddin ", "1234-5678 90", "Sonali Bank",
				"Dhanmondi", "200 262 123"));

		assertThat(fields.kind()).isEqualTo(PayoutAccountKind.BANK);
		assertThat(fields.accountName()).isEqualTo("Rahim Uddin");
		assertThat(fields.accountNumber()).isEqualTo("1234567890");
		assertThat(fields.routingNumber()).isEqualTo("200262123");
		assertThat(fields.walletNumber()).isNull();
	}

	@Test
	void aBankEntryNeedsEveryBankField() {
		assertInvalid(bank(null, "1234567890", "Sonali Bank", "Dhanmondi", "200262123"));
		assertInvalid(bank("Rahim", "1234567890", " ", "Dhanmondi", "200262123"));
		assertInvalid(bank("Rahim", "1234567890", "Sonali Bank", "", "200262123"));
	}

	@Test
	void aRoutingNumberIsExactlyNineDigits() {
		for (String routing : List.of("20026212", "2002621234", "20026212A", "")) {
			assertInvalid(bank("Rahim", "1234567890", "Sonali Bank", "Dhanmondi", routing));
		}
	}

	@Test
	void anAccountNumberIsSixToTwentyDigits() {
		assertInvalid(bank("Rahim", "12345", "Sonali Bank", "Dhanmondi", "200262123"));
		assertInvalid(bank("Rahim", "1".repeat(21), "Sonali Bank", "Dhanmondi", "200262123"));
		assertInvalid(bank("Rahim", "12345x7890", "Sonali Bank", "Dhanmondi", "200262123"));
		assertThat(PayoutAccountRules.validate(bank("Rahim", "123456", "Sonali Bank", "Dhanmondi", "200262123"))
			.accountNumber()).isEqualTo("123456");
	}

	@Test
	void aWalletEntryKeepsItsNumberInTheStoredMobileForm() {
		for (String kind : List.of("BKASH", "nagad", " Rocket ")) {
			PayoutAccountRules.Fields fields = PayoutAccountRules.validate(wallet(kind, "01712 345678"));
			assertThat(fields.kind()).isEqualTo(PayoutAccountKind.valueOf(kind.trim().toUpperCase()));
			assertThat(fields.walletNumber()).isEqualTo("+8801712345678");
			assertThat(fields.accountNumber()).isNull();
			assertThat(fields.bankName()).isNull();
		}
		assertThat(PayoutAccountRules.validate(wallet("BKASH", "+8801712345678")).walletNumber()).isEqualTo("+8801712345678");
	}

	@Test
	void aWalletNumberMustBeABangladeshMobileNumber() {
		for (String number : List.of("0171234567", "017123456789", "02123456789", "+8801812345", "", " ")) {
			assertInvalid(wallet("BKASH", number));
		}
		assertInvalid(wallet("BKASH", null));
	}

	@Test
	void anUnknownOrMissingKindIsRefused() {
		assertInvalid(wallet("PAYPAL", "01712345678"));
		assertInvalid(wallet(null, "01712345678"));
		assertInvalid(wallet("  ", "01712345678"));
		assertThatThrownBy(() -> PayoutAccountRules.validate(null)).isInstanceOf(ApiException.class);
	}

	@Test
	void aListShowsOnlyTheLastFourDigits() {
		assertThat(PayoutAccountRules.mask("+8801712345678")).isEqualTo("•••• 5678");
		assertThat(PayoutAccountRules.mask("1234567890")).isEqualTo("•••• 7890");
		assertThat(PayoutAccountRules.mask("123")).isEqualTo("•••• 123");
		assertThat(PayoutAccountRules.mask(null)).isEqualTo("••••");
	}

	@Test
	void aPersonKeepsAtMostFiveLiveAccounts() {
		assertThat(PayoutAccountRules.MAX_ACCOUNTS).isEqualTo(5);
	}

	@Test
	void onlySystemAdminsAndInstructorsHoldTheOwnPayPermission() {
		PermissionCatalog catalog = new PermissionCatalog(List.of(new PayoutAccountPermissionContributor()));

		assertThat(catalog.allows(Role.SYSTEM_ADMIN, Permissions.STAFF_PAY_SELF, false)).isTrue();
		assertThat(catalog.allows(Role.INSTRUCTOR, Permissions.STAFF_PAY_SELF, false)).isTrue();
		assertThat(catalog.allows(Role.MASTER_ADMIN, Permissions.STAFF_PAY_SELF, false)).isFalse();
		assertThat(catalog.allows(Role.STUDENT, Permissions.STAFF_PAY_SELF, true)).isFalse();
	}

	private static void assertInvalid(PayoutAccountRequest request) {
		assertThatThrownBy(() -> PayoutAccountRules.validate(request)).isInstanceOfSatisfying(ApiException.class,
				error -> assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
	}

	private static PayoutAccountRequest bank(String name, String number, String bankName, String branch, String routing) {
		return new PayoutAccountRequest(BANK_KIND, name, number, bankName, branch, routing, null);
	}

	private static PayoutAccountRequest wallet(String kind, String number) {
		return new PayoutAccountRequest(kind, null, null, null, null, null, number);
	}

}
