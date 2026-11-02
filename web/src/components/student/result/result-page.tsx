"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { Info, RotateCcw } from "lucide-react";
import { PdfViewer } from "@/components/pdf/pdf-viewer";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { NotFoundState } from "@/components/ui/not-found-state";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { Stat, StatRow } from "@/components/ui/stat";
import { Tabs } from "@/components/ui/tabs";
import { ReviewCard } from "@/components/student/reviews/review-card";
import { RetakeRequestPanel } from "@/components/student/result/retake-request-panel";
import { ResultSummary } from "@/components/student/result/result-summary";
import { McqQuestion, resolvePaperLanguage, type OptionLabels } from "@/components/student/sitting/mcq-question";
import { usePaperTextSize } from "@/components/student/sitting/paper-text-size";
import { useLocale } from "@/i18n/locale-context";
import { useUser } from "@/lib/auth/session-context";
import { useNotice } from "@/lib/notices/use-notice";
import { apiBlob } from "@/lib/api/client";
import { ApiError, isUnknownItemError } from "@/lib/api/errors";
import { studentHome } from "@/lib/config";
import { answerPdfPath, evaluatedPdfPath, useLeaderboard, useSittingResult, useStudentExam, type SittingPosition } from "@/lib/api/exam/sitting";
import { formatDateTime, formatNumber, pickTitle } from "@/lib/format";
import { isMissing } from "@/lib/api/missing";
import { useServerNow } from "@/lib/time/clock";

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
  right: "border-ok/50 bg-ok/10 text-ok-text",
  wrong: "border-danger/50 bg-danger/10 text-danger",
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
  const [paperTab, setPaperTab] = useState<"evaluated" | "submitted">("evaluated");
  const [reviewFilter, setReviewFilter] = useState<ReviewFilter>("all");
  const textSize = usePaperTextSize();
  const user = useUser();

  const studentLine = user ? (user.studentCode ? `${user.fullName} · ${user.studentCode}` : user.fullName) : undefined;

  // The exam read refuses an archived or unpublished exam, so the result's own title stands in for it.
  const examTitle = exam.data ?? (result.data?.examTitleEn ? { titleEn: result.data.examTitleEn, titleBn: result.data.examTitleBn } : null);
  const header = (
    <PageHeader
      title={examTitle ? pickTitle(examTitle, locale) : t("result.page.title")}
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

  // An archived exam refuses the exam read, but its posted result stays readable: the page then renders from the result alone.
  if (exam.isError && isUnknownItemError(exam.error) && !result.isSuccess) {
    return (
      <div className="flex flex-col gap-4">
        {header}
        <NotFoundState title={t("common.notFound.exam")} body={t("common.notFound.itemBody")} backHref={studentHome} backLabel={t("common.guard.goHome")} />
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
        <ErrorState error={result.error} onRetry={() => result.refetch()} />
      </div>
    );
  }

  const data = result.data;
  const written = (exam.data?.examType ?? data.examType) === "WRITTEN";
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

  const verdict = notSubmitted || isMissing(data.passed) ? "muted" : data.passed ? "ok" : "danger";
  const meterPercent =
    !written && !isMissing(data.score) && data.maximum > 0 ? Math.min(100, Math.max(0, (data.score / data.maximum) * 100)) : null;
  const passPercent = !isMissing(data.passPercent) ? data.passPercent : null;

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
        <div role="note" className="flex items-center gap-2 rounded-card border border-warn/40 bg-warn/10 px-4 py-3 text-meta font-semibold text-warn">
          <Info aria-hidden="true" className="size-4 shrink-0" />
          {t("result.page.practice")}
        </div>
      ) : null}

      {written ? (
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
      ) : (
        <ResultSummary
          scoreLabel={t("result.stat.obtained")}
          score={scoreCell}
          percent={meterPercent}
          passPercent={passPercent}
          passLabel={passPercent !== null ? t("result.hub.passLine", { p: n(passPercent) }) : null}
          outcome={outcome}
          verdict={verdict}
          rankLabel={t("result.stat.rank")}
          rank={mine ? n(mine.rank) : t("result.stat.none")}
          maximumLabel={t("result.board.maximum")}
          maximum={n(data.maximum)}
        />
      )}

      {!written && data.position && (data.position.belowPass || data.position.of > 0) ? (
        <p className="flex items-center gap-2 text-meta text-muted">
          <Info aria-hidden="true" className="size-4 shrink-0" />
          {t(positionKey(data.position), { rank: n(data.position.rank), of: n(data.position.of) })}
        </p>
      ) : null}

      {!written && exam.data?.retakeAvailable === true ? (
        <Card className="flex flex-wrap items-center justify-between gap-4 border-accent/40">
          <div className="flex min-w-0 items-center gap-3">
            <span aria-hidden="true" className="grid size-10 shrink-0 place-items-center rounded-full bg-accent/15 text-accent-text">
              <RotateCcw className="size-5" />
            </span>
            <p className="text-body text-text">{t("result.page.retakeNote")}</p>
          </div>
          <Button href={`/exam/${examId}/sit?start=retake`} className="shrink-0">
            {t("result.page.retake")}
          </Button>
        </Card>
      ) : null}

      {notSubmitted ? <p className="text-body font-semibold text-text">{t("result.written.couldNotFinish")}</p> : null}
      {notSubmitted ? <p className="text-body text-muted">{t("result.written.notSubmitted")}</p> : null}

      {awaiting && !notSubmitted ? <p className="text-body text-muted">{t("result.written.awaiting")}</p> : null}

      {written && published && data.comment ? (
        <Card padding="sm" className="grid gap-1">
          <h2 className="text-label font-semibold text-muted">{t("result.written.comment")}</h2>
          <p className="whitespace-pre-line text-text">{data.comment}</p>
        </Card>
      ) : null}

      {written ? <RetakeRequestPanel examId={examId} enabled /> : null}

      {written && (data.answerPdf || data.evaluatedPdf) ? (
        <section className="grid gap-4">
          <Tabs
            ariaLabel={t("result.written.tabsLabel")}
            variant="pill"
            tabs={[
              { value: "evaluated", label: t("result.written.tabEvaluated") },
              { value: "submitted", label: t("result.written.tabSubmitted") },
            ]}
            value={paperTab}
            onChange={(value) => setPaperTab(value as "evaluated" | "submitted")}
          />
          {paperTab === "evaluated" ? (
            <div className="grid gap-4">
              {published && data.evaluatedPdf ? (
                <PdfViewer
                  load={() => apiBlob(evaluatedPdfPath(examId))}
                  studentLine={studentLine}
                  allowDownload
                  fileName="evaluated-paper.pdf"
                  label={t("result.written.markedScript")}
                />
              ) : (
                <p className="text-body text-muted">{t("result.written.noMarked")}</p>
              )}
            </div>
          ) : data.answerPdf ? (
            <PdfViewer
              load={() => apiBlob(answerPdfPath(examId))}
              studentLine={studentLine}
              allowDownload
              fileName="submitted-paper.pdf"
              label={t("result.written.yourAnswer")}
            />
          ) : (
            <p className="text-body text-muted">{t("result.written.noAnswer")}</p>
          )}
        </section>
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
          <nav aria-label={t("result.review.jumpLabel")} className="flex flex-wrap gap-2">
            {visibleItems.map((item) => (
              <a
                key={item.question.id}
                href={`#review-q-${item.number}`}
                aria-label={t("result.review.jumpItem", { number: n(item.number), outcome: outcomeLabel[item.outcome] })}
                className={`inline-flex size-10 items-center justify-center rounded-control border text-body font-semibold tabular-nums transition-colors hover:border-accent focus-visible:outline-2 focus-visible:outline-accent ${jumpTone[item.outcome]}`}
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

      {/* A submitted MCQ whose review is still closed says when it opens; the page reads the result again at that time. */}
      {!written && !data.questions ? (
        data.reviewOpensAt ? (
          <ReviewOpensNote opensAt={data.reviewOpensAt} onDue={result.refetch} />
        ) : (
          <p className="text-body text-muted">{t("result.review.afterDeadline")}</p>
        )
      ) : null}

      {/* The merit list reads the exam too, so the link is left out when the exam read is refused. */}
      {exam.data ? (
        <Link href={`/exam/${examId}/leaderboard`} className="text-accent underline underline-offset-4">
          {t("result.page.leaderboard")}
        </Link>
      ) : null}

      {/* The rate form sits at the end of the page, after the answers and the merit list. */}
      <div id="exam-review" className="scroll-mt-24">
        <ReviewCard kind="exam" id={examId} />
      </div>
    </div>
  );
}

/** While a submitted MCQ's review is closed, the review (and the time it opens) is asked for again once that time comes. */
const REVIEW_RECHECK_MS = 5_000;

/**
 * The note in place of the review. `onDue` reads the result again once `opensAt` has passed (on the server's clock), so the
 * review appears without a reload. While the server still answers closed, it is asked again every few seconds.
 */
function ReviewOpensNote({ opensAt, onDue }: { opensAt: string; onDue: () => void }) {
  const { t, locale } = useLocale();
  const now = useServerNow(1000);
  const dueAt = Date.parse(opensAt);
  const invalid = Number.isNaN(dueAt);
  const due = !invalid && now >= dueAt;
  const askedAt = useRef<number | null>(null);
  useEffect(() => {
    if (!due) return;
    if (askedAt.current !== null && now - askedAt.current < REVIEW_RECHECK_MS) return;
    askedAt.current = now;
    onDue();
  }, [due, now, onDue]);

  if (invalid) {
    return <p className="text-body text-muted">{t("result.review.afterDeadline")}</p>;
  }
  return <p className="text-body font-semibold text-text">{t("result.review.opensAt", { time: formatDateTime(opensAt, locale) })}</p>;
}
