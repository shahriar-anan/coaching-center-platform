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
import { McqQuestion, resolvePaperLanguage, type OptionLabels } from "@/components/student/sitting/mcq-question";
import { PaperTextSizeControl, usePaperTextSize } from "@/components/student/sitting/paper-text-size";
import { useLocale } from "@/i18n/locale-context";
import { useUser } from "@/lib/auth/session-context";
import { useNotice } from "@/lib/notices/use-notice";
import { apiBlob } from "@/lib/api/client";
import { ApiError } from "@/lib/api/errors";
import { evaluatedPdfPath, useLeaderboard, useSittingResult, useStudentExam, type SittingPosition } from "@/lib/api/exam/sitting";
import { formatNumber, pickTitle } from "@/lib/format";
import { isMissing } from "@/lib/api/missing";

const LETTERS_BN = ["ক", "খ", "গ", "ঘ", "ঙ", "চ"] as const;
const LETTERS_EN = ["A", "B", "C", "D", "E", "F"] as const;

type Outcome = "right" | "wrong" | "skipped" | "withheld";
type ReviewFilter = "all" | Outcome;

/** Right, wrong or not answered, from the saved selection and the correct flag the server sends after the deadline. */
/**
 * A question whose answers are withheld: no option carries `correct` and there is no
 * explanation (plan 6.3). The client never works out correctness itself.
 */
export function isWithheld(question: {
  explanation?: string | null;
  options: { correct: boolean | null }[];
}): boolean {
  return question.options.length > 0 && question.options.every((option) => isMissing(option.correct)) && (question.explanation ?? null) === null;
}

/**
 * Right only on an exact match: every option the student chose is correct, and every correct option was
 * chosen. A multi-answer question with some of its correct options is wrong.
 */
export function outcomeOf(question: {
  explanation?: string | null;
  options: { selected: boolean | null; correct: boolean | null }[];
}): Outcome {
  const chosen = question.options.filter((option) => option.selected === true);
  if (chosen.length === 0) return "skipped";
  if (isWithheld(question)) return "withheld";
  const exact =
    chosen.every((option) => option.correct === true) &&
    question.options.every((option) => option.correct !== true || option.selected === true);
  return exact ? "right" : "wrong";
}

/** The message key for the position line: a rank on the board, a relative rank, or no rank below the pass mark. */
export function positionKey(position: SittingPosition): "result.position.belowPass" | "result.position.current" | "result.position.board" | "result.position.relative" {
  if (position.belowPass) return "result.position.belowPass";
  if (position.type === "CURRENT") return "result.position.current";
  if (position.type === "BOARD") return "result.position.board";
  return "result.position.relative";
}

const jumpTone: Record<Outcome, string> = {
  right: "border-ok bg-ok/10 text-ok",
  wrong: "border-danger bg-danger/10 text-danger",
  skipped: "border-border bg-bg text-muted",
  withheld: "border-border bg-bg text-muted",
};

/**
 * Result for one student's sitting. MCQ: score, pass, and the review after the deadline. Written:
 * the four states from the brief (failed to submit, awaiting, stored, published).
 */
export function ResultPage({ examId }: { examId: string }) {
  const { t, locale } = useLocale();
  useNotice();
  const n = (value: number) => formatNumber(value, locale);
  const exam = useStudentExam(examId);
  const result = useSittingResult(examId);
  // The merit list is empty until it opens; once it is, the student's own row gives their rank.
  const board = useLeaderboard(examId);
  const mine = board.data?.rows.find((row) => row.mine) ?? null;
  const [showScript, setShowScript] = useState(false);
  const [reviewFilter, setReviewFilter] = useState<ReviewFilter>("all");
  const textSize = usePaperTextSize();
  const user = useUser();

  const header = (
    <PageHeader
      title={exam.data ? pickTitle(exam.data, locale) : t("result.page.title")}
      breadcrumbs={[{ label: t("result.hub.title"), href: "/results" }, { label: t("result.page.title") }]}
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
  const published = written && !isMissing(data.score);
  const notSubmitted = data.status === "FAILED_TO_SUBMIT";
  const awaiting = written && !published && !notSubmitted;

  const scoreCell = notSubmitted
    ? n(0)
    : isMissing(data.score)
      ? t("result.stat.none")
      : t("result.stat.score", { score: n(data.score), maximum: n(data.maximum) });

  const outcome = notSubmitted
    ? t("result.outcome.notSubmitted")
    : isMissing(data.passed)
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
    selectAll: t("sitting.paper.selectAll"),
    confirm: t("sitting.paper.confirm"),
    final: t("sitting.paper.final"),
  };
  // No paper language (or BOTH) follows the interface: the letters and the script do, and no language is named.
  const paperScript = resolvePaperLanguage(exam.data?.paperLanguage, locale);
  const letters = paperScript === "EN" ? LETTERS_EN : LETTERS_BN;

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
    withheld: t("result.review.withheldShort"),
  };
  // While answers are withheld, right and wrong cannot be shown, so those two filters are hidden.
  const anyWithheld = reviewItems.some((item) => item.outcome === "withheld");
  const activeFilter: ReviewFilter =
    anyWithheld && (reviewFilter === "right" || reviewFilter === "wrong") ? "all" : reviewFilter;
  const visibleItems = reviewItems.filter((item) => activeFilter === "all" || item.outcome === activeFilter);

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

      {!written && data.position ? (
        <p className="text-body text-text">
          {t(positionKey(data.position), { rank: n(data.position.rank), of: n(data.position.of) })}
        </p>
      ) : null}

      {!written && !isMissing(data.passed) ? (
        <Pill tone={data.passed ? "ok" : "danger"}>{outcome}</Pill>
      ) : null}

      {notSubmitted ? <p className="text-body font-semibold text-text">{t("result.written.couldNotFinish")}</p> : null}
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
                studentLine={user ? (user.studentCode ? `${user.fullName} · ${user.studentCode}` : user.fullName) : undefined}
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
          {anyWithheld ? <p className="text-body text-muted">{t("result.review.withheld")}</p> : null}
          <Tabs
            tabs={[
              { value: "all", label: t("result.review.filterAll"), badge: reviewItems.length },
              ...(anyWithheld
                ? []
                : [
                    { value: "wrong", label: t("result.review.filterWrong"), badge: outcomeCount("wrong") },
                    { value: "right", label: t("result.review.filterRight"), badge: outcomeCount("right") },
                  ]),
              { value: "skipped", label: t("result.review.filterSkipped"), badge: outcomeCount("skipped") },
            ]}
            value={activeFilter}
            onChange={(value) => setReviewFilter(value as ReviewFilter)}
            ariaLabel={t("result.review.title")}
          />
          {anyWithheld ? <p className="text-meta text-muted">{t("result.review.filterWhy")}</p> : null}
          <PaperTextSizeControl size={textSize} className="justify-end" />
          <nav aria-label={t("result.review.jumpLabel")} className="grid grid-cols-[repeat(auto-fill,minmax(44px,1fr))] gap-2">
            {visibleItems.map((item) => (
              <a
                key={item.question.id}
                href={`#review-q-${item.number}`}
                aria-label={t("result.review.jumpItem", { number: n(item.number), outcome: outcomeLabel[item.outcome] })}
                className={`flex h-11 items-center justify-center rounded-control border text-body tabular-nums ${jumpTone[item.outcome]}`}
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
                selectedOptionIds={item.question.options.filter((option) => option.selected === true).map((option) => option.id)}
                flagged={false}
                paperLanguage={paperScript}
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
