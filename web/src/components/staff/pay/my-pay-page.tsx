"use client";

import { useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { Stat, StatRow } from "@/components/ui/stat";
import { Table, type Column } from "@/components/ui/table";
import { useMyPayLedger, useMyPaySummary, type PayLedgerRow } from "@/lib/api/exam/staff-pay";
import { useRole } from "@/lib/auth/guards";
import { formatDate, formatMoney } from "@/lib/format";
import { kindKey, methodKey } from "./pay-format";
import { MySalaryPanel } from "./salary-panel";
import { MyMarkingPay } from "./marking-section";
import { MyExamsSection } from "./my-exams-section";
import { isMissing } from "@/lib/api/missing";

/**
 * "My pay" for a System Admin or an instructor: the totals, the salary and next pay date for a System Admin, and every payment
 * and entry with its date, method, account text and reference. Read only; the person confirms nothing (plan P13).
 */
export function MyPayPage() {
  const { t, locale } = useLocale();
  const { isSystemAdmin, isInstructor } = useRole();
  const summary = useMyPaySummary();
  const [page, setPage] = useState(0);
  const ledger = useMyPayLedger(page);

  const money = (value: number | null | undefined) => (value === null || value === undefined ? "" : formatMoney(value, locale) ?? "");
  const signed = (value: number) => (value < 0 ? `−${money(-value)}` : money(value));

  const columns: Column<PayLedgerRow>[] = [
    {
      key: "date",
      header: t("staffPay.ledger.date"),
      nowrap: true,
      cell: (row) => formatDate(row.paidOn ?? row.dueOn, locale),
    },
    {
      key: "kind",
      header: t("staffPay.ledger.kind"),
      cell: (row) => (
        <span className="grid gap-0.5">
          <span>{t(kindKey(row.kind))}</span>
          {row.voided ? <Pill tone="muted">{t("staffPay.ledger.voided")}</Pill> : null}
        </span>
      ),
    },
    {
      key: "amount",
      header: t("staffPay.ledger.amount"),
      align: "end",
      nowrap: true,
      cell: (row) => <span className={row.voided ? "text-muted line-through" : "tabular-nums"}>{signed(row.amount)}</span>,
    },
    {
      key: "method",
      header: t("staffPay.ledger.method"),
      mobileHidden: true,
      cell: (row) => (
        <span className="grid gap-0.5">
          {row.method ? <span>{t(methodKey(row.method) ?? "")}</span> : null}
          {row.accountSnapshot ? <span className="text-meta text-muted">{row.accountSnapshot}</span> : null}
        </span>
      ),
    },
    {
      key: "reference",
      header: t("staffPay.ledger.reference"),
      mobileHidden: true,
      cell: (row) =>
        row.voided ? `${t("staffPay.ledger.voidedBecause")}: ${row.voidReason ?? ""}` : [row.reference, row.note].filter(Boolean).join(" · "),
    },
  ];

  return (
    <div className="grid gap-6">
      <PageHeader
        title={t("staffPay.my.title")}
        actions={
          isSystemAdmin || isInstructor ? (
            <Button variant="secondary" href="/staff/account">
              {t("staffPay.my.payDetails")}
            </Button>
          ) : undefined
        }
      />

      {summary.isError ? (
        <ErrorState onRetry={() => void summary.refetch()} error={summary.error} />
      ) : !summary.data ? (
        <Skeleton variant="rows" count={2} />
      ) : (
        <>
          <Card>
            <StatRow>
              <Stat value={money(summary.data.summary.charged)} label={t("staffPay.summary.charged")} />
              <Stat value={money(summary.data.summary.paid)} label={t("staffPay.summary.paid")} />
              <Stat value={signed(summary.data.summary.balance)} label={t("staffPay.summary.balance")} />
              <Stat value={money(summary.data.summary.overdue)} label={t("staffPay.summary.overdue")} />
            </StatRow>
            {summary.data.summary.balance < 0 ? (
              <p className="mt-3 text-meta text-muted">
                {t("staffPay.summary.advance", { amount: money(-summary.data.summary.balance) })}
              </p>
            ) : null}
          </Card>
          {/* An unset salary is left out of the API's JSON (undefined), so isMissing, never `=== null`. */}
          {summary.data.role === "SYSTEM_ADMIN" && !isMissing(summary.data.monthlySalary) ? <MySalaryPanel /> : null}
          {isMissing(summary.data.monthlySalary) && summary.data.role === "SYSTEM_ADMIN" ? (
            <p className="text-meta text-muted">{t("staffPay.my.notSetYet")}</p>
          ) : null}
        </>
      )}

      {summary.data?.role === "INSTRUCTOR" ? <MyMarkingPay /> : null}
      {summary.data?.role === "INSTRUCTOR" ? <MyExamsSection /> : null}

      <section className="grid gap-3" aria-label={t("staffPay.my.ledgerTitle")}>
        <h2 className="text-section text-text">{t("staffPay.my.ledgerTitle")}</h2>
        {ledger.isError ? (
          <ErrorState onRetry={() => void ledger.refetch()} error={ledger.error} />
        ) : !ledger.data ? (
          <Skeleton variant="rows" count={4} />
        ) : ledger.data.content.length === 0 ? (
          <EmptyState title={t("staffPay.my.emptyTitle")} body={t("staffPay.my.emptyBody")} />
        ) : (
          <>
            <Table<PayLedgerRow> columns={columns} rows={ledger.data.content} rowKey={(row) => row.id} />
            <Pagination page={ledger.data.page} totalPages={ledger.data.totalPages} onPage={setPage} />
          </>
        )}
      </section>
    </div>
  );
}
