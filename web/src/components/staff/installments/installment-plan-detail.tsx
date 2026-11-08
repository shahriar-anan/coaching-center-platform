"use client";

import Link from "next/link";
import { useState, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pill, type PillTone } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, type Column } from "@/components/ui/table";
import { useToast } from "@/components/ui/toast";
import { ReasonDialog } from "@/components/staff/access/reason-dialog";
import { adminErrorText } from "@/components/staff/access/admin-error";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { staffHref } from "@/lib/config";
import {
  downloadInstallmentReceipt,
  useInstallmentPlan,
  useRestoreInstallmentAccess,
  useRevokeInstallmentAccess,
  useVoidInstallmentPayment,
  type InstallmentPayment,
  type InstallmentPlanDetail,
  type InstallmentRowState,
} from "@/lib/api/exam/staff-installments";
import { StatusPill } from "./installments-list";
import { RecordPaymentDialog } from "./record-payment-dialog";
import { ScheduleChangeDialog } from "./schedule-change-dialog";
import { scheduleLines, toPaisa, type ScheduleLine } from "./installment-model";

const ROW_TONE: Record<InstallmentRowState, PillTone> = {
  PAID: "ok",
  PART_PAID: "accent",
  OVERDUE: "danger",
  DUE: "warn",
  UPCOMING: "muted",
};

/**
 * One direct-enrolment plan for the Master Admin: its figures, its schedule with each row's state, its payments with a receipt
 * download, and the actions that change it. After any change the list, summary, enrolled lists and bell are refreshed.
 */
export function InstallmentPlanScreen({ planId }: { planId: string }) {
  const detail = useInstallmentPlan(planId);
  if (detail.isError) {
    return <ErrorState onRetry={() => void detail.refetch()} error={detail.error} />;
  }
  if (!detail.data) {
    return <Skeleton variant="card" count={2} />;
  }
  return <PlanView detail={detail.data} />;
}

function PlanView({ detail }: { detail: InstallmentPlanDetail }) {
  const { t } = useLocale();
  const { formatMoney, formatNumber, formatDate, pickTitle } = useFormat();
  const { toast } = useToast();
  const { plan, schedule, payments } = detail;
  const lines = scheduleLines(schedule);
  const item = pickTitle({ titleEn: plan.itemTitleEn, titleBn: plan.itemTitleBn }) || t("staffAdmin.installments.untitled");
  const name = plan.studentName || "—";
  const dueOpen = toPaisa(plan.due) > 0;
  const hasUnpaidRows = lines.some((line) => toPaisa(line.uncovered) > 0);
  // Only the latest payment that is not voided can be voided (plan D16, E37).
  const live = payments.filter((payment) => !payment.voidedAt);
  const latestLive = live.length > 0 ? live[live.length - 1] : null;

  const [recording, setRecording] = useState(false);
  const [rescheduling, setRescheduling] = useState(false);
  const [voiding, setVoiding] = useState<InstallmentPayment | null>(null);
  const [revoking, setRevoking] = useState(false);
  const [restoring, setRestoring] = useState(false);
  const [downloading, setDownloading] = useState<string | null>(null);
  const voidPayment = useVoidInstallmentPayment();
  const revokeAccess = useRevokeInstallmentAccess();
  const restoreAccess = useRestoreInstallmentAccess(plan.planId);

  const scheduleColumns: Column<ScheduleLine>[] = [
    {
      key: "no",
      header: t("staffAdmin.installments.detail.schedule.columns.no"),
      cell: (row) => <span className="tabular-nums">{formatNumber(row.seq)}</span>,
    },
    { key: "date", header: t("staffAdmin.installments.detail.schedule.columns.date"), nowrap: true, cell: (row) => formatDate(row.dueOn) },
    {
      key: "amount",
      header: t("staffAdmin.installments.detail.schedule.columns.amount"),
      align: "end",
      nowrap: true,
      cell: (row) => formatMoney(row.amount) ?? "—",
    },
    {
      key: "paid",
      header: t("staffAdmin.installments.detail.schedule.columns.paid"),
      align: "end",
      nowrap: true,
      cell: (row) => formatMoney(row.paid) ?? "—",
    },
    {
      key: "uncovered",
      header: t("staffAdmin.installments.detail.schedule.columns.uncovered"),
      align: "end",
      nowrap: true,
      cell: (row) => formatMoney(row.uncovered) ?? "—",
    },
    {
      key: "state",
      header: t("staffAdmin.installments.detail.schedule.columns.state"),
      cell: (row) => (
        <span className="flex flex-wrap gap-1.5">
          <Pill tone={ROW_TONE[row.state]}>{t(`staffAdmin.installments.detail.schedule.state.${row.state}`)}</Pill>
          {row.isNext ? <Pill tone="accent">{t("staffAdmin.installments.detail.schedule.next")}</Pill> : null}
        </span>
      ),
    },
  ];

  async function download(payment: InstallmentPayment) {
    setDownloading(payment.id);
    try {
      await downloadInstallmentReceipt(payment.id, payment.receiptNo);
    } catch {
      toast({ title: t("staffAdmin.installments.detail.payments.downloadFailed"), tone: "danger" });
    } finally {
      setDownloading(null);
    }
  }

  const paymentColumns: Column<InstallmentPayment>[] = [
    { key: "receipt", header: t("staffAdmin.installments.detail.payments.columns.receipt"), cell: (row) => <span className="font-latin">{row.receiptNo}</span> },
    { key: "date", header: t("staffAdmin.installments.detail.payments.columns.date"), nowrap: true, cell: (row) => formatDate(row.paidOn) },
    {
      key: "method",
      header: t("staffAdmin.installments.detail.payments.columns.method"),
      cell: (row) => t(`staffAdmin.enrol.payment.methods.${row.method}`),
    },
    {
      key: "reference",
      header: t("staffAdmin.installments.detail.payments.columns.reference"),
      cell: (row) => <span className="font-latin break-all tabular-nums text-muted">{row.reference}</span>,
    },
    { key: "amount", header: t("staffAdmin.installments.detail.payments.columns.amount"), align: "end", nowrap: true, cell: (row) => formatMoney(row.amount) ?? "—" },
    {
      key: "balance",
      header: t("staffAdmin.installments.detail.payments.columns.balance"),
      align: "end",
      nowrap: true,
      cell: (row) => formatMoney(row.dueAfter) ?? "—",
    },
    {
      key: "status",
      header: t("staffAdmin.installments.detail.payments.columns.status"),
      cell: (row) =>
        row.voidedAt ? (
          <Pill tone="danger">{t("staffAdmin.installments.detail.payments.voided")}</Pill>
        ) : (
          <Pill tone="ok">{t("staffAdmin.installments.detail.payments.live")}</Pill>
        ),
    },
    {
      key: "actions",
      header: "",
      align: "end",
      cell: (row) => (
        <div className="flex flex-wrap justify-end gap-2">
          <Button variant="secondary" size="sm" loading={downloading === row.id} onClick={() => void download(row)}>
            {t("staffAdmin.installments.detail.payments.download")}
          </Button>
          {latestLive && latestLive.id === row.id ? (
            <Button variant="danger" size="sm" onClick={() => setVoiding(row)}>
              {t("staffAdmin.installments.detail.payments.void")}
            </Button>
          ) : null}
        </div>
      ),
    },
  ];

  return (
    <div className="grid gap-5">
      <PageHeader
        title={item}
        breadcrumbs={[
          { label: t("common.staffNav.dashboard"), href: "/staff" },
          { label: t("staffAdmin.installments.title"), href: "/staff/installments" },
          { label: item },
        ]}
      />

      <Card>
        <div className="grid gap-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <Link href={staffHref(`/staff/students/${plan.studentId}`)} className="text-section font-semibold text-text underline-offset-4 hover:underline">
              {name}
            </Link>
            <StatusPill status={plan.status} />
          </div>
          <dl className="grid grid-cols-2 gap-4 md:grid-cols-4">
            <Fact label={t("staffAdmin.installments.detail.fields.total")}>{formatMoney(plan.agreedTotal) ?? "—"}</Fact>
            <Fact label={t("staffAdmin.installments.detail.fields.paid")}>{formatMoney(plan.paid) ?? "—"}</Fact>
            <Fact label={t("staffAdmin.installments.detail.fields.due")}>{formatMoney(plan.due) ?? "—"}</Fact>
            <Fact label={t("staffAdmin.installments.detail.fields.next")}>
              {plan.nextDueOn
                ? t("staffAdmin.installments.nextDue", { amount: formatMoney(plan.nextDueAmount ?? 0) ?? "", date: formatDate(plan.nextDueOn) })
                : t("staffAdmin.installments.nothingDue")}
            </Fact>
          </dl>
          <dl className="grid gap-4 sm:grid-cols-2">
            <Fact label={t("staffAdmin.installments.detail.fields.item")}>
              <span className="grid gap-0.5">
                <span>{item}</span>
                <span className="text-meta text-muted">{t(`staffAdmin.installments.kind.${plan.scope}`)}</span>
              </span>
            </Fact>
            <Fact label={t("staffAdmin.installments.detail.fields.access")}>
              {plan.accessRevoked ? (
                <Pill tone="muted">{t("staffAdmin.installments.detail.access.revoked")}</Pill>
              ) : (
                <Pill tone="ok">{t("staffAdmin.installments.detail.access.active")}</Pill>
              )}
            </Fact>
          </dl>

          <div className="flex flex-wrap items-center gap-2">
            <Button onClick={() => setRecording(true)} disabled={!dueOpen}>
              {t("staffAdmin.installments.detail.actions.record")}
            </Button>
            {dueOpen && hasUnpaidRows ? (
              <Button variant="secondary" onClick={() => setRescheduling(true)}>
                {t("staffAdmin.installments.detail.actions.schedule")}
              </Button>
            ) : null}
            {plan.accessRevoked ? (
              <Button variant="secondary" onClick={() => setRestoring(true)}>
                {t("staffAdmin.installments.detail.actions.restore")}
              </Button>
            ) : (
              <Button variant="danger" onClick={() => setRevoking(true)}>
                {t("staffAdmin.installments.detail.actions.revoke")}
              </Button>
            )}
            {dueOpen ? null : <span className="text-meta text-muted">{t("staffAdmin.installments.detail.actions.nothingDue")}</span>}
          </div>
        </div>
      </Card>

      <section aria-labelledby="installment-schedule" className="grid gap-3">
        <div className="grid gap-1">
          <h2 id="installment-schedule" className="text-section font-semibold text-text">
            {t("staffAdmin.installments.detail.schedule.title")}
          </h2>
          <p className="text-meta text-muted">{t("staffAdmin.installments.detail.schedule.body")}</p>
        </div>
        <Table columns={scheduleColumns} rows={lines} rowKey={(row) => String(row.seq)} />
      </section>

      <section aria-labelledby="installment-payments" className="grid gap-3">
        <h2 id="installment-payments" className="text-section font-semibold text-text">
          {t("staffAdmin.installments.detail.payments.title")}
        </h2>
        {payments.length === 0 ? (
          <EmptyState title={t("staffAdmin.installments.detail.payments.empty")} />
        ) : (
          <Table columns={paymentColumns} rows={payments} rowKey={(row) => row.id} />
        )}
      </section>

      {recording ? <RecordPaymentDialog plan={plan} payments={payments} onClose={() => setRecording(false)} /> : null}
      {rescheduling ? <ScheduleChangeDialog plan={plan} schedule={schedule} onClose={() => setRescheduling(false)} /> : null}

      <ReasonDialog
        open={voiding !== null}
        onOpenChange={(open) => {
          if (!open) {
            setVoiding(null);
          }
        }}
        title={t("staffAdmin.installments.detail.void.title")}
        body={t("staffAdmin.installments.detail.void.body")}
        label={t("staffAdmin.installments.detail.void.reason")}
        requiredMessage={t("staffAdmin.installments.detail.void.reasonRequired")}
        confirmLabel={t("staffAdmin.installments.detail.void.confirm")}
        tone="danger"
        onConfirm={async (reason) => {
          const payment = voiding;
          if (!payment) {
            return;
          }
          try {
            await voidPayment.mutateAsync({ paymentId: payment.id, reason });
            toast({ title: t("staffAdmin.installments.detail.void.done"), tone: "ok" });
          } catch (caught) {
            throw new Error(adminErrorText(caught, t));
          }
        }}
      />

      <ReasonDialog
        open={revoking}
        onOpenChange={setRevoking}
        title={t("staffAdmin.installments.detail.revoke.title")}
        body={t("staffAdmin.installments.detail.revoke.body", { name, item })}
        label={t("staffAdmin.installments.detail.revoke.reason")}
        requiredMessage={t("staffAdmin.installments.detail.revoke.reasonRequired")}
        confirmLabel={t("staffAdmin.installments.detail.revoke.confirm")}
        tone="danger"
        onConfirm={async (reason) => {
          try {
            await revokeAccess.mutateAsync({ grantId: plan.grantId, reason });
            toast({ title: t("staffAdmin.installments.detail.revoke.done"), tone: "ok" });
          } catch (caught) {
            throw new Error(adminErrorText(caught, t));
          }
        }}
      />

      <ConfirmDialog
        open={restoring}
        onOpenChange={setRestoring}
        title={t("staffAdmin.installments.detail.restore.title")}
        body={t("staffAdmin.installments.detail.restore.body", { name, item })}
        confirmLabel={t("staffAdmin.installments.detail.restore.confirm")}
        onConfirm={async () => {
          try {
            await restoreAccess.mutateAsync();
            toast({ title: t("staffAdmin.installments.detail.restore.done"), tone: "ok" });
          } catch (caught) {
            throw new Error(adminErrorText(caught, t));
          }
        }}
      />
    </div>
  );
}

/** One labelled figure in the summary card. */
function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid content-start gap-1">
      <dt className="text-meta text-muted">{label}</dt>
      <dd className="text-body tabular-nums text-text">{children}</dd>
    </div>
  );
}
