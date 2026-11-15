"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { Select } from "@/components/ui/select";
import { TextInput } from "@/components/ui/text-input";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { ApiError } from "@/lib/api/errors";
import { dhakaToday, toNumber } from "@/components/staff/offline/offline-model";
import { methodLabelKey } from "@/components/staff/offline/labels";
import { offlineErrorText } from "@/components/staff/offline/offline-error";
import { useRecordClassPayment, type ClassRow, type RecordPaymentBody } from "@/lib/api/exam/instructor-pay";
import { amountDifference, dueOf } from "./pay-model";

const METHODS = ["CASH", "BKASH", "NAGAD", "ROCKET", "BANK"] as const;
type Method = (typeof METHODS)[number];

/**
 * Records what was paid to a class's instructor. The amount is prefilled with the due amount and today's Dhaka date. When the amount
 * differs from the due amount (an advance, a deduction), a note is required to say why, and the difference is shown.
 */
export function ClassPaymentDialog({
  row,
  batchName,
  open,
  onOpenChange,
  canApprove,
}: {
  row: ClassRow;
  batchName: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  canApprove: boolean;
}) {
  const { t } = useLocale();
  const { formatDate, formatMoney } = useFormat();
  const { toast } = useToast();
  const record = useRecordClassPayment();
  const due = dueOf(row);
  const today = dhakaToday();
  const [amount, setAmount] = useState(prefilledAmount(row));
  const [method, setMethod] = useState<Method>("CASH");
  const [reference, setReference] = useState("");
  const [paidOn, setPaidOn] = useState(today);
  const [note, setNote] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});

  const paid = Number(amount);
  const amountValid = amount.trim() !== "" && Number.isFinite(paid) && paid > 0;
  const difference = amountValid ? amountDifference(paid, due) : 0;
  const differs = amountValid && difference !== 0;

  function validate(): Record<string, string> {
    const problems: Record<string, string> = {};
    if (!amountValid) {
      problems.amount = t("instructorPay.payment.errors.amount");
    }
    if (!paidOn) {
      problems.paidOn = t("instructorPay.payment.errors.paidOn");
    } else if (paidOn > today) {
      problems.paidOn = t("instructorPay.payment.errors.future");
    }
    if (differs && note.trim() === "") {
      problems.note = t("instructorPay.payment.errors.noteRequired");
    }
    return problems;
  }

  async function submit() {
    const problems = validate();
    setErrors(problems);
    if (Object.keys(problems).length > 0) {
      return;
    }
    const body: RecordPaymentBody = {
      amount: amount.trim(),
      method,
      paidOn,
      reference: reference.trim() || undefined,
      note: note.trim() || undefined,
    };
    try {
      const updated = await record.mutateAsync({ classId: row.id, body });
      toast({
        title: updated.status === "PAID" ? t("instructorPay.payment.recorded") : t("instructorPay.payment.pending"),
        tone: "ok",
      });
      onOpenChange(false);
    } catch (error) {
      const fields =
        error instanceof ApiError && error.fieldErrors
          ? Object.fromEntries(error.fieldErrors.map((problem) => [problem.field, problem.message]))
          : {};
      setErrors({ form: offlineErrorText(error, t), ...fields });
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={t("instructorPay.payment.title")}
      description={canApprove ? t("instructorPay.payment.masterNote") : t("instructorPay.payment.staffNote")}
      footer={
        <>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            {t("common.actions.cancel")}
          </Button>
          <Button onClick={() => void submit()} loading={record.isPending}>
            {t("instructorPay.payment.submit")}
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        <p className="grid gap-0.5 rounded-control bg-bg px-3 py-2 text-meta text-muted">
          <span className="text-body text-text">
            {row.instructorName ?? row.instructorText ?? ""} · {row.subject}
          </span>
          <span>
            {formatDate(row.heldOn)} · {batchName}
          </span>
          <span>{t("instructorPay.payment.due", { amount: formatMoney(due) ?? "" })}</span>
        </p>
        {errors.form ? (
          <p role="alert" className="text-meta text-danger">
            {errors.form}
          </p>
        ) : null}
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label={t("instructorPay.payment.amount")} error={errors.amount} required>
            <TextInput inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value)} invalid={Boolean(errors.amount)} />
          </Field>
          <Field label={t("instructorPay.payment.method")} required>
            <Select value={method} onChange={(event) => setMethod(event.target.value as Method)}>
              {METHODS.map((value) => (
                <option key={value} value={value}>
                  {t(methodLabelKey(value))}
                </option>
              ))}
            </Select>
          </Field>
          <Field label={t("instructorPay.payment.paidOn")} hint={t("instructorPay.payment.paidOnHint")} error={errors.paidOn} required>
            <TextInput type="date" max={today} value={paidOn} onChange={(event) => setPaidOn(event.target.value)} invalid={Boolean(errors.paidOn)} />
          </Field>
          <Field label={t("instructorPay.payment.reference")} hint={t("instructorPay.payment.referenceHint")}>
            <TextInput value={reference} onChange={(event) => setReference(event.target.value)} />
          </Field>
        </div>
        {differs ? (
          <p role="status" className="rounded-control border border-warn/40 bg-warn/10 px-3 py-2 text-meta text-text">
            {difference > 0
              ? t("instructorPay.payment.differsMore", { difference: formatMoney(Math.abs(difference)) ?? "" })
              : t("instructorPay.payment.differsLess", { difference: formatMoney(Math.abs(difference)) ?? "" })}
          </p>
        ) : null}
        <Field label={t("instructorPay.payment.note")} error={errors.note} required={differs}>
          <Textarea rows={2} value={note} onChange={(event) => setNote(event.target.value)} invalid={Boolean(errors.note)} />
        </Field>
      </div>
    </Dialog>
  );
}

/** The amount the dialog starts with, as the screens read the due amount. Exported so the prefill can be tested on its own. */
export function prefilledAmount(row: ClassRow): string {
  return row.amount == null ? "" : String(toNumber(row.amount));
}
