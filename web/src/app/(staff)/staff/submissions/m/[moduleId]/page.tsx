"use client";

import { Suspense, use, useMemo } from "react";
import { useRouter } from "next/navigation";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { PageHeader } from "@/components/ui/page-header";
import { Pill } from "@/components/ui/pill";
import { SearchInput } from "@/components/ui/search-input";
import { Select } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Stat, StatRow } from "@/components/ui/stat";
import { Table, type Column } from "@/components/ui/table";
import {
  matchesName,
  matchesSubmissionFilter,
  SUBMISSION_FILTERS,
  type SubmissionFilter,
} from "@/components/staff/people/people-copy";
import { ALL_STAFF, RequireStaff } from "@/lib/auth/guards";
import {
  useModuleSubmissions,
  useParticipation,
  type ParticipationRow,
  type SubmissionRow,
} from "@/lib/api/exam/staff-people";
import { useFormat } from "@/lib/format/use-format";
import { useListParams } from "@/lib/url/list-params";
import { useLocale } from "@/i18n/locale-context";

export default function StaffModuleSubmissionsPage({ params }: { params: Promise<{ moduleId: string }> }) {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={5} />}>
      <ModuleRoute params={params} />
    </Suspense>
  );
}

function ModuleRoute({ params }: { params: Promise<{ moduleId: string }> }) {
  const { moduleId } = use(params);
  return (
    <RequireStaff roles={ALL_STAFF}>
      <ModuleSubmissions moduleId={moduleId} />
    </RequireStaff>
  );
}

function ModuleSubmissions({ moduleId }: { moduleId: string }) {
  const { t } = useLocale();
  const format = useFormat();
  const router = useRouter();
  const [params, setParams] = useListParams({ filter: "all", exam: "", q: "" });
  const submissions = useModuleSubmissions(moduleId);
  const written = useParticipation({
    page: 0,
    size: 100,
    moduleId,
    type: "WRITTEN",
    from: "",
    to: "",
    q: "",
    sort: "title",
  });

  const exams = useMemo(() => written.data?.content ?? [], [written.data]);
  const examTitle = useMemo(() => {
    const map = new Map<string, string>();
    for (const exam of exams) {
      map.set(exam.examId, format.pickTitle({ titleEn: exam.titleEn, titleBn: exam.titleBn }));
    }
    return map;
  }, [exams, format]);

  const moduleTitle = exams[0]
    ? format.pickTitle({ titleEn: exams[0].moduleTitleEn, titleBn: exams[0].moduleTitleBn })
    : t("staffPeople.submissions.title");

  const rows = (submissions.data ?? []).filter(
    (row) =>
      (params.exam === "" || row.examId === params.exam) &&
      matchesSubmissionFilter(row, params.filter as SubmissionFilter) &&
      matchesName(row, params.q),
  );

  const columns: Column<SubmissionRow>[] = [
    { key: "student", header: t("staffPeople.submission.student"), cell: (row) => row.studentName },
    { key: "exam", header: t("staffPeople.submission.exam"), cell: (row) => examTitle.get(row.examId) ?? "—" },
    {
      key: "status",
      header: t("staffPeople.submission.status"),
      cell: (row) => t(`staffPeople.submission.statusWord.${row.status}`),
    },
    {
      key: "score",
      header: t("staffPeople.submission.score"),
      align: "end",
      cell: (row) => (row.score === null ? "—" : `${format.formatNumber(row.score)} / ${format.formatNumber(row.maximum)}`),
    },
    { key: "kind", header: t("staffPeople.submission.kind"), cell: (row) => t(`staffPeople.submission.kindWord.${row.kind}`) },
    { key: "answer", header: t("staffPeople.submission.answerFile"), cell: (row) => yesNo(row.answerPdf, t) },
    { key: "evaluated", header: t("staffPeople.submission.evaluatedFile"), cell: (row) => yesNo(row.evaluatedPdf === true, t) },
    { key: "examiner", header: t("staffPeople.submission.examiner"), cell: (row) => row.evaluatorName ?? "—" },
    {
      key: "published",
      header: t("staffPeople.submission.published"),
      cell: (row) => (row.published ? <Pill tone="ok">{t("staffPeople.submission.published")}</Pill> : yesNo(false, t)),
    },
  ];

  const sum = (pick: (row: ParticipationRow) => number | null) =>
    exams.reduce((total, row) => total + (pick(row) ?? 0), 0);

  return (
    <div className="grid gap-5">
      <PageHeader
        title={moduleTitle}
        breadcrumbs={[
          { label: t("common.staffNav.dashboard"), href: "/staff" },
          { label: t("staffPeople.submissions.title"), href: "/staff/submissions" },
          { label: moduleTitle },
        ]}
      />

      <StatRow>
        <Stat value={format.formatNumber(sum((row) => row.awaitingMarking))} label={t("staffPeople.participation.columns.awaiting")} />
        <Stat value={format.formatNumber(sum((row) => row.draftsAwaitingPublish))} label={t("staffPeople.participation.columns.drafts")} />
        <Stat value={format.formatNumber(sum((row) => row.published))} label={t("staffPeople.participation.columns.published")} />
      </StatRow>

      <div className="grid gap-4 sm:grid-cols-3">
        <Field label={t("staffPeople.submissions.filterLabel")}>
          <Select value={params.filter} onChange={(event) => setParams({ filter: event.target.value })}>
            {SUBMISSION_FILTERS.map((filter) => (
              <option key={filter} value={filter}>
                {t(`staffPeople.submissions.filter.${filter}`)}
              </option>
            ))}
          </Select>
        </Field>
        <Field label={t("staffPeople.submissions.examLabel")}>
          <Select value={params.exam} onChange={(event) => setParams({ exam: event.target.value })}>
            <option value="">{t("staffPeople.submissions.allExams")}</option>
            {exams.map((exam) => (
              <option key={exam.examId} value={exam.examId}>
                {examTitle.get(exam.examId)}
              </option>
            ))}
          </Select>
        </Field>
        <Field label={t("staffPeople.submissions.searchLabel")}>
          <SearchInput id="submissions-q" value={params.q} onChange={(q) => setParams({ q })} placeholder={t("staffPeople.submissions.searchPlaceholder")} />
        </Field>
      </div>

      {submissions.isError ? (
        <ErrorState onRetry={() => submissions.refetch()} />
      ) : (
        <Table
          columns={columns}
          rows={rows}
          rowKey={(row) => row.id}
          loading={submissions.isPending}
          onRowClick={(row) => router.push(`/staff/submissions/${row.id}?m=${moduleId}`)}
          empty={<EmptyState title={t("staffPeople.submissions.noRows")} body={t("staffPeople.submissions.noRowsBody")} />}
        />
      )}
    </div>
  );
}

function yesNo(value: boolean, t: (key: string) => string) {
  return value ? t("staffPeople.yesNo.yes") : t("staffPeople.yesNo.no");
}
