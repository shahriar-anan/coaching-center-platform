"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { useQueries } from "@tanstack/react-query";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { Stat, StatRow } from "@/components/ui/stat";
import { Tabs } from "@/components/ui/tabs";
import { useLocale } from "@/i18n/locale-context";
import { apiRequest } from "@/lib/api/client";
import { sittingKeys, useModuleExams, useMyModules, type HubExam, type ReviewedResult } from "@/lib/api/exam/sitting";
import { formatDate, formatNumber, pickTitle } from "@/lib/format";
import { Button } from "@/components/ui/button";
import { ExamStatusPill } from "@/components/student/catalog/status-pill";
import { TypeFilter, filterByType, typeLabelKey, useTypeFilterParam } from "@/components/student/catalog/type-filter";
import type { MyStatus } from "@/lib/api/exam/catalog";
import { isMissing } from "@/lib/api/missing";

const RESULT_STATUSES = new Set(["RESULT_READY", "FAILED_TO_SUBMIT"]);
const MARKING_STATUSES = new Set(["AWAITING_RESULT"]);
/** Missed, open, or submitted but not yet due: listed on their own tab so the history is complete. */
const OTHER_STATUSES = new Set(["MISSED", "OPEN", "IN_PROGRESS", "SUBMITTED"]);

type Tab = "results" | "marking" | "other";

/** Newest first, by the day the exam is live. Undated exams sort last. */
function newestFirst(a: HubExam, b: HubExam): number {
  const left = a.liveAt ? Date.parse(a.liveAt) : 0;
  const right = b.liveAt ? Date.parse(b.liveAt) : 0;
  return right - left;
}

export function ResultsHub() {
  const { t, locale } = useLocale();
  const n = (value: number) => formatNumber(value, locale);
  const [tab, setTab] = useState<Tab>("results");
  const [type, setType] = useTypeFilterParam();
  const modules = useMyModules();
  const moduleList = useMemo(() => modules.data?.content ?? [], [modules.data]);
  const moduleIds = useMemo(() => moduleList.map((module) => module.id), [moduleList]);
  const moduleTitle = useMemo(
    () => new Map(moduleList.map((module) => [module.id, pickTitle(module, locale)])),
    [moduleList, locale],
  );
  const examQueries = useModuleExams(moduleIds);

  const loadingExams = examQueries.some((query) => query.isPending);
  const failedExams = examQueries.some((query) => query.isError);

  const exams = useMemo(
    () =>
      examQueries
        .flatMap((query) => query.data?.content ?? [])
        .filter((exam) => RESULT_STATUSES.has(exam.myStatus) || MARKING_STATUSES.has(exam.myStatus) || OTHER_STATUSES.has(exam.myStatus))
        .sort(newestFirst),
    [examQueries],
  );
  // The MCQ and Written filter (Phase 4, A3) narrows every tab, the summary and the badges together.
  const typed = filterByType(exams, type, (exam) => exam.examType);
  const resultRows = typed.filter((exam) => RESULT_STATUSES.has(exam.myStatus));
  const markingRows = typed.filter((exam) => MARKING_STATUSES.has(exam.myStatus));
  const otherRows = typed.filter((exam) => OTHER_STATUSES.has(exam.myStatus));
  const visible = tab === "results" ? resultRows : tab === "marking" ? markingRows : otherRows;

  // Marks and pass for each result row, used by the Passed count and the result tab.
  const resultQueries = useQueries({
    // Marks feed the Passed count on every tab, so they are fetched for every result row.
    queries: resultRows.map((exam) => ({
      queryKey: sittingKeys.result(exam.id),
      queryFn: () => apiRequest<ReviewedResult>(`/api/v1/live-exams/${exam.id}/result`),
      staleTime: 60_000,
    })),
  });
  const resultById = new Map(
    resultRows.map((exam, index) => [exam.id, resultQueries[index]?.data] as const),
  );
  const passedCount = resultRows.filter((exam) => resultById.get(exam.id)?.passed === true).length;

  const header = <PageHeader title={t("result.hub.title")} />;

  if (modules.isPending || loadingExams) {
    return (
      <div className="flex flex-col gap-4">
        {header}
        <p role="status" className="sr-only">{t("result.page.loading")}</p>
        <Skeleton variant="rows" count={3} />
      </div>
    );
  }

  if (modules.isError || failedExams) {
    return (
      <div className="flex flex-col gap-4">
        {header}
        <ErrorState onRetry={() => void modules.refetch()} />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4 pb-8">
      {header}

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

      {visible.length === 0 ? (
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
        <ul className="flex flex-col gap-3">
          {visible.map((exam) => {
            const result = resultById.get(exam.id);
            return (
              <li key={exam.id}>
                <Card href={tab === "other" ? `/exam/${exam.id}` : `/exam/${exam.id}/result`}>
                  <p className="text-body text-muted">{moduleTitle.get(exam.moduleId) ?? ""}</p>
                  <p className="font-semibold text-text">{pickTitle(exam, locale)}</p>
                  {exam.passPercent !== null && exam.passPercent !== undefined ? (
                    <p className="text-meta text-muted">{t("result.hub.passLine", { p: n(exam.passPercent) })}</p>
                  ) : null}
                  <p className="mt-1 flex flex-wrap items-center gap-2 text-body text-muted">
                    <span>{exam.examType === "MCQ" ? t("result.hub.mcq") : t("result.hub.written")}</span>
                    <span aria-hidden="true">·</span>
                    <span>{exam.liveAt ? formatDate(exam.liveAt, locale) : t("result.hub.undated")}</span>
                    {tab === "results" && result ? (
                      <Pill tone={result.passed ? "ok" : "muted"}>
                        {isMissing(result.score)
                          ? t("result.stat.none")
                          : t("result.stat.score", { score: n(result.score), maximum: n(result.maximum) })}
                        {" · "}
                        {result.passed ? t("result.outcome.passed") : t("result.outcome.failed")}
                      </Pill>
                    ) : null}
                    {tab === "results" && result?.kind === "RETAKE" ? (
                      <Pill tone="warn">{t("result.page.practice")}</Pill>
                    ) : null}
                    {tab === "marking" ? <Pill tone="warn">{t("result.hub.awaiting")}</Pill> : null}
                    {tab === "other" ? (
                      <ExamStatusPill status={exam.myStatus as MyStatus} examType={exam.examType} retakeAvailable={null} />
                    ) : null}
                  </p>
                </Card>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
