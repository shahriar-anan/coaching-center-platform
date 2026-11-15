"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { Select } from "@/components/ui/select";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { ApiError } from "@/lib/api/errors";
import { useRecordPayment, type FeePayment } from "@/lib/api/exam/offline-admin";
import { dhakaToday, toNumber } from "./offline-model";
import { methodLabelKey } from "./labels";
import { offlineErrorText } from "./offline-error";

const METHODS = ["CASH", "BKASH", "NAGAD", "ROCKET", "BANK"] as const;

/** The API needs a reference for every payment. For cash it is filled in from the paid-on date, as the direct-enrolment dialog does. */
const REFERENCE = /^[ -~]{1,100}$/;
function cashReference(paidOn: string): string {
  return `CASH-${paidOn.replaceAll("-", "")}`;
}

/**
 * Record a monthly payment (plan 6.3). It is prefilled with the next due amount and today's Dhaka date. A payment by staff waits for the
 * Master Admin's approval and counts only then; the Master Admin's own counts at once (plan D15).
 */
export function RecordPaymentDialog({
  admissionId,
  open,
  onOpenChange,
  nextDue,
  canApprove,
}: {
  admissionId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  nextDue: { amount: number | string; dueOn: string } | null;
  canApprove: boolean;
}) {
  const { t } = useLocale();
  const { formatDate, formatMoney } = useFormat();
  const { toast } = useToast();
  const record = useRecordPayment(admissionId);
  const [amount, setAmount] = useState(nextDue ? String(toNumber(nextDue.amount)) : "");
  const [method, setMethod] = useState<(typeof METHODS)[number]>("CASH");
  const [paidOn, setPaidOn] = useState(dhakaToday());
  const [reference, setReference] = useState(() => cashReference(dhakaToday()));
  // The filled-in cash reference follows the date and the method until the admin types one.
  const [referenceTyped, setReferenceTyped] = useState(false);
  const [note, setNote] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});

  function validate(): Record<string, string> {
    const problems: Record<string, string> = {};
    const value = Number(amount);
    if (!amount.trim() || !Number.isFinite(value) || value <= 0) {
      problems.amount = t("staffOffline.pay.errors.amount");
    }
    if (!paidOn) {
      problems.paidOn = t("staffOffline.pay.errors.paidOn");
    }
    if (!REFERENCE.test(reference.trim())) {
      problems.reference = t("staffOffline.pay.errors.reference");
    }
    return problems;
  }

  async function submit() {
    const problems = validate();
    setErrors(problems);
    if (Object.keys(problems).length > 0) {
      return;
    }
    try {
      const payment: FeePayment = await record.mutateAsync({
        amount: amount.trim(),
        method,
        reference: reference.trim(),
        paidOn,
        note: note.trim() || undefined,
      });
      toast({
        title: payment.status === "PENDING" ? t("staffOffline.pay.pending") : t("staffOffline.pay.recorded"),
        tone: "ok",
      });
      onOpenChange(false);
    } catch (error) {
      // Each field the API names is marked with this dialog's own words, so the highlighted field matches the summary line.
      const fields: Record<string, string> = {};
      for (const problem of error instanceof ApiError && error.fieldErrors ? error.fieldErrors : []) {
        fields[problem.field] =
          problem.field === "reference"
            ? t("staffOffline.pay.errors.reference")
            : problem.field === "amount"
              ? t("staffOffline.pay.errors.amount")
              : problem.field === "paidOn"
                ? t("staffOffline.pay.errors.paidOn")
                : problem.message;
      }
      setErrors({ form: offlineErrorText(error, t), ...fields });
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={t("staffOffline.pay.title")}
      description={
        canApprove ? t("staffOffline.pay.masterNote") : t("staffOffline.pay.staffNote")
      }
      footer={
        <>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            {t("common.actions.cancel")}
          </Button>
          <Button onClick={() => void submit()} loading={record.isPending}>
            {t("staffOffline.pay.submit")}
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        {nextDue ? (
          <p className="rounded-control bg-bg px-3 py-2 text-meta text-muted">
            {t("staffOffline.pay.nextDue", { amount: formatMoney(nextDue.amount) ?? "", date: formatDate(nextDue.dueOn) })}
          </p>
        ) : null}
        {errors.form ? <p role="alert" className="text-meta text-danger">{errors.form}</p> : null}
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label={t("staffOffline.pay.amount")} error={errors.amount} required>
            <TextInput inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value)} invalid={Boolean(errors.amount)} />
          </Field>
          <Field label={t("staffOffline.pay.method")} required>
            <Select
              value={method}
              onChange={(event) => {
                const next = event.target.value as (typeof METHODS)[number];
                setMethod(next);
                if (!referenceTyped) setReference(next === "CASH" ? cashReference(paidOn) : "");
              }}
            >
              {METHODS.map((value) => (
                <option key={value} value={value}>
                  {t(methodLabelKey(value))}
                </option>
              ))}
            </Select>
          </Field>
          <Field label={t("staffOffline.pay.paidOn")} error={errors.paidOn} required>
            <TextInput
              type="date"
              value={paidOn}
              onChange={(event) => {
                setPaidOn(event.target.value);
                if (!referenceTyped && method === "CASH") setReference(cashReference(event.target.value));
              }}
              invalid={Boolean(errors.paidOn)}
            />
          </Field>
          <Field
            label={t("staffOffline.pay.reference")}
            hint={method === "CASH" && !referenceTyped ? t("staffOffline.pay.referenceHintCash") : t("staffOffline.pay.referenceHint")}
            error={errors.reference}
            required
          >
            <TextInput
              value={reference}
              onChange={(event) => {
                setReference(event.target.value);
                setReferenceTyped(true);
              }}
              invalid={Boolean(errors.reference)}
            />
          </Field>
        </div>
        <Field label={t("staffOffline.pay.note")}>
          <TextInput value={note} onChange={(event) => setNote(event.target.value)} />
        </Field>
      </div>
    </Dialog>
  );
}
