"use client";

import { useLocale } from "@/i18n/locale-context";
import { formatDate, formatMoney } from "@/lib/format";
import { cn } from "@/components/ui/cn";
import type { PriceLabel } from "./price-view";

/**
 * A price for a list row or the detail: one price, or the effective price beside the list price struck through, with a green
 * discount chip and the end date (plan Q15, S14.2). The discounted block is one wrapping line, so on a narrow row the end date
 * drops to its own line instead of squeezing the amounts. Free, package-only and no price each get a short word, never a zero
 * (plan S2.2).
 */
export function PriceLine({ label, size = "row", className }: { label: PriceLabel; size?: "row" | "detail"; className?: string }) {
  const { t, locale } = useLocale();
  const amountClass = size === "detail" ? "text-card-title font-extrabold text-text-accent" : "text-body font-bold text-text";

  if (label.kind === "free") {
    return <span className={cn("font-bold text-ok-text", className)}>{t("catalog.price.free")}</span>;
  }
  if (label.kind === "moduleOnly") {
    return <span className={cn("text-meta text-muted", className)}>{t("catalog.price.moduleOnly")}</span>;
  }
  if (label.kind === "none") {
    return <span className={cn("text-meta text-muted", className)}>{t("catalog.priceNotSet")}</span>;
  }
  if (label.kind === "price") {
    return <span className={cn("tabular-nums", amountClass, className)}>{formatMoney(label.amount, locale)}</span>;
  }
  const endText = label.endsAt ? t("catalog.price.until", { date: formatDate(label.endsAt, locale, { year: "auto" }) }) : null;
  return (
    <span className={cn("flex flex-wrap items-center justify-end gap-x-2 gap-y-0.5 text-end", className)}>
      <span className={cn("tabular-nums", amountClass)}>{formatMoney(label.effective, locale)}</span>
      <s className="text-meta tabular-nums text-muted">
        <span className="sr-only">{t("catalog.price.listLabel")} </span>
        {formatMoney(label.list, locale)}
      </s>
      <span className="rounded-control bg-ok/10 px-1.5 py-0.5 text-pill font-bold text-ok-text">
        {t("catalog.price.discount", { percent: String(label.percent) })}
      </span>
      {endText ? <span className="text-meta text-muted">{endText}</span> : null}
    </span>
  );
}
