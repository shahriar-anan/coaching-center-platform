"use client";

import type { UseQueryResult } from "@tanstack/react-query";
import { useLocale } from "@/i18n/locale-context";
import { formatNumber, pickTitle } from "@/lib/format";
import type { PackageRef, PublicModule } from "@/lib/api/exam/types";
import { Card } from "@/components/ui/card";
import { modulePath, type Door } from "./catalog-helpers";
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
        <ExamPackageCard key={ref.id} door={door} pkgRef={ref} pkg={reads[index]?.data} />
      ))}
    </div>
  );
}

function ExamPackageCard({ door, pkgRef, pkg }: { door: Door; pkgRef: PackageRef; pkg: PublicModule | undefined }) {
  const { t, locale } = useLocale();
  const title = pkg ? pickTitle(pkg, locale) : pickTitle(pkgRef, locale);
  const examCount = (pkg?.mcqCount ?? 0) + (pkg?.writtenCount ?? 0);
  return (
    <Card href={modulePath(door, pkgRef.id)} padding="md">
      <p className="text-card-title font-bold">{title}</p>
      <div className="mt-1 flex flex-wrap items-center justify-between gap-2 text-meta">
        {pkg ? <span className="text-muted">{t("common.purchase.shared.examCount", { count: formatNumber(examCount, locale) })}</span> : null}
        {pkg ? <PriceLine label={priceLabel(pkg.pricing)} /> : null}
      </div>
    </Card>
  );
}
