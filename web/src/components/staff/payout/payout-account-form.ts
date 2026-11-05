import type { PayoutAccount, PayoutAccountInput, PayoutAccountKind } from "@/lib/api/exam/payout-accounts";

// The form's state and its checks for one payout account (staff pay plan, phase 2). The API checks the same rules again and
// is the authority; these checks only stop an obvious mistake before the call and name the field.

export interface PayoutAccountDraft {
  kind: PayoutAccountKind;
  accountName: string;
  accountNumber: string;
  bankName: string;
  branch: string;
  routingNumber: string;
  walletNumber: string;
}

export type PayoutAccountFieldError = "required" | "accountNumber" | "routingNumber" | "walletNumber";

export type PayoutAccountDraftErrors = Partial<Record<keyof PayoutAccountDraft, PayoutAccountFieldError>>;

const NAME_MIN = 2;
const NAME_MAX = 120;

/** A bank account number: 6 to 20 digits once spaces and dashes are dropped. */
const ACCOUNT_NUMBER = /^[0-9]{6,20}$/;

/** A routing number: exactly 9 digits. */
const ROUTING_NUMBER = /^[0-9]{9}$/;

/** A Bangladesh mobile number: 01 and nine more digits, or +8801 and nine more. */
const WALLET_NUMBER = /^(01[0-9]{9}|\+8801[0-9]{9})$/;

export function emptyDraft(kind: PayoutAccountKind = "BANK"): PayoutAccountDraft {
  return { kind, accountName: "", accountNumber: "", bankName: "", branch: "", routingNumber: "", walletNumber: "" };
}

/** The draft an edit form starts from: the account's full numbers, so the owner sees what is stored. */
export function draftFrom(account: PayoutAccount): PayoutAccountDraft {
  return {
    kind: account.kind,
    accountName: account.accountName ?? "",
    accountNumber: account.accountNumber ?? "",
    bankName: account.bankName ?? "",
    branch: account.branch ?? "",
    routingNumber: account.routingNumber ?? "",
    walletNumber: account.walletNumber ?? "",
  };
}

/** Spaces and dashes are dropped from a number before it is checked or sent, as the API does. */
export function compactNumber(raw: string): string {
  return raw.replace(/[\s-]/g, "");
}

/** The field errors for a draft. An empty object means the draft may be sent. */
export function checkDraft(draft: PayoutAccountDraft): PayoutAccountDraftErrors {
  const errors: PayoutAccountDraftErrors = {};
  if (draft.kind !== "BANK") {
    if (!WALLET_NUMBER.test(compactNumber(draft.walletNumber))) {
      errors.walletNumber = "walletNumber";
    }
    return errors;
  }
  if (!inRange(draft.accountName, NAME_MIN, NAME_MAX)) {
    errors.accountName = "required";
  }
  if (!ACCOUNT_NUMBER.test(compactNumber(draft.accountNumber))) {
    errors.accountNumber = "accountNumber";
  }
  if (!inRange(draft.bankName, NAME_MIN, NAME_MAX)) {
    errors.bankName = "required";
  }
  if (!inRange(draft.branch, NAME_MIN, NAME_MAX)) {
    errors.branch = "required";
  }
  if (!ROUTING_NUMBER.test(compactNumber(draft.routingNumber))) {
    errors.routingNumber = "routingNumber";
  }
  return errors;
}

/** The request for a checked draft. Only the fields the kind uses are sent. */
export function inputFrom(draft: PayoutAccountDraft): PayoutAccountInput {
  if (draft.kind === "BANK") {
    return {
      kind: "BANK",
      accountName: draft.accountName.trim(),
      accountNumber: compactNumber(draft.accountNumber),
      bankName: draft.bankName.trim(),
      branch: draft.branch.trim(),
      routingNumber: compactNumber(draft.routingNumber),
    };
  }
  return { kind: draft.kind, walletNumber: compactNumber(draft.walletNumber) };
}

function inRange(raw: string, min: number, max: number): boolean {
  const length = raw.trim().length;
  return length >= min && length <= max;
}
