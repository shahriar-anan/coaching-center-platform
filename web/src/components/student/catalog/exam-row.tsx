"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { useLocale } from "@/i18n/locale-context";
import { formatDate, formatTime, pickTitle } from "@/lib/format";
import type { CategoryExam } from "@/lib/api/exam/types";
import { Pill } from "@/components/ui/pill";
import { examPath, phaseStatus } from "./catalog-helpers";
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
  footer,
  right,
}: {
  href: string;
  title: string;
  meta?: ReactNode;
  summary?: string | null;
  footer?: ReactNode;
  right: ReactNode;
}) {
  return (
    <Link
      href={href}
      prefetch
      className="group flex min-h-11 min-w-0 items-center gap-3 rounded-card border border-border bg-card p-4 transition-colors hover:border-accent"
    >
      <span className="min-w-0 flex-1">
        {meta ? <span className="mb-1 flex min-w-0 flex-wrap items-center gap-2 text-meta text-muted">{meta}</span> : null}
        <span className="line-clamp-1 block text-card-title font-bold group-hover:text-text-accent">{title}</span>
        {summary ? <span className="mt-1 line-clamp-2 block text-body leading-relaxed text-muted">{summary}</span> : null}
        {footer ? <span className="mt-2 flex min-w-0 flex-wrap items-center gap-1.5">{footer}</span> : null}
      </span>
      <span className="shrink-0 text-end">{right}</span>
    </Link>
  );
}

/** The package tags of one exam: up to two names, then a count of the rest (plan S2.2). */
function PackageTags({ exam }: { exam: CategoryExam }) {
  const { locale, t } = useLocale();
  const shown = exam.packages.slice(0, 2);
  const rest = exam.packages.length - shown.length;
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
 * One exam in a category's exam list (NR-01, S2.2): its type, date and time, window status, price with any discount, and the
 * packages it is in.
 */
export function CatalogExamRow({ exam }: { exam: CategoryExam }) {
  const { t, locale } = useLocale();
  const status = phaseStatus(exam.phase);
  const when = exam.liveAt
    ? `${formatDate(exam.liveAt, locale, { year: "auto" })} · ${formatTime(exam.liveAt, locale)}`
    : t("catalog.dateTbd");
  return (
    <ExamRow
      href={examPath(exam.id)}
      title={pickTitle(exam, locale)}
      meta={
        <>
          <TypePill type={exam.examType} />
          <span>{when}</span>
        </>
      }
      footer={exam.packages.length > 0 ? <PackageTags exam={exam} /> : null}
      right={
        <span className="grid justify-items-end gap-1.5">
          <Pill tone={status.tone} pulse={status.pulse}>
            {t(status.key)}
          </Pill>
          <PriceLine label={priceLabel(exam.pricing)} />
        </span>
      }
    />
  );
}
