"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { Select } from "@/components/ui/select";
import { TextInput } from "@/components/ui/text-input";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/toast";
import { adminErrorText } from "@/components/staff/access/admin-error";
import {
  PAYMENT_METHODS,
  isDate,
  paisaToInput,
  paisaToNumber,
  todayInDhaka,
  type PaymentMethod,
} from "@/components/staff/enrol/payment-model";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import {
  useRecordInstallmentPayment,
  type InstallmentPayment,
  type InstallmentPlan,
} from "@/lib/api/exam/staff-installments";
import {
  checkRecordPayment,
  freeCashReference,
  suggestedPaymentPaisa,
  toPaisa,
  type RecordErrors,
  type RecordField,
  type RecordForm,
} from "./installment-model";

/**
 * Records one payment on a plan. Mounted only while open, so each opening starts from fresh suggestions: the amount is the next
 * installment's uncovered amount, and a cash reference is generated and kept only while the admin has not typed one. The
 * amount may not be more than the amount still due, and the reference must be new on this plan (plan section 4).
 */
export function RecordPaymentDialog({
  plan,
  payments,
  onClose,
}: {
  plan: InstallmentPlan;
  payments: InstallmentPayment[];
  onClose: () => void;
}) {
  const { t } = useLocale();
  const { formatMoney } = useFormat();
  const { toast } = useToast();
  const record = useRecordInstallmentPayment(plan.planId);
  const today = todayInDhaka();
  // The item's ID is the first 8 characters of the cash reference (plan C4). A plan always has one of the two.
  const itemId = plan.examId ?? plan.moduleId ?? plan.planId;
  const takenReferences = payments.filter((payment) => !payment.voidedAt).map((payment) => payment.reference);
  const duePaisa = toPaisa(plan.due);

  const [form, setForm] = useState<RecordForm>(() => ({
    amountText: paisaToInput(suggestedPaymentPaisa(plan)),
    method: "CASH",
    referenceText: freeCashReference(itemId, today, takenReferences),
    paidOnText: today,
    note: "",
  }));
  const [referenceTyped, setReferenceTyped] = useState(false);
  const [tried, setTried] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  const check = checkRecordPayment(form, { duePaisa, today, takenReferences });

  function chooseMethod(method: PaymentMethod) {
    setForm((current) => ({
      ...current,
      method,
      referenceText: referenceTyped
        ? current.referenceText
        : method === "CASH"
          ? freeCashReference(itemId, current.paidOnText, takenReferences)
          : "",
    }));
  }

  function choosePaidOn(date: string) {
    setForm((current) => ({
      ...current,
      paidOnText: date,
      referenceText:
        referenceTyped || current.method !== "CASH" || !isDate(date)
          ? current.referenceText
          : freeCashReference(itemId, date, takenReferences),
    }));
  }

  /** The message for a field, once the admin has tried to save. */
  function message(field: RecordField): string | undefined {
    if (!tried || check.ok) {
      return undefined;
    }
    const code = (check.errors as RecordErrors)[field];
    return code ? t(`staffAdmin.installments.record.error.${code}`) : undefined;
  }

  async function save() {
    setTried(true);
    setFailure(null);
    if (!check.ok) {
      return;
    }
    try {
      await record.mutateAsync({
        amount: paisaToNumber(check.amountPaisa),
        method: form.method,
        reference: check.reference,
        paidOn: form.paidOnText,
        note: check.note,
      });
      toast({ title: t("staffAdmin.installments.record.done"), tone: "ok" });
      onClose();
    } catch (caught) {
      setFailure(adminErrorText(caught, t));
    }
  }

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !record.isPending) {
          onClose();
        }
      }}
      title={t("staffAdmin.installments.record.title")}
      description={t("staffAdmin.installments.record.body")}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={record.isPending}>
            {t("common.actions.cancel")}
          </Button>
          <Button loading={record.isPending} disabled={record.isPending} onClick={() => void save()}>
            {t("staffAdmin.installments.record.submit")}
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        <p className="rounded-control border border-border bg-bg px-3.5 py-2.5 text-meta tabular-nums text-text">
          {t("staffAdmin.installments.record.stillDue", { amount: formatMoney(plan.due) ?? "" })}
        </p>

        <Field label={t("staffAdmin.installments.record.amount")} hint={t("staffAdmin.installments.record.amountHint")} error={message("amount")} required>
          <TextInput
            inputMode="decimal"
            value={form.amountText}
            invalid={Boolean(message("amount"))}
            onChange={(event) => setForm((current) => ({ ...current, amountText: event.target.value }))}
          />
        </Field>

        <Field label={t("staffAdmin.installments.record.method")} required>
          <Select value={form.method} onChange={(event) => chooseMethod(event.target.value as PaymentMethod)}>
            {PAYMENT_METHODS.map((value) => (
              <option key={value} value={value}>
                {t(`staffAdmin.enrol.payment.methods.${value}`)}
              </option>
            ))}
          </Select>
        </Field>

        <Field
          label={t("staffAdmin.installments.record.reference")}
          hint={
            form.method === "CASH" && !referenceTyped
              ? t("staffAdmin.installments.record.referenceHintCash")
              : t("staffAdmin.installments.record.referenceHint")
          }
          error={message("reference")}
          required
        >
          <TextInput
            value={form.referenceText}
            maxLength={100}
            invalid={Boolean(message("reference"))}
            onChange={(event) => {
              setReferenceTyped(true);
              setForm((current) => ({ ...current, referenceText: event.target.value }));
            }}
          />
        </Field>

        <Field label={t("staffAdmin.installments.record.paidOn")} error={message("paidOn")} required>
          <TextInput
            type="date"
            max={today}
            value={form.paidOnText}
            invalid={Boolean(message("paidOn"))}
            onChange={(event) => choosePaidOn(event.target.value)}
          />
        </Field>

        <Field label={t("staffAdmin.installments.record.note")} error={message("note")}>
          <Textarea
            value={form.note}
            maxLength={500}
            rows={2}
            invalid={Boolean(message("note"))}
            onChange={(event) => setForm((current) => ({ ...current, note: event.target.value }))}
          />
        </Field>

        {failure ? (
          <p role="alert" className="text-meta text-danger">
            {failure}
          </p>
        ) : null}
      </div>
    </Dialog>
  );
}
