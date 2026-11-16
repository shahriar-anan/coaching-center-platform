"use client";

import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { Select } from "@/components/ui/select";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { ApiError } from "@/lib/api/errors";
import { useBatches } from "@/lib/api/exam/offline-admin";
import { useAddClass, useBatchClasses, type RoutineKind } from "@/lib/api/exam/offline-batch-pay";
import { useFormat } from "@/lib/format/use-format";
import { timePlus } from "@/components/staff/offline/add-class-dialog";
import { dhakaToday } from "@/components/staff/offline/offline-model";
import { offlineErrorText } from "@/components/staff/offline/offline-error";

const KINDS: RoutineKind[] = ["LECTURE", "EXAM", "OTHER"];

/** Minutes since midnight of an HH:mm(:ss) time, or null. */
function minutesOf(time: string | null | undefined): number | null {
  const match = /^(\d{1,2}):(\d{2})/.exec(time ?? "");
  return match ? Number(match[1]) * 60 + Number(match[2]) : null;
}

/**
 * A class this instructor took, added from their page (17 Nov), for one that is not in the routine or before the routine is updated.
 * The admin says whether it goes into the batch's routine too. Kept out, it is only a row of the instructor's pay: students never see
 * it and a routine upload never touches it. Put in, it is a normal class of the batch; when the batch already has a class at that time
 * on that day, the dialog names it and asks whether this one replaces it (an exchange: that class is cancelled) or both are kept.
 */
export function InstructorAddClassDialog({
  instructorId,
  open,
  onOpenChange,
}: {
  instructorId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t, locale } = useLocale();
  const { formatTime } = useFormat();
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const batches = useBatches("");
  const active = (batches.data ?? []).filter((batch) => batch.status !== "ARCHIVED");
  const [batchId, setBatchId] = useState("");
  const batch = active.find((item) => item.id === batchId);
  const add = useAddClass(batchId);
  const [heldOn, setHeldOn] = useState(dhakaToday());
  const [startsAt, setStartsAt] = useState("");
  const [endsAt, setEndsAt] = useState("");
  const [endTyped, setEndTyped] = useState(false);
  const [subject, setSubject] = useState("");
  const [topic, setTopic] = useState("");
  const [kind, setKind] = useState<RoutineKind>("LECTURE");
  const [inRoutine, setInRoutine] = useState<"" | "yes" | "no">("");
  const [replaceId, setReplaceId] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const month = heldOn.slice(0, 7);
  const dayClasses = useBatchClasses(batchId, { month: month || undefined });

  // The batch's classes that day whose time overlaps this one: the ones this class could replace.
  const classMinutes = batch?.defaultClassMinutes ?? 60;
  const start = minutesOf(startsAt);
  const end = minutesOf(endsAt) ?? (start === null ? null : start + classMinutes);
  const clashes =
    !batchId || start === null || end === null
      ? []
      : (dayClasses.data ?? []).filter((item) => {
          if (item.heldOn !== heldOn || item.status === "CANCELLED" || item.inRoutine === false) return false;
          const itemStart = minutesOf(item.startsAt);
          if (itemStart === null) return false;
          const itemEnd = minutesOf(item.endsAt) ?? itemStart + classMinutes;
          return itemStart < end && start < itemEnd;
        });

  function close() {
    setErrors({});
    onOpenChange(false);
  }

  async function submit() {
    const problems: Record<string, string> = {};
    if (!batchId) problems.batchId = t("instructorPay.addClass.errors.batch");
    if (!heldOn) problems.heldOn = t("instructorPay.addClass.errors.date");
    if (!startsAt) problems.startsAt = t("instructorPay.addClass.errors.start");
    if (!subject.trim()) problems.subject = t("instructorPay.addClass.errors.subject");
    if (!inRoutine) problems.inRoutine = t("instructorPay.addClass.errors.routine");
    setErrors(problems);
    if (Object.keys(problems).length > 0) return;
    try {
      await add.mutateAsync({
        heldOn,
        startsAt,
        endsAt: endsAt || undefined,
        subject: subject.trim(),
        topic: topic.trim() || undefined,
        kind,
        instructorId,
        inRoutine: inRoutine === "yes",
        replaceClassId: inRoutine === "yes" && replaceId ? replaceId : undefined,
      });
      await queryClient.invalidateQueries();
      toast({ title: t(inRoutine === "yes" ? "instructorPay.addClass.addedRoutine" : "instructorPay.addClass.addedPayOnly"), tone: "ok" });
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
      title={t("instructorPay.addClass.title")}
      description={t("instructorPay.addClass.body")}
      footer={
        <>
          <Button variant="ghost" onClick={close}>
            {t("common.actions.cancel")}
          </Button>
          <Button onClick={() => void submit()} loading={add.isPending}>
            {t("instructorPay.addClass.submit")}
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
        <Field label={t("instructorPay.addClass.batch")} error={errors.batchId} required>
          <Select value={batchId} onChange={(event) => setBatchId(event.target.value)} invalid={Boolean(errors.batchId)}>
            <option value="">{t("instructorPay.addClass.chooseBatch")}</option>
            {active.map((item) => (
              <option key={item.id} value={item.id}>
                {locale === "bn" ? item.nameBn || item.nameEn : item.nameEn}
              </option>
            ))}
          </Select>
        </Field>
        <Field label={t("instructorPay.addClass.date")} error={errors.heldOn} required>
          <TextInput type="date" value={heldOn} onChange={(event) => setHeldOn(event.target.value)} invalid={Boolean(errors.heldOn)} />
        </Field>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label={t("instructorPay.addClass.start")} error={errors.startsAt} required>
            <TextInput
              type="time"
              value={startsAt}
              onChange={(event) => {
                setStartsAt(event.target.value);
                if (!endTyped) setEndsAt(timePlus(event.target.value, batch?.defaultClassMinutes ?? 60));
              }}
              invalid={Boolean(errors.startsAt)}
            />
          </Field>
          <Field label={t("instructorPay.addClass.end")}>
            <TextInput
              type="time"
              value={endsAt}
              onChange={(event) => {
                setEndsAt(event.target.value);
                setEndTyped(true);
              }}
            />
          </Field>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label={t("instructorPay.addClass.subject")} error={errors.subject} required>
            <TextInput value={subject} onChange={(event) => setSubject(event.target.value)} invalid={Boolean(errors.subject)} />
          </Field>
          <Field label={t("instructorPay.addClass.kind")}>
            <Select value={kind} onChange={(event) => setKind(event.target.value as RoutineKind)}>
              {KINDS.map((value) => (
                <option key={value} value={value}>
                  {t(`instructorPay.addClass.kinds.${value}`)}
                </option>
              ))}
            </Select>
          </Field>
        </div>
        <Field label={t("instructorPay.addClass.topic")}>
          <TextInput value={topic} onChange={(event) => setTopic(event.target.value)} />
        </Field>

        <fieldset className="grid gap-2 rounded-control border border-border-control p-3">
          <legend className="px-1 text-label font-medium text-text">{t("instructorPay.addClass.routineQuestion")}</legend>
          <label className="flex min-h-11 cursor-pointer items-start gap-2 text-body text-text">
            <input type="radio" name="in-routine" className="mt-1 size-4 accent-accent" checked={inRoutine === "yes"} onChange={() => setInRoutine("yes")} />
            <span>{t("instructorPay.addClass.routineYes")}</span>
          </label>
          <label className="flex min-h-11 cursor-pointer items-start gap-2 text-body text-text">
            <input
              type="radio"
              name="in-routine"
              className="mt-1 size-4 accent-accent"
              checked={inRoutine === "no"}
              onChange={() => {
                setInRoutine("no");
                setReplaceId("");
              }}
            />
            <span>{t("instructorPay.addClass.routineNo")}</span>
          </label>
          {errors.inRoutine ? <p className="text-meta text-danger">{errors.inRoutine}</p> : null}
        </fieldset>

        {inRoutine === "yes" && clashes.length > 0 ? (
          <div role="status" className="grid gap-2 rounded-control border border-warn/50 bg-warn/5 p-3">
            <p className="text-body font-medium text-text">{t("instructorPay.addClass.clashTitle")}</p>
            <ul className="grid gap-1 text-body text-text">
              {clashes.map((item) => (
                <li key={item.id}>
                  {item.subject}
                  {item.topic ? ` · ${item.topic}` : ""}
                  {item.startsAt ? ` · ${formatTime(`${item.heldOn}T${item.startsAt}+06:00`)}` : ""}
                  {item.instructorName ? ` · ${item.instructorName}` : ""}
                </li>
              ))}
            </ul>
            <label className="flex min-h-11 cursor-pointer items-start gap-2 text-body text-text">
              <input type="radio" name="clash" className="mt-1 size-4 accent-accent" checked={replaceId === ""} onChange={() => setReplaceId("")} />
              <span>{t("instructorPay.addClass.keepBoth")}</span>
            </label>
            {clashes.map((item) => (
              <label key={item.id} className="flex min-h-11 cursor-pointer items-start gap-2 text-body text-text">
                <input type="radio" name="clash" className="mt-1 size-4 accent-accent" checked={replaceId === item.id} onChange={() => setReplaceId(item.id)} />
                <span>{t("instructorPay.addClass.replace", { subject: item.subject })}</span>
              </label>
            ))}
            {errors.replaceClassId ? <p className="text-meta text-danger">{errors.replaceClassId}</p> : null}
          </div>
        ) : null}
        {inRoutine === "no" ? <p className="text-meta text-muted">{t("instructorPay.addClass.payOnlyNote")}</p> : null}
      </div>
    </Dialog>
  );
}
