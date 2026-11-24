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
import { ApiError } from "@/lib/api/errors";
import { useBatches } from "@/lib/api/exam/offline-admin";
import {
  useEditInstructorPayment,
  useRecordInstructorPayment,
  type InstructorPayment,
  type PayKind,
  type PayMethod,
  type PaymentBody,
} from "@/lib/api/exam/instructor-pay";
import { dhakaToday, toNumber } from "@/components/staff/offline/offline-model";
import { methodLabelKey } from "@/components/staff/offline/labels";
import { offlineErrorText } from "@/components/staff/offline/offline-error";
import { PAY_KINDS, PAY_METHODS, kindKey } from "./pay-model";

/** The form's values, as typed. */
export type PaymentDraft = {
  paidOn: string;
  kind: PayKind | "";
  classCount: string;
  amount: string;
  method: PayMethod;
  reference: string;
  batchId: string;
  description: string;
  note: string;
};

const AMOUNT = /^\d+(\.\d{1,2})?$/;
const REFERENCE = /^[ -~]{1,100}$/;

/** A new payment: today's Dhaka date and cash; the type is left for the admin to choose. */
export function emptyDraft(): PaymentDraft {
  return { paidOn: dhakaToday(), kind: "", classCount: "", amount: "", method: "CASH", reference: "", batchId: "", description: "", note: "" };
}

/** A payment as the form shows it, for a correction. */
export function draftOf(payment: InstructorPayment): PaymentDraft {
  return {
    paidOn: payment.paidOn,
    kind: payment.kind,
    classCount: payment.classCount == null ? "" : String(payment.classCount),
    amount: String(toNumber(payment.amount)),
    method: payment.method,
    reference: payment.reference ?? "",
    batchId: payment.batchId ?? "",
    description: payment.description ?? "",
    note: payment.note ?? "",
  };
}

/**
 * The checks the API makes too (plan 3.1): a date not after today, a type, classes 1 to 200 or none, an amount above zero and up to
 * ৳10,00,000 with two decimals at most, a reference of printable characters, and a description for Other. A class payment from before
 * 24 Nov has its type and description from the class, so they are not checked.
 */
export function draftProblems(draft: PaymentDraft, t: (key: string) => string, fromClass = false): Record<string, string> {
  const problems: Record<string, string> = {};
  if (!draft.paidOn || draft.paidOn > dhakaToday()) {
    problems.paidOn = t("instructorPay.form.errors.paidOn");
  }
  if (!fromClass && !draft.kind) {
    problems.kind = t("instructorPay.form.errors.kind");
  }
  const classCount = draft.classCount.trim();
  if (!fromClass && classCount && (!/^\d+$/.test(classCount) || Number(classCount) < 1 || Number(classCount) > 200)) {
    problems.classCount = t("instructorPay.form.errors.classCount");
  }
  const amount = draft.amount.trim();
  if (!AMOUNT.test(amount) || Number(amount) <= 0 || Number(amount) > 1_000_000) {
    problems.amount = t("instructorPay.form.errors.amount");
  }
  if (draft.reference.trim() && !REFERENCE.test(draft.reference.trim())) {
    problems.reference = t("instructorPay.form.errors.reference");
  }
  if (!fromClass && draft.kind === "OTHER" && !draft.description.trim()) {
    problems.description = t("instructorPay.form.errors.description");
  }
  return problems;
}

/** What the API takes. Empty optional fields go as null. */
export function bodyOf(draft: PaymentDraft): PaymentBody {
  const optional = (value: string) => (value.trim() ? value.trim() : null);
  return {
    paidOn: draft.paidOn,
    kind: (draft.kind || "OTHER") as PayKind,
    classCount: draft.classCount.trim() ? Number(draft.classCount.trim()) : null,
    amount: draft.amount.trim(),
    method: draft.method,
    reference: optional(draft.reference),
    batchId: draft.batchId || null,
    description: optional(draft.description),
    note: optional(draft.note),
  };
}

/** The API's field errors, by field, so they show under their fields. */
function fieldErrors(error: unknown): Record<string, string> {
  return error instanceof ApiError && error.fieldErrors
    ? Object.fromEntries(error.fieldErrors.map((problem) => [problem.field, problem.message]))
    : {};
}

/**
 * The fields of a payment (plan 3.1): date, type, number of classes, amount, method, reference, batch, description and note. With
 * `fromClass` (a class payment from before 24 Nov) only the money fields can change: type, classes, batch and description are the class's.
 */
export function PaymentFields({
  value,
  errors,
  fromClass = false,
  onChange,
}: {
  value: PaymentDraft;
  errors: Record<string, string>;
  fromClass?: boolean;
  onChange: (patch: Partial<PaymentDraft>) => void;
}) {
  const { t, locale } = useLocale();
  const batches = useBatches("");
  const other = value.kind === "OTHER";
  return (
    <div className="grid gap-4">
      {fromClass ? <p className="rounded-control bg-bg px-3 py-2 text-meta text-muted">{t("instructorPay.form.fromClass")}</p> : null}
      {/* items-start: a field whose neighbour has a hint keeps its own height (browser check, 24 Nov). */}
      <div className="grid items-start gap-4 sm:grid-cols-2">
        <Field label={t("instructorPay.form.paidOn")} hint={t("instructorPay.form.paidOnHint")} error={errors.paidOn} required>
          <TextInput
            className="h-11"
            type="date"
            value={value.paidOn}
            max={dhakaToday()}
            invalid={Boolean(errors.paidOn)}
            onChange={(event) => onChange({ paidOn: event.target.value })}
          />
        </Field>
        {fromClass ? null : (
          <Field label={t("instructorPay.form.kind")} error={errors.kind} required>
            <Select className="h-11" value={value.kind} invalid={Boolean(errors.kind)} onChange={(event) => onChange({ kind: event.target.value as PayKind })}>
              <option value="" disabled>
                {t("instructorPay.form.chooseKind")}
              </option>
              {PAY_KINDS.map((kind) => (
                <option key={kind} value={kind}>
                  {t(kindKey(kind))}
                </option>
              ))}
            </Select>
          </Field>
        )}
        {fromClass ? null : (
          <Field label={t("instructorPay.form.classCount")} hint={t("instructorPay.form.classCountHint")} error={errors.classCount}>
            <TextInput
            className="h-11"
              inputMode="numeric"
              value={value.classCount}
              invalid={Boolean(errors.classCount)}
              onChange={(event) => onChange({ classCount: event.target.value })}
            />
          </Field>
        )}
        <Field label={t("instructorPay.form.amount")} error={errors.amount} required>
          <TextInput
            className="h-11"
            inputMode="decimal"
            value={value.amount}
            invalid={Boolean(errors.amount)}
            onChange={(event) => onChange({ amount: event.target.value })}
          />
        </Field>
        <Field label={t("instructorPay.form.method")} error={errors.method} required>
          <Select className="h-11" value={value.method} onChange={(event) => onChange({ method: event.target.value as PayMethod })}>
            {PAY_METHODS.map((method) => (
              <option key={method} value={method}>
                {t(methodLabelKey(method))}
              </option>
            ))}
          </Select>
        </Field>
        <Field label={t("instructorPay.form.reference")} hint={t("instructorPay.form.referenceHint")} error={errors.reference}>
          <TextInput className="h-11" value={value.reference} invalid={Boolean(errors.reference)} onChange={(event) => onChange({ reference: event.target.value })} />
        </Field>
        {fromClass ? null : (
          <Field label={t("instructorPay.form.batch")} hint={t("instructorPay.form.batchHint")} error={errors.batchId}>
            <Select className="h-11" value={value.batchId} onChange={(event) => onChange({ batchId: event.target.value })}>
              <option value="">{t("instructorPay.form.noBatch")}</option>
              {(batches.data ?? []).map((batch) => (
                <option key={batch.id} value={batch.id}>
                  {locale === "bn" ? batch.nameBn || batch.nameEn : batch.nameEn || batch.nameBn}
                </option>
              ))}
            </Select>
          </Field>
        )}
      </div>
      {fromClass ? null : (
        <Field
          label={t(other ? "instructorPay.form.description" : "instructorPay.form.descriptionOptional")}
          hint={t("instructorPay.form.descriptionHint")}
          error={errors.description}
          required={other}
        >
          <Textarea
            rows={2}
            value={value.description}
            invalid={Boolean(errors.description)}
            onChange={(event) => onChange({ description: event.target.value })}
          />
        </Field>
      )}
      <Field label={t("instructorPay.form.note")} error={errors.note}>
        <Textarea rows={2} value={value.note} onChange={(event) => onChange({ note: event.target.value })} />
      </Field>
    </div>
  );
}

/**
 * The Record payment tab (D7): the form, saved at once (only the Master Admin records). After a save the form clears, keeping the
 * date and the method, so the next payment of the day is quick.
 */
export function RecordPaymentForm({ instructorId, onRecorded }: { instructorId: string; onRecorded?: () => void }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const record = useRecordInstructorPayment(instructorId);
  const [draft, setDraft] = useState<PaymentDraft>(emptyDraft);
  const [errors, setErrors] = useState<Record<string, string>>({});

  async function submit() {
    const problems = draftProblems(draft, t);
    setErrors(problems);
    if (Object.keys(problems).length > 0) {
      return;
    }
    try {
      await record.mutateAsync(bodyOf(draft));
      toast({ title: t("instructorPay.form.recorded"), tone: "ok" });
      setDraft((current) => ({ ...emptyDraft(), paidOn: current.paidOn, method: current.method }));
      onRecorded?.();
    } catch (error) {
      setErrors({ form: offlineErrorText(error, t), ...fieldErrors(error) });
    }
  }

  return (
    <form
      className="grid gap-4 rounded-card border border-border bg-card p-4 sm:p-5"
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      {errors.form ? (
        <p role="alert" className="text-meta text-danger">
          {errors.form}
        </p>
      ) : null}
      <PaymentFields value={draft} errors={errors} onChange={(patch) => setDraft((current) => ({ ...current, ...patch }))} />
      <div className="flex justify-end">
        <Button type="submit" loading={record.isPending}>
          {t("instructorPay.form.submit")}
        </Button>
      </div>
    </form>
  );
}

/** Corrects a payment (D9), with the reason for the correction. It applies at once and is kept in Payment records. */
export function EditPaymentDialog({
  payment,
  onOpenChange,
}: {
  payment: InstructorPayment;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useLocale();
  const { toast } = useToast();
  const edit = useEditInstructorPayment();
  const fromClass = payment.recordKind === "CLASS";
  const [draft, setDraft] = useState<PaymentDraft>(() => draftOf(payment));
  const [reason, setReason] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});

  async function submit() {
    const problems = draftProblems(draft, t, fromClass);
    if (!reason.trim()) {
      problems.editReason = t("instructorPay.edit.errors.reason");
    }
    setErrors(problems);
    if (Object.keys(problems).length > 0) {
      return;
    }
    const full = bodyOf(draft);
    const body = fromClass
      ? { paidOn: full.paidOn, amount: full.amount, method: full.method, reference: full.reference, note: full.note, editReason: reason.trim() }
      : { ...full, editReason: reason.trim() };
    try {
      await edit.mutateAsync({ payment, body });
      toast({ title: t("instructorPay.edit.saved"), tone: "ok" });
      onOpenChange(false);
    } catch (error) {
      const fields = fieldErrors(error);
      setErrors({ form: fields.amount === "Nothing is changed." ? t("instructorPay.edit.errors.nothing") : offlineErrorText(error, t), ...fields });
    }
  }

  return (
    <Dialog
      open
      onOpenChange={onOpenChange}
      title={t("instructorPay.edit.title")}
      className="sm:max-w-2xl"
      footer={
        <>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            {t("common.actions.cancel")}
          </Button>
          <Button onClick={() => void submit()} loading={edit.isPending}>
            {t("instructorPay.edit.submit")}
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
        <PaymentFields
          value={draft}
          errors={errors}
          fromClass={fromClass}
          onChange={(patch) => setDraft((current) => ({ ...current, ...patch }))}
        />
        <Field label={t("instructorPay.edit.reason")} hint={t("instructorPay.edit.reasonHint")} error={errors.editReason} required>
          <Textarea rows={2} value={reason} invalid={Boolean(errors.editReason)} onChange={(event) => setReason(event.target.value)} />
        </Field>
      </div>
    </Dialog>
  );
}
