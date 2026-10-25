"use client";

import type { ReactNode } from "react";
import { useLocale } from "@/i18n/locale-context";
import { formatMoney } from "@/lib/format";
import { cn } from "@/components/ui/cn";

/**
 * The price bar at the top of an offer. The price is shown with Bengali digits;
 * when it is not set, the bar says so and gives no buy action. `status` and
 * `action` sit on the right on desktop and below the price on phones.
 */
export function PriceBar({
  price,
  priceNote,
  status,
  action,
  secondary,
}: {
  price: number | null;
  priceNote?: string;
  status?: ReactNode;
  action?: ReactNode;
  secondary?: ReactNode;
}) {
  const { t, locale } = useLocale();
  const formatted = formatMoney(price, locale);
  return (
    <section className="grid gap-3 rounded-card border border-border bg-card p-4 md:flex md:items-center md:justify-between md:gap-4">
      <div className="grid min-w-0 gap-1">
        {formatted !== null ? (
          <p className="text-section font-semibold tabular-nums text-text-accent">{formatted}</p>
        ) : (
          <p className="text-card font-medium text-muted">{t("common.purchase.shared.noPrice")}</p>
        )}
        {priceNote ? <p className="text-meta text-muted">{priceNote}</p> : null}
        {secondary ? <div className="text-meta text-muted">{secondary}</div> : null}
      </div>
      {status || action ? (
        <div className={cn("flex flex-wrap items-center gap-3", "md:justify-end")}>
          {status}
          {action}
        </div>
      ) : null}
    </section>
  );
}
