"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { useLocale } from "@/i18n/locale-context";
import { formatMoney, formatNumber } from "@/lib/format";
import { ErrorState } from "@/components/ui/error-state";
import { Skeleton } from "@/components/ui/skeleton";
import {
  OFFLINE_SOURCES,
  useFinanceBatches,
  useFinanceInstructors,
  type FinanceFilters,
  type FinanceSource,
  type FinanceSummary,
} from "@/lib/api/exam/finance";
import { entriesHref, type FinanceUrlState } from "@/components/staff/finance/finance-model";
import { SOURCE_COLOR } from "@/components/staff/finance/finance-theme";
import { cn } from "@/components/ui/cn";

// The tabs under the chart (plan 8.4): by source, by batch and by instructor. Every money figure links to
// the entries behind it, on the entries page (/staff/finance/entries) with the same dates, batch and sources.

type Column = { key: string; label: string; numeric?: boolean };
type Row = { key: string; cells: Record<string, ReactNode> };

/**
 * A table on a wide screen; a stack of cards on a phone, one card per row, each field under its own label. Both are rendered from
 * the same rows, so the phone view is never missing a field.
 */
function FinanceTable({ caption, columns, rows }: { caption: string; columns: Column[]; rows: Row[] }) {
  return (
    <>
      <div className="hidden overflow-x-auto md:block">
        <table className="w-full min-w-lg border-collapse text-body">
          <caption className="sr-only">{caption}</caption>
          <thead>
            <tr className="border-b border-border text-muted">
              {columns.map((column) => (
                <th key={column.key} scope="col" className={cn("py-2 pe-3 font-medium", column.numeric ? "text-end" : "text-start")}>
                  {column.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.key} className="border-b border-border/60">
                {columns.map((column, index) =>
                  index === 0 ? (
                    <th key={column.key} scope="row" className="py-2 pe-3 text-start font-normal text-text">
                      {row.cells[column.key]}
                    </th>
                  ) : (
                    <td key={column.key} className={cn("py-2 pe-3 tabular-nums", column.numeric && "text-end")}>
                      {row.cells[column.key]}
                    </td>
                  ),
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <ul className="grid gap-3 md:hidden" aria-label={caption}>
        {rows.map((row) => (
          <li key={row.key} className="grid gap-2 rounded-card border border-border bg-card p-4">
            <div className="text-card-title text-text">{row.cells[columns[0].key]}</div>
            <dl className="grid grid-cols-2 gap-x-3 gap-y-1.5 text-meta">
              {columns.slice(1).map((column) => (
                <div key={column.key} className="contents">
                  <dt className="text-muted">{column.label}</dt>
                  <dd className="text-end tabular-nums text-text">{row.cells[column.key]}</dd>
                </div>
              ))}
            </dl>
          </li>
        ))}
      </ul>
    </>
  );
}

/** A figure that is a link to its entries, styled as text so the number still reads as a number. */
function FigureLink({ href, children }: { href: string; children: ReactNode }) {
  return (
    <Link href={href} className="tabular-nums text-text-accent underline-offset-2 hover:underline">
      {children}
    </Link>
  );
}

function SourceSwatch({ source, label }: { source: FinanceSource; label: string }) {
  return (
    <span className="inline-flex items-center gap-2">
      <span aria-hidden="true" className="inline-block h-3 w-3 shrink-0 rounded-[3px]" style={{ background: SOURCE_COLOR[source] }} />
      {label}
    </span>
  );
}

export function BySourceTab({
  summary,
  state,
}: {
  summary: FinanceSummary;
  state: FinanceUrlState;
}) {
  const { t, locale } = useLocale();
  if (summary.sources.length === 0) {
    return <p className="text-body text-muted">{t("finance.bySource.empty")}</p>;
  }
  const columns: Column[] = [
    { key: "source", label: t("finance.bySource.source") },
    { key: "count", label: t("finance.bySource.count"), numeric: true },
    { key: "total", label: t("finance.bySource.total"), numeric: true },
    { key: "share", label: t("finance.bySource.share"), numeric: true },
  ];
  const rows: Row[] = summary.sources.map((row) => {
    const side = row.direction === "INCOME" ? summary.income : summary.expense;
    const sharePercent = side > 0 ? (row.total * 100) / side : null;
    const label = t(`finance.source.${row.source}`);
    return {
      key: row.source,
      cells: {
        source: <SourceSwatch source={row.source} label={label} />,
        count: formatNumber(row.count, locale),
        total: (
          <FigureLink href={entriesHref(state, [row.source])}>{formatMoney(row.total, locale)}</FigureLink>
        ),
        share: sharePercent === null ? "—" : `${formatNumber(sharePercent, locale, { decimals: 1 })}%`,
      },
    };
  });
  return (
    <div className="grid gap-3">
      <FinanceTable caption={t("finance.tabs.source")} columns={columns} rows={rows} />
      <p className="text-meta text-muted">{t("finance.bySource.shareNote")}</p>
    </div>
  );
}

export function ByBatchTab({ filters, state }: { filters: FinanceFilters; state: FinanceUrlState }) {
  const { t, locale } = useLocale();
  const query = useFinanceBatches(filters);
  if (query.isPending) {
    return <Skeleton variant="rows" count={3} />;
  }
  if (query.isError) {
    return <ErrorState onRetry={() => void query.refetch()} message={t("finance.errors.load")} error={query.error} />;
  }
  const rows = query.data ?? [];
  if (rows.length === 0) {
    return <p className="text-body text-muted">{t("finance.byBatch.empty")}</p>;
  }
  const columns: Column[] = [
    { key: "batch", label: t("finance.byBatch.batch") },
    { key: "fees", label: t("finance.byBatch.fees"), numeric: true },
    { key: "cost", label: t("finance.byBatch.cost"), numeric: true },
    { key: "net", label: t("finance.byBatch.net"), numeric: true },
    { key: "margin", label: t("finance.byBatch.margin"), numeric: true },
  ];
  const tableRows: Row[] = rows.map((row) => ({
    key: row.batchId,
    cells: {
      batch: row.name,
      fees: <FigureLink href={entriesHref(state, ["OFFLINE_FEES"], row.batchId)}>{formatMoney(row.feesCollected, locale)}</FigureLink>,
      cost: (
        <FigureLink href={entriesHref(state, ["OFFLINE_INSTRUCTORS"], row.batchId)}>{formatMoney(row.instructorCost, locale)}</FigureLink>
      ),
      net: (
        <FigureLink href={entriesHref(state, OFFLINE_SOURCES, row.batchId)}>{formatMoney(row.net, locale)}</FigureLink>
      ),
      margin:
        row.marginPercent == null
          ? t("finance.byBatch.noMargin")
          : `${formatNumber(row.marginPercent, locale, { decimals: 1 })}%`,
    },
  }));
  return <FinanceTable caption={t("finance.tabs.batch")} columns={columns} rows={tableRows} />;
}

export function ByInstructorTab({ filters, state }: { filters: FinanceFilters; state: FinanceUrlState }) {
  const { t, locale } = useLocale();
  const query = useFinanceInstructors(filters.from, filters.to);
  if (query.isPending) {
    return <Skeleton variant="rows" count={3} />;
  }
  if (query.isError) {
    return <ErrorState onRetry={() => void query.refetch()} message={t("finance.errors.load")} error={query.error} />;
  }
  const rows = query.data ?? [];
  if (rows.length === 0) {
    return <p className="text-body text-muted">{t("finance.byInstructor.empty")}</p>;
  }
  const columns: Column[] = [
    { key: "name", label: t("finance.byInstructor.name") },
    { key: "payments", label: t("finance.byInstructor.payments"), numeric: true },
    { key: "paid", label: t("finance.byInstructor.paid"), numeric: true },
  ];
  const tableRows: Row[] = rows.map((row) => ({
    key: row.instructorId,
    cells: {
      name: row.name,
      payments: formatNumber(row.payments, locale),
      // The paid figure is the approved payments, which are the entries; the batch filter does not apply to instructors.
      paid: <FigureLink href={entriesHref(state, ["OFFLINE_INSTRUCTORS"], null)}>{formatMoney(row.paid, locale)}</FigureLink>,
    },
  }));
  return (
    <div className="grid gap-3">
      <FinanceTable caption={t("finance.tabs.instructor")} columns={columns} rows={tableRows} />
      <p className="text-meta text-muted">{t("finance.byInstructor.note")}</p>
    </div>
  );
}
