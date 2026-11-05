"use client";

import Link from "next/link";
import { useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { cn } from "@/components/ui/cn";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, type Column } from "@/components/ui/table";
import { Tabs } from "@/components/ui/tabs";
import { PayAnalytics } from "@/components/staff/pay/pay-analytics";
import { PayOverviewBand } from "@/components/staff/pay/pay-overview-band";
import { usePayPeople, type PayPersonRow, type PayRole } from "@/lib/api/exam/staff-pay";
import { formatDate, formatMoney } from "@/lib/format";

type RoleFilter = PayRole | "ALL";

/**
 * The Master Admin's staff pay page: everyone's balance, what is overdue and when they were last paid. Each name opens the
 * person's page. A "Pay not set up" badge marks a missing salary or payout account; nothing is blocked by it.
 */
export function PayPeoplePage() {
  const { t, locale } = useLocale();
  const [role, setRole] = useState<RoleFilter>("ALL");
  const [owing, setOwing] = useState(false);
  const [view, setView] = useState<"people" | "analytics">("people");
  const people = usePayPeople(role, owing);

  const money = (value: number) => formatMoney(value, locale) ?? "";

  const columns: Column<PayPersonRow>[] = [
    {
      key: "name",
      header: t("staffPay.people.name"),
      cell: (row) => (
        <Link href={`/staff/pay/${row.id}`} className="font-medium text-accent underline-offset-4 hover:underline">
          {row.fullName || t("staffPay.people.unnamed")}
        </Link>
      ),
    },
    {
      key: "role",
      header: t("staffPay.people.role"),
      cell: (row) => t(`staffPay.role.${row.role}`),
      mobileHidden: true,
    },
    {
      key: "balance",
      header: t("staffPay.people.balance"),
      align: "end",
      nowrap: true,
      cell: (row) => <span className="tabular-nums">{money(row.balance)}</span>,
    },
    {
      key: "overdue",
      header: t("staffPay.people.overdue"),
      align: "end",
      nowrap: true,
      cell: (row) => (
        <span className={cn("tabular-nums", row.overdue > 0 && "font-semibold text-danger")}>{money(row.overdue)}</span>
      ),
    },
    {
      key: "last",
      header: t("staffPay.people.lastPaid"),
      nowrap: true,
      mobileHidden: true,
      cell: (row) => (row.lastPaymentOn ? formatDate(row.lastPaymentOn, locale) : t("staffPay.people.neverPaid")),
    },
    {
      key: "setup",
      header: "",
      cell: (row) =>
        row.payNotSetUp ? <Pill tone="warn">{t("staffPay.people.payNotSetUp")}</Pill> : null,
    },
  ];

  const roles: { value: RoleFilter; label: string }[] = [
    { value: "ALL", label: t("staffPay.people.filterAll") },
    { value: "INSTRUCTOR", label: t("staffPay.role.INSTRUCTOR") },
    { value: "SYSTEM_ADMIN", label: t("staffPay.role.SYSTEM_ADMIN") },
  ];

  return (
    <div className="grid gap-5">
      <PageHeader
        title={t("staffPay.people.title")}
        actions={
          <Link
            href="/staff/pay/settings"
            className="inline-flex min-h-10 items-center rounded-control border border-border px-4 text-label text-text hover:bg-bg"
          >
            {t("staffPay.people.settingsLink")}
          </Link>
        }
      />

      <Tabs
        tabs={[
          { value: "people", label: t("staffPayOverview.tabs.people") },
          { value: "analytics", label: t("staffPayOverview.tabs.analytics") },
        ]}
        value={view}
        onChange={(next) => setView(next === "analytics" ? "analytics" : "people")}
        ariaLabel={t("staffPayOverview.tabs.label")}
      />

      {view === "people" ? (
        <>
          <PayOverviewBand />

      <div className="flex flex-wrap items-center gap-2" role="group" aria-label={t("staffPay.people.filterLabel")}>
        {roles.map((option) => (
          <button
            key={option.value}
            type="button"
            aria-pressed={role === option.value}
            onClick={() => setRole(option.value)}
            className={cn(
              "min-h-10 rounded-control border px-3 text-label",
              role === option.value ? "border-accent bg-accent/10 text-text" : "border-border text-muted hover:bg-bg",
            )}
          >
            {option.label}
          </button>
        ))}
        <button
          type="button"
          aria-pressed={owing}
          onClick={() => setOwing((value) => !value)}
          className={cn(
            "min-h-10 rounded-control border px-3 text-label",
            owing ? "border-accent bg-accent/10 text-text" : "border-border text-muted hover:bg-bg",
          )}
        >
          {t("staffPay.people.owingOnly")}
        </button>
      </div>

      {people.isError ? (
        <ErrorState onRetry={() => void people.refetch()} error={people.error} />
      ) : !people.data ? (
        <Skeleton variant="rows" count={4} />
      ) : (
        <Table<PayPersonRow>
          columns={columns}
          rows={people.data}
          rowKey={(row) => row.id}
          empty={<EmptyState title={t("staffPay.people.empty")} body={t("staffPay.people.emptyBody")} />}
        />
      )}
        </>
      ) : (
        <PayAnalytics />
      )}
    </div>
  );
}
