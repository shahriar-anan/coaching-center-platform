"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useEditClass, type BatchClass, type ClassEditBody } from "@/lib/api/exam/offline-batch-pay";
import { offlineErrorText } from "./offline-error";

/**
 * Edits a class: the date, the times, the subject and topic. The instructor is changed from the class's own Change button (the instructor
 * dialog), so it is not here. A class carries no pay since 24 Nov.
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
  const [details, setDetails] = useState<ClassDetails>(detailsOf(item));
  const [errors, setErrors] = useState<Record<string, string>>({});

  async function submit() {
    const problems = classDetailProblems(details, t);
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
