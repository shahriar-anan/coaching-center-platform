"use client";

import Link from "next/link";
import { useState } from "react";
import { cn } from "@/components/ui/cn";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, type Column } from "@/components/ui/table";
import { TextInput } from "@/components/ui/text-input";
import { useLocale } from "@/i18n/locale-context";
import {
  usePayAnalytics,
  type PayAnalytics,
  type PayRangePerson,
} from "@/lib/api/exam/staff-pay-analytics";
import { formatMoney, formatNumber } from "@/lib/format";
import {
  defaultRange,
  monthLabel,
  rangeProblem,
  type RangeProblem,
} from "@/components/staff/pay/pay-analytics-format";
import { PayrollChart, PayrollTable } from "@/components/staff/pay/pay-payroll-chart";

const PROBLEM_KEY: Record<RangeProblem, string> = {
  invalid: "staffPayOverview.analytics.rangeInvalid",
  backwards: "staffPayOverview.analytics.rangeBackwards",
  long: "staffPayOverview.analytics.rangeLong",
};

/**
 * The Analytics tab on the Master Admin's staff pay page (staff pay plan, phase 6). The range is two months, the last twelve by
 * default. Each part loads from one call and shows its own error, so a failed part never hides the others.
 */
export function PayAnalytics() {
  const { t } = useLocale();
  const [range, setRange] = useState(() => defaultRange());
  const problem = rangeProblem(range.from, range.to);
  const analytics = usePayAnalytics(problem ? null : range.from, problem ? null : range.to);

  return (
    <div className="grid gap-6">
      <div className="flex flex-wrap items-end gap-4">
        <Field label={t("staffPayOverview.analytics.from")}>
          <TextInput
            type="month"
            value={range.from}
            onChange={(event) => setRange((current) => ({ ...current, from: event.target.value }))}
          />
        </Field>
        <Field label={t("staffPayOverview.analytics.to")}>
          <TextInput
            type="month"
            value={range.to}
            onChange={(event) => setRange((current) => ({ ...current, to: event.target.value }))}
          />
        </Field>
      </div>

      {problem ? (
        <p role="alert" className="text-meta text-danger">
          {t(PROBLEM_KEY[problem])}
        </p>
      ) : analytics.isError ? (
        <ErrorState onRetry={() => void analytics.refetch()} error={analytics.error} />
      ) : !analytics.data ? (
        <Skeleton variant="rows" count={3} />
      ) : (
        <AnalyticsBody data={analytics.data} />
      )}
    </div>
  );
}

function AnalyticsBody({ data }: { data: PayAnalytics }) {
  const { t } = useLocale();
  return (
    <div className="grid gap-8">
      <section aria-labelledby="pay-payroll-title" className="grid gap-3">
        <h2 id="pay-payroll-title" className="text-section font-bold text-text">
          {t("staffPayOverview.analytics.payrollTitle")}
        </h2>
        <p className="text-meta text-muted">{t("staffPayOverview.analytics.payrollBody")}</p>
        <PayrollChart rows={data.payroll} />
        <PayrollTable rows={data.payroll} />
      </section>

      <section aria-labelledby="pay-sheets-title" className="grid gap-3">
        <h2 id="pay-sheets-title" className="text-section font-bold text-text">
          {t("staffPayOverview.analytics.sheetsTitle")}
        </h2>
        <p className="text-meta text-muted">{t("staffPayOverview.analytics.sheetsBody")}</p>
        <SheetsTable data={data} />
      </section>

      <section aria-labelledby="pay-range-people-title" className="grid gap-3">
        <h2 id="pay-range-people-title" className="text-section font-bold text-text">
          {t("staffPayOverview.analytics.peopleTitle")}
        </h2>
        <p className="text-meta text-muted">{t("staffPayOverview.analytics.peopleBody")}</p>
        <PeopleRangeTable rows={data.people} />
      </section>
    </div>
  );
}

/** Sheets published per teacher, one column per month. Zero months show as 0. */
function SheetsTable({ data }: { data: PayAnalytics }) {
  const { t, locale } = useLocale();
  if (data.sheets.length === 0) {
    return <EmptyState title={t("staffPayOverview.analytics.sheetsEmpty")} />;
  }
  const count = (value: number) => formatNumber(value, locale);
  return (
    <div className="overflow-x-auto rounded-card border border-border bg-card">
      <table className="w-full min-w-[30rem] border-collapse text-meta">
        <thead>
          <tr className="border-b border-border text-muted">
            <th scope="col" className="px-3 py-2 text-start font-medium">
              {t("staffPayOverview.analytics.sheetsTeacher")}
            </th>
            {data.months.map((month) => (
              <th key={month} scope="col" className="px-2 py-2 text-end font-medium whitespace-nowrap">
                {monthLabel(month, locale)}
              </th>
            ))}
            <th scope="col" className="px-3 py-2 text-end font-medium">
              {t("staffPayOverview.analytics.sheetsTotal")}
            </th>
          </tr>
        </thead>
        <tbody>
          {data.sheets.map((row) => (
            <tr key={row.userId} className="border-b border-border/60 last:border-b-0">
              <th scope="row" className="px-3 py-2 text-start font-normal">
                <Link href={`/staff/pay/${row.userId}`} className="text-accent underline-offset-4 hover:underline">
                  {row.fullName || t("staffPay.people.unnamed")}
                </Link>
              </th>
              {row.byMonth.map((cell) => (
                <td
                  key={cell.month}
                  className={cn("px-2 py-2 text-end tabular-nums", cell.sheets === 0 ? "text-muted" : "text-text")}
                >
                  {count(cell.sheets)}
                </td>
              ))}
              <td className="px-3 py-2 text-end font-semibold tabular-nums text-text">{count(row.total)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Each person's earned or due, paid and balance over the range. */
function PeopleRangeTable({ rows }: { rows: PayRangePerson[] }) {
  const { t, locale } = useLocale();
  const money = (value: number) => formatMoney(value, locale) ?? "";
  const columns: Column<PayRangePerson>[] = [
    {
      key: "name",
      header: t("staffPayOverview.analytics.name"),
      cell: (row) => (
        <Link href={`/staff/pay/${row.userId}`} className="font-medium text-accent underline-offset-4 hover:underline">
          {row.fullName || t("staffPay.people.unnamed")}
        </Link>
      ),
    },
    {
      key: "role",
      header: t("staffPayOverview.analytics.role"),
      cell: (row) => t(`staffPay.role.${row.role}`),
      mobileHidden: true,
    },
    {
      key: "earned",
      header: t("staffPayOverview.analytics.earned"),
      align: "end",
      nowrap: true,
      cell: (row) => <span className="tabular-nums">{money(row.earned)}</span>,
    },
    {
      key: "paid",
      header: t("staffPayOverview.analytics.paid"),
      align: "end",
      nowrap: true,
      cell: (row) => <span className="tabular-nums">{money(row.paid)}</span>,
    },
    {
      key: "balance",
      header: t("staffPayOverview.analytics.balance"),
      align: "end",
      nowrap: true,
      cell: (row) => <span className="tabular-nums font-semibold">{money(row.balance)}</span>,
    },
  ];
  return (
    <Table<PayRangePerson>
      columns={columns}
      rows={rows}
      rowKey={(row) => row.userId}
      empty={
        <EmptyState
          title={t("staffPayOverview.analytics.peopleEmpty")}
          body={t("staffPayOverview.analytics.peopleEmptyBody")}
        />
      }
    />
  );
}
