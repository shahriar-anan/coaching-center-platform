"use client";

import type { UseQueryResult } from "@tanstack/react-query";
import type { Locale } from "@/i18n/locale-context";
import { useLocale } from "@/i18n/locale-context";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Pill, type PillTone } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { Stat, StatRow } from "@/components/ui/stat";
import { Table, type Column } from "@/components/ui/table";
import { useMySalary, usePaySalary, type PaySalaryMonth, type PaySalaryView } from "@/lib/api/exam/staff-pay";
import { formatDate, formatMoney } from "@/lib/format";
import { isMissing } from "@/lib/api/missing";

// The System Admin salary (staff pay plan, phase 5): the salary card with the next pay date and what is due on it, and the list
// of salary months with their status. The same panel serves the person's own My pay and the Master Admin's person page.

const STATUS_TONE: Record<PaySalaryMonth["status"], PillTone> = {
  PAID: "ok",
  DUE: "muted",
  OVERDUE: "danger",
};

/** The month a salary is for, as "October 2026" (or the Bangla month and year). */
export function salaryMonthLabel(period: string, locale: Locale): string {
  // Any day of the month gives the same month name; the day and its Bangla digits are dropped.
  return formatDate(`${period}-15`, locale).replace(/^\S+\s/, "");
}

/** The signed-in System Admin's salary, for My pay. */
export function MySalaryPanel() {
  const query = useMySalary();
  return <SalaryPanel query={query} />;
}

/** One System Admin's salary, for the Master Admin's person page. */
export function PersonSalaryPanel({ userId }: { userId: string }) {
  const query = usePaySalary(userId);
  return <SalaryPanel query={query} />;
}

function SalaryPanel({ query }: { query: UseQueryResult<PaySalaryView, Error> }) {
  const { t, locale } = useLocale();

  const money = (value: number | null | undefined) => (value === null || value === undefined ? "" : formatMoney(value, locale) ?? "");

  const columns: Column<PaySalaryMonth>[] = [
    {
      key: "month",
      header: t("staffPay.salary.month"),
      nowrap: true,
      cell: (row) => salaryMonthLabel(row.period, locale),
    },
    {
      key: "dueOn",
      header: t("staffPay.salary.dueDate"),
      nowrap: true,
      cell: (row) => formatDate(row.dueOn, locale),
    },
    {
      key: "amount",
      header: t("staffPay.salary.amount"),
      align: "end",
      nowrap: true,
      cell: (row) => <span className="tabular-nums">{money(row.amount)}</span>,
    },
    {
      key: "status",
      header: t("staffPay.salary.statusLabel"),
      cell: (row) => <Pill tone={STATUS_TONE[row.status]}>{t(`staffPay.salary.status.${row.status}`)}</Pill>,
    },
  ];

  if (query.isError) {
    return <ErrorState onRetry={() => void query.refetch()} error={query.error} />;
  }
  if (!query.data) {
    return <Skeleton variant="rows" count={3} />;
  }
  const view = query.data;

  let nextAmount: string;
  if (isMissing(view.nextPayDate)) {
    nextAmount = t("staffPay.salary.nextNone");
  } else if (view.nextPayAmount === 0) {
    nextAmount = t("staffPay.salary.nextPaid");
  } else {
    nextAmount = money(view.nextPayAmount);
  }

  return (
    <section className="grid gap-4" aria-label={t("staffPay.salary.title")}>
      <Card>
        <h2 className="text-section text-text">{t("staffPay.salary.title")}</h2>
        <div className="mt-4">
          <StatRow>
            <Stat value={money(view.monthlySalary)} label={t("staffPay.summary.salary")} />
            <Stat value={view.nextPayDate ? formatDate(view.nextPayDate, locale) : "—"} label={t("staffPay.salary.nextDate")} />
            <Stat value={nextAmount} label={t("staffPay.salary.nextAmount")} />
          </StatRow>
        </div>
      </Card>

      <div className="grid gap-3">
        <h3 className="text-label text-text">{t("staffPay.salary.months")}</h3>
        {view.months.length === 0 ? (
          <EmptyState title={t("staffPay.salary.empty")} body={t("staffPay.salary.emptyBody")} />
        ) : (
          <Table<PaySalaryMonth> columns={columns} rows={view.months} rowKey={(row) => row.period} />
        )}
      </div>
    </section>
  );
}
