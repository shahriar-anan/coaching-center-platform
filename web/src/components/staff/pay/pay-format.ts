import { ApiError } from "@/lib/api/errors";
import type { LedgerKind, PayLedgerRow, PayMethod } from "@/lib/api/exam/staff-pay";
import { dhakaParts } from "@/lib/format";

// Pure helpers for the staff pay screens (staff pay plan, phase 3). No React here, so the rules can be tested directly.

export const PAY_METHODS: PayMethod[] = ["BANK", "BKASH", "NAGAD", "ROCKET"];

/** The translation key for a payment method. */
export function methodKey(method: PayMethod | null | undefined): string | null {
  return method ? `staffPay.method.${method}` : null;
}

/** The translation key for a ledger line's kind. */
export function kindKey(kind: LedgerKind): string {
  return `staffPay.kind.${kind}`;
}

/** Only a live payment or adjustment can be voided. A salary or marking line is offset by an adjustment instead. */
export function isVoidable(row: Pick<PayLedgerRow, "kind" | "voided">): boolean {
  return !row.voided && (row.kind === "PAYMENT" || row.kind === "ADJUSTMENT");
}

/**
 * A typed amount as a number of taka, or null when it is not a positive amount with at most two decimal places. Commas are
 * accepted and dropped, so "10,000" reads as 10000.
 */
export function parseAmount(text: string): number | null {
  const cleaned = text.replace(/,/g, "").trim();
  if (!/^\d+(\.\d{1,2})?$/.test(cleaned)) {
    return null;
  }
  const value = Number(cleaned);
  return value > 0 ? value : null;
}

/** A payment above the balance is an advance: the screen asks the Master Admin to tick it first. */
export function isAdvance(amount: number, balance: number): boolean {
  return amount > balance;
}

/** Today's date in Dhaka as YYYY-MM-DD, the default for a payment or adjustment date. */
export function todayInDhaka(now: number = Date.now()): string {
  const parts = dhakaParts(now);
  return `${parts.year}-${String(parts.month + 1).padStart(2, "0")}-${String(parts.day).padStart(2, "0")}`;
}

/** An adjustment amount: a non-zero number of taka, plus or minus, with at most two decimal places. */
export function parseSignedAmount(text: string): number | null {
  const cleaned = text.replace(/,/g, "").trim();
  if (!/^-?\d+(\.\d{1,2})?$/.test(cleaned)) {
    return null;
  }
  const value = Number(cleaned);
  return value === 0 ? null : value;
}

/** The balance a payment starts from: what is owed now, so the amount field can default to it. */
export function defaultPaymentAmount(owedNow: number): string {
  return owedNow > 0 ? owedNow.toFixed(2) : "";
}

/** A translation key for a failed pay call, by the API's error code. Anything unknown reads as the generic message. */
export function payErrorKey(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return "staffPay.errors.generic";
  }
  switch (error.code) {
    case "PAYMENT_EXCEEDS_BALANCE":
      return "staffPay.errors.exceedsBalance";
    case "LEDGER_NOT_VOIDABLE":
      return "staffPay.errors.notVoidable";
    case "LEDGER_ALREADY_VOIDED":
      return "staffPay.errors.alreadyVoided";
    case "VALIDATION_FAILED":
      return "staffPay.errors.check";
    case "ACCESS_DENIED":
      return "staffPay.errors.denied";
    default:
      return "staffPay.errors.generic";
  }
}
