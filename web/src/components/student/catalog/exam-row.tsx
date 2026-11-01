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
 * One exam in a package's exam list: a title on one line, a meta line (type and date) under it, a one-line summary, and the
 * status or price on the right, centred on the row. The text column takes the free width and the right column keeps its size,
 * so a long line can never push the status or price out of the row. The Wish List mark, when given, is the last item.
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
    <div className="group relative flex min-h-14 min-w-0 items-center gap-2 rounded-card border border-border bg-card px-4 py-3 transition-colors hover:border-accent hover:bg-text/[0.06]">
      <Link href={href} prefetch className="flex min-h-11 min-w-0 flex-1 items-center gap-2">
        <span className="min-w-0 flex-1">
          <span className="line-clamp-1 block text-body font-semibold leading-snug group-hover:text-text-accent">{title}</span>
          {meta ? <span className="mt-0.5 flex min-w-0 flex-wrap items-center gap-2 text-meta text-muted">{meta}</span> : null}
          {summary ? <span className="mt-0.5 line-clamp-1 block text-meta text-muted">{summary}</span> : null}
        </span>
        <span className="shrink-0 text-end">{right}</span>
      </Link>
      {favourite}
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

/**
 * One exam in an exam list (NR-01, S2.2, S3 and S14.2), in three zones. The top row has the type, the popular marker and the window
 * status, with the Wish List mark last. The middle has the title (up to two lines) and the date on one line. The bottom row, under a
 * thin divider, has the price on the right, so a long title or a discount never squeezes the others. The packages an exam is in
 * are not shown on the card; the exam's detail page lists them. The title link covers the row (a stretched link), and the
 * favourite mark sits above it.
 */
export function CatalogExamRow({ exam }: { exam: ExamRowFields }) {
  const { t, locale } = useLocale();
  const status = phaseStatus(exam.phase ?? "");
  const when = exam.liveAt
    ? `${formatDate(exam.liveAt, locale, { year: "auto" })} · ${formatTime(exam.liveAt, locale)}`
    : t("catalog.dateTbd");
  const href = examPath(exam.id);
  return (
    <div className="group relative flex min-h-14 min-w-0 flex-col rounded-card border border-border bg-card px-4 py-3 transition-colors hover:border-accent hover:bg-text/[0.06]">
      <div className="flex min-w-0 items-center gap-2">
        <span className="flex min-w-0 flex-1 flex-wrap items-center gap-2 text-meta text-muted">
          <TypePill type={exam.examType} />
          {exam.popular ? <Pill tone="accent">{t("catalog.popular.badge")}</Pill> : null}
        </span>
        <span className="shrink-0 whitespace-nowrap">
          <Pill tone={status.tone} pulse={status.pulse}>
            {t(status.key)}
          </Pill>
        </span>
        <FavouriteButton kind="exam" id={exam.id} initial={exam.favourite} next={href} placement="inline" />
      </div>
      <Link
        href={href}
        prefetch
        className="mt-1 line-clamp-2 block text-body font-semibold leading-snug after:absolute after:inset-0 after:content-[''] group-hover:text-text-accent"
      >
        {pickTitle(exam, locale)}
      </Link>
      <span className="mt-0.5 block truncate text-meta text-muted">{when}</span>
      <div className="mt-2 flex flex-wrap items-center justify-between gap-x-2 gap-y-1 border-t border-border pt-2">
        <span className="ml-auto min-w-0 max-w-full">
          <PriceLine label={priceLabel(exam.pricing)} />
        </span>
      </div>
    </div>
  );
}
