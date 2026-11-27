package com.coachingcenter.api.accounting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * The rules of one ledger entry (plan 4.4 and 4.5, contracts.md section 5). Pure: the caller loads the group, type, payee and account
 * rows and passes them in; this class says whether the entry may be saved and what to store. L calls it on create, on several at once
 * and on edit. A refusal is an ApiException (409 ACC_REFUSED, or a 400 field error); nothing here touches the database.
 */
public final class AccRules {

	public static final String CHARGE = "CHARGE";

	public static final String PAYMENT = "PAYMENT";

	public static final String SPEND = "SPEND";

	public static final String INCOME = "INCOME";

	private static final Set<String> KINDS = Set.of(CHARGE, PAYMENT, SPEND, INCOME);

	private AccRules() {
	}

	/** A group as loaded: its mode (PAYEES or LOG), direction (EXPENSE or INCOME) and whether it is archived. */
	public record GroupRef(UUID id, String mode, String direction, boolean archived) {
	}

	/** A type as loaded. */
	public record TypeRef(UUID id, UUID groupId, String kind, boolean needsPeriod, boolean needsDescription, boolean archived) {
	}

	/** A payee as loaded: only its group matters here. */
	public record PayeeRef(UUID id, UUID groupId) {
	}

	/** A money account as loaded. */
	public record AccountRef(UUID id, boolean archived) {
	}

	/**
	 * The entry as the user gave it. Money and quantity are already read by AccMoney.parseMoney and parseQuantity. The type, payee and
	 * account are passed as ids; the caller loads the rows that match them, or passes null when the id is null.
	 */
	public record Draft(String kind, UUID payeeId, UUID typeId, UUID accountId, LocalDate date, String period, BigDecimal quantity,
			BigDecimal unitPrice, BigDecimal amount, String description) {
	}

	/**
	 * What an edit keeps: the entry's own group, type and account. An archived one is allowed when it is kept (plan 4.4). Null for a new
	 * entry.
	 */
	public record Was(UUID groupId, UUID typeId, UUID accountId) {
	}

	/**
	 * The checked entry, with the values to store. The amount is signed: a deduction charge is negative. Quantity and unit price are
	 * null unless the type is counted (quantity is also kept for an optional spending quantity).
	 */
	public record Checked(String kind, UUID payeeId, UUID typeId, UUID accountId, LocalDate date, String period, BigDecimal quantity,
			BigDecimal unitPrice, BigDecimal amount, boolean counted, boolean deduction) {
	}

	/**
	 * Checks one entry and returns the values to store.
	 *
	 * @param legacy true for a moved-over entry of a counted type that has no unit price (its amount was typed; plan 4.4)
	 * @param today today in Dhaka, from AccDates.today
	 */
	public static Checked check(Draft draft, GroupRef group, TypeRef type, PayeeRef payee, AccountRef account, Was was, boolean legacy,
			LocalDate today) {
		String kind = draft.kind();
		if (kind == null || !KINDS.contains(kind)) {
			throw AccRefusals.validation("kind", "Choose the kind of entry.");
		}
		boolean payeeKind = CHARGE.equals(kind) || PAYMENT.equals(kind);
		boolean fits = payeeKind ? "PAYEES".equals(group.mode())
				: "LOG".equals(group.mode()) && (SPEND.equals(kind) ? "EXPENSE".equals(group.direction())
						: "INCOME".equals(group.direction()));
		if (!fits) {
			throw AccRefusals.refuse("group_mode");
		}
		if (group.archived() && (was == null || !group.id().equals(was.groupId()))) {
			throw AccRefusals.refuse("group_archived");
		}
		if (draft.date() == null) {
			throw AccRefusals.validation("date", "Enter the date.");
		}
		AccDates.notFuture(draft.date(), today);

		// Payee: a charge and a payment name one, and it must be in this group. Other kinds never store one.
		UUID payeeOut = null;
		if (payeeKind) {
			if (draft.payeeId() == null) {
				throw AccRefusals.refuse("payee_required");
			}
			if (payee == null) {
				throw AccRefusals.notFound("payee");
			}
			if (!payee.groupId().equals(group.id())) {
				throw AccRefusals.refuse("payee_wrong_group");
			}
			payeeOut = payee.id();
		}

		// Type: every kind but a payment names one of the group's types; a payment never does.
		TypeRef typeChosen = null;
		if (!PAYMENT.equals(kind)) {
			if (draft.typeId() == null) {
				throw AccRefusals.validation("typeId", "Choose a type.");
			}
			if (type == null) {
				throw AccRefusals.notFound("type");
			}
			if (!type.groupId().equals(group.id())) {
				throw AccRefusals.refuse("type_wrong_group");
			}
			if (type.archived() && (was == null || !type.id().equals(was.typeId()))) {
				throw AccRefusals.refuse("type_archived");
			}
			if ("DEDUCTION".equals(type.kind()) && !CHARGE.equals(kind)) {
				throw AccRefusals.refuse("group_mode");
			}
			typeChosen = type;
		}
		boolean counted = typeChosen != null && "COUNTED".equals(typeChosen.kind());
		boolean deduction = typeChosen != null && "DEDUCTION".equals(typeChosen.kind());
		UUID typeOut = typeChosen == null ? null : typeChosen.id();

		// Month, when the type asks for one.
		String periodOut = null;
		if (typeChosen != null && typeChosen.needsPeriod()) {
			if (draft.period() == null || draft.period().isBlank()) {
				throw AccRefusals.refuse("period_required");
			}
			periodOut = AccDates.parseMonth(draft.period(), "period").toString();
		}

		// Description, when the type asks for one.
		if (typeChosen != null && typeChosen.needsDescription()
				&& (draft.description() == null || draft.description().isBlank())) {
			throw AccRefusals.refuse("description_required");
		}

		// Amounts. A counted type is quantity times unit price (the amount sent is ignored), except a moved-over counted entry with no
		// unit price, which keeps its typed amount.
		BigDecimal quantityOut;
		BigDecimal unitOut = null;
		BigDecimal amountOut;
		if (counted && !(legacy && draft.unitPrice() == null)) {
			if (draft.quantity() == null) {
				throw AccRefusals.refuse("quantity_required");
			}
			if (draft.unitPrice() == null) {
				throw AccRefusals.refuse("price_required");
			}
			if (draft.unitPrice().compareTo(AccMoney.MAX_UNIT_PRICE) > 0) {
				throw AccRefusals.refuse("amount_too_large");
			}
			amountOut = AccMoney.counted(draft.quantity(), draft.unitPrice());
			if (amountOut.compareTo(AccMoney.MAX_AMOUNT) > 0) {
				throw AccRefusals.refuse("amount_too_large");
			}
			if (amountOut.signum() <= 0) {
				throw AccRefusals.validation("amount", "Enter an amount above 0.");
			}
			quantityOut = draft.quantity();
			unitOut = draft.unitPrice();
		}
		else {
			if (draft.amount() == null) {
				throw AccRefusals.validation("amount", "Enter the amount.");
			}
			if (draft.amount().signum() <= 0) {
				throw AccRefusals.validation("amount", "Enter an amount above 0.");
			}
			if (draft.amount().compareTo(AccMoney.MAX_AMOUNT) > 0) {
				throw AccRefusals.refuse("amount_too_large");
			}
			amountOut = draft.amount().setScale(2, RoundingMode.HALF_UP);
			// A payment has no quantity. An optional quantity on another kind is kept; a unit price is only for counted types.
			quantityOut = PAYMENT.equals(kind) ? null : draft.quantity();
		}
		if (deduction && CHARGE.equals(kind)) {
			amountOut = amountOut.negate();
		}

		// Money account: every kind but a charge names one, and it must not be archived unless the entry keeps it.
		UUID accountOut = null;
		if (!CHARGE.equals(kind)) {
			if (draft.accountId() == null) {
				throw AccRefusals.refuse("account_required");
			}
			if (account == null) {
				throw AccRefusals.notFound("account");
			}
			if (account.archived() && (was == null || !account.id().equals(was.accountId()))) {
				throw AccRefusals.refuse("account_archived");
			}
			accountOut = account.id();
		}

		return new Checked(kind, payeeOut, typeOut, accountOut, draft.date(), periodOut, quantityOut, unitOut, amountOut, counted,
				deduction);
	}

	/**
	 * A payment made together with a charge ("Paid now", A6). The amount is above 0 and not more than the charge; a deduction cannot be
	 * paid now; the account is required and, unless the caller keeps it, must not be archived. The result is a PAYMENT to save.
	 *
	 * @param wasAccountId the account an edit keeps, or null
	 */
	public static Checked paidNow(Checked charge, BigDecimal paid, AccountRef account, UUID wasAccountId) {
		if (!CHARGE.equals(charge.kind())) {
			throw new IllegalArgumentException("Paid now needs a charge.");
		}
		if (charge.deduction()) {
			throw AccRefusals.refuse("paid_now_deduction");
		}
		if (paid == null || paid.signum() <= 0) {
			throw AccRefusals.validation("paidAmount", "Enter an amount above 0.");
		}
		if (paid.compareTo(charge.amount()) > 0) {
			throw AccRefusals.refuse("paid_now_too_much");
		}
		if (account == null) {
			throw AccRefusals.refuse("account_required");
		}
		if (account.archived() && !account.id().equals(wasAccountId)) {
			throw AccRefusals.refuse("account_archived");
		}
		return new Checked(PAYMENT, charge.payeeId(), null, account.id(), charge.date(), null, null, null,
				paid.setScale(2, RoundingMode.HALF_UP), false, false);
	}

}
