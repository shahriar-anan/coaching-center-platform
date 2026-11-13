"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { Pill } from "@/components/ui/pill";
import { Table, type Column } from "@/components/ui/table";
import { Tabs } from "@/components/ui/tabs";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useRole } from "@/lib/auth/guards";
import { useFormat } from "@/lib/format/use-format";
import {
  useApprovePayment,
  useFeePayments,
  useOverdueAdmissions,
  type FeePayment,
  type OverdueRow,
  type PaymentStatus,
} from "@/lib/api/exam/offline-admin";
import { useListParams } from "@/lib/url/list-params";
import { PaymentDecisionButtons } from "./payment-decisions";
import { toNumber, PAYMENT_TONE } from "./offline-model";
import { paymentStatusKey, methodLabelKey } from "./labels";
import { offlineErrorText } from "./offline-error";

const TABS = ["pending", "recorded", "overdue"] as const;
type FeeTab = (typeof TABS)[number];

/** The overdue list is paged, as the server pages it. */
const OVERDUE_SIZE = 20;

/**
 * The fee approvals (plan 6.3). The Master Admin sees the payments waiting for approval, approves one or several after one confirmation,
 * and rejects with a reason. Staff see the payments they recorded and their state. The Overdue tab lists the admissions with a due past
 * its date, most days late first.
 */
export function FeeApprovals() {
  const { t } = useLocale();
  const role = useRole();
  const [params, setParams] = useListParams({ tab: "", page: 0 });
  const isMaster = role.canApproveOfflineFee;
  const tab: FeeTab = (TABS as readonly string[]).includes(params.tab) ? (params.tab as FeeTab) : isMaster ? "pending" : "recorded";
  const tabs = [
    ...(isMaster ? [{ value: "pending", label: t("staffOffline.feeApprovals.tabs.pending") }] : []),
    { value: "recorded", label: isMaster ? t("staffOffline.feeApprovals.tabs.all") : t("staffOffline.feeApprovals.tabs.mine") },
    { value: "overdue", label: t("staffOffline.feeApprovals.tabs.overdue") },
  ];

  return (
    <div className="grid gap-5">
      <PageHeader
        title={t("staffOffline.feeApprovals.title")}
        breadcrumbs={[{ label: t("common.staffNav.dashboard"), href: "/staff" }, { label: t("staffOffline.feeApprovals.title") }]}
      />
      <Tabs tabs={tabs} value={tab} onChange={(value) => setParams({ tab: value })} ariaLabel={t("staffOffline.feeApprovals.tabsLabel")} wrap />
      {tab === "pending" ? <PaymentsTable status="PENDING" canDecide={isMaster} /> : null}
      {tab === "recorded" ? <PaymentsTable status="" canDecide={false} canApproveAll={false} /> : null}
      {tab === "overdue" ? <OverdueTable page={params.page} onPage={(page) => setParams({ page })} /> : null}
    </div>
  );
}

function PaymentsTable({ status, canDecide, canApproveAll = true }: { status: PaymentStatus | ""; canDecide: boolean; canApproveAll?: boolean }) {
  const { t } = useLocale();
  const { formatDate, formatDateTime, formatMoney } = useFormat();
  const { toast } = useToast();
  const payments = useFeePayments(status);
  const approve = useApprovePayment();
  const [selected, setSelected] = useState<string[]>([]);
  const [confirming, setConfirming] = useState(false);
  const rows = useMemo(() => payments.data ?? [], [payments.data]);
  const pendingIds = useMemo(() => new Set(rows.filter((row) => row.status === "PENDING").map((row) => row.id)), [rows]);
  const chosen = rows.filter((row) => selected.includes(row.id) && pendingIds.has(row.id));

  function toggle(id: string) {
    setSelected((current) => (current.includes(id) ? current.filter((item) => item !== id) : [...current, id]));
  }

  const columns: Column<FeePayment>[] = [
    ...(canDecide && status === "PENDING"
      ? [
          {
            key: "select",
            header: "",
            width: "w-10",
            cell: (row: FeePayment) => (
              <input
                type="checkbox"
                aria-label={t("staffOffline.feeApprovals.select", { name: row.name })}
                className="size-5 accent-accent"
                checked={selected.includes(row.id)}
                onChange={() => toggle(row.id)}
              />
            ),
          } satisfies Column<FeePayment>,
        ]
      : []),
    {
      key: "student",
      header: t("staffOffline.feeApprovals.columns.student"),
      mobileLabel: t("staffOffline.feeApprovals.columns.student"),
      cell: (row) => (
        <div className="grid gap-0.5">
          <Link href={`/staff/admissions/${row.admissionId}?tab=fees`} className="font-medium text-text underline-offset-4 hover:underline">
            {row.name}
          </Link>
          {row.batchNameEn ? <span className="text-meta text-muted">{row.batchNameEn}</span> : null}
        </div>
      ),
    },
    {
      key: "amount",
      header: t("staffOffline.feeApprovals.columns.amount"),
      mobileLabel: t("staffOffline.feeApprovals.columns.amount"),
      align: "end",
      cell: (row) => <span className="font-latin">{formatMoney(toNumber(row.amount))}</span>,
    },
    {
      key: "method",
      header: t("staffOffline.feeApprovals.columns.method"),
      mobileLabel: t("staffOffline.feeApprovals.columns.method"),
      cell: (row) => (
        <div className="grid gap-0.5">
          <span>{t(methodLabelKey(row.method))}</span>
          {row.reference ? <span className="break-all text-meta text-muted">{row.reference}</span> : null}
        </div>
      ),
    },
    {
      key: "paidOn",
      header: t("staffOffline.feeApprovals.columns.paidOn"),
      mobileLabel: t("staffOffline.feeApprovals.columns.paidOn"),
      cell: (row) => formatDate(row.paidOn),
    },
    {
      key: "recorded",
      header: t("staffOffline.feeApprovals.columns.recorded"),
      mobileLabel: t("staffOffline.feeApprovals.columns.recorded"),
      cell: (row) => (
        <div className="grid gap-0.5">
          <span>{row.recordedByName ?? "—"}</span>
          <span className="text-meta text-muted">{formatDateTime(row.recordedAt)}</span>
        </div>
      ),
    },
    {
      key: "status",
      header: t("staffOffline.feeApprovals.columns.status"),
      mobileLabel: t("staffOffline.feeApprovals.columns.status"),
      cell: (row) => <Pill tone={PAYMENT_TONE[row.status]}>{t(paymentStatusKey(row.status))}</Pill>,
    },
    {
      key: "actions",
      header: "",
      mobileLabel: t("staffOffline.feeApprovals.columns.actions"),
      align: "end",
      cell: (row) =>
        canDecide && row.status === "PENDING" ? (
          <PaymentDecisionButtons payment={row} />
        ) : row.status === "REJECTED" && row.decisionReason ? (
          <span className="text-meta text-danger">{row.decisionReason}</span>
        ) : null,
    },
  ];

  if (payments.isError) {
    return <ErrorState onRetry={() => payments.refetch()} error={payments.error} />;
  }

  return (
    <div className="grid gap-4">
      {canDecide && canApproveAll && status === "PENDING" && chosen.length > 0 ? (
        <div className="flex flex-wrap items-center gap-3 rounded-control border border-border-control bg-card px-3 py-2">
          <span className="text-body text-text">{t("staffOffline.feeApprovals.selected", { count: chosen.length })}</span>
          <Button size="sm" onClick={() => setConfirming(true)}>
            {t("staffOffline.feeApprovals.approveSelected", { count: chosen.length })}
          </Button>
        </div>
      ) : null}
      <Table
        columns={columns}
        rows={rows}
        rowKey={(row) => row.id}
        loading={payments.isPending}
        empty={
          <EmptyState
            title={t(status === "PENDING" ? "staffOffline.feeApprovals.emptyPending" : "staffOffline.feeApprovals.emptyRecorded")}
            body={t(status === "PENDING" ? "staffOffline.feeApprovals.emptyPendingBody" : "staffOffline.feeApprovals.emptyRecordedBody")}
          />
        }
      />
      <ConfirmDialog
        open={confirming}
        onOpenChange={setConfirming}
        title={t("staffOffline.feeApprovals.confirmTitle", { count: chosen.length })}
        body={t("staffOffline.feeApprovals.confirmBody")}
        confirmLabel={t("staffOffline.feeApprovals.confirmButton", { count: chosen.length })}
        onConfirm={async () => {
          // Each payment is approved in turn. A failure stops the run; the ones already approved are left out of the selection.
          const approvedIds: string[] = [];
          for (const payment of chosen) {
            try {
              await approve.mutateAsync(payment.id);
              approvedIds.push(payment.id);
            } catch (error) {
              setSelected((current) => current.filter((id) => !approvedIds.includes(id)));
              throw new Error(
                t("staffOffline.feeApprovals.partial", { done: approvedIds.length, total: chosen.length, reason: offlineErrorText(error, t) }),
              );
            }
          }
          setSelected([]);
          toast({ title: t("staffOffline.feeApprovals.approvedAll", { count: approvedIds.length }), tone: "ok" });
        }}
      >
        <ul className="grid gap-1 text-body text-text">
          {chosen.map((payment) => (
            <li key={payment.id}>
              {payment.name} · {formatMoney(toNumber(payment.amount))} · {t(methodLabelKey(payment.method))}
            </li>
          ))}
        </ul>
      </ConfirmDialog>
    </div>
  );
}

/**
/**
 * The admissions with a due past its date and money still owed, one row each, from the server's overdue list (plan 6.3). The server
 * sends the oldest overdue due of each admission and the total it owes, so no schedule is read here.
 */
function OverdueTable({ page, onPage }: { page: number; onPage: (page: number) => void }) {
  const { t } = useLocale();
  const { formatDate, formatMoney } = useFormat();
  const overdue = useOverdueAdmissions(page, OVERDUE_SIZE);

  const columns: Column<OverdueRow>[] = [
    {
      key: "student",
      header: t("staffOffline.feeApprovals.overdue.student"),
      mobileLabel: t("staffOffline.feeApprovals.overdue.student"),
      cell: (row) => (
        <div className="grid gap-0.5">
          <Link href={`/staff/admissions/${row.admissionId}?tab=fees`} className="font-medium text-text underline-offset-4 hover:underline">
            {row.name}
          </Link>
          {row.batchNameEn ? <span className="text-meta text-muted">{row.batchNameEn}</span> : null}
        </div>
      ),
    },
    {
      key: "amount",
      header: t("staffOffline.feeApprovals.overdue.amount"),
      mobileLabel: t("staffOffline.feeApprovals.overdue.amount"),
      align: "end",
      cell: (row) => <span className="font-latin text-danger">{formatMoney(toNumber(row.amount))}</span>,
    },
    {
      key: "dueOn",
      header: t("staffOffline.feeApprovals.overdue.dueOn"),
      mobileLabel: t("staffOffline.feeApprovals.overdue.dueOn"),
      cell: (row) => formatDate(row.dueOn),
    },
    {
      key: "days",
      header: t("staffOffline.feeApprovals.overdue.days"),
      mobileLabel: t("staffOffline.feeApprovals.overdue.days"),
      cell: (row) => <span className="font-latin">{row.daysLate}</span>,
    },
    {
      key: "owedTotal",
      header: t("staffOffline.feeApprovals.overdue.owedTotal"),
      mobileLabel: t("staffOffline.feeApprovals.overdue.owedTotal"),
      align: "end",
      cell: (row) => <span className="font-latin">{formatMoney(toNumber(row.owedTotal))}</span>,
    },
    {
      key: "action",
      header: "",
      align: "end",
      cell: (row) => (
        <Button size="sm" href={`/staff/admissions/${row.admissionId}?tab=fees&pay=1`}>
          {t("staffOffline.actions.recordPayment")}
        </Button>
      ),
    },
  ];

  if (overdue.isError) {
    return <ErrorState onRetry={() => overdue.refetch()} error={overdue.error} />;
  }
  return (
    <div className="grid gap-4">
      <Table
        columns={columns}
        rows={overdue.data?.content ?? []}
        rowKey={(row) => row.admissionId}
        loading={overdue.isPending}
        empty={<EmptyState title={t("staffOffline.feeApprovals.overdue.empty")} body={t("staffOffline.feeApprovals.overdue.emptyBody")} />}
      />
      {overdue.data && overdue.data.totalPages > 1 ? (
        <Pagination page={overdue.data.page} totalPages={overdue.data.totalPages} onPage={onPage} />
      ) : null}
    </div>
  );
}
