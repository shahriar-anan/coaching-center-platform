"use client";

import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import type { PriceView } from "@/lib/api/exam/staff-catalog";

/**
 * What the price cell shows. A discount is shown only when the API's effective price is below the list price. A discount
 * that has ended comes back with an effective price equal to the list price, so it shows as a plain price.
 */
export type PriceCell =
  | { kind: "free" }
  | { kind: "none" }
  | { kind: "plain"; amount: number }
  | { kind: "discount"; list: number; effective: number; percent: number };

export function priceCell(pricing: PriceView | null | undefined): PriceCell {
  if (pricing?.free) {
    return { kind: "free" };
  }
  if (!pricing || pricing.price === null) {
    return { kind: "none" };
  }
  const effective = pricing.effectivePrice ?? pricing.price;
  if (typeof pricing.discountPercent === "number" && effective < pricing.price) {
    return { kind: "discount", list: pricing.price, effective, percent: pricing.discountPercent };
  }
  return { kind: "plain", amount: effective };
}

/**
 * The price of an exam on the Exams page. A discounted price shows the list price struck through, the effective price
 * beside it, and the discount percent. Digits and money come from the shared formatters.
 */
export function ExamPrice({ pricing }: { pricing: PriceView | null | undefined }) {
  const { t } = useLocale();
  const { formatMoney, formatNumber } = useFormat();
  const cell = priceCell(pricing);
  if (cell.kind === "free") {
    return <>{t("staffExams.row.free")}</>;
  }
  if (cell.kind === "none") {
    return <>{t("staffExams.row.noPrice")}</>;
  }
  if (cell.kind === "plain") {
    return <>{formatMoney(cell.amount)}</>;
  }
  return (
    <span className="inline-flex flex-wrap items-baseline gap-x-2">
      <s className="text-muted">{formatMoney(cell.list)}</s>
      <span className="font-semibold text-text">{formatMoney(cell.effective)}</span>
      <span className="text-meta text-muted">{t("staffExams.row.discountOff", { percent: formatNumber(cell.percent) })}</span>
    </span>
  );
}
