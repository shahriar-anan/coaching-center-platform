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
export function writeErrorText(error: unknown, t: Translate): string {
  if (error instanceof ApiError) {
    if (error.code === "ACC_REFUSED" && error.message.startsWith("accounting.errors.")) {
      return t(error.message);
    }
    if (error.code === "CONFLICT" || error.code === "STALE_VERSION") {
      return t("accounting.common.stale");
    }
  }
  return t("accounting.common.failed");
}
