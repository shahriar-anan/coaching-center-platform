"use client";

import { Suspense } from "react";
import Link from "next/link";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { SearchInput } from "@/components/ui/search-input";
import { Select } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { RowMenu } from "@/components/ui/row-menu";
import { SummaryStrip } from "@/components/ui/summary-strip";
import { Table, type Column } from "@/components/ui/table";
import { TextInput } from "@/components/ui/text-input";
import { ALL_STAFF, RequireStaff } from "@/lib/auth/guards";
import {
  useModuleOptions,
  useParticipation,
  useParticipationSummary,
  type ParticipationRow,
} from "@/lib/api/exam/staff-people";
import { useFormat } from "@/lib/format/use-format";
import { useListParams } from "@/lib/url/list-params";
import { useLocale } from "@/i18n/locale-context";

export default function StaffParticipationPage() {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={5} />}>
      <RequireStaff roles={ALL_STAFF}>
        <Participation />
      </RequireStaff>
    </Suspense>
  );
}

const DASH = "—";

/** A date in Dhaka time, at the start or the end of the day, for the from and to filters. */
function dhakaDay(date: string, end: boolean): string {
  return end ? `${date}T23:59:59+06:00` : `${date}T00:00:00+06:00`;
}

function Participation() {
  const { t } = useLocale();
  const format = useFormat();
  const [params, setParams] = useListParams({
    page: 0,
    size: 20,
    moduleId: "",
    type: "",
    from: "",
    to: "",
    q: "",
    sort: "liveAt",
  });
  const rows = useParticipation({
    page: params.page,
    size: params.size,
    moduleId: params.moduleId,
    type: params.type,
    from: params.from ? dhakaDay(params.from, false) : "",
    to: params.to ? dhakaDay(params.to, true) : "",
    q: params.q,
    sort: params.sort,
  });
  const summary = useParticipationSummary();
  const modules = useModuleOptions();

  const columns: Column<ParticipationRow>[] = [
    {
      key: "exam",
      header: t("staffPeople.participation.columns.exam"),
      cell: (row) => format.pickTitle({ titleEn: row.titleEn, titleBn: row.titleBn }),
    },
    {
      key: "module",
      header: t("staffPeople.participation.columns.module"),
      cell: (row) => format.pickTitle({ titleEn: row.moduleTitleEn, titleBn: row.moduleTitleBn }),
    },
    {
      key: "type",
      header: t("staffPeople.participation.columns.type"),
      cell: (row) => t(row.examType === "MCQ" ? "staffPeople.participation.type.MCQ" : "staffPeople.participation.type.WRITTEN"),
    },
    {
      key: "start",
      header: t("staffPeople.participation.columns.start"),
      cell: (row) => (row.liveAt ? format.formatDateTime(row.liveAt) : t("common.dateTime.notSet")),
    },
    {
      key: "end",
      header: t("staffPeople.participation.columns.end"),
      cell: (row) => (row.endAt ? format.formatDateTime(row.endAt) : t("common.dateTime.notSet")),
    },
    { key: "sitting", header: t("staffPeople.participation.columns.sitting"), align: "end", cell: (row) => format.formatNumber(row.sitting) },
    { key: "submitted", header: t("staffPeople.participation.columns.submitted"), align: "end", cell: (row) => format.formatNumber(row.submitted) },
    { key: "failed", header: t("staffPeople.participation.columns.failed"), align: "end", cell: (row) => format.formatNumber(row.failedToSubmit) },
    {
      key: "awaiting",
      header: t("staffPeople.participation.columns.awaiting"),
      align: "end",
      cell: (row) => writtenCount(row, row.awaitingMarking, t),
    },
    {
      key: "drafts",
      header: t("staffPeople.participation.columns.drafts"),
      align: "end",
      cell: (row) => writtenCount(row, row.draftsAwaitingPublish, t),
    },
    {
      key: "published",
      header: t("staffPeople.participation.columns.published"),
      align: "end",
      cell: (row) => (row.published === null ? DASH : format.formatNumber(row.published)),
    },
    { key: "later", header: t("staffPeople.participation.columns.later"), align: "end", cell: (row) => format.formatNumber(row.retakes) },
    {
      key: "menu",
      header: "",
      align: "end",
      cell: (row) => (
        <RowMenu
          label={t("staffPeople.participation.rowMenu.label", {
            name: format.pickTitle({ titleEn: row.titleEn, titleBn: row.titleBn }),
          })}
          items={[
            { label: t("staffResults.rowMenu.openResults"), href: `/staff/exams/${row.examId}/results` },
            { label: t("staffResults.rowMenu.openLeaderboard"), href: `/staff/exams/${row.examId}/leaderboard` },
          ]}
        />
      ),
    },
  ];

  return (
    <div className="grid gap-5">
      <PageHeader
        title={t("staffPeople.participation.title")}
        breadcrumbs={[{ label: t("common.staffNav.dashboard"), href: "/staff" }, { label: t("staffPeople.participation.title") }]}
      />

      {summary.isError ? (
        <p className="text-meta text-muted">{t("staffPeople.participation.summaryError")}</p>
      ) : (
        <SummaryStrip
          loading={summary.isPending}
          items={[
            {
              label: t("staffPeople.participation.summary.awaiting"),
              value: format.formatNumber(summary.data?.awaitingMarking ?? 0),
              tone: "warn",
            },
            {
              label: t("staffPeople.participation.summary.drafts"),
              value: format.formatNumber(summary.data?.draftsAwaitingPublish ?? 0),
              tone: "warn",
            },
          ]}
        />
      )}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <Field label={t("staffPeople.participation.filters.module")}>
          <Select value={params.moduleId} onChange={(event) => setParams({ moduleId: event.target.value })}>
            <option value="">{t("staffPeople.participation.filters.allModules")}</option>
            {(modules.data?.content ?? []).map((module) => (
              <option key={module.id} value={module.id}>
                {format.pickTitle({ titleEn: module.titleEn, titleBn: module.titleBn })}
              </option>
            ))}
          </Select>
        </Field>
        <Field label={t("staffPeople.participation.filters.type")}>
          <Select value={params.type} onChange={(event) => setParams({ type: event.target.value })}>
            <option value="">{t("staffPeople.participation.filters.allTypes")}</option>
            <option value="MCQ">{t("staffPeople.participation.type.MCQ")}</option>
            <option value="WRITTEN">{t("staffPeople.participation.type.WRITTEN")}</option>
          </Select>
        </Field>
        <Field label={t("staffPeople.participation.filters.sort")}>
          <Select value={params.sort} onChange={(event) => setParams({ sort: event.target.value })}>
            <option value="liveAt">{t("staffPeople.participation.sort.date")}</option>
            <option value="title">{t("staffPeople.participation.sort.title")}</option>
            <option value="sitting">{t("staffPeople.participation.sort.sitting")}</option>
          </Select>
        </Field>
        <Field label={t("staffPeople.participation.filters.from")}>
          <TextInput type="date" value={params.from} onChange={(event) => setParams({ from: event.target.value })} />
        </Field>
        <Field label={t("staffPeople.participation.filters.to")}>
          <TextInput type="date" value={params.to} onChange={(event) => setParams({ to: event.target.value })} />
        </Field>
        <Field label={t("staffPeople.participation.filters.text")}>
          <SearchInput id="participation-q" value={params.q} onChange={(q) => setParams({ q })} placeholder={t("staffPeople.participation.filters.textPlaceholder")} />
        </Field>
      </div>

      {rows.isError ? (
        <ErrorState onRetry={() => rows.refetch()} />
      ) : (
        <>
          <Table
            columns={columns}
            rows={rows.data?.content ?? []}
            rowKey={(row) => row.examId}
            loading={rows.isPending}
            empty={<EmptyState title={t("staffPeople.participation.empty")} body={t("staffPeople.participation.emptyBody")} />}
          />
          {rows.data ? (
            <Pagination page={rows.data.page} totalPages={rows.data.totalPages} onPage={(page) => setParams({ page })} />
          ) : null}
        </>
      )}
    </div>
  );
}

/** A written count opens that module's submissions, filtered to the exam. MCQ rows show a dash. */
function writtenCount(row: ParticipationRow, value: number | null, t: (key: string) => string) {
  if (value === null) {
    return DASH;
  }
  const label = String(value);
  return (
    <Link
      href={`/staff/submissions/m/${row.moduleId}?exam=${row.examId}`}
      className="text-action underline-offset-4 hover:underline"
      aria-label={`${label} · ${t("staffPeople.participation.openSubmissions")}`}
    >
      {label}
    </Link>
  );
}
