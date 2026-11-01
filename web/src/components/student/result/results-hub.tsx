"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { Stat, StatRow } from "@/components/ui/stat";
import { Tabs } from "@/components/ui/tabs";
import { useLocale } from "@/i18n/locale-context";
import { useMyExamResults, type MyExamResult } from "@/lib/api/exam/sitting";
import { formatDate, formatNumber, pickTitle } from "@/lib/format";
import { ExamStatusPill } from "@/components/student/catalog/status-pill";
import { TypeFilter, typeLabelKey, useTypeFilterParam } from "@/components/student/catalog/type-filter";
import { RateLink, canRateSitting } from "@/components/student/reviews/rate-link";
import type { MyStatus } from "@/lib/api/exam/catalog";
import { reviewedTargets, useMyReviews } from "@/lib/api/exam/reviews";
import { isMissing } from "@/lib/api/missing";

type Tab = "results" | "marking" | "other";

/** A score the student may see, or a script that never got in: the Results tab. */
function isResult(row: MyExamResult): boolean {
  return row.resultVisible || row.status === "FAILED_TO_SUBMIT";
}

/**
 * Where a row sits. A submitted script or answer sheet that is not released yet is being marked, straight after the submit.
 * Only an attempt still in progress (or otherwise unfinished) is in "In progress".
 */
function tabOf(row: MyExamResult): Tab {
  if (isResult(row)) return "results";
  return row.status === "SUBMITTED" || row.status === "AWAITING_RESULT" ? "marking" : "other";
}

export function ResultsHub() {
  const { t, locale } = useLocale();
  const n = (value: number) => formatNumber(value, locale);
  const [tab, setTab] = useState<Tab>("results");
  const [type, setType] = useTypeFilterParam();
  // The type narrows the list on the server, so every tab, the summary and the badges follow it.
  const list = useMyExamResults(type);
  const rows = useMemo(() => list.data?.pages.flatMap((page) => page.content) ?? [], [list.data]);
  // The reviews the student has written, so a row already rated shows no "Rate". A failed read hides the action.
  const myReviews = useMyReviews(true);
  const reviewed = reviewedTargets(myReviews.data);

  const resultRows = rows.filter((row) => tabOf(row) === "results");
  const markingRows = rows.filter((row) => tabOf(row) === "marking");
  const otherRows = rows.filter((row) => tabOf(row) === "other");
  const visible = tab === "results" ? resultRows : tab === "marking" ? markingRows : otherRows;
  const passedCount = resultRows.filter((row) => row.passed === true).length;

  const header = <PageHeader title={t("result.hub.title")} />;

  if (list.isPending) {
    return (
      <div className="flex flex-col gap-4">
        {header}
        <p role="status" className="sr-only">{t("result.page.loading")}</p>
        <Skeleton variant="rows" count={3} />
      </div>
    );
  }

  // A failed first page has nothing to show; a failed later page is shown above the rows already loaded.
  if (list.isError && !list.data) {
    return (
      <div className="flex flex-col gap-4">
        {header}
        <ErrorState error={list.error} onRetry={() => void list.refetch()} />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4 pb-8">
      {header}
      {list.isError ? <ErrorState error={list.error} onRetry={() => void list.refetch()} /> : null}

      <StatRow>
        <Stat value={n(resultRows.length)} label={t("result.hub.summaryTaken")} />
        <Stat value={n(passedCount)} label={t("result.hub.summaryPassed")} />
        <Stat value={n(markingRows.length)} label={t("result.hub.summaryWaiting")} />
      </StatRow>

      <Tabs
        ariaLabel={t("result.hub.title")}
        tabs={[
          { value: "results", label: t("result.hub.tabResults"), badge: resultRows.length },
          { value: "marking", label: t("result.hub.tabMarking"), badge: markingRows.length },
          { value: "other", label: t("result.hub.tabOther"), badge: otherRows.length },
        ]}
        value={tab}
        onChange={(value) => setTab(value as Tab)}
      />
      <div>
        <TypeFilter value={type} onChange={setType} />
      </div>

      {visible.length === 0 && !list.hasNextPage ? (
        <EmptyState
          title={
            type
              ? t("result.hub.emptyOfType", { type: t(typeLabelKey(type)) })
              : tab === "results" ? t("result.hub.empty") : tab === "marking" ? t("result.hub.emptyMarking") : t("result.hub.emptyOther")
          }
          body={!type && tab === "results" ? t("result.hub.emptyBody") : undefined}
          action={
            type ? (
              <Button variant="secondary" onClick={() => setType(null)}>{t("catalog.typeFilter.showAll")}</Button>
            ) : tab === "results" ? (
              <Link href="/routine" className="text-accent underline underline-offset-4">
                {t("result.hub.emptyAction")}
              </Link>
            ) : undefined
          }
        />
      ) : (
        <ul className="flex flex-col gap-2">
          {visible.map((row) => {
            const moduleTitle = pickTitle({ titleEn: row.moduleTitleEn, titleBn: row.moduleTitleBn }, locale);
            return (
              <li key={row.examId}>
                <Card padding="sm" href={tab === "other" ? `/exam/${row.examId}` : `/exam/${row.examId}/result`}>
                  {moduleTitle ? <p className="text-meta text-muted">{moduleTitle}</p> : null}
                  <p className="text-body font-semibold text-text">{pickTitle(row, locale)}</p>
                  {!isMissing(row.passPercent) ? (
                    <p className="text-meta text-muted">{t("result.hub.passLine", { p: n(row.passPercent) })}</p>
                  ) : null}
                  <p className="mt-0.5 flex flex-wrap items-center gap-2 text-meta text-muted">
                    <span>{row.examType === "MCQ" ? t("result.hub.mcq") : t("result.hub.written")}</span>
                    <span aria-hidden="true">·</span>
                    <span>{row.liveAt ? formatDate(row.liveAt, locale) : t("result.hub.undated")}</span>
                    {tab === "results" ? (
                      <Pill tone={row.passed ? "ok" : "muted"}>
                        {isMissing(row.score)
                          ? t("result.stat.none")
                          : t("result.stat.score", { score: n(row.score), maximum: n(row.maximum) })}
                        {" · "}
                        {row.passed ? t("result.outcome.passed") : t("result.outcome.failed")}
                      </Pill>
                    ) : null}
                    {row.kind === "RETAKE" ? <Pill tone="warn">{t("result.page.practice")}</Pill> : null}
                    {tab === "marking" ? <Pill tone="warn">{t("result.hub.awaiting")}</Pill> : null}
                    {tab === "other" ? (
                      <ExamStatusPill status={row.status as MyStatus} examType={row.examType} retakeAvailable={null} />
                    ) : null}
                  </p>
                </Card>
                {tab !== "other" && canRateSitting(row.status, row.examId, reviewed) ? (
                  <div className="mt-1 flex justify-end">
                    <RateLink examId={row.examId} />
                  </div>
                ) : null}
              </li>
            );
          })}
        </ul>
      )}

      {list.hasNextPage ? (
        <div className="flex justify-center">
          <Button variant="secondary" loading={list.isFetchingNextPage} onClick={() => void list.fetchNextPage()}>
            {t("result.hub.loadMore")}
          </Button>
        </div>
      ) : null}
    </div>
  );
}
