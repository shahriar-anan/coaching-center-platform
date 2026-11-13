"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Pill, type PillTone } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, type Column } from "@/components/ui/table";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { openAuthorizedFile, useAdmissionFees, type DueView, type FeePayment } from "@/lib/api/exam/offline-admin";
import type { AdmissionDetail } from "@/lib/api/exam/offline-admin";
import { MoneyPills } from "./money-pills";
import { moneyPills, nextOwedDue, daysLate, dhakaToday, toNumber, PAYMENT_TONE } from "./offline-model";
import { dueStateKey, methodLabelKey, paymentStatusKey } from "./labels";
import { RecordPaymentDialog } from "./record-payment-dialog";
import { PaymentDecisionButtons } from "./payment-decisions";

const DUE_TONE: Record<DueView["state"], PillTone> = {
  PAID: "ok",
  PART_PAID: "warn",
  UNPAID: "warn",
};

/**
 * The fees of an onboarded admission (plan 6.3): what is owed and overdue, the dues, and the payments with their status. Record payment is
 * prefilled with the next due and today. A payment by staff shows as waiting until the Master Admin approves it.
 */
export function FeesTab({
  admission,
  canRecord,
  canApprove,
  openOnLoad,
}: {
  admission: AdmissionDetail;
  canRecord: boolean;
  canApprove: boolean;
  /** Opens Record payment at once, when the page was reached from the queue's Record payment action. */
  openOnLoad: boolean;
}) {
  const { t } = useLocale();
  const { formatDate, formatMoney } = useFormat();
  const { toast } = useToast();
  const fees = useAdmissionFees(admission.id);
  const [paying, setPaying] = useState(openOnLoad);
  const today = dhakaToday();

  if (fees.isPending) {
    return <Skeleton variant="card" />;
  }
  if (fees.isError) {
    return <ErrorState onRetry={() => fees.refetch()} error={fees.error} />;
  }
  const schedule = fees.data;
  const next = nextOwedDue(schedule.dues);
  const overdue = toNumber(schedule.overdue);

  const dueColumns: Column<DueView>[] = [
    { key: "seq", header: "#", cell: (due) => <span className="font-latin">{due.seq}</span>, width: "w-12" },
    {
      key: "dueOn",
      header: t("staffOffline.fees.dueOn"),
      mobileLabel: t("staffOffline.fees.dueOn"),
      cell: (due) => formatDate(due.dueOn),
    },
    {
      key: "amount",
      header: t("staffOffline.fees.amount"),
      mobileLabel: t("staffOffline.fees.amount"),
      align: "end",
      cell: (due) => <span className="font-latin">{formatMoney(toNumber(due.amount))}</span>,
    },
    {
      key: "owed",
      header: t("staffOffline.fees.owed"),
      mobileLabel: t("staffOffline.fees.owed"),
      align: "end",
      cell: (due) => <span className="font-latin">{formatMoney(toNumber(due.owed))}</span>,
    },
    {
      key: "state",
      header: t("staffOffline.fees.state"),
      mobileLabel: t("staffOffline.fees.state"),
      cell: (due) => (
        <div className="grid gap-0.5">
          <span>
            <Pill tone={due.overdue ? "danger" : DUE_TONE[due.state]}>{t(dueStateKey(due.state))}</Pill>
          </span>
          {due.overdue ? (
            <span className="text-meta text-danger">
              {t("staffOffline.fees.daysLate", { days: daysLate(due.dueOn, today) })}
            </span>
          ) : null}
        </div>
      ),
    },
  ];

  return (
    <div className="grid gap-5">
      <div className="grid gap-3 rounded-card border border-border-control bg-card p-4 sm:flex sm:flex-wrap sm:items-center sm:justify-between">
        <div className="grid gap-2">
          <MoneyPills pills={moneyPills(schedule)} />
          <p className="text-body text-text">
            {t("staffOffline.fees.owedTotal", { amount: formatMoney(toNumber(schedule.owed)) ?? "" })}
          </p>
          {next ? (
            <p className="text-meta text-muted">
              {t("staffOffline.fees.nextDue", { amount: formatMoney(toNumber(next.owed)) ?? "", date: formatDate(next.dueOn) })}
            </p>
          ) : (
            <p className="text-meta text-muted">{t("staffOffline.fees.noneOwed")}</p>
          )}
        </div>
        {canRecord ? (
          <Button onClick={() => setPaying(true)}>{t("staffOffline.fees.record")}</Button>
        ) : null}
      </div>

      {overdue > 0 ? (
        <p role="status" className="rounded-control border border-danger/40 bg-danger/10 px-3 py-2 text-body text-danger">
          {t("staffOffline.fees.overdueLine", { amount: formatMoney(overdue) ?? "" })}
        </p>
      ) : null}

      <section aria-labelledby="fees-schedule" className="grid gap-2">
        <h3 id="fees-schedule" className="text-card-title text-text">
          {t("staffOffline.fees.schedule")}
        </h3>
        <Table
          columns={dueColumns}
          rows={schedule.dues}
          rowKey={(due) => due.id}
          empty={<EmptyState title={t("staffOffline.fees.noDues")} body={t("staffOffline.fees.noDuesBody")} />}
        />
      </section>

      <section aria-labelledby="fees-payments" className="grid gap-2">
        <h3 id="fees-payments" className="text-card-title text-text">
          {t("staffOffline.fees.payments")}
        </h3>
        {schedule.payments.length === 0 ? (
          <p className="text-meta text-muted">{t("staffOffline.fees.noPayments")}</p>
        ) : (
          <ul className="grid gap-2">
            {schedule.payments.map((payment) => (
              <PaymentRow
                key={payment.id}
                payment={payment}
                canApprove={canApprove}
                onReceipt={async () => {
                  try {
                    await openAuthorizedFile(`/api/v1/offline-fee-payments/${payment.id}/receipt.pdf`);
                  } catch {
                    toast({ title: t("staffOffline.errors.generic"), tone: "danger" });
                  }
                }}
              />
            ))}
          </ul>
        )}
      </section>

      {canRecord ? (
        <RecordPaymentDialog
          admissionId={admission.id}
          open={paying}
          onOpenChange={setPaying}
          nextDue={next ? { amount: next.owed, dueOn: next.dueOn } : null}
          canApprove={canApprove}
        />
      ) : null}
    </div>
  );
}

function PaymentRow({
  payment,
  canApprove,
  onReceipt,
}: {
  payment: FeePayment;
  canApprove: boolean;
  onReceipt: () => void;
}) {
  const { t } = useLocale();
  const { formatDate, formatMoney, formatDateTime } = useFormat();
  return (
    <li className="grid gap-2 rounded-control border border-border-control bg-card px-3 py-3">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-latin text-body font-semibold text-text">{formatMoney(toNumber(payment.amount))}</span>
        <Pill tone={PAYMENT_TONE[payment.status]}>{t(paymentStatusKey(payment.status))}</Pill>
        <span className="text-meta text-muted">{t(methodLabelKey(payment.method))}</span>
        <span className="text-meta text-muted">{formatDate(payment.paidOn)}</span>
      </div>
      <p className="text-meta text-muted">
        {t("staffOffline.fees.recordedBy", {
          name: payment.recordedByName ?? "—",
          time: formatDateTime(payment.recordedAt),
        })}
      </p>
      {payment.reference ? <p className="text-meta text-muted">{t("staffOffline.fees.reference", { reference: payment.reference })}</p> : null}
      {payment.status === "REJECTED" && payment.decisionReason ? (
        <p className="text-meta text-danger">{t("staffOffline.fees.rejectedReason", { reason: payment.decisionReason })}</p>
      ) : null}
      {payment.status === "APPROVED" && payment.receiptNo ? (
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-latin text-meta text-muted">{t("staffOffline.fees.receipt", { number: payment.receiptNo })}</span>
          <Button size="sm" variant="secondary" onClick={onReceipt}>
            {t("staffOffline.fees.openReceipt")}
          </Button>
        </div>
      ) : null}
      {payment.status === "PENDING" && canApprove ? <PaymentDecisionButtons payment={payment} /> : null}
    </li>
  );
}
