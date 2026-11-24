"use client";

import Link from "next/link";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { Field } from "@/components/ui/field";
import { Pill } from "@/components/ui/pill";
import { Table, type Column } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useVoidInstructorPayment, type InstructorPayment, type PaymentList } from "@/lib/api/exam/instructor-pay";
import { useFormat } from "@/lib/format/use-format";
import { toNumber } from "@/components/staff/offline/offline-model";
import { methodLabelKey } from "@/components/staff/offline/labels";
import { offlineErrorText } from "@/components/staff/offline/offline-error";
import { STATUS_TONE, classCountText, kindKey, statusKey } from "./pay-model";
import { EditPaymentDialog } from "./record-payment-form";

/**
 * A list of instructor payments (plan 3.2, 3.4): date, type and classes, batch, description, amount, method and reference, status. On an
 * instructor's page (`actions`) each paid payment has Edit and Void; on the Instructor payments page (`showInstructor`) each row names
 * its instructor and links to them. The total of the paid rows sits under the table.
 */
export function PaymentTable({
  list,
  loading,
  filtered = false,
  showInstructor = false,
  actions = false,
}: {
  list: PaymentList | undefined;
  loading: boolean;
  filtered?: boolean;
  showInstructor?: boolean;
  actions?: boolean;
}) {
  const { t, locale } = useLocale();
  const { formatDate, formatMoney, formatNumber } = useFormat();
  const [editing, setEditing] = useState<InstructorPayment | null>(null);
  const [voiding, setVoiding] = useState<InstructorPayment | null>(null);
  const rows = list?.items ?? [];

  const columns: Column<InstructorPayment>[] = [
    {
      key: "date",
      header: t("instructorPay.history.columns.date"),
      mobileLabel: t("instructorPay.history.columns.date"),
      nowrap: true,
      cell: (row) => formatDate(row.paidOn),
    },
    ...(showInstructor
      ? [
          {
            key: "instructor",
            header: t("instructorPay.history.columns.instructor"),
            mobileLabel: t("instructorPay.history.columns.instructor"),
            cell: (row: InstructorPayment) => (
              <Link href={`/staff/offline-instructors/${row.instructorId}`} className="font-medium text-text underline-offset-4 hover:underline">
                {row.instructorName}
              </Link>
            ),
          } satisfies Column<InstructorPayment>,
        ]
      : []),
    {
      key: "kind",
      header: t("instructorPay.history.columns.kind"),
      mobileLabel: t("instructorPay.history.columns.kind"),
      cell: (row) => {
        const classes = classCountText(row, t, formatNumber);
        return (
          <div className="grid gap-0.5">
            <span>{t(kindKey(row.kind))}</span>
            {classes ? <span className="text-meta text-muted">{classes}</span> : null}
          </div>
        );
      },
    },
    {
      key: "description",
      header: t("instructorPay.history.columns.description"),
      mobileLabel: t("instructorPay.history.columns.description"),
      cell: (row) => (
        <div className="grid min-w-0 gap-0.5">
          <span className="break-words text-text">
            {row.recordKind === "CLASS" && row.classOn
              ? t("instructorPay.history.classOf", { subject: row.description ?? "", date: formatDate(row.classOn) })
              : (row.description ?? "—")}
          </span>
          <span className="text-meta text-muted">
            {row.batchNameEn
              ? locale === "bn"
                ? row.batchNameBn || row.batchNameEn
                : row.batchNameEn
              : t("instructorPay.history.noBatch")}
          </span>
          {row.note ? <span className="break-words text-meta text-muted">{row.note}</span> : null}
        </div>
      ),
    },
    {
      key: "amount",
      header: t("instructorPay.history.columns.amount"),
      mobileLabel: t("instructorPay.history.columns.amount"),
      align: "end",
      nowrap: true,
      cell: (row) => (
        <span className={row.status === "APPROVED" ? "font-latin tabular-nums" : "font-latin tabular-nums text-muted line-through"}>
          {formatMoney(toNumber(row.amount))}
        </span>
      ),
    },
    {
      key: "method",
      header: t("instructorPay.history.columns.method"),
      mobileLabel: t("instructorPay.history.columns.method"),
      cell: (row) => (
        <div className="grid gap-0.5">
          <span>{t(methodLabelKey(row.method))}</span>
          {row.reference ? <span className="break-all text-meta text-muted">{row.reference}</span> : null}
        </div>
      ),
    },
    {
      key: "status",
      header: t("instructorPay.history.columns.status"),
      mobileLabel: t("instructorPay.history.columns.status"),
      cell: (row) => (
        <div className="grid justify-items-start gap-1">
          <Pill tone={STATUS_TONE[row.status]}>{t(statusKey(row.status))}</Pill>
          {row.status === "VOIDED" && row.voidReason ? (
            <span className="text-meta text-muted">{t("instructorPay.history.voidReason", { reason: row.voidReason })}</span>
          ) : null}
          {row.correctedAt ? (
            <span className="text-meta text-muted">{t("instructorPay.history.corrected", { date: formatDate(row.correctedAt) })}</span>
          ) : null}
        </div>
      ),
    },
    ...(actions
      ? [
          {
            key: "actions",
            header: "",
            mobileLabel: t("instructorPay.history.columns.actions"),
            align: "end",
            cell: (row: InstructorPayment) =>
              row.status === "APPROVED" ? (
                <div className="flex flex-wrap justify-end gap-2">
                  <Button size="sm" variant="secondary" onClick={() => setEditing(row)}>
                    {t("instructorPay.history.edit")}
                  </Button>
                  <Button size="sm" variant="ghost" onClick={() => setVoiding(row)}>
                    {t("instructorPay.history.void")}
                  </Button>
                </div>
              ) : null,
          } satisfies Column<InstructorPayment>,
        ]
      : []),
  ];

  return (
    <div className="grid gap-3">
      <Table
        columns={columns}
        rows={rows}
        rowKey={(row) => `${row.recordKind}-${row.id}`}
        loading={loading}
        empty={
          filtered ? (
            <EmptyState title={t("instructorPay.history.emptyFiltered")} />
          ) : (
            <EmptyState title={t("instructorPay.history.empty")} body={t("instructorPay.history.emptyBody")} />
          )
        }
      />
      {rows.length > 0 ? (
        <p className="text-end text-body font-semibold text-text">
          {t("instructorPay.history.total", { amount: formatMoney(toNumber(list?.total)) ?? "" })}
        </p>
      ) : null}
      {list?.truncated ? <p className="text-meta text-muted">{t("instructorPay.history.truncated")}</p> : null}
      {editing ? <EditPaymentDialog key={editing.id} payment={editing} onOpenChange={(open) => !open && setEditing(null)} /> : null}
      {voiding ? <VoidPaymentDialog payment={voiding} onClose={() => setVoiding(null)} /> : null}
    </div>
  );
}

/** Voids a payment made by mistake (D16), with a reason. */
function VoidPaymentDialog({ payment, onClose }: { payment: InstructorPayment; onClose: () => void }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const { formatDate, formatMoney } = useFormat();
  const voidPayment = useVoidInstructorPayment();
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    if (!reason.trim()) {
      setError(t("instructorPay.void.errors.reason"));
      return;
    }
    try {
      await voidPayment.mutateAsync({ payment, reason: reason.trim() });
      toast({ title: t("instructorPay.void.voided"), tone: "ok" });
      onClose();
    } catch (caught) {
      setError(offlineErrorText(caught, t));
    }
  }

  return (
    <Dialog
      open
      onOpenChange={(next) => (next ? undefined : onClose())}
      title={t("instructorPay.void.title")}
      description={t("instructorPay.void.body", {
        amount: formatMoney(toNumber(payment.amount)) ?? "",
        name: payment.instructorName,
        date: formatDate(payment.paidOn),
      })}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            {t("common.actions.cancel")}
          </Button>
          <Button variant="danger" onClick={() => void submit()} loading={voidPayment.isPending}>
            {t("instructorPay.void.submit")}
          </Button>
        </>
      }
    >
      <Field label={t("instructorPay.void.reason")} error={error ?? undefined} required>
        <Textarea rows={3} value={reason} onChange={(event) => setReason(event.target.value)} invalid={Boolean(error)} />
      </Field>
    </Dialog>
  );
}
