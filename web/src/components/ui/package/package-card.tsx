"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { useLocale } from "@/i18n/locale-context";
import { formatNumber } from "@/lib/format";
import type { PriceView } from "@/lib/api/exam/types";
import { PackageCover } from "@/components/ui/package/package-cover";
import { PriceLine } from "@/components/student/catalog/price-line";
import { priceLabel, pricingOf } from "@/components/student/catalog/price-view";

/**
 * A package in a grid: a 16:9 cover, the title and summary (each at most two lines), and one
 * meta row with the exam count and the price. The price is the same line the detail page shows, so a
 * discounted package carries its discount chip, struck list price and end date. Long text is clamped,
 * never pushed out of the card.
 */
export function PackageCard({
  href,
  cover,
  type,
  title,
  summary,
  examCount,
  price,
  pricing,
  footer,
}: {
  href: string;
  cover: string | null;
  type: "MCQ" | "WRITTEN" | null;
  title: string;
  summary?: string | null;
  examCount: number;
  price: number | null;
  /** The package's `pricing` object, which carries the discount. Falls back to the bare `price` when absent. */
  pricing?: PriceView | null;
  footer?: ReactNode;
}) {
  const { t, locale } = useLocale();
  return (
    <Link
      href={href}
      prefetch
      className="group flex h-full min-w-0 flex-col overflow-hidden rounded-card border border-border bg-card transition duration-200 ease-out hover:-translate-y-0.5 hover:border-accent"
    >
      <PackageCover src={cover} alt={t("catalog.package.coverAlt", { title })} type={type} title={title} className="rounded-none" />
      <div className="flex min-w-0 flex-1 flex-col gap-2 p-4">
        <p className="line-clamp-2 text-card-title font-bold leading-snug text-text group-hover:text-text-accent">{title}</p>
        {summary ? <p className="line-clamp-2 text-body leading-relaxed text-muted">{summary}</p> : null}
        {/* Wraps instead of squeezing: on a narrow card the price (with its discount and end date) drops under the count. */}
        <div className="mt-auto flex min-w-0 flex-wrap items-center justify-between gap-x-3 gap-y-2 border-t border-border pt-3 text-meta">
          <span className="min-w-0 tabular-nums text-muted">
            {t("common.purchase.shared.examCount", { count: formatNumber(examCount, locale) })}
          </span>
          <span className="min-w-0 max-w-full text-end">
            <PriceLine label={priceLabel(pricingOf(pricing, price))} endOnOwnLine />
          </span>
        </div>
        {footer ? <div>{footer}</div> : null}
      </div>
    </Link>
  );
}
