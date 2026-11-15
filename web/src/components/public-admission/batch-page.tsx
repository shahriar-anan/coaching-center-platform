"use client";

import { useState } from "react";
import { FileText } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ErrorState } from "@/components/ui/error-state";
import { NotFoundState } from "@/components/ui/not-found-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { PdfViewer } from "@/components/pdf/pdf-viewer";
import { useLocale } from "@/i18n/locale-context";
import { offlineAdmissionEnabled } from "@/lib/config";
import { loadPublicBatchRoutinePdf, usePublicBatch } from "@/lib/api/exam/public-admission";
import { formatDate } from "@/lib/format";
import { isNotFoundError } from "@/lib/api/errors";
import { BatchCover, batchDatesLine, batchFeeLine, batchName } from "./batch-parts";

/** The paragraphs of a description: blank lines separate them. Each language is shown as it was written. */
function paragraphsOf(text: string | null): string[] {
  return text ? text.split(/\n{2,}/).map((part) => part.trim()).filter(Boolean) : [];
}

/**
 * `/admission/batches/{id}` (plan 2A): the batch's photo, description, dates, fee and routine copy, with Apply. The routine is the student
 * copy (no instructor names), shown in the PDF viewer the rest of the site uses.
 */
export function BatchPage({ id }: { id: string }) {
  const { t, locale } = useLocale();
  const batch = usePublicBatch(id);
  const [routineOpen, setRoutineOpen] = useState(false);

  if (!offlineAdmissionEnabled) {
    return <NotFoundState title={t("common.notFound.title")} body={t("common.notFound.body")} backHref="/" backLabel={t("common.guard.goHome")} />;
  }
  if (batch.isPending) {
    return <Skeleton variant="card" count={2} />;
  }
  if (batch.isError || !batch.data) {
    return isNotFoundError(batch.error) ? (
      <NotFoundState title={t("publicAdmission.batch.missingTitle")} body={t("publicAdmission.batch.missingBody")} backHref="/admission" backLabel={t("publicAdmission.batch.back")} />
    ) : (
      <ErrorState error={batch.error} onRetry={() => void batch.refetch()} />
    );
  }

  const b = batch.data;
  const name = batchName(b, locale);
  const crumbs = [
    { label: t("catalog.home"), href: "/" },
    { label: t("publicAdmission.list.title"), href: "/admission" },
    { label: name },
  ];
  const description = [
    { lang: "en", parts: paragraphsOf(b.descriptionEn) },
    { lang: "bn", parts: paragraphsOf(b.descriptionBn) },
  ].filter((block) => block.parts.length > 0);

  return (
    <div className="grid gap-6">
      <PageHeader title={name} breadcrumbs={crumbs} />

      {/* A small thumbnail, only when there is a photo: the title is already in the header, and the description is the main content. */}
      {b.photoUrl ? (
        <BatchCover
          photoUrl={b.photoUrl}
          title={name}
          slim
          className=""
          alt={t("publicAdmission.card.coverAlt", { title: name })}
          plainLabel={t("publicAdmission.card.plainLabel")}
        />
      ) : null}

      <div className="flex flex-wrap items-center gap-3">
        <Pill tone={b.admissionsOpen ? "ok" : "muted"}>
          {t(b.admissionsOpen ? "publicAdmission.batch.open" : "publicAdmission.batch.closed")}
        </Pill>
        <span className="text-body tabular-nums text-muted">{batchDatesLine(b, locale, t)}</span>
      </div>

      <dl className="grid gap-3 sm:grid-cols-3">
        <div className="grid gap-0.5 rounded-control border border-border bg-card px-4 py-3">
          <dt className="text-meta text-muted">{t("publicAdmission.batch.fee")}</dt>
          <dd className="font-bold tabular-nums text-text">{batchFeeLine(b, locale, t)}</dd>
        </div>
        <div className="grid gap-0.5 rounded-control border border-border bg-card px-4 py-3">
          <dt className="text-meta text-muted">{t("publicAdmission.batch.startsOn")}</dt>
          <dd className="font-bold tabular-nums text-text">{b.startsOn ? formatDate(b.startsOn, locale, { year: "auto" }) : "—"}</dd>
        </div>
        <div className="grid gap-0.5 rounded-control border border-border bg-card px-4 py-3">
          <dt className="text-meta text-muted">{t("publicAdmission.batch.endsOn")}</dt>
          <dd className="font-bold tabular-nums text-text">{formatDate(b.endsOn, locale, { year: "auto" })}</dd>
        </div>
      </dl>

      <section aria-labelledby="batch-about" className="grid gap-3">
        <h2 id="batch-about" className="text-card-title font-bold text-text">
          {t("publicAdmission.batch.about")}
        </h2>
        {description.length === 0 ? (
          <p className="text-body text-muted">{t("publicAdmission.batch.noDescription")}</p>
        ) : (
          description.map((block) => (
            <div key={block.lang} lang={block.lang} className="grid gap-2">
              {block.parts.map((part, index) => (
                <p key={index} className="whitespace-pre-line break-words text-body leading-relaxed text-text">
                  {part}
                </p>
              ))}
            </div>
          ))
        )}
      </section>

      {b.routineAvailable ? (
        <section aria-labelledby="batch-routine" className="grid gap-3 rounded-card border border-border bg-card p-4 sm:p-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="grid min-w-0 gap-0.5">
              <h2 id="batch-routine" className="text-card-title font-bold text-text">
                {t("publicAdmission.batch.routine")}
              </h2>
              <p className="text-meta text-muted">{t("publicAdmission.batch.routineBody")}</p>
            </div>
            <Button variant="secondary" size="sm" onClick={() => setRoutineOpen((open) => !open)} aria-expanded={routineOpen}>
              <FileText aria-hidden="true" className="size-4" />
              {t(routineOpen ? "publicAdmission.batch.closeRoutine" : "publicAdmission.batch.openRoutine")}
            </Button>
          </div>
          {routineOpen ? (
            <PdfViewer
              load={() => loadPublicBatchRoutinePdf(b.id)}
              allowDownload
              fileName={`${name}.pdf`}
              label={t("publicAdmission.batch.pdfLabel", { batch: name })}
            />
          ) : null}
        </section>
      ) : null}

      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border pt-5">
        <Button variant="ghost" href="/admission">
          {t("publicAdmission.batch.changeBatch")}
        </Button>
        {b.admissionsOpen ? (
          <Button size="lg" href={`/admission/apply/${b.id}`}>
            {t("publicAdmission.card.apply")}
          </Button>
        ) : (
          <span className="text-body font-semibold text-muted">{t("publicAdmission.batch.closedNote")}</span>
        )}
      </div>
    </div>
  );
}
