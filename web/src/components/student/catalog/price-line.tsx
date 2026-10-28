"use client";

import { useLocale } from "@/i18n/locale-context";
import { formatDate, formatMoney } from "@/lib/format";
import { cn } from "@/components/ui/cn";
import type { PriceLabel } from "./price-view";

/**
 * A price for a list row or the detail: one price, or the list price struck through beside the effective price with the
 * discount and its end date (plan Q15). Free, package-only and no price each get a short word, never a zero (plan S2.2).
 */
export function PriceLine({ label, size = "row", className }: { label: PriceLabel; size?: "row" | "detail"; className?: string }) {
  const { t, locale } = useLocale();
  const amountClass = size === "detail" ? "text-card-title font-extrabold text-text-accent" : "text-label font-bold text-text";

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
    <span className={cn("grid justify-items-end gap-0.5", className)}>
      <span className="flex flex-wrap items-baseline justify-end gap-x-2">
        <s className="text-meta tabular-nums text-muted">
          <span className="sr-only">{t("catalog.price.listLabel")} </span>
          {formatMoney(label.list, locale)}
        </s>
        <span className={cn("tabular-nums", amountClass)}>{formatMoney(label.effective, locale)}</span>
      </span>
      <span className="text-meta text-ok-text">
        {t("catalog.price.discount", { percent: String(label.percent) })}
        {endText ? ` · ${endText}` : ""}
      </span>
    </span>
  );
}
