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

const RESULT_STATUSES = new Set(["RESULT_READY", "FAILED_TO_SUBMIT"]);
const MARKING_STATUSES = new Set(["AWAITING_RESULT"]);

type Tab = "results" | "marking";

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
        .filter((exam) => RESULT_STATUSES.has(exam.myStatus) || MARKING_STATUSES.has(exam.myStatus))
        .sort(newestFirst),
    [examQueries],
  );
  const resultRows = exams.filter((exam) => RESULT_STATUSES.has(exam.myStatus));
  const markingRows = exams.filter((exam) => MARKING_STATUSES.has(exam.myStatus));
  const visible = tab === "results" ? resultRows : markingRows;

  // Marks and pass are fetched for the visible result rows only.
  const resultQueries = useQueries({
    queries: (tab === "results" ? resultRows : []).map((exam) => ({
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
        ]}
        value={tab}
        onChange={(value) => setTab(value as Tab)}
      />

      {visible.length === 0 ? (
        <EmptyState
          title={tab === "results" ? t("result.hub.empty") : t("result.hub.emptyMarking")}
          body={tab === "results" ? t("result.hub.emptyBody") : undefined}
          action={
            tab === "results" ? (
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
                <Card href={`/exam/${exam.id}/result`}>
                  <p className="text-sm text-muted">{moduleTitle.get(exam.moduleId) ?? ""}</p>
                  <p className="font-semibold text-text">{pickTitle(exam, locale)}</p>
                  <p className="mt-1 flex flex-wrap items-center gap-2 text-sm text-muted">
                    <span>{exam.examType === "MCQ" ? t("result.hub.mcq") : t("result.hub.written")}</span>
                    <span aria-hidden="true">·</span>
                    <span>{exam.liveAt ? formatDate(exam.liveAt, locale) : t("result.hub.undated")}</span>
                    {tab === "results" && result ? (
                      <Pill tone={result.passed ? "ok" : "muted"}>
                        {result.score === null
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
