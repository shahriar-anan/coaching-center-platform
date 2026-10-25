"use client";

import { useState } from "react";
import Link from "next/link";
import { PdfViewer } from "@/components/pdf/pdf-viewer";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { Stat, StatRow } from "@/components/ui/stat";
import { Tabs } from "@/components/ui/tabs";
import { McqQuestion, type OptionLabels } from "@/components/student/sitting/mcq-question";
import { PaperTextSizeControl, usePaperTextSize } from "@/components/student/sitting/paper-text-size";
import { useLocale } from "@/i18n/locale-context";
import { apiBlob } from "@/lib/api/client";
import { ApiError } from "@/lib/api/errors";
import { evaluatedPdfPath, useLeaderboard, useSittingResult, useStudentExam } from "@/lib/api/exam/sitting";
import { formatNumber, pickTitle } from "@/lib/format";

const LETTERS_BN = ["ক", "খ", "গ", "ঘ", "ঙ", "চ"] as const;
const LETTERS_EN = ["A", "B", "C", "D", "E", "F"] as const;

type Outcome = "right" | "wrong" | "skipped";
type ReviewFilter = "all" | Outcome;

/** Right, wrong or not answered, from the saved selection and the correct flag the server sends after the deadline. */
export function outcomeOf(question: { options: { selected: boolean | null; correct: boolean | null }[] }): Outcome {
  const picked = question.options.find((option) => option.selected === true);
  if (!picked) return "skipped";
  return picked.correct === true ? "right" : "wrong";
}

const jumpTone: Record<Outcome, string> = {
  right: "border-ok bg-ok/10 text-ok",
  wrong: "border-danger bg-danger/10 text-danger",
  skipped: "border-border bg-bg text-muted",
};

/**
 * Result for one student's sitting. MCQ: score, pass, and the review after the deadline. Written:
 * the four states from the brief (failed to submit, awaiting, stored, published).
 */
export function ResultPage({ examId }: { examId: string }) {
  const { t, locale } = useLocale();
  const n = (value: number) => formatNumber(value, locale);
  const exam = useStudentExam(examId);
  const result = useSittingResult(examId);
  // The merit list is empty until it opens; once it is, the student's own row gives their rank.
  const board = useLeaderboard(examId);
  const mine = board.data?.rows.find((row) => row.mine) ?? null;
  const [showScript, setShowScript] = useState(false);
  const [reviewFilter, setReviewFilter] = useState<ReviewFilter>("all");
  const textSize = usePaperTextSize();

  const header = (
    <PageHeader
      title={exam.data ? pickTitle(exam.data, locale) : t("result.page.title")}
      breadcrumbs={[{ label: t("result.page.title") }]}
    />
  );

  if (result.isPending || exam.isPending) {
    return (
      <div className="flex flex-col gap-4">
        {header}
        <p role="status" className="sr-only">
          {t("result.page.loading")}
        </p>
        <Skeleton variant="card" />
      </div>
    );
  }

  if (result.isError) {
    const error = result.error;
    if (error instanceof ApiError && error.code === "SITTING_NOT_OPEN") {
      return (
        <div className="flex flex-col gap-4">
          {header}
          <EmptyState
            title={t("result.page.notReady")}
            action={<Button href={`/exam/${examId}`} variant="secondary">{t("result.page.backToExam")}</Button>}
          />
        </div>
      );
    }
    if (error instanceof ApiError && error.code === "RESOURCE_NOT_FOUND") {
      return (
        <div className="flex flex-col gap-4">
          {header}
          <EmptyState
            title={t("result.page.notSat")}
            action={<Button href={`/exam/${examId}`} variant="secondary">{t("result.page.backToExam")}</Button>}
          />
        </div>
      );
    }
    return (
      <div className="flex flex-col gap-4">
        {header}
        <ErrorState onRetry={() => result.refetch()} />
      </div>
    );
  }

  const data = result.data;
  const written = exam.data?.examType === "WRITTEN";
  const published = written && data.score !== null;
  const notSubmitted = data.status === "FAILED_TO_SUBMIT";
  const awaiting = written && !published && !notSubmitted;

  const scoreCell = notSubmitted
    ? "০"
    : data.score === null
      ? t("result.stat.none")
      : t("result.stat.score", { score: n(data.score), maximum: n(data.maximum) });

  const outcome = notSubmitted
    ? t("result.outcome.notSubmitted")
    : data.passed === null
      ? t("result.outcome.pending")
      : data.passed
        ? t("result.outcome.passed")
        : t("result.outcome.failed");

  const labels: OptionLabels = {
    selected: t("sitting.paper.selected"),
    flag: t("sitting.paper.flag"),
    yourAnswer: t("result.review.yourAnswer"),
    correct: t("result.review.correct"),
    explanation: t("result.review.explanation"),
  };
  const letters = exam.data?.paperLanguage === "EN" ? LETTERS_EN : LETTERS_BN;

  const reviewItems = (data.questions ?? []).map((question, index) => ({
    question,
    number: index + 1,
    outcome: outcomeOf(question),
  }));
  const outcomeCount = (outcome: Outcome) => reviewItems.filter((item) => item.outcome === outcome).length;
  const outcomeLabel: Record<Outcome, string> = {
    right: t("result.review.filterRight"),
    wrong: t("result.review.filterWrong"),
    skipped: t("result.review.filterSkipped"),
  };
  const visibleItems = reviewItems.filter((item) => reviewFilter === "all" || item.outcome === reviewFilter);

  return (
    <div className="flex flex-col gap-4 pb-8">
      {header}

      {data.kind === "RETAKE" ? (
        <Pill tone="warn">{t("result.page.practice")}</Pill>
      ) : null}

      <StatRow className="[&>div>span:first-child]:text-accent">
        <Stat value={written && awaiting ? t("result.stat.none") : scoreCell} label={t("result.stat.obtained")} />
        <Stat
          value={awaiting ? t("result.stat.none") : outcome}
          label={t("result.stat.outcome")}
        />
        <Stat
          value={written && awaiting ? t("result.outcome.pending") : mine ? n(mine.rank) : t("result.stat.none")}
          label={written && awaiting ? t("result.stat.marking") : t("result.stat.rank")}
        />
      </StatRow>

      {!written && data.passed !== null ? (
        <Pill tone={data.passed ? "ok" : "danger"}>{outcome}</Pill>
      ) : null}

      {notSubmitted ? <p className="text-body text-muted">{t("result.written.notSubmitted")}</p> : null}

      {awaiting && !notSubmitted ? <p className="text-body text-muted">{t("result.written.awaiting")}</p> : null}

      {published && data.comment ? (
        <Card>
          <h2 className="text-label font-semibold text-muted">{t("result.written.comment")}</h2>
          <p className="mt-2 whitespace-pre-line text-text">{data.comment}</p>
        </Card>
      ) : null}

      {published && data.evaluatedPdf ? (
        <Card>
          <Button variant="secondary" onClick={() => setShowScript((value) => !value)} aria-expanded={showScript}>
            {showScript ? t("result.written.hideMarked") : t("result.written.viewMarked")}
          </Button>
          {showScript ? (
            <div className="mt-4">
              <PdfViewer
                load={() => apiBlob(evaluatedPdfPath(examId))}
                allowDownload={false}
                label={t("result.written.markedScript")}
              />
            </div>
          ) : null}
        </Card>
      ) : null}

      {!written && data.questions ? (
        <section aria-labelledby="review-title" className="flex flex-col gap-3" style={textSize.style}>
          <h2 id="review-title" className="text-section font-semibold text-text">
            {t("result.review.title")}
          </h2>
          <Tabs
            tabs={[
              { value: "all", label: t("result.review.filterAll"), badge: reviewItems.length },
              { value: "wrong", label: t("result.review.filterWrong"), badge: outcomeCount("wrong") },
              { value: "right", label: t("result.review.filterRight"), badge: outcomeCount("right") },
              { value: "skipped", label: t("result.review.filterSkipped"), badge: outcomeCount("skipped") },
            ]}
            value={reviewFilter}
            onChange={(value) => setReviewFilter(value as ReviewFilter)}
            ariaLabel={t("result.review.title")}
          />
          <PaperTextSizeControl size={textSize} className="justify-end" />
          <nav aria-label={t("result.review.jumpLabel")} className="grid grid-cols-[repeat(auto-fill,minmax(40px,1fr))] gap-2">
            {visibleItems.map((item) => (
              <a
                key={item.question.id}
                href={`#review-q-${item.number}`}
                aria-label={t("result.review.jumpItem", { number: n(item.number), outcome: outcomeLabel[item.outcome] })}
                className={`flex h-10 items-center justify-center rounded-control border text-body tabular-nums ${jumpTone[item.outcome]}`}
              >
                {n(item.number)}
              </a>
            ))}
          </nav>
          {visibleItems.length === 0 ? <p className="text-body text-muted">{t("result.review.filterEmpty")}</p> : null}
          {visibleItems.map((item) => (
            <div key={item.question.id} id={`review-q-${item.number}`} className="scroll-mt-24">
              <McqQuestion
                question={item.question}
                index={item.number}
                selectedOptionId={item.question.options.find((option) => option.selected === true)?.id}
                flagged={false}
                paperLanguage={exam.data?.paperLanguage === "EN" ? "EN" : "BN"}
                editable={false}
                reviewing
                labels={labels}
                questionLabel={t("result.review.question", { number: n(item.number) })}
                letters={letters}
                onPick={() => undefined}
                onFlag={() => undefined}
              />
            </div>
          ))}
        </section>
      ) : null}

      {!written && !data.questions ? <p className="text-body text-muted">{t("result.review.afterDeadline")}</p> : null}

      <Link href={`/exam/${examId}/leaderboard`} className="text-accent underline underline-offset-4">
        {t("result.page.leaderboard")}
      </Link>
    </div>
  );
}
