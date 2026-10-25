"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { useLocale } from "@/i18n/locale-context";
import { formatMoney, formatNumber } from "@/lib/format";

/**
 * A purchasable product as one link card, with the mockup's lift and gold
 * border on hover. The price line says so when no price is set.
 */
export function ProductCard({
  href,
  title,
  subtitle,
  pills,
  price,
  examCount,
  footer,
}: {
  href: string;
  title: string;
  subtitle?: string;
  pills?: ReactNode;
  price: number | null;
  examCount: number;
  footer?: ReactNode;
}) {
  const { t, locale } = useLocale();
  const priceText = formatMoney(price, locale);
  return (
    <Link
      href={href}
      prefetch
      className="grid gap-3 rounded-card border border-border bg-card p-4 transition duration-200 ease-out hover:-translate-y-0.5 hover:border-accent"
    >
      <div className="grid gap-1">
        <span className="text-card font-bold text-text">{title}</span>
        {subtitle ? <span className="text-meta text-muted">{subtitle}</span> : null}
      </div>
      {pills ? <div className="flex flex-wrap gap-2">{pills}</div> : null}
      <div className="flex flex-wrap items-center justify-between gap-2 text-meta">
        <span className="tabular-nums text-muted">
          {t("common.purchase.shared.examCount", { count: formatNumber(examCount, locale) })}
        </span>
        <span className="font-semibold tabular-nums text-text">
          {priceText ?? t("common.purchase.shared.noPrice")}
        </span>
      </div>
      {footer ? <div>{footer}</div> : null}
    </Link>
  );
}
