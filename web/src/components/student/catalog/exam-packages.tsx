"use client";

import Link from "next/link";
import type { UseQueryResult } from "@tanstack/react-query";
import { useLocale } from "@/i18n/locale-context";
import { formatNumber, pickTitle } from "@/lib/format";
import type { PackageRef, PublicModule } from "@/lib/api/exam/types";
import { Pill } from "@/components/ui/pill";
import { modulePath, type Door } from "./catalog-helpers";
import { FavouriteButton } from "./favourite-button";
import { PriceLine } from "./price-line";
import { priceLabel } from "./price-view";

/**
 * The packages an exam is in (plan S2.4): each card has the title, the exam count, the price the package sells at, and a link to
 * the package page, which does not change. A card waits for its read before it shows the count and the price.
 */
export function ExamPackages({
  door,
  packages,
  reads,
}: {
  door: Door;
  packages: PackageRef[];
  reads: UseQueryResult<PublicModule>[];
}) {
  return (
    <div className="grid gap-3">
      {packages.map((ref, index) => (
        <PackageLinkCard key={ref.id} href={modulePath(door, ref.id)} pkg={reads[index]?.data} fallback={ref} />
      ))}
    </div>
  );
}

/**
 * A package as a row card: its title, its exam count, its price, and the favourite mark (plan S3.3). The title link covers the card
 * (a stretched link), and the favourite mark sits above it. A package still loading shows its title from `fallback`.
 */
export function PackageLinkCard({
  href,
  pkg,
  fallback,
  popular,
}: {
  href: string;
  pkg: PublicModule | undefined;
  fallback: PackageRef;
  popular?: boolean;
}) {
  const { t, locale } = useLocale();
  const title = pkg ? pickTitle(pkg, locale) : pickTitle(fallback, locale);
  const examCount = (pkg?.mcqCount ?? 0) + (pkg?.writtenCount ?? 0);
  return (
    <div className="group relative flex min-h-11 min-w-0 items-center gap-3 rounded-card border border-border bg-card p-4 transition-colors hover:border-accent">
      <div className="min-w-0 flex-1">
        <p className="flex min-w-0 flex-wrap items-center gap-2">
          {popular ? <Pill tone="accent">{t("catalog.popular.badge")}</Pill> : null}
          <Link href={href} prefetch={false} className="text-card-title font-bold after:absolute after:inset-0 after:content-[''] group-hover:text-text-accent">
            {title}
          </Link>
        </p>
        {pkg ? (
          <div className="mt-1 flex flex-wrap items-center justify-between gap-2 text-meta">
            <span className="text-muted">{t("common.purchase.shared.examCount", { count: formatNumber(examCount, locale) })}</span>
            <PriceLine label={priceLabel(pkg.pricing)} />
          </div>
        ) : null}
      </div>
      <FavouriteButton kind="package" id={fallback.id} initial={pkg?.favourite} next={href} />
    </div>
  );
}
