"use client";

import { Suspense, use } from "react";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { SearchInput } from "@/components/ui/search-input";
import { Select } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { SummaryStrip } from "@/components/ui/summary-strip";
import { RESULT_KINDS, RESULT_SORTS, RESULT_STATUSES, resultsSummaryItems } from "@/components/staff/results/results-copy";
import { ReleasePanel } from "@/components/staff/results/release-panel";
import { ResultsTable } from "@/components/staff/results/results-table";
import { ALL_STAFF, RequireStaff, useRole } from "@/lib/auth/guards";
import { useExamResults } from "@/lib/api/exam/staff-results";
import { useStaffExam } from "@/lib/api/exam/staff-ops";
import { useFormat } from "@/lib/format/use-format";
import { useListParams } from "@/lib/url/list-params";
import { useLocale } from "@/i18n/locale-context";

export default function ExamResultsPage({ params }: { params: Promise<{ examId: string }> }) {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={5} />}>
      <ResultsRoute params={params} />
    </Suspense>
  );
}

function ResultsRoute({ params }: { params: Promise<{ examId: string }> }) {
  const { examId } = use(params);
  return (
    <RequireStaff roles={ALL_STAFF}>
      <ResultsReport examId={examId} />
    </RequireStaff>
  );
}

/** The results report: a summary strip, then the filtered and sorted table. Every filter is in the URL. */
function ResultsReport({ examId }: { examId: string }) {
  const { t } = useLocale();
  const format = useFormat();
  const { isMasterAdmin, isSystemAdmin } = useRole();
  const [params, setParams] = useListParams({
    page: 0,
    size: 20,
    kind: "",
    status: "",
    q: "",
    sort: "submittedAt,desc",
  });
  const exam = useStaffExam(examId);
  const report = useExamResults(examId, params);
  const examWritten = exam.data?.examType === "WRITTEN";

  const examTitle = exam.data ? format.pickTitle({ titleEn: exam.data.titleEn, titleBn: exam.data.titleBn }) : "";
  const leaderboardHref = `/staff/exams/${examId}/leaderboard`;
  const canOpenStudents = isMasterAdmin || isSystemAdmin;
  const filtered = params.kind !== "" || params.status !== "" || params.q !== "";

  return (
    <div className="grid gap-5">
      <PageHeader
        title={t("staffResults.title")}
        breadcrumbs={[
          { label: t("common.staffNav.dashboard"), href: "/staff" },
          { label: examTitle || t("staffResults.title") },
        ]}
        actions={
          <>
            <a
              href={leaderboardHref}
              className="inline-flex min-h-10 items-center rounded-control px-3 text-label text-action underline-offset-4 hover:underline"
            >
              {t("staffResults.openLeaderboard")}
            </a>
          </>
        }
      />

      {isMasterAdmin && examWritten ? (
        <div id="release" className="scroll-mt-24">
          <ReleasePanel examId={examId} examTitle={examTitle} />
        </div>
      ) : null}

      {report.isError ? (
        <ErrorState onRetry={() => report.refetch()} />
      ) : (
        <SummaryStrip
          loading={report.isPending}
          items={resultsSummaryItems(report.data?.summary ?? EMPTY_SUMMARY, t, format)}
        />
      )}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Field label={t("staffResults.filters.search")}>
          <SearchInput
            id="results-q"
            value={params.q}
            onChange={(q) => setParams({ q })}
            placeholder={t("staffResults.filters.searchPlaceholder")}
          />
        </Field>
        <Field label={t("staffResults.filters.kind")}>
          <Select value={params.kind} onChange={(event) => setParams({ kind: event.target.value })}>
            <option value="">{t("staffResults.filters.kindAll")}</option>
            {RESULT_KINDS.map((kind) => (
              <option key={kind} value={kind}>
                {t(`staffResults.kind.${kind}`)}
              </option>
            ))}
          </Select>
        </Field>
        <Field label={t("staffResults.filters.status")}>
          <Select value={params.status} onChange={(event) => setParams({ status: event.target.value })}>
            <option value="">{t("staffResults.filters.statusAll")}</option>
            {RESULT_STATUSES.map((status) => (
              <option key={status} value={status}>
                {t(`staffResults.status.${status}`)}
              </option>
            ))}
          </Select>
        </Field>
        <Field label={t("staffResults.filters.sort")}>
          <Select value={params.sort} onChange={(event) => setParams({ sort: event.target.value })}>
            {RESULT_SORTS.map((sort) => (
              <option key={sort.value} value={sort.value}>
                {t(`staffResults.sort.${sort.key}`)}
              </option>
            ))}
          </Select>
        </Field>
      </div>
      {filtered ? (
        <div>
          <Button variant="ghost" onClick={() => setParams({ kind: "", status: "", q: "" })}>
            {t("staffResults.filters.reset")}
          </Button>
        </div>
      ) : null}

      {report.isError ? null : (
        <>
          <ResultsTable
            rows={report.data?.rows.content ?? []}
            loading={report.isPending}
            examWritten={examWritten}
            canOpenStudents={canOpenStudents}
            leaderboardHref={leaderboardHref}
            empty={
              filtered ? (
                <EmptyState title={t("staffResults.empty.noMatch")} body={t("staffResults.empty.noMatchBody")} />
              ) : (
                <EmptyState title={t("staffResults.empty.title")} body={t("staffResults.empty.body")} />
              )
            }
          />
          {report.data ? (
            <Pagination
              page={report.data.rows.page}
              totalPages={report.data.rows.totalPages}
              onPage={(page) => setParams({ page })}
            />
          ) : null}
        </>
      )}
    </div>
  );
}

/** Zeros shown while the report loads; the strip is in its loading state then, so no real count is claimed. */
const EMPTY_SUMMARY = {
  participants: 0,
  submitted: 0,
  failedToSubmit: 0,
  averageScore: null,
  highestScore: null,
  lowestScore: null,
  passCount: 0,
  passRate: null,
  practiceParticipants: 0,
};
