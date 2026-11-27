"use client";

import Link from "next/link";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { PrintArea } from "@/components/ui/print-area";
import { Select } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, type Column } from "@/components/ui/table";
import { Tabs } from "@/components/ui/tabs";
import { TextInput } from "@/components/ui/text-input";
import { useLocale } from "@/i18n/locale-context";
import { useGroups } from "@/lib/api/accounting/groups";
import { useGroupSummary, useMonthlyReport, useOwedReport } from "@/lib/api/accounting/reports";
import type { PayeeRow } from "@/lib/api/accounting/types";
import { dhakaToday } from "@/components/staff/offline/offline-model";
import { ACCOUNTING_HOME, AccountingFrame } from "../accounting-frame";
import { BalanceText } from "../balance-text";
import { money, monthLabel, monthOf, pickName, shiftMonth, writeErrorText } from "../accounting-model";
import { downloadCsv, toCsv } from "../csv";

type ReportTab = "owed" | "monthly" | "summary";

/** The reports (accounting-plan 4.6): owed now, spending and income by month, and one group's summary. Each has Print and CSV. */
export function ReportsPage() {
  const { t } = useLocale();
  const [tab, setTab] = useState<ReportTab>("owed");
  return (
    <AccountingFrame title={t("accounting.reports.title")}>
      <div className="grid gap-5">
        <Tabs
          ariaLabel={t("accounting.reports.title")}
          tabs={[
            { value: "owed", label: t("accounting.reports.tabs.owed") },
            { value: "monthly", label: t("accounting.reports.tabs.monthly") },
            { value: "summary", label: t("accounting.reports.tabs.summary") },
          ]}
          value={tab}
          onChange={(value) => setTab(value as ReportTab)}
        />
        {tab === "owed" ? <OwedReportView /> : null}
        {tab === "monthly" ? <MonthlyReportView /> : null}
        {tab === "summary" ? <SummaryReportView /> : null}
      </div>
    </AccountingFrame>
  );
}

function ReportActions({ onCsv, disabled }: { onCsv: () => void; disabled?: boolean }) {
  const { t } = useLocale();
  return (
    <div className="flex flex-wrap gap-2 print:hidden">
      <Button variant="secondary" onClick={() => window.print()} disabled={disabled}>
        {t("accounting.reports.print")}
      </Button>
      <Button variant="secondary" onClick={onCsv} disabled={disabled}>
        {t("accounting.reports.csv")}
      </Button>
    </div>
  );
}

/** Owed now: each group, every payee with a balance that is not zero, the group total and the grand total. */
function OwedReportView() {
  const { t, locale } = useLocale();
  const owed = useOwedReport();
  if (owed.isError) {
    return <ErrorState onRetry={() => void owed.refetch()} error={owed.error} />;
  }
  if (owed.isPending) {
    return <Skeleton variant="rows" count={4} />;
  }
  const data = owed.data;
  const csv = () => {
    const rows: (string | number)[][] = [[t("accounting.reports.csvHeaders.group"), t("accounting.reports.csvHeaders.payee"), t("accounting.reports.csvHeaders.balance")]];
    for (const group of data.groups) {
      for (const payee of group.payees) {
        rows.push([pickName(group, locale), payee.name, payee.balance]);
      }
      rows.push([pickName(group, locale), t("accounting.reports.total"), group.owed]);
    }
    rows.push([t("accounting.reports.total"), "", data.total]);
    downloadCsv("accounting-owed.csv", toCsv(rows));
  };
  const columns: Column<PayeeRow>[] = [
    {
      key: "name",
      header: t("accounting.payees.columns.name"),
      mobileLabel: t("accounting.payees.columns.name"),
      cell: (row) => (
        <Link href={`${ACCOUNTING_HOME}/payees/${row.id}`} className="text-text underline-offset-4 hover:underline">
          {row.name}
        </Link>
      ),
    },
    { key: "balance", header: t("accounting.payees.columns.balance"), mobileLabel: t("accounting.payees.columns.balance"), align: "end", nowrap: true, cell: (row) => <BalanceText balance={row.balance} /> },
  ];
  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-label text-text">
          {t("accounting.reports.owedTotal")}: <strong>{money(data.total, locale)}</strong>
          <span className="text-muted"> · {t("accounting.balance.advanceAll")} {money(data.advance, locale)}</span>
        </p>
        <ReportActions onCsv={csv} />
      </div>
      <PrintArea>
        <div className="grid gap-5">
          {data.groups.length === 0 ? <EmptyState title={t("accounting.reports.owedNone")} /> : null}
          {data.groups.map((group) => (
            <section key={group.groupId} className="grid gap-2">
              <h2 className="text-section font-semibold text-text">
                {pickName(group, locale)} <span className="text-body text-muted">· {money(group.owed, locale)}</span>
              </h2>
              <Table columns={columns} rows={group.payees} rowKey={(row) => row.id} />
            </section>
          ))}
        </div>
      </PrintArea>
    </div>
  );
}

/** By month: a row per group, a column per month, and the expense and income totals. At most 36 months. */
function MonthlyReportView() {
  const { t, locale } = useLocale();
  const thisMonth = monthOf(dhakaToday());
  const [to, setTo] = useState(thisMonth);
  const [from, setFrom] = useState(shiftMonth(thisMonth, -11));
  const span = monthsBetween(from, to);
  const tooWide = span < 1 || span > 36;
  const report = useMonthlyReport(tooWide ? "" : from, tooWide ? "" : to);

  const csv = () => {
    const data = report.data;
    if (!data) {
      return;
    }
    const rows: (string | number)[][] = [[t("accounting.reports.csvHeaders.group"), ...data.months, t("accounting.reports.total")]];
    for (const row of data.rows) {
      rows.push([pickName(row, locale), ...row.amounts, row.total]);
    }
    rows.push([t("accounting.kind.SPEND"), ...data.expense, ""]);
    rows.push([t("accounting.kind.INCOME"), ...data.income, ""]);
    downloadCsv(`accounting-by-month-${from}-${to}.csv`, toCsv(rows));
  };

  const columns: Column<{ groupId: string; nameEn: string; nameBn: string | null; amounts: string[]; total: string }>[] = [
    { key: "group", header: t("accounting.reports.csvHeaders.group"), mobileLabel: t("accounting.reports.csvHeaders.group"), cell: (row) => pickName(row, locale) },
    ...(report.data?.months ?? []).map((month, index) => ({
      key: month,
      header: monthLabel(month, locale),
      mobileLabel: monthLabel(month, locale),
      align: "end" as const,
      nowrap: true,
      cell: (row: { amounts: string[] }) => money(row.amounts[index], locale),
    })),
    { key: "total", header: t("accounting.reports.total"), mobileLabel: t("accounting.reports.total"), align: "end", nowrap: true, cell: (row) => money(row.total, locale) },
  ];

  return (
    <div className="grid gap-4">
      <div className="grid gap-3 sm:grid-cols-[1fr_1fr_auto] sm:items-end">
        <Field label={t("accounting.reports.from")} error={span > 36 ? t("accounting.reports.tooWide") : undefined}>
          <TextInput type="month" value={from} max={to} onChange={(event) => setFrom(event.target.value)} />
        </Field>
        <Field label={t("accounting.reports.to")}>
          <TextInput type="month" value={to} max={thisMonth} onChange={(event) => setTo(event.target.value)} />
        </Field>
        <ReportActions onCsv={csv} disabled={!report.data} />
      </div>
      {report.isError ? (
        <ErrorState onRetry={() => void report.refetch()} error={report.error} />
      ) : tooWide ? (
        <p role="alert" className="text-meta text-danger">{t("accounting.reports.tooWide")}</p>
      ) : (
        <PrintArea>
          <Table columns={columns} rows={report.data?.rows ?? []} rowKey={(row) => row.groupId} loading={report.isPending} />
          {report.data ? (
            <dl className="mt-3 grid gap-2 text-body sm:grid-cols-2">
              <div className="flex gap-2">
                <dt className="text-muted">{t("accounting.kind.SPEND")}:</dt>
                <dd className="font-medium">{report.data.expense.map((value, index) => `${monthLabel(report.data.months[index], locale)} ${money(value, locale)}`).join(" · ")}</dd>
              </div>
              <div className="flex gap-2">
                <dt className="text-muted">{t("accounting.kind.INCOME")}:</dt>
                <dd className="font-medium">{report.data.income.map((value, index) => `${monthLabel(report.data.months[index], locale)} ${money(value, locale)}`).join(" · ")}</dd>
              </div>
            </dl>
          ) : null}
        </PrintArea>
      )}
    </div>
  );
}

/** Group summary: totals per type (quantity and amount) and, for a Payees group, per person (charged and paid). */
function SummaryReportView() {
  const { t, locale } = useLocale();
  const today = dhakaToday();
  const groups = useGroups(true);
  const [groupId, setGroupId] = useState("");
  const [from, setFrom] = useState(`${today.slice(0, 7)}-01`);
  const [to, setTo] = useState(today);
  const summary = useGroupSummary(groupId, from, to);
  const chosen = (groups.data ?? []).find((group) => group.id === groupId) ?? null;

  const csv = () => {
    const data = summary.data;
    if (!data) {
      return;
    }
    const rows: (string | number)[][] = [[t("accounting.reports.csvHeaders.type"), t("accounting.reports.csvHeaders.unit"), t("accounting.reports.csvHeaders.quantity"), t("accounting.reports.csvHeaders.amount"), t("accounting.reports.csvHeaders.count")]];
    for (const row of data.byType) {
      rows.push([pickName(row, locale), pickName({ nameEn: row.unitEn ?? "", nameBn: row.unitBn }, locale), row.quantity ?? "", row.amount, row.count]);
    }
    if (data.byPayee.length > 0) {
      rows.push([]);
      rows.push([t("accounting.reports.csvHeaders.payee"), t("accounting.reports.csvHeaders.charged"), t("accounting.reports.csvHeaders.paid")]);
      for (const row of data.byPayee) {
        rows.push([row.name, row.charged, row.paid]);
      }
    }
    downloadCsv(`accounting-summary-${from}-${to}.csv`, toCsv(rows));
  };

  const typeColumns: Column<{ typeId: string; nameEn: string; nameBn: string | null; kind: string; unitEn: string | null; unitBn: string | null; quantity: string | null; amount: string; count: number }>[] = [
    { key: "type", header: t("accounting.reports.csvHeaders.type"), mobileLabel: t("accounting.reports.csvHeaders.type"), cell: (row) => pickName(row, locale) },
    { key: "quantity", header: t("accounting.reports.csvHeaders.quantity"), mobileLabel: t("accounting.reports.csvHeaders.quantity"), align: "end", cell: (row) => (row.quantity ? `${row.quantity} ${pickName({ nameEn: row.unitEn ?? "", nameBn: row.unitBn }, locale)}` : "—") },
    { key: "count", header: t("accounting.reports.csvHeaders.count"), mobileLabel: t("accounting.reports.csvHeaders.count"), align: "end", cell: (row) => String(row.count) },
    { key: "amount", header: t("accounting.reports.csvHeaders.amount"), mobileLabel: t("accounting.reports.csvHeaders.amount"), align: "end", nowrap: true, cell: (row) => money(row.amount, locale) },
  ];
  const payeeColumns: Column<{ payeeId: string; name: string; charged: string; paid: string }>[] = [
    { key: "name", header: t("accounting.reports.csvHeaders.payee"), mobileLabel: t("accounting.reports.csvHeaders.payee"), cell: (row) => row.name },
    { key: "charged", header: t("accounting.reports.csvHeaders.charged"), mobileLabel: t("accounting.reports.csvHeaders.charged"), align: "end", nowrap: true, cell: (row) => money(row.charged, locale) },
    { key: "paid", header: t("accounting.reports.csvHeaders.paid"), mobileLabel: t("accounting.reports.csvHeaders.paid"), align: "end", nowrap: true, cell: (row) => money(row.paid, locale) },
  ];

  return (
    <div className="grid gap-4">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-[1fr_1fr_1fr_auto] lg:items-end">
        <Field label={t("accounting.reports.group")}>
          <Select value={groupId} onChange={(event) => setGroupId(event.target.value)}>
            <option value="">{t("accounting.reports.chooseGroup")}</option>
            {(groups.data ?? []).map((group) => (
              <option key={group.id} value={group.id}>
                {pickName(group, locale)}
              </option>
            ))}
          </Select>
        </Field>
        <Field label={t("accounting.entries.filters.from")}>
          <TextInput type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
        </Field>
        <Field label={t("accounting.entries.filters.to")}>
          <TextInput type="date" value={to} onChange={(event) => setTo(event.target.value)} />
        </Field>
        <ReportActions onCsv={csv} disabled={!summary.data} />
      </div>
      {!groupId ? (
        <EmptyState title={t("accounting.reports.pickGroup")} />
      ) : summary.isError ? (
        <ErrorState onRetry={() => void summary.refetch()} error={summary.error} />
      ) : (
        <PrintArea>
          <div className="grid gap-5">
            <h2 className="text-section font-semibold text-text">{chosen ? pickName(chosen, locale) : ""}</h2>
            {summary.data ? (
              <p className="text-label text-text">
                {t("accounting.kind.SPEND")}: <strong>{money(summary.data.spent, locale)}</strong> · {t("accounting.kind.INCOME")}:{" "}
                <strong>{money(summary.data.income, locale)}</strong> · {t("accounting.kind.CHARGE")}: <strong>{money(summary.data.charged, locale)}</strong> ·{" "}
                {t("accounting.kind.PAYMENT")}: <strong>{money(summary.data.paid, locale)}</strong>
              </p>
            ) : null}
            <Table columns={typeColumns} rows={summary.data?.byType ?? []} rowKey={(row) => row.typeId} loading={summary.isPending} />
            {chosen?.mode === "PAYEES" ? (
              <Table columns={payeeColumns} rows={summary.data?.byPayee ?? []} rowKey={(row) => row.payeeId} />
            ) : null}
          </div>
        </PrintArea>
      )}
    </div>
  );
}

/** How many months a range covers, both ends included. */
export function monthsBetween(from: string, to: string): number {
  if (!from || !to) {
    return 0;
  }
  const [fy, fm] = from.split("-").map(Number);
  const [ty, tm] = to.split("-").map(Number);
  return (ty * 12 + tm) - (fy * 12 + fm) + 1;
}
