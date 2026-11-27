"use client";

import { useLocale } from "@/i18n/locale-context";
import type { Money } from "@/lib/api/accounting/types";
import { balanceSide, money } from "./accounting-model";

/** A balance in words and colour: Owed (amber), Advance (blue), Settled (muted). Positive is owed, negative an advance (plan 4.4). */
export function BalanceText({ balance, large = false }: { balance: Money; large?: boolean }) {
  const { t, locale } = useLocale();
  const side = balanceSide(balance);
  const amount = money(Math.abs(Number(balance)).toFixed(2), locale);
  const size = large ? "text-title" : "text-body";
  if (side === "owed") {
    return <span className={`${size} font-semibold text-warn`}>{t("accounting.balance.owed", { amount })}</span>;
  }
  if (side === "advance") {
    return <span className={`${size} font-semibold text-accent-text`}>{t("accounting.balance.advance", { amount })}</span>;
  }
  return <span className={`${size} text-muted`}>{t("accounting.balance.settled")}</span>;
}
