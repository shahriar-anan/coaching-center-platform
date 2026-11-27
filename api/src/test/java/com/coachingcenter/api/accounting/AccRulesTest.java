package com.coachingcenter.api.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import com.coachingcenter.api.accounting.AccRules.AccountRef;
import com.coachingcenter.api.accounting.AccRules.Checked;
import com.coachingcenter.api.accounting.AccRules.Draft;
import com.coachingcenter.api.accounting.AccRules.GroupRef;
import com.coachingcenter.api.accounting.AccRules.PayeeRef;
import com.coachingcenter.api.accounting.AccRules.TypeRef;
import com.coachingcenter.api.accounting.AccRules.Was;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/**
 * The entry rules (plan 4.4, 4.5): every refusal key the rules can raise, the field errors, and the values stored for each kind.
 */
class AccRulesTest {

	private static final LocalDate TODAY = LocalDate.parse("2026-11-27");

	private static final LocalDate DAY = LocalDate.parse("2026-11-20");

	private static final GroupRef PAYEES = new GroupRef(AccIds.GROUP_OFFLINE_INSTRUCTORS, "PAYEES", "EXPENSE", false);

	private static final GroupRef PERSONAL = new GroupRef(AccIds.GROUP_PERSONAL, "LOG", "EXPENSE", false);

	private static final GroupRef OTHER_INCOME = new GroupRef(AccIds.GROUP_OTHER_INCOME, "LOG", "INCOME", false);

	private static final TypeRef LECTURE = new TypeRef(AccIds.TYPE_LECTURE, AccIds.GROUP_OFFLINE_INSTRUCTORS, "COUNTED", false, false,
			false);

	private static final TypeRef BONUS = new TypeRef(AccIds.TYPE_INSTRUCTOR_BONUS, AccIds.GROUP_OFFLINE_INSTRUCTORS, "AMOUNT", false,
			false, false);

	private static final TypeRef DEDUCTION = new TypeRef(AccIds.TYPE_INSTRUCTOR_DEDUCTION, AccIds.GROUP_OFFLINE_INSTRUCTORS,
			"DEDUCTION", false, false, false);

	private static final TypeRef OTHER = new TypeRef(AccIds.TYPE_INSTRUCTOR_OTHER, AccIds.GROUP_OFFLINE_INSTRUCTORS, "AMOUNT", false,
			true, false);

	private static final TypeRef PERSONAL_FOOD = new TypeRef(UUID.randomUUID(), AccIds.GROUP_PERSONAL, "AMOUNT", false, false, false);

	private static final TypeRef SALARY = new TypeRef(UUID.randomUUID(), AccIds.GROUP_STAFF, "AMOUNT", true, false, false);

	private static final PayeeRef RAHIM = new PayeeRef(UUID.randomUUID(), AccIds.GROUP_OFFLINE_INSTRUCTORS);

	private static final AccountRef CASH = new AccountRef(AccIds.ACCOUNT_CASH, false);

	@Test
	void aCountedChargeIsQuantityTimesPriceWhateverAmountWasSent() {
		Checked charge = check(draft("CHARGE", RAHIM.id(), LECTURE.id(), null, "12", "1000", "1", null), PAYEES, LECTURE, RAHIM, null,
				null);
		assertThat(charge.amount()).isEqualByComparingTo("12000.00");
		assertThat(charge.quantity()).isEqualByComparingTo("12");
		assertThat(charge.unitPrice()).isEqualByComparingTo("1000");
		assertThat(charge.payeeId()).isEqualTo(RAHIM.id());
		assertThat(charge.accountId()).isNull();
		assertThat(charge.counted()).isTrue();
	}

	@Test
	void aDeductionChargeIsStoredNegative() {
		Checked deduction = check(draft("CHARGE", RAHIM.id(), DEDUCTION.id(), null, null, null, "500", null), PAYEES, DEDUCTION, RAHIM,
				null, null);
		assertThat(deduction.amount()).isEqualByComparingTo("-500.00");
		assertThat(deduction.deduction()).isTrue();
	}

	@Test
	void aMonthlyPaymentAndAnAmountChargeKeepTheirValues() {
		Checked bonus = check(draft("CHARGE", RAHIM.id(), BONUS.id(), null, null, null, "300", null), PAYEES, BONUS, RAHIM, null, null);
		assertThat(bonus.amount()).isEqualByComparingTo("300.00");
		assertThat(bonus.quantity()).isNull();

		Checked salary = check(draft("CHARGE", UUID.randomUUID(), SALARY.id(), null, null, null, "25000", "2026-11"),
				new GroupRef(AccIds.GROUP_STAFF, "PAYEES", "EXPENSE", false), SALARY, new PayeeRef(UUID.randomUUID(), AccIds.GROUP_STAFF),
				null, null);
		assertThat(salary.period()).isEqualTo("2026-11");
	}

	@Test
	void aSpendingIsStoredWithItsAccountAndWithoutAPayee() {
		Checked spend = check(draft("SPEND", null, PERSONAL_FOOD.id(), AccIds.ACCOUNT_CASH, null, null, "80", null), PERSONAL,
				PERSONAL_FOOD, null, CASH, null);
		assertThat(spend.payeeId()).isNull();
		assertThat(spend.accountId()).isEqualTo(AccIds.ACCOUNT_CASH);
		assertThat(spend.amount()).isEqualByComparingTo("80.00");
	}

	@Test
	void aPaymentHasNoTypeAndNeedsAnAccount() {
		Draft draft = draft("PAYMENT", RAHIM.id(), LECTURE.id(), AccIds.ACCOUNT_CASH, null, null, "500", null);
		Checked payment = check(draft, PAYEES, null, RAHIM, CASH, null);
		assertThat(payment.typeId()).isNull();
		assertThat(payment.amount()).isEqualByComparingTo("500.00");
	}

	@Test
	void aLegacyCountedEntryKeepsItsTypedAmountAndHasNoPrice() {
		Checked legacy = AccRules.check(draft("CHARGE", RAHIM.id(), LECTURE.id(), null, "3", null, "1500", null), PAYEES, LECTURE,
				RAHIM, null, null, true, TODAY);
		assertThat(legacy.amount()).isEqualByComparingTo("1500.00");
		assertThat(legacy.quantity()).isEqualByComparingTo("3");
		assertThat(legacy.unitPrice()).isNull();
	}

	@Test
	void anEditKeepsItsArchivedTypeAndAccountButANewChoiceOfThemIsRefused() {
		TypeRef archived = new TypeRef(BONUS.id(), AccIds.GROUP_OFFLINE_INSTRUCTORS, "AMOUNT", false, false, true);
		AccountRef archivedAccount = new AccountRef(AccIds.ACCOUNT_BKASH, true);
		Draft draft = draft("CHARGE", RAHIM.id(), archived.id(), null, null, null, "300", null);
		Checked kept = check(draft, PAYEES, archived, RAHIM, null, new Was(AccIds.GROUP_OFFLINE_INSTRUCTORS, archived.id(), null));
		assertThat(kept.typeId()).isEqualTo(archived.id());
		assertRefused(() -> check(draft, PAYEES, archived, RAHIM, null, null), "type_archived");

		Draft payment = draft("PAYMENT", RAHIM.id(), null, AccIds.ACCOUNT_BKASH, null, null, "100", null);
		assertRefused(() -> check(payment, PAYEES, null, RAHIM, archivedAccount, null), "account_archived");
		Checked keptAccount = check(payment, PAYEES, null, RAHIM, archivedAccount,
				new Was(AccIds.GROUP_OFFLINE_INSTRUCTORS, null, AccIds.ACCOUNT_BKASH));
		assertThat(keptAccount.accountId()).isEqualTo(AccIds.ACCOUNT_BKASH);
	}

	@Test
	void anArchivedGroupTakesNoNewEntry() {
		GroupRef archived = new GroupRef(AccIds.GROUP_PERSONAL, "LOG", "EXPENSE", true);
		Draft draft = draft("SPEND", null, PERSONAL_FOOD.id(), AccIds.ACCOUNT_CASH, null, null, "80", null);
		assertRefused(() -> check(draft, archived, PERSONAL_FOOD, null, CASH, null), "group_archived");
		assertThat(check(draft, archived, PERSONAL_FOOD, null, CASH,
				new Was(AccIds.GROUP_PERSONAL, PERSONAL_FOOD.id(), AccIds.ACCOUNT_CASH)).amount()).isEqualByComparingTo("80.00");
	}

	@Test
	void aKindMustFitTheGroupsModeAndDirection() {
		assertRefused(() -> check(draft("CHARGE", RAHIM.id(), LECTURE.id(), null, "1", "10", null, null), PERSONAL, LECTURE, RAHIM,
				null, null), "group_mode");
		assertRefused(() -> check(draft("SPEND", null, PERSONAL_FOOD.id(), AccIds.ACCOUNT_CASH, null, null, "5", null), PAYEES,
				PERSONAL_FOOD, null, CASH, null), "group_mode");
		assertRefused(() -> check(draft("INCOME", null, PERSONAL_FOOD.id(), AccIds.ACCOUNT_CASH, null, null, "5", null), PERSONAL,
				PERSONAL_FOOD, null, CASH, null), "group_mode");
		TypeRef personalDeduction = new TypeRef(UUID.randomUUID(), AccIds.GROUP_PERSONAL, "DEDUCTION", false, false, false);
		assertRefused(() -> check(draft("SPEND", null, personalDeduction.id(), AccIds.ACCOUNT_CASH, null, null, "5", null), PERSONAL,
				personalDeduction, null, CASH, null), "group_mode");
		assertThat(check(draft("INCOME", null, PERSONAL_FOOD.id(), AccIds.ACCOUNT_CASH, null, null, "5", null), OTHER_INCOME,
				new TypeRef(PERSONAL_FOOD.id(), AccIds.GROUP_OTHER_INCOME, "AMOUNT", false, false, false), null, CASH, null).kind())
				.isEqualTo("INCOME");
	}

	@Test
	void payeeAndTypeBelongToTheGroup() {
		assertRefused(() -> check(draft("CHARGE", null, LECTURE.id(), null, "1", "10", null, null), PAYEES, LECTURE, RAHIM, null, null),
				"payee_required");
		PayeeRef otherGroup = new PayeeRef(UUID.randomUUID(), AccIds.GROUP_STAFF);
		assertRefused(() -> check(draft("CHARGE", otherGroup.id(), LECTURE.id(), null, "1", "10", null, null), PAYEES, LECTURE,
				otherGroup, null, null), "payee_wrong_group");
		TypeRef staffType = new TypeRef(UUID.randomUUID(), AccIds.GROUP_STAFF, "AMOUNT", false, false, false);
		assertRefused(() -> check(draft("CHARGE", RAHIM.id(), staffType.id(), null, null, null, "10", null), PAYEES, staffType, RAHIM,
				null, null), "type_wrong_group");
	}

	@Test
	void aMonthIsNeededWhenTheTypeAsksForOne() {
		assertRefused(() -> check(draft("CHARGE", UUID.randomUUID(), SALARY.id(), null, null, null, "25000", null),
				new GroupRef(AccIds.GROUP_STAFF, "PAYEES", "EXPENSE", false), SALARY,
				new PayeeRef(UUID.randomUUID(), AccIds.GROUP_STAFF), null, null), "period_required");
	}

	@Test
	void aDescriptionIsNeededWhenTheTypeAsksForOne() {
		assertRefused(() -> check(draft("CHARGE", RAHIM.id(), OTHER.id(), null, null, null, "100", null), PAYEES, OTHER, RAHIM, null,
				null), "description_required");
	}

	@Test
	void aCountedEntryNeedsAQuantityAndAPrice() {
		assertRefused(() -> check(draft("CHARGE", RAHIM.id(), LECTURE.id(), null, null, "1000", null, null), PAYEES, LECTURE, RAHIM,
				null, null), "quantity_required");
		assertRefused(() -> check(draft("CHARGE", RAHIM.id(), LECTURE.id(), null, "12", null, null, null), PAYEES, LECTURE, RAHIM,
				null, null), "price_required");
	}

	@Test
	void amountsAboveTheCapsAreRefused() {
		assertRefused(() -> check(draft("CHARGE", RAHIM.id(), LECTURE.id(), null, "1", "1000001", null, null), PAYEES, LECTURE, RAHIM,
				null, null), "amount_too_large");
		assertRefused(() -> check(draft("CHARGE", RAHIM.id(), LECTURE.id(), null, "100000", "1000000", null, null), PAYEES, LECTURE,
				RAHIM, null, null), "amount_too_large");
		assertRefused(() -> check(draft("CHARGE", RAHIM.id(), BONUS.id(), null, null, null, "10000000.01", null), PAYEES, BONUS, RAHIM,
				null, null), "amount_too_large");
	}

	@Test
	void anAmountAboveZeroIsRequiredAndAPaymentNeedsAnAccount() {
		assertFieldError(() -> check(draft("CHARGE", RAHIM.id(), BONUS.id(), null, null, null, "0", null), PAYEES, BONUS, RAHIM, null,
				null), "amount");
		assertRefused(() -> check(draft("PAYMENT", RAHIM.id(), null, null, null, null, "100", null), PAYEES, null, RAHIM, null, null),
				"account_required");
	}

	@Test
	void aDateAfterTodayIsRefusedAndADateIsRequired() {
		Draft tomorrow = new Draft("CHARGE", RAHIM.id(), BONUS.id(), null, LocalDate.parse("2026-11-28"), null, null, null,
				new BigDecimal("100"), null);
		assertRefused(() -> AccRules.check(tomorrow, PAYEES, BONUS, RAHIM, null, null, false, TODAY), "future_date");
		assertThat(AccRules.check(new Draft("CHARGE", RAHIM.id(), BONUS.id(), null, TODAY, null, null, null,
				new BigDecimal("100"), null), PAYEES, BONUS, RAHIM, null, null, false, TODAY).date()).isEqualTo(TODAY);
		assertFieldError(() -> AccRules.check(new Draft("CHARGE", RAHIM.id(), BONUS.id(), null, null, null, null, null,
				new BigDecimal("100"), null), PAYEES, BONUS, RAHIM, null, null, false, TODAY), "date");
	}

	@Test
	void theKindIsRequired() {
		assertFieldError(() -> check(draft("SALE", RAHIM.id(), BONUS.id(), null, null, null, "100", null), PAYEES, BONUS, RAHIM, null,
				null), "kind");
	}

	@Test
	void paidNowIsAPaymentOfUpToTheChargeAndNotForADeduction() {
		Checked charge = check(draft("CHARGE", RAHIM.id(), LECTURE.id(), null, "12", "1000", null, null), PAYEES, LECTURE, RAHIM, null,
				null);
		Checked paid = AccRules.paidNow(charge, new BigDecimal("5000"), CASH, null);
		assertThat(paid.kind()).isEqualTo("PAYMENT");
		assertThat(paid.payeeId()).isEqualTo(RAHIM.id());
		assertThat(paid.accountId()).isEqualTo(AccIds.ACCOUNT_CASH);
		assertThat(paid.amount()).isEqualByComparingTo("5000.00");

		assertRefused(() -> AccRules.paidNow(charge, new BigDecimal("12001"), CASH, null), "paid_now_too_much");
		assertRefused(() -> AccRules.paidNow(charge, new BigDecimal("100"), null, null), "account_required");
		assertRefused(() -> AccRules.paidNow(charge, new BigDecimal("100"), new AccountRef(AccIds.ACCOUNT_BKASH, true), null),
				"account_archived");
		Checked deduction = check(draft("CHARGE", RAHIM.id(), DEDUCTION.id(), null, null, null, "500", null), PAYEES, DEDUCTION, RAHIM,
				null, null);
		assertRefused(() -> AccRules.paidNow(deduction, new BigDecimal("100"), CASH, null), "paid_now_deduction");
	}

	private static Checked check(Draft draft, GroupRef group, TypeRef type, PayeeRef payee, AccountRef account, Was was) {
		return AccRules.check(draft, group, type, payee, account, was, false, TODAY);
	}

	/** A draft dated on DAY, with the given values; a null amount argument stays null. */
	private static Draft draft(String kind, UUID payee, UUID type, UUID account, String quantity, String unitPrice, String amount,
			String period) {
		return new Draft(kind, payee, type, account, DAY, period, quantity == null ? null : new BigDecimal(quantity),
				unitPrice == null ? null : new BigDecimal(unitPrice), amount == null ? null : new BigDecimal(amount), null);
	}

	private static void assertRefused(ThrowingCallable call, String key) {
		assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, exception -> {
			assertThat(exception.code()).isEqualTo(ErrorCode.ACC_REFUSED);
			assertThat(exception.getMessage()).isEqualTo("accounting.errors." + key);
		});
	}

	private static void assertFieldError(ThrowingCallable call, String field) {
		assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, exception -> {
			assertThat(exception.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
			assertThat(exception.status().value()).isEqualTo(400);
			assertThat(exception.details().toString()).contains("field=" + field);
		});
	}

}
