"use client";

import { useState } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { Pill, type PillTone } from "@/components/ui/pill";
import { Select } from "@/components/ui/select";
import { Table, type Column } from "@/components/ui/table";
import { TextInput } from "@/components/ui/text-input";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { ApiError } from "@/lib/api/errors";
import { useBatches } from "@/lib/api/exam/offline-admin";
import {
  useDecideOtherPayment,
  useOtherPayments,
  usePendingOtherPayments,
  useRecordOtherPayment,
  type OtherPayment,
} from "@/lib/api/exam/instructor-pay";
import { useRole } from "@/lib/auth/guards";
import { useFormat } from "@/lib/format/use-format";
import { dhakaToday, toNumber } from "@/components/staff/offline/offline-model";
import { methodLabelKey } from "@/components/staff/offline/labels";
import { offlineErrorText } from "@/components/staff/offline/offline-error";

const METHODS = ["CASH", "BKASH", "NAGAD", "ROCKET", "BANK"] as const;
const AMOUNT = /^\d+(\.\d{1,2})?$/;
const TONE: Record<OtherPayment["status"], PillTone> = { PENDING: "accent", APPROVED: "ok", REJECTED: "danger", VOIDED: "muted" };

/**
 * Records a payment to the instructor for something that is not a class (17 Nov): travel, a bonus, an advance. The admin types what it
 * is for. A batch is optional; with one, Finance counts it in that batch's instructor cost. A staff payment waits for the Master Admin.
 */
export function RecordOtherPaymentDialog({
  instructorId,
  instructorName,
  open,
  onOpenChange,
}: {
  instructorId: string;
  instructorName: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t, locale } = useLocale();
  const { toast } = useToast();
  const role = useRole();
  const record = useRecordOtherPayment(instructorId);
  const batches = useBatches("");
  const [reason, setReason] = useState("");
  const [amount, setAmount] = useState("");
  const [paidOn, setPaidOn] = useState(dhakaToday());
  const [method, setMethod] = useState<(typeof METHODS)[number]>("CASH");
  const [reference, setReference] = useState("");
  const [batchId, setBatchId] = useState("");
  const [note, setNote] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});

  function close() {
    setErrors({});
    onOpenChange(false);
  }

  async function submit() {
    const problems: Record<string, string> = {};
    if (!reason.trim()) problems.reason = t("instructorPay.other.errors.reason");
    if (!AMOUNT.test(amount.trim()) || Number(amount) <= 0) problems.amount = t("instructorPay.other.errors.amount");
    if (!paidOn || paidOn > dhakaToday()) problems.paidOn = t("instructorPay.other.errors.paidOn");
    setErrors(problems);
    if (Object.keys(problems).length > 0) return;
    try {
      const saved = await record.mutateAsync({
        reason: reason.trim(),
        amount: amount.trim(),
        paidOn,
        method,
        reference: reference.trim() || undefined,
        note: note.trim() || undefined,
        batchId: batchId || undefined,
      });
      toast({ title: t(saved.status === "APPROVED" ? "instructorPay.other.recorded" : "instructorPay.other.pending"), tone: "ok" });
      setReason("");
      setAmount("");
      setReference("");
      setNote("");
      close();
    } catch (error) {
      const fields =
        error instanceof ApiError && error.fieldErrors ? Object.fromEntries(error.fieldErrors.map((problem) => [problem.field, problem.message])) : {};
      setErrors({ form: offlineErrorText(error, t), ...fields });
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => (next ? onOpenChange(true) : close())}
      title={t("instructorPay.other.title", { name: instructorName })}
      description={t(role.canApproveOfflineClass ? "instructorPay.other.masterNote" : "instructorPay.other.staffNote")}
      footer={
        <>
          <Button variant="ghost" onClick={close}>
            {t("common.actions.cancel")}
          </Button>
          <Button onClick={() => void submit()} loading={record.isPending}>
            {t("instructorPay.other.submit")}
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        {errors.form ? (
          <p role="alert" className="text-meta text-danger">
            {errors.form}
          </p>
        ) : null}
        <Field label={t("instructorPay.other.reason")} hint={t("instructorPay.other.reasonHint")} error={errors.reason} required>
          <Textarea rows={2} value={reason} onChange={(event) => setReason(event.target.value)} invalid={Boolean(errors.reason)} />
        </Field>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label={t("instructorPay.other.amount")} error={errors.amount} required>
            <TextInput inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value)} invalid={Boolean(errors.amount)} />
          </Field>
          <Field label={t("instructorPay.other.paidOn")} error={errors.paidOn} required>
            <TextInput type="date" value={paidOn} max={dhakaToday()} onChange={(event) => setPaidOn(event.target.value)} invalid={Boolean(errors.paidOn)} />
          </Field>
          <Field label={t("instructorPay.other.method")} required>
            <Select value={method} onChange={(event) => setMethod(event.target.value as (typeof METHODS)[number])}>
              {METHODS.map((value) => (
                <option key={value} value={value}>
                  {t(methodLabelKey(value))}
                </option>
              ))}
            </Select>
          </Field>
          <Field label={t("instructorPay.other.reference")} error={errors.reference}>
            <TextInput value={reference} onChange={(event) => setReference(event.target.value)} invalid={Boolean(errors.reference)} />
          </Field>
        </div>
        <Field label={t("instructorPay.other.batch")} hint={t("instructorPay.other.batchHint")}>
          <Select value={batchId} onChange={(event) => setBatchId(event.target.value)}>
            <option value="">{t("instructorPay.other.noBatch")}</option>
            {(batches.data ?? []).map((batch) => (
              <option key={batch.id} value={batch.id}>
                {locale === "bn" ? batch.nameBn || batch.nameEn : batch.nameEn}
              </option>
            ))}
          </Select>
        </Field>
        <Field label={t("instructorPay.other.note")}>
          <Textarea rows={2} value={note} onChange={(event) => setNote(event.target.value)} />
        </Field>
      </div>
    </Dialog>
  );
}

/** Asks for the reason of a reject or a void, then sends it. */
function ReasonDialog({ payment, action, onClose }: { payment: OtherPayment; action: "reject" | "void"; onClose: () => void }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const decide = useDecideOtherPayment();
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    if (!reason.trim()) {
      setError(t("instructorPay.other.errors.decision"));
      return;
    }
    try {
      await decide.mutateAsync({ id: payment.id, action, reason: reason.trim() });
      toast({ title: t(action === "reject" ? "instructorPay.other.rejected" : "instructorPay.other.voided"), tone: "ok" });
      onClose();
    } catch (caught) {
      setError(offlineErrorText(caught, t));
    }
  }

  return (
    <Dialog
      open
      onOpenChange={(next) => (next ? undefined : onClose())}
      title={t(action === "reject" ? "instructorPay.other.rejectTitle" : "instructorPay.other.voidTitle")}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            {t("common.actions.cancel")}
          </Button>
          <Button variant="danger" onClick={() => void submit()} loading={decide.isPending}>
            {t(action === "reject" ? "instructorPay.other.reject" : "instructorPay.other.void")}
          </Button>
        </>
      }
    >
      <Field label={t("instructorPay.other.decisionReason")} error={error ?? undefined} required>
        <Textarea rows={3} value={reason} onChange={(event) => setReason(event.target.value)} invalid={Boolean(error)} />
      </Field>
    </Dialog>
  );
}

/** The other payments as a table: when, what for, batch, amount, method, status, and the Master Admin's actions. */
export function OtherPaymentsTable({ rows, loading, showInstructor }: { rows: OtherPayment[]; loading: boolean; showInstructor?: boolean }) {
  const { t, locale } = useLocale();
  const { formatDate, formatMoney } = useFormat();
  const { toast } = useToast();
  const role = useRole();
  const decide = useDecideOtherPayment();
  const [asking, setAsking] = useState<{ payment: OtherPayment; action: "reject" | "void" } | null>(null);

  const columns: Column<OtherPayment>[] = [
    { key: "date", header: t("instructorPay.other.columns.date"), nowrap: true, cell: (row) => formatDate(row.paidOn) },
    ...(showInstructor
      ? [
          {
            key: "instructor",
            header: t("instructorPay.other.columns.instructor"),
            cell: (row: OtherPayment) => (
              <Link href={`/staff/offline-instructors/${row.instructorId}`} className="font-medium text-text underline-offset-4 hover:underline">
                {row.instructorName}
              </Link>
            ),
          } satisfies Column<OtherPayment>,
        ]
      : []),
    {
      key: "reason",
      header: t("instructorPay.other.columns.reason"),
      cell: (row) => (
        <div className="grid gap-0.5">
          <span className="break-words text-text">{row.reason}</span>
          {row.batchNameEn ? <span className="text-meta text-muted">{locale === "bn" ? row.batchNameBn || row.batchNameEn : row.batchNameEn}</span> : null}
        </div>
      ),
    },
    {
      key: "amount",
      header: t("instructorPay.other.columns.amount"),
      align: "end",
      nowrap: true,
      cell: (row) => <span className="font-latin tabular-nums">{formatMoney(toNumber(row.amount))}</span>,
    },
    {
      key: "method",
      header: t("instructorPay.other.columns.method"),
      cell: (row) => (
        <div className="grid gap-0.5">
          <span>{t(methodLabelKey(row.method))}</span>
          {row.reference ? <span className="break-all text-meta text-muted">{row.reference}</span> : null}
        </div>
      ),
    },
    {
      key: "status",
      header: t("instructorPay.other.columns.status"),
      cell: (row) => (
        <div className="grid gap-1">
          <Pill tone={TONE[row.status]}>{t(`instructorPay.other.status.${row.status}`)}</Pill>
          {row.decisionReason ? <span className="text-meta text-muted">{row.decisionReason}</span> : null}
        </div>
      ),
    },
    {
      key: "actions",
      header: "",
      mobileLabel: t("instructorPay.other.columns.actions"),
      align: "end",
      cell: (row) =>
        !role.canApproveOfflineClass ? null : row.status === "PENDING" ? (
          <div className="flex flex-wrap justify-end gap-2">
            <Button
              size="sm"
              loading={decide.isPending && decide.variables?.id === row.id}
              onClick={() =>
                decide.mutate(
                  { id: row.id, action: "approve" },
                  {
                    onSuccess: () => toast({ title: t("instructorPay.other.approved"), tone: "ok" }),
                    onError: (error) => toast({ title: offlineErrorText(error, t), tone: "danger" }),
                  },
                )
              }
            >
              {t("instructorPay.other.approve")}
            </Button>
            <Button size="sm" variant="secondary" onClick={() => setAsking({ payment: row, action: "reject" })}>
              {t("instructorPay.other.reject")}
            </Button>
          </div>
        ) : row.status === "APPROVED" ? (
          <Button size="sm" variant="ghost" onClick={() => setAsking({ payment: row, action: "void" })}>
            {t("instructorPay.other.void")}
          </Button>
        ) : null,
    },
  ];

  return (
    <>
      <Table
        columns={columns}
        rows={rows}
        rowKey={(row) => row.id}
        loading={loading}
        empty={<EmptyState title={t("instructorPay.other.empty")} body={t("instructorPay.other.emptyBody")} />}
      />
      {asking ? <ReasonDialog payment={asking.payment} action={asking.action} onClose={() => setAsking(null)} /> : null}
    </>
  );
}

/** The instructor's other payments on their page, with Record other payment. */
export function OtherPaymentsSection({ instructorId, instructorName }: { instructorId: string; instructorName: string }) {
  const { t } = useLocale();
  const role = useRole();
  const payments = useOtherPayments(instructorId);
  const [recording, setRecording] = useState(false);
  return (
    <section aria-labelledby="other-payments" className="grid gap-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="other-payments" className="text-section font-semibold text-text">
          {t("instructorPay.other.sectionTitle")}
        </h2>
        {role.canSettleOfflineClass ? (
          <Button variant="secondary" onClick={() => setRecording(true)}>
            {t("instructorPay.other.open")}
          </Button>
        ) : null}
      </div>
      {payments.isError ? (
        <ErrorState onRetry={() => void payments.refetch()} error={payments.error} />
      ) : (
        <OtherPaymentsTable rows={payments.data ?? []} loading={payments.isPending} />
      )}
      <RecordOtherPaymentDialog instructorId={instructorId} instructorName={instructorName} open={recording} onOpenChange={setRecording} />
    </section>
  );
}

/** The other payments waiting for the Master Admin, under the classes on the Waiting for approval tab. Hidden when there are none. */
export function PendingOtherPayments() {
  const { t } = useLocale();
  const pending = usePendingOtherPayments();
  if (pending.isPending || (pending.data ?? []).length === 0) return null;
  return (
    <section aria-labelledby="pending-other" className="grid gap-3">
      <h2 id="pending-other" className="text-section font-semibold text-text">
        {t("instructorPay.other.pendingTitle")}
      </h2>
      <OtherPaymentsTable rows={pending.data ?? []} loading={false} showInstructor />
    </section>
  );
}
