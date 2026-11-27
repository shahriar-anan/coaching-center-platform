import type { Locale } from "@/i18n/locale-context";
import type { AccountKind, Money } from "@/lib/api/accounting/types";
import { ApiError } from "@/lib/api/errors";
import { formatMoney } from "@/lib/format";

/*
 * Small helpers for the accounting screens (docs/accounting/accounting-plan.md). Money is a string from the API; it is shown with the
 * site's money format. The seeded money accounts are English only, so their Bangla names come from their kind (accountLabel).
 */

export type Translate = (key: string, params?: Record<string, string | number>) => string;

/** The English names the seed gives the money accounts (plan 5.3). Only these get a Bangla name by kind. */
export const SEEDED_ACCOUNT_NAMES: Record<AccountKind, string> = {
  CASH: "Cash box",
  BKASH: "bKash",
  NAGAD: "Nagad",
  ROCKET: "Rocket",
  BANK: "Bank",
};

/** The English or Bangla name of a record with both, the English one when there is no Bangla name (the API answers null then). */
export function pickName(record: { nameEn: string; nameBn: string | null }, locale: Locale): string {
  return locale === "bn" ? record.nameBn || record.nameEn : record.nameEn;
}

/** A money account's name. In Bangla a seeded account shows its Bangla name by kind; a name the admin typed is shown as it is. */
export function accountLabel(account: { name: string; kind: AccountKind | null }, locale: Locale, t: Translate): string {
  if (locale !== "bn") {
    return account.name;
  }
  const kind = account.kind ?? seededKindOf(account.name);
  if (kind && SEEDED_ACCOUNT_NAMES[kind] === account.name) {
    return t(`accounting.accountKind.${kind}`);
  }
  return account.name;
}

function seededKindOf(name: string): AccountKind | null {
  const found = (Object.keys(SEEDED_ACCOUNT_NAMES) as AccountKind[]).find((kind) => SEEDED_ACCOUNT_NAMES[kind] === name);
  return found ?? null;
}

/** A money figure in the site's format (`৳2,500.00`). A missing one reads as a dash. */
export function money(value: Money | null | undefined, locale: Locale): string {
  return formatMoney(value ?? null, locale) ?? "—";
}

/** Which side of the ledger a payee's balance is on. Owed is positive, advance negative (plan 4.4). */
export function balanceSide(balance: Money): "owed" | "advance" | "settled" {
  const amount = Number(balance);
  if (amount > 0) {
    return "owed";
  }
  return amount < 0 ? "advance" : "settled";
}

/** Changes a list's position by one step up or down. Returns the new order of the ids, or null when the move is off the list. */
export function moveId(ids: string[], id: string, step: -1 | 1): string[] | null {
  const index = ids.indexOf(id);
  const target = index + step;
  if (index < 0 || target < 0 || target >= ids.length) {
    return null;
  }
  const next = [...ids];
  [next[index], next[target]] = [next[target], next[index]];
  return next;
}

/**
 * The text to show for a failed write. A refusal (409 ACC_REFUSED) carries its message key, `accounting.errors.<key>`; a stale version
 * (409 CONFLICT or STALE_VERSION) asks the person to reload; anything else gets the generic failure text.
 */
export function writeErrorText(error: unknown, t: Translate, options: { withLine?: boolean } = {}): string {
  if (error instanceof ApiError) {
    // A save of several lines names the bad line: {"line": i} on a refusal, or a field "entries[i].…" on a field error.
    const line = options.withLine === false ? null : errorLine(error);
    const withLine = (text: string) => (line === null ? text : t("accounting.common.onLine", { text, line: line + 1 }));
    if (error.code === "ACC_REFUSED" && error.message.startsWith("accounting.errors.")) {
      return withLine(t(error.message));
    }
    if (line !== null && error.fieldErrors?.length) {
      return withLine(error.fieldErrors[0].message);
    }
    if (error.code === "CONFLICT" || error.code === "STALE_VERSION") {
      return t("accounting.common.stale");
    }
  }
  return t("accounting.common.failed");
}

/** The 0-based line of a several-lines save that the error is about, or null. */
export function errorLine(error: ApiError): number | null {
  const details = error.details as { line?: unknown } | null | undefined;
  if (details && typeof details.line === "number") {
    return details.line;
  }
  const field = error.fieldErrors?.[0]?.field ?? "";
  const match = /^entries\[(\d+)\]/.exec(field);
  return match ? Number(match[1]) : null;
}

/** A typed amount as the API takes it: two decimals, more than 0, with at most 2 decimal places. Null when it is not one. */
export function parseAmount(text: string): string | null {
  const value = text.trim().replace(/,/g, "");
  if (!/^\d{1,9}(\.\d{1,2})?$/.test(value)) {
    return null;
  }
  const amount = Number(value);
  return amount > 0 ? amount.toFixed(2) : null;
}

/** A typed quantity: more than 0, up to 2 decimals (plan 4.4). Returned without trailing zeros, as the API answers them. */
export function parseQuantity(text: string): string | null {
  const value = text.trim().replace(/,/g, "");
  if (!/^\d{1,6}(\.\d{1,2})?$/.test(value)) {
    return null;
  }
  const quantity = Number(value);
  return quantity > 0 ? String(quantity) : null;
}

/** Quantity × unit price, rounded half up to the paisa (the same rule as AccMoney.counted). Shown live; the server computes the saved one. */
export function countedAmount(quantity: string, unitPrice: string): string {
  const q = Math.round(Number(quantity) * 100);
  const p = Math.round(Number(unitPrice) * 100);
  const cents = Math.floor((q * p + 50) / 100);
  return (cents / 100).toFixed(2);
}

/** The month (`YYYY-MM`) of a Dhaka day. */
export function monthOf(date: string): string {
  return date.slice(0, 7);
}

/** A month moved by some months, as `YYYY-MM`. */
export function shiftMonth(month: string, delta: number): string {
  const [year, mon] = month.split("-").map(Number);
  const index = year * 12 + (mon - 1) + delta;
  const y = Math.floor(index / 12);
  const m = (index % 12) + 1;
  return `${y}-${String(m).padStart(2, "0")}`;
}

/** A month's name for the site: "November 2026" or "নভেম্বর ২০২৬". */
export function monthLabel(month: string, locale: Locale): string {
  const [year, mon] = month.split("-").map(Number);
  return new Intl.DateTimeFormat(locale === "bn" ? "bn-BD" : "en-GB", { month: "long", year: "numeric", timeZone: "UTC" }).format(
    new Date(Date.UTC(year, mon - 1, 1)),
  );
}

/** A unit as it reads with a count: "40 answer sheets" in English (singular in the data), Bangla unchanged. */
export function unitWithCount(unit: string, quantity: string, locale: Locale): string {
  return locale === "en" && quantity !== "1" ? pluralEnglish(unit) : unit;
}

/** The plural of a unit typed in the singular: class -> classes, poster -> posters, hour -> hours, body -> bodies. */
export function pluralEnglish(unit: string): string {
  if (/(s|x|z|ch|sh)$/i.test(unit)) return `${unit}es`;
  if (/[^aeiou]y$/i.test(unit)) return `${unit.slice(0, -1)}ies`;
  return `${unit}s`;
}
