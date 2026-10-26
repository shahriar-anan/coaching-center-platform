"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { useLocale } from "@/i18n/locale-context";
import { formatMoney, formatNumber } from "@/lib/format";
import { PackageCover } from "@/components/ui/package/package-cover";

/**
 * A package in a grid: a 16:9 cover, the title and summary (each at most two lines), and one
 * meta row with the exam count and the price. Long text is clamped, never pushed out of the card.
 */
export function PackageCard({
  href,
  cover,
  type,
  title,
  summary,
  examCount,
  price,
  footer,
}: {
  href: string;
  cover: string | null;
  type: "MCQ" | "WRITTEN" | null;
  title: string;
  summary?: string | null;
  examCount: number;
  price: number | null;
  footer?: ReactNode;
}) {
  const { t, locale } = useLocale();
  const priceText = formatMoney(price, locale);
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
        <div className="mt-auto flex min-w-0 items-center justify-between gap-3 border-t border-border pt-3 text-meta">
          <span className="min-w-0 truncate tabular-nums text-muted">
            {t("common.purchase.shared.examCount", { count: formatNumber(examCount, locale) })}
          </span>
          <span className="shrink-0 text-label font-bold tabular-nums text-text-accent">
            {priceText ?? t("common.purchase.shared.noPrice")}
          </span>
        </div>
        {footer ? <div>{footer}</div> : null}
      </div>
    </Link>
  );
}
