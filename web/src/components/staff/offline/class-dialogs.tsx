"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { Select } from "@/components/ui/select";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import {
  useEditClass,
  useMarkNotTaken,
  useRecordClassPayment,
  useSetRate,
  type BatchClass,
  type ClassEditBody,
  type PayMethod,
} from "@/lib/api/exam/offline-batch-pay";
import { useFormat } from "@/lib/format/use-format";
import { dhakaToday } from "./offline-model";
import { methodLabelKey } from "./labels";
import { amountOf, dhakaInstant } from "./batch-pay-format";
import { offlineErrorText } from "./offline-error";

const METHODS: PayMethod[] = ["CASH", "BKASH", "NAGAD", "ROCKET", "BANK"];

/** The reason of a class that did not run, as the one-tap choices give it. */
const REASON_KEYS = ["batchPay.classes.notTaken.absent", "batchPay.classes.notTaken.cancelled", "batchPay.classes.notTaken.holiday"] as const;

/**
 * Edits a class that is not settled: the rate for this class, the date, the times, the subject and topic. The instructor is changed from
 * the class's own Change button (the instructor dialog), so it is not here.
 */
export function ClassEditDialog({
  item,
  open,
  onOpenChange,
}: {
  item: BatchClass;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useLocale();
  const { toast } = useToast();
  const edit = useEditClass();
  const [override, setOverride] = useState(item.rateOverride == null ? "" : String(amountOf(item.rateOverride) ?? ""));
  const [details, setDetails] = useState<ClassDetails>(detailsOf(item));
  const [errors, setErrors] = useState<Record<string, string>>({});

  async function submit() {
    const problems = classDetailProblems(details, t);
    const trimmedOverride = override.trim();
    if (trimmedOverride !== "" && (!Number.isFinite(Number(trimmedOverride)) || Number(trimmedOverride) < 0)) {
      problems.override = t("batchPay.classes.edit.errors.override");
    }
    setErrors(problems);
    if (Object.keys(problems).length > 0) {
      return;
    }
    const body: ClassEditBody = {
      version: item.version,
      // A class with a result keeps its date (results rule A1): the date is not sent, so the API does not see a change.
      ...(item.hasResult ? {} : { heldOn: details.heldOn }),
      startsAt: details.startsAt,
      endsAt: details.endsAt,
      subject: details.subject.trim(),
      topic: details.topic.trim(),
      rateOverride: trimmedOverride === "" ? null : trimmedOverride,
    };
    try {
      await edit.mutateAsync({ id: item.id, body });
      toast({ title: t("batchPay.classes.edit.saved"), tone: "ok" });
      onOpenChange(false);
    } catch (failure) {
      setErrors({ form: offlineErrorText(failure, t) });
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={t("batchPay.classes.edit.title")}
      className="sm:max-w-lg"
      footer={
        <>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            {t("common.actions.cancel")}
          </Button>
          <Button onClick={() => void submit()} loading={edit.isPending}>
            {t("batchPay.classes.edit.submit")}
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
        <Field label={t("batchPay.classes.edit.override")} hint={t("batchPay.classes.edit.overrideHint")} error={errors.override}>
          <TextInput inputMode="decimal" value={override} invalid={Boolean(errors.override)} onChange={(event) => setOverride(event.target.value)} />
        </Field>
        <ClassDetailsFields
          value={details}
          errors={errors}
          dateLocked={item.hasResult === true}
          onChange={(patch) => setDetails((current) => ({ ...current, ...patch }))}
        />
      </div>
    </Dialog>
  );
}

/** The date, the start and end, the subject and the topic of a class: the fields the Add and Edit class dialogs share. */
export type ClassDetails = { heldOn: string; startsAt: string; endsAt: string; subject: string; topic: string };

/** The shared fields of a class as they are in the batch now. Times are HH:mm. */
export function detailsOf(item: BatchClass): ClassDetails {
  return {
    heldOn: item.heldOn,
    startsAt: (item.startsAt ?? "").slice(0, 5),
    endsAt: (item.endsAt ?? "").slice(0, 5),
    subject: item.subject,
    topic: item.topic ?? "",
  };
}

/** The checks both dialogs run on the shared fields: a subject, and a start and an end time. */
export function classDetailProblems(details: ClassDetails, t: (key: string) => string): Record<string, string> {
  const problems: Record<string, string> = {};
  if (!details.heldOn) {
    problems.heldOn = t("batchPay.classes.edit.errors.date");
  }
  if (!details.subject.trim()) {
    problems.subject = t("batchPay.classes.edit.errors.subject");
  }
  if (!details.startsAt || !details.endsAt) {
    problems.time = t("batchPay.classes.edit.errors.time");
  }
  return problems;
}

/**
 * The date, the start and end, the subject and the topic. The date has a row of its own; start and end share the next row, wide enough
 * for the time with its AM or PM. The errors are the keys of `classDetailProblems`.
 */
export function ClassDetailsFields({
  value,
  errors,
  dateLocked = false,
  onChange,
}: {
  value: ClassDetails;
  errors: Record<string, string>;
  /** An exam with a result: its date is read-only, with the reason under it (results rule A1). */
  dateLocked?: boolean;
  onChange: (patch: Partial<ClassDetails>) => void;
}) {
  const { t } = useLocale();
  return (
    <>
      <div className="grid min-w-0 gap-3">
        <div className="min-w-0">
          {dateLocked ? (
            <Field label={t("batchPay.classes.edit.date")} hint={t("batchPay.classes.edit.dateLocked")}>
              <TextInput type="date" className="w-full min-w-0" value={value.heldOn} readOnly />
            </Field>
          ) : (
            <Field label={t("batchPay.classes.edit.date")} error={errors.heldOn}>
              <TextInput
                type="date"
                className="w-full min-w-0"
                value={value.heldOn}
                invalid={Boolean(errors.heldOn)}
                onChange={(event) => onChange({ heldOn: event.target.value })}
              />
            </Field>
          )}
        </div>
        <div className="grid min-w-0 gap-3 sm:grid-cols-2">
          <div className="min-w-0">
            <Field label={t("batchPay.classes.edit.start")} error={errors.time}>
              <TextInput
                type="time"
                className="w-full min-w-0"
                value={value.startsAt}
                invalid={Boolean(errors.time)}
                onChange={(event) => onChange({ startsAt: event.target.value })}
              />
            </Field>
          </div>
          <div className="min-w-0">
            <Field label={t("batchPay.classes.edit.end")}>
              <TextInput
                type="time"
                className="w-full min-w-0"
                value={value.endsAt}
                invalid={Boolean(errors.time)}
                onChange={(event) => onChange({ endsAt: event.target.value })}
              />
            </Field>
          </div>
        </div>
      </div>
      <Field label={t("batchPay.classes.edit.subject")} error={errors.subject} required>
        <TextInput value={value.subject} invalid={Boolean(errors.subject)} onChange={(event) => onChange({ subject: event.target.value })} />
      </Field>
      <Field label={t("batchPay.classes.edit.topic")}>
        <TextInput value={value.topic} onChange={(event) => onChange({ topic: event.target.value })} />
      </Field>
    </>
  );
}

/**
 * Records what the centre paid the class's instructor (instructor pay, not a student fee). It names the instructor and the class, and is
 * prefilled with the due amount and today's Dhaka date. A class with no amount (no rate for its instructor in this batch) gets one here
 * first: the instructor's rate for the batch, or, ticked, a rate for this class only. A different amount needs a note. A payment by staff
 * waits for the Master Admin's approval (plan F9).
 */
export function ClassPayDialog({ item, open, onOpenChange }: { item: BatchClass; open: boolean; onOpenChange: (open: boolean) => void }) {
  const { t } = useLocale();
  const { formatMoney, formatDate, formatTimeRange } = useFormat();
  const { toast } = useToast();
  const pay = useRecordClassPayment();
  const setRate = useSetRate(item.batchId);
  const editClass = useEditClass();
  // The due amount, kept here so a rate set in this dialog fills it without reopening.
  const [due, setDue] = useState(amountOf(item.amount));
  const today = dhakaToday();
  const [amount, setAmount] = useState(due === null ? "" : String(due));
  const [rate, setRateValue] = useState("");
  const [onlyThis, setOnlyThis] = useState(false);
  const [rateError, setRateError] = useState<string | null>(null);
  const name = item.instructorName ?? item.instructorText ?? "";
  const time = item.startsAt
    ? formatTimeRange(dhakaInstant(item.heldOn, item.startsAt), dhakaInstant(item.heldOn, item.endsAt ?? item.startsAt))
    : "";

  async function applyRate() {
    const value = rate.trim();
    if (value === "" || !Number.isFinite(Number(value)) || Number(value) <= 0) {
      setRateError(t("batchPay.classes.pay.rateInvalid"));
      return;
    }
    setRateError(null);
    try {
      if (onlyThis) {
        await editClass.mutateAsync({ id: item.id, body: { version: item.version, rateOverride: value } });
      } else if (item.instructorId) {
        await setRate.mutateAsync({ instructorId: item.instructorId, rate: value });
      }
      setDue(Number(value));
      setAmount(value);
      toast({ title: onlyThis ? t("batchPay.classes.pay.rateSetClass") : t("batchPay.classes.pay.rateSetBatch", { name }), tone: "ok" });
    } catch (failure) {
      setRateError(offlineErrorText(failure, t));
    }
  }
  const [method, setMethod] = useState<PayMethod>("CASH");
  const [paidOn, setPaidOn] = useState(today);
  const [reference, setReference] = useState("");
  const [note, setNote] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});

  const paid = Number(amount);
  const difference = due === null || amount.trim() === "" || !Number.isFinite(paid) ? 0 : Math.round((paid - due) * 100) / 100;
  const blocked = due === null || !item.instructorId;

  async function submit() {
    const problems: Record<string, string> = {};
    if (!amount.trim() || !Number.isFinite(paid) || paid <= 0) {
      problems.amount = t("batchPay.classes.pay.errors.amount");
    }
    if (!paidOn) {
      problems.paidOn = t("batchPay.classes.pay.errors.paidOn");
    } else if (paidOn > today) {
      problems.paidOn = t("batchPay.classes.pay.errors.future");
    }
    if (difference !== 0 && !note.trim()) {
      problems.note = t("batchPay.classes.pay.noteRequired");
    }
    setErrors(problems);
    if (Object.keys(problems).length > 0) {
      return;
    }
    try {
      await pay.mutateAsync({
        id: item.id,
        body: {
          amount: amount.trim(),
          method,
          paidOn,
          reference: reference.trim() || undefined,
          note: note.trim() || undefined,
        },
      });
      toast({ title: t("batchPay.classes.pay.recorded"), tone: "ok" });
      onOpenChange(false);
    } catch (failure) {
      setErrors({ form: offlineErrorText(failure, t) });
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={name ? t("batchPay.classes.pay.titleFor", { name }) : t("batchPay.classes.pay.title")}
      footer={
        <>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            {t("common.actions.cancel")}
          </Button>
          <Button onClick={() => void submit()} disabled={blocked} loading={pay.isPending}>
            {t("batchPay.classes.pay.submit")}
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
        <div className="grid gap-0.5 rounded-control bg-bg px-3 py-2.5">
          <span className="text-meta text-muted">{t("batchPay.classes.pay.forClass")}</span>
          <span className="break-words font-semibold text-text">
            {[formatDate(item.heldOn), time, item.subject].filter(Boolean).join(" · ")}
          </span>
          {due !== null ? (
            <span className="text-body text-text">{t("batchPay.classes.pay.due", { amount: formatMoney(due) ?? "" })}</span>
          ) : null}
        </div>
        {!item.instructorId ? <p className="text-meta text-warn">{t("batchPay.classes.pay.noInstructor")}</p> : null}
        {due === null && item.instructorId ? (
          <div className="grid gap-3 rounded-control border border-warn/50 bg-warn/5 p-3">
            <p className="text-meta font-semibold text-text">{t("batchPay.classes.pay.noRate", { name })}</p>
            <div className="flex flex-wrap items-end gap-2">
              <label className="grid gap-1.5 text-meta text-muted">
                {t("batchPay.classes.pay.rateLabel")}
                <TextInput
                  inputMode="decimal"
                  className="max-w-40"
                  value={rate}
                  invalid={Boolean(rateError)}
                  onChange={(event) => setRateValue(event.target.value)}
                />
              </label>
              <Button size="sm" onClick={() => void applyRate()} loading={setRate.isPending || editClass.isPending}>
                {t("batchPay.classes.pay.useRate")}
              </Button>
            </div>
            <label className="flex items-start gap-2 text-meta text-text">
              <input type="checkbox" className="mt-0.5 size-4 accent-[var(--accent)]" checked={onlyThis} onChange={(event) => setOnlyThis(event.target.checked)} />
              <span>
                {t("batchPay.classes.pay.onlyThis")}
                <span className="block text-muted">
                  {onlyThis ? t("batchPay.classes.pay.onlyThisHint") : t("batchPay.classes.pay.batchRateHint", { name })}
                </span>
              </span>
            </label>
            {rateError ? (
              <p role="alert" className="text-meta text-danger">
                {rateError}
              </p>
            ) : null}
          </div>
        ) : null}
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label={t("batchPay.classes.pay.amount")} error={errors.amount} required>
            <TextInput inputMode="decimal" value={amount} invalid={Boolean(errors.amount)} onChange={(event) => setAmount(event.target.value)} />
          </Field>
          <Field label={t("batchPay.classes.pay.method")} required>
            <Select value={method} onChange={(event) => setMethod(event.target.value as PayMethod)}>
              {METHODS.map((value) => (
                <option key={value} value={value}>
                  {t(methodLabelKey(value))}
                </option>
              ))}
            </Select>
          </Field>
          <Field label={t("batchPay.classes.pay.paidOn")} error={errors.paidOn} required>
            <TextInput type="date" value={paidOn} invalid={Boolean(errors.paidOn)} onChange={(event) => setPaidOn(event.target.value)} />
          </Field>
          <Field label={t("batchPay.classes.pay.reference")} hint={t("batchPay.classes.pay.referenceHint")}>
            <TextInput value={reference} onChange={(event) => setReference(event.target.value)} />
          </Field>
        </div>
        {difference !== 0 && due !== null ? (
          <p className="text-meta text-warn">
            {difference > 0
              ? t("batchPay.classes.pay.moreThanDue", { amount: formatMoney(difference) ?? "" })
              : t("batchPay.classes.pay.lessThanDue", { amount: formatMoney(Math.abs(difference)) ?? "" })}
          </p>
        ) : null}
        <Field label={t("batchPay.classes.pay.note")} error={errors.note} required={difference !== 0}>
          <TextInput value={note} invalid={Boolean(errors.note)} onChange={(event) => setNote(event.target.value)} />
        </Field>
      </div>
    </Dialog>
  );
}

/** Marks a class not taken. The reason is asked for; the one-tap choices fill it in and it can still be changed. */
export function ClassNotTakenDialog({ item, open, onOpenChange }: { item: BatchClass; open: boolean; onOpenChange: (open: boolean) => void }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const mark = useMarkNotTaken();
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  async function submit() {
    if (!reason.trim()) {
      setError(t("batchPay.classes.notTaken.errors.reason"));
      return;
    }
    setError(null);
    try {
      await mark.mutateAsync({ id: item.id, reason: reason.trim() });
      toast({ title: t("batchPay.classes.notTaken.sent"), tone: "ok" });
      onOpenChange(false);
    } catch (failure) {
      setFormError(offlineErrorText(failure, t));
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={t("batchPay.classes.notTaken.title")}
      description={t("batchPay.classes.notTaken.body")}
      footer={
        <>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            {t("common.actions.cancel")}
          </Button>
          <Button variant="danger" onClick={() => void submit()} loading={mark.isPending}>
            {t("batchPay.classes.notTaken.submit")}
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        {formError ? (
          <p role="alert" className="text-meta text-danger">
            {formError}
          </p>
        ) : null}
        <div className="grid gap-2">
          <span className="text-label font-medium text-text">{t("batchPay.classes.notTaken.quick")}</span>
          <div className="flex flex-wrap gap-2">
            {REASON_KEYS.map((key) => {
              const label = t(key);
              const pressed = reason === label;
              return (
                <Button key={key} size="sm" variant={pressed ? "primary" : "secondary"} aria-pressed={pressed} onClick={() => setReason(label)}>
                  {label}
                </Button>
              );
            })}
          </div>
        </div>
        <Field label={t("batchPay.classes.notTaken.reason")} error={error ?? undefined} required>
          <TextInput value={reason} invalid={Boolean(error)} onChange={(event) => setReason(event.target.value)} />
        </Field>
      </div>
    </Dialog>
  );
}
