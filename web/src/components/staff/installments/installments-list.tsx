"use client";

import Link from "next/link";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { Pill } from "@/components/ui/pill";
import { SearchInput } from "@/components/ui/search-input";
import { Skeleton } from "@/components/ui/skeleton";
import { SummaryStrip, type SummaryItem } from "@/components/ui/summary-strip";
import { Table, type Column } from "@/components/ui/table";
import { Tabs } from "@/components/ui/tabs";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { staffHref } from "@/lib/config";
import {
  useInstallmentPlans,
  useInstallmentSummary,
  type InstallmentPlan,
  type InstallmentStatus,
} from "@/lib/api/exam/staff-installments";
import { useListParams } from "@/lib/url/list-params";
import {
  INSTALLMENT_FILTERS,
  activeScopes,
  filterFromParam,
  statusForFilter,
  statusKey,
  statusTone,
  summaryFigures,
  type ScopeKey,
} from "./installment-model";

/** The copy key for each narrowing filter, under `staffAdmin.installments.scope`. */
const SCOPE_COPY: Record<ScopeKey, string> = { studentId: "student", examId: "exam", moduleId: "module" };

/** A plan's status as a pill. The word is always shown; the colour only reinforces it. */
export function StatusPill({ status }: { status: InstallmentStatus }) {
  const { t } = useLocale();
  return <Pill tone={statusTone(status)}>{t(statusKey(status))}</Pill>;
}

/**
 * The Master Admin's list of installment plans: a summary band, filter chips, a search box, one row per plan, and paging. The
 * filters studentId, examId and moduleId come from the URL, so the links from other screens land on the right view (D18).
 */
export function InstallmentsList() {
  const { t } = useLocale();
  const { formatMoney, formatNumber, formatDate, pickTitle } = useFormat();
  const [params, setParams] = useListParams({ page: 0, q: "", filter: "ALL", studentId: "", examId: "", moduleId: "" });
  const filter = filterFromParam(params.filter);
  const scopes = activeScopes({ studentId: params.studentId, examId: params.examId, moduleId: params.moduleId });
  const plans = useInstallmentPlans({
    page: params.page,
    status: statusForFilter(filter),
    q: params.q,
    studentId: params.studentId,
    examId: params.examId,
    moduleId: params.moduleId,
  });
  const summary = useInstallmentSummary();
  const figures = summaryFigures(summary.data);

  const summaryItems: SummaryItem[] = [
    {
      label: t("staffAdmin.installments.summary.totalDue"),
      value: figures.totalDue === null ? "—" : (formatMoney(figures.totalDue) ?? "—"),
    },
    {
      label: t("staffAdmin.installments.summary.overdue"),
      value: figures.overdue === null ? "—" : formatNumber(figures.overdue),
      tone: figures.overdue ? "danger" : "muted",
    },
    {
      label: t("staffAdmin.installments.summary.dueSoon"),
      value: figures.dueSoon === null ? "—" : formatNumber(figures.dueSoon),
      tone: figures.dueSoon ? "warn" : "muted",
    },
  ];

  const columns: Column<InstallmentPlan>[] = [
    {
      key: "student",
      header: t("staffAdmin.installments.columns.student"),
      cell: (row) => (
        <Link
          href={staffHref(`/staff/installments/${row.planId}`)}
          className="font-medium text-text underline-offset-4 hover:underline"
        >
          {row.studentName || "—"}
        </Link>
      ),
    },
    {
      key: "item",
      header: t("staffAdmin.installments.columns.item"),
      cell: (row) => (
        <span className="grid gap-0.5">
          <span className="text-text">
            {pickTitle({ titleEn: row.itemTitleEn, titleBn: row.itemTitleBn }) || t("staffAdmin.installments.untitled")}
          </span>
          <span className="text-meta text-muted">{t(`staffAdmin.installments.kind.${row.scope}`)}</span>
        </span>
      ),
    },
    { key: "total", header: t("staffAdmin.installments.columns.total"), align: "end", nowrap: true, cell: (row) => formatMoney(row.agreedTotal) ?? "—" },
    { key: "paid", header: t("staffAdmin.installments.columns.paid"), align: "end", nowrap: true, cell: (row) => formatMoney(row.paid) ?? "—" },
    {
      key: "due",
      header: t("staffAdmin.installments.columns.due"),
      align: "end",
      nowrap: true,
      cell: (row) => <span className={row.due > 0 ? "font-medium text-text" : "text-muted"}>{formatMoney(row.due) ?? "—"}</span>,
    },
    {
      key: "next",
      header: t("staffAdmin.installments.columns.next"),
      cell: (row) =>
        row.nextDueOn
          ? t("staffAdmin.installments.nextDue", {
              amount: formatMoney(row.nextDueAmount ?? 0) ?? "",
              date: formatDate(row.nextDueOn),
            })
          : t("staffAdmin.installments.nothingDue"),
    },
    {
      key: "status",
      header: t("staffAdmin.installments.columns.status"),
      cell: (row) => <StatusPill status={row.status} />,
    },
  ];

  return (
    <div className="grid gap-5">
      <PageHeader
        title={t("staffAdmin.installments.title")}
        breadcrumbs={[{ label: t("common.staffNav.dashboard"), href: "/staff" }, { label: t("staffAdmin.installments.title") }]}
      />
      <p className="text-meta text-muted">{t("staffAdmin.installments.intro")}</p>

      <SummaryStrip items={summaryItems} loading={summary.isPending} />

      <div className="grid gap-3">
        <Tabs
          ariaLabel={t("staffAdmin.installments.filters.label")}
          value={filter}
          onChange={(value) => setParams({ filter: value })}
          variant="pill"
          wrap
          tabs={INSTALLMENT_FILTERS.map((value) => ({ value, label: t(`staffAdmin.installments.filters.${value}`) }))}
        />
        <SearchInput
          id="installments-q"
          value={params.q}
          onChange={(q) => setParams({ q })}
          placeholder={t("staffAdmin.installments.searchPlaceholder")}
        />
        {scopes.length > 0 ? (
          <div className="flex flex-wrap items-center gap-2 rounded-control border border-border bg-card px-3.5 py-2.5 text-meta">
            <span className="text-muted">{t("staffAdmin.installments.scope.label")}</span>
            {scopes.map((key) => (
              <Pill key={key} tone="accent">
                {t(`staffAdmin.installments.scope.${SCOPE_COPY[key]}`)}
              </Pill>
            ))}
            <Button variant="ghost" size="sm" onClick={() => setParams({ studentId: "", examId: "", moduleId: "" })}>
              {t("staffAdmin.installments.scope.clear")}
            </Button>
          </div>
        ) : null}
      </div>

      {plans.isError ? (
        <ErrorState message={t("staffAdmin.installments.loadError")} onRetry={() => void plans.refetch()} error={plans.error} />
      ) : plans.isPending ? (
        <Skeleton variant="rows" count={4} />
      ) : (
        <>
          <Table
            columns={columns}
            rows={plans.data?.content ?? []}
            rowKey={(row) => row.planId}
            loading={plans.isFetching}
            empty={<EmptyState title={t("staffAdmin.installments.empty")} body={t("staffAdmin.installments.emptyBody")} />}
          />
          {plans.data ? (
            <Pagination page={plans.data.page} totalPages={plans.data.totalPages} onPage={(page) => setParams({ page })} />
          ) : null}
        </>
      )}
    </div>
  );
}
