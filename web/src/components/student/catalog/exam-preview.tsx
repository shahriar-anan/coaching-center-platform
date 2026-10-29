"use client";

import { useState } from "react";
import { openPreviewPdf, usePreviewPaper, type PreviewQuestion } from "@/lib/api/exam/preview";
import { ApiError } from "@/lib/api/errors";
import { useLocale } from "@/i18n/locale-context";
import { formatDuration, formatNumber, pickTitle } from "@/lib/format";
import { cn } from "@/components/ui/cn";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { examPath } from "./catalog-helpers";
import { detailFrame } from "./detail-layout";
import { TypePill } from "./status-pill";

const LETTERS_BN = ["ক", "খ", "গ", "ঘ", "ঙ", "চ"] as const;
const LETTERS_EN = ["A", "B", "C", "D", "E", "F"] as const;

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div className="grid grid-cols-1 content-start gap-0.5">
      <dt className="text-meta text-muted">{label}</dt>
      <dd className="break-words text-label font-bold tabular-nums">{value}</dd>
    </div>
  );
}

/**
 * The paper as a student previews it (C10', P-b): the title, the facts, then each question with its options and marks. The API
 * sends no key, so none is drawn. On a desktop the title sits in a column beside the paper; on a phone the title comes first.
 */
export function ExamPreview({ examId }: { examId: string }) {
  const { t, locale } = useLocale();
  const preview = usePreviewPaper(examId);
  const [pdfFailed, setPdfFailed] = useState(false);
  const data = preview.data;

  if (preview.isPending) {
    return (
      <>
        <PageHeader title={t("catalog.loading")} />
        <Skeleton variant="card" count={3} />
      </>
    );
  }
  if (!data) {
    if (preview.error instanceof ApiError && preview.error.code === "RESOURCE_NOT_FOUND") {
      return (
        <>
          <PageHeader title={t("catalog.notFound")} />
          <EmptyState
            title={t("catalog.notFound")}
            body={t("catalog.notFoundBody")}
            action={<Button variant="secondary" href={examPath(examId)}>{t("catalog.preview.backToExam")}</Button>}
          />
        </>
      );
    }
    return (
      <>
        <PageHeader title={t("catalog.loadFailed")} />
        <ErrorState onRetry={() => void preview.refetch()} />
      </>
    );
  }

  const title = pickTitle(data, locale);
  const stemFace = data.paperLanguage === "BN" ? "font-bn" : "font-latin";
  const letters = data.paperLanguage === "BN" ? LETTERS_BN : LETTERS_EN;
  const questions = data.questions ?? [];

  return (
    <div className={cn(detailFrame, "pb-10")}>
      <div className="mt-4 grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(0,2fr)] lg:items-start lg:gap-x-8">
        <header className="grid min-w-0 grid-cols-1 content-start gap-4 lg:sticky lg:top-20">
          <div className="flex flex-wrap items-center gap-2">
            <TypePill type={data.examType} />
          </div>
          <h1 className="break-words text-2xl font-extrabold leading-tight tracking-tight md:text-3xl">{title}</h1>
          <p className="text-body text-muted">{t("catalog.preview.note")}</p>
          <dl className="grid grid-cols-2 gap-x-4 gap-y-4 rounded-card border border-border bg-card p-5">
            <Fact label={t("catalog.exam.duration")} value={formatDuration(data.durationMinutes, locale)} />
            <Fact label={t("catalog.exam.fullMarks")} value={formatNumber(data.maximum, locale)} />
            <Fact
              label={t("catalog.exam.paperLanguage")}
              value={data.paperLanguage === "EN" ? t("catalog.exam.langEn") : t("catalog.exam.langBn")}
            />
            {data.passPercent !== null ? (
              <Fact label={t("catalog.exam.passMark")} value={t("catalog.exam.passMarkValue", { p: formatNumber(data.passPercent, locale) })} />
            ) : null}
          </dl>
          <Button variant="secondary" href={examPath(examId)}>
            {t("catalog.preview.backToExam")}
          </Button>
        </header>

        <section className="grid min-w-0 grid-cols-1 gap-4">
          <h2 className="text-section font-bold">{t("catalog.preview.questionPaper")}</h2>
          {data.examType === "WRITTEN" ? (
            <div className="grid grid-cols-1 justify-items-start gap-3 rounded-card border border-border bg-card p-5">
              {data.hasPaper ? (
                <Button
                  variant="secondary"
                  onClick={() => {
                    setPdfFailed(false);
                    openPreviewPdf(examId).catch(() => setPdfFailed(true));
                  }}
                >
                  {t("catalog.preview.openPdf")}
                </Button>
              ) : (
                <p className="text-muted">{t("catalog.preview.noPaper")}</p>
              )}
              {pdfFailed ? <p role="alert" className="text-body text-danger">{t("catalog.preview.pdfFailed")}</p> : null}
            </div>
          ) : questions.length === 0 ? (
            <p className="rounded-card border border-dashed border-border bg-card p-5 text-muted">{t("catalog.preview.noQuestions")}</p>
          ) : (
            <ol className="grid grid-cols-1 gap-4">
              {questions.map((question, index) => (
                <QuestionCard
                  key={question.id}
                  question={question}
                  number={index + 1}
                  stemFace={stemFace}
                  letters={letters}
                />
              ))}
            </ol>
          )}
        </section>
      </div>
    </div>
  );
}

function QuestionCard({
  question,
  number,
  stemFace,
  letters,
}: {
  question: PreviewQuestion;
  number: number;
  stemFace: string;
  letters: readonly string[];
}) {
  const { t, locale } = useLocale();
  return (
    <li className="grid min-w-0 grid-cols-1 gap-3 rounded-card border border-border bg-card p-4">
      <div className="flex items-start justify-between gap-3">
        <h3 className="text-label font-semibold text-muted">{t("sitting.paper.question", { number: formatNumber(number, locale) })}</h3>
        <span className="shrink-0 text-meta text-muted">{t("sitting.paper.marks", { marks: formatNumber(question.marks, locale) })}</span>
      </div>
      <p className={cn("break-words leading-relaxed text-text", stemFace)}>{question.stem}</p>
      <ul className="grid grid-cols-1 gap-2">
        {question.options.map((option, position) => (
          <li key={option.id} className="flex min-w-0 gap-3 rounded-control border border-border px-3 py-2.5">
            <span className="shrink-0 font-semibold text-muted">{letters[position] ?? formatNumber(position + 1, locale)}</span>
            <span className={cn("min-w-0 break-words", stemFace)}>{option.text}</span>
          </li>
        ))}
      </ul>
    </li>
  );
}
