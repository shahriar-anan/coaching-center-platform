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
    <div className="grid gap-2">
      {packages.map((ref, index) => (
        <PackageLinkCard key={ref.id} href={modulePath(door, ref.id)} pkg={reads[index]?.data} fallback={ref} />
      ))}
    </div>
  );
}

/**
 * A package as a row card: its title, its exam count under the title, its price on the right, and the favourite mark as the last
 * item (plan S3.3). The title link covers the card (a stretched link), and the favourite mark sits above it. A package still
 * loading shows its title from `fallback`.
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
    <div className="group relative flex min-h-14 min-w-0 items-center gap-2 rounded-card border border-border bg-card px-4 py-3 transition-colors hover:border-accent hover:bg-text/[0.06]">
      <div className="min-w-0 flex-1">
        <p className="flex min-w-0 items-center gap-2">
          {popular ? <span className="shrink-0"><Pill tone="accent">{t("catalog.popular.badge")}</Pill></span> : null}
          <Link
            href={href}
            prefetch={false}
            className="line-clamp-1 block min-w-0 text-body font-semibold leading-snug after:absolute after:inset-0 after:content-[''] group-hover:text-text-accent"
          >
            {title}
          </Link>
        </p>
        {pkg ? (
          <p className="mt-0.5 text-meta text-muted">{t("common.purchase.shared.examCount", { count: formatNumber(examCount, locale) })}</p>
        ) : null}
      </div>
      {pkg ? (
        <span className="shrink-0 text-end">
          <PriceLine label={priceLabel(pkg.pricing)} />
        </span>
      ) : null}
      <FavouriteButton kind="package" id={fallback.id} initial={pkg?.favourite} next={href} placement="inline" />
    </div>
  );
}
