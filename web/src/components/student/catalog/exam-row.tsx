"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { useLocale } from "@/i18n/locale-context";
import { formatDate, formatTime, pickTitle } from "@/lib/format";
import type { ExamType, PackageRef, PriceView, Titled } from "@/lib/api/exam/types";
import { Pill } from "@/components/ui/pill";
import { examPath, phaseStatus } from "./catalog-helpers";
import { FavouriteButton } from "./favourite-button";
import { PriceLine } from "./price-line";
import { priceLabel } from "./price-view";
import { TypePill } from "./status-pill";

/**
 * One exam in a package's exam list: a title on one line, a meta line (type and date), a summary clamped
 * to two lines, and the status or price on the right. The text column takes the free width and the
 * right column keeps its size, so a long line can never push the status or price out of the row.
 */
export function ExamRow({
  href,
  title,
  meta,
  summary,
  right,
  favourite,
}: {
  href: string;
  title: string;
  meta?: ReactNode;
  summary?: string | null;
  right: ReactNode;
  /** The Wish List button. It sits outside the link, because a button cannot live inside a link. */
  favourite?: ReactNode;
}) {
  return (
    <div className="group flex min-h-11 min-w-0 items-center gap-3 rounded-card border border-border bg-card p-4 transition-colors hover:border-accent">
      <Link href={href} prefetch className="flex min-h-11 min-w-0 flex-1 items-center gap-3">
        <span className="min-w-0 flex-1">
          {meta ? <span className="mb-1 flex min-w-0 flex-wrap items-center gap-2 text-meta text-muted">{meta}</span> : null}
          <span className="line-clamp-1 block text-card-title font-bold group-hover:text-text-accent">{title}</span>
          {summary ? <span className="mt-1 line-clamp-2 block text-body leading-relaxed text-muted">{summary}</span> : null}
        </span>
        <span className="shrink-0 text-end">{right}</span>
      </Link>
      {favourite ? <span className="shrink-0">{favourite}</span> : null}
    </div>
  );
}

/**
 * What a row in an exam list needs. The category list, the home popular list, the favourites and the exam read all carry these
 * fields, so one row serves them all.
 */
export interface ExamRowFields extends Titled {
  id: string;
  examType: ExamType;
  /** UNANNOUNCED, UPCOMING, LIVE, ENDED or PRACTICE. */
  phase?: string | null;
  liveAt: string | null;
  pricing?: PriceView | null;
  packages?: PackageRef[];
  popular?: boolean;
  favourite?: boolean | null;
}

/** The package tags of one exam: up to two names, then a count of the rest (plan S2.2). */
function PackageTags({ packages }: { packages: PackageRef[] }) {
  const { locale, t } = useLocale();
  const shown = packages.slice(0, 2);
  const rest = packages.length - shown.length;
  return (
    <>
      {shown.map((pkg) => (
        <Pill key={pkg.id} tone="muted">
          {pickTitle(pkg, locale)}
        </Pill>
      ))}
      {rest > 0 ? <span className="text-meta text-muted">{t("catalog.packageTagsMore", { n: String(rest) })}</span> : null}
    </>
  );
}

/**
 * One exam in an exam list (NR-01, S2.2 and S3): its type, popular marker, date and time, window status, price with any discount,
 * the packages it is in, and the favourite mark. The title link covers the row (a stretched link), and the favourite mark sits
 * above it.
 */
export function CatalogExamRow({ exam }: { exam: ExamRowFields }) {
  const { t, locale } = useLocale();
  const status = phaseStatus(exam.phase ?? "");
  const when = exam.liveAt
    ? `${formatDate(exam.liveAt, locale, { year: "auto" })} · ${formatTime(exam.liveAt, locale)}`
    : t("catalog.dateTbd");
  const href = examPath(exam.id);
  const packages = exam.packages ?? [];
  return (
    <div className="group relative flex min-h-11 min-w-0 items-center gap-3 rounded-card border border-border bg-card p-4 transition-colors hover:border-accent">
      <span className="min-w-0 flex-1">
        <span className="mb-1 flex min-w-0 flex-wrap items-center gap-2 text-meta text-muted">
          <TypePill type={exam.examType} />
          {exam.popular ? <Pill tone="accent">{t("catalog.popular.badge")}</Pill> : null}
          <span>{when}</span>
        </span>
        <Link
          href={href}
          prefetch
          className="line-clamp-1 block text-card-title font-bold after:absolute after:inset-0 after:content-[''] group-hover:text-text-accent"
        >
          {pickTitle(exam, locale)}
        </Link>
        {packages.length > 0 ? (
          <span className="mt-2 flex min-w-0 flex-wrap items-center gap-1.5">
            <PackageTags packages={packages} />
          </span>
        ) : null}
      </span>
      <span className="grid shrink-0 justify-items-end gap-1.5 text-end">
        <Pill tone={status.tone} pulse={status.pulse}>
          {t(status.key)}
        </Pill>
        <PriceLine label={priceLabel(exam.pricing)} />
      </span>
      <FavouriteButton kind="exam" id={exam.id} initial={exam.favourite} next={href} />
    </div>
  );
}
