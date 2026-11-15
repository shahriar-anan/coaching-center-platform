"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { Select } from "@/components/ui/select";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import {
  useAddClass,
  useBatchRates,
  useInstructors,
  type ClassCreateBody,
  type RoutineKind,
} from "@/lib/api/exam/offline-batch-pay";
import { ClassDetailsFields, classDetailProblems, type ClassDetails } from "./class-dialogs";
import { kindKey } from "./batch-pay-format";
import { offlineErrorText } from "./offline-error";
import { dhakaToday } from "./offline-model";

const KINDS: RoutineKind[] = ["LECTURE", "EXAM", "OTHER"];

/** The time `minutes` after a start time (HH:mm), so the end of a class added by hand follows its start. Empty when there is no start. */
export function timePlus(startsAt: string, minutes: number): string {
  const match = /^(\d{1,2}):(\d{2})/.exec(startsAt);
  if (!match) {
    return "";
  }
  const total = (Number(match[1]) * 60 + Number(match[2]) + minutes) % (24 * 60);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${pad(Math.floor(total / 60))}:${pad(total % 60)}`;
}

/**
 * Adds a class by hand to a batch that is not archived (plan 7.8). It has the fields of the Edit class dialog, and the kind and the
 * instructor. The end follows the start by the batch's class length until it is changed. The API checks the rest.
 */
export function AddClassDialog({
  batchId,
  classMinutes,
  open,
  onOpenChange,
  onAdded,
}: {
  batchId: string;
  classMinutes: number;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Called with the date of the new class, once it is saved. */
  onAdded: (heldOn: string) => void;
}) {
  const { t } = useLocale();
  const { toast } = useToast();
  const add = useAddClass(batchId);
  const instructors = useInstructors(open);
  const rates = useBatchRates(batchId);
  const [kind, setKind] = useState<RoutineKind>("LECTURE");
  const [instructorId, setInstructorId] = useState("");
  const [details, setDetails] = useState<ClassDetails>({ heldOn: dhakaToday(), startsAt: "", endsAt: "", subject: "", topic: "" });
  const [endTouched, setEndTouched] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});

  // The batch's own instructors (the ones with a rate in this batch) come first; the other active instructors follow.
  const batchInstructors = (rates.data ?? []).map((rate) => ({ id: rate.instructorId, name: rate.name }));
  const batchIds = new Set(batchInstructors.map((instructor) => instructor.id));
  const otherInstructors = (instructors.data ?? []).filter((instructor) => !batchIds.has(instructor.id));

  function change(patch: Partial<ClassDetails>) {
    if (patch.endsAt !== undefined) {
      setEndTouched(true);
    }
    setDetails((current) => {
      const next = { ...current, ...patch };
      if (patch.startsAt !== undefined && !endTouched) {
        next.endsAt = timePlus(patch.startsAt, classMinutes);
      }
      return next;
    });
  }

  async function submit() {
    const problems = classDetailProblems(details, t);
    setErrors(problems);
    if (Object.keys(problems).length > 0) {
      return;
    }
    const body: ClassCreateBody = {
      heldOn: details.heldOn,
      startsAt: details.startsAt,
      endsAt: details.endsAt,
      subject: details.subject.trim(),
      topic: details.topic.trim() || undefined,
      kind,
      instructorId: instructorId || undefined,
    };
    try {
      await add.mutateAsync(body);
      toast({ title: t("batchPay.classes.add.saved"), tone: "ok" });
      onAdded(details.heldOn);
      onOpenChange(false);
    } catch (failure) {
      setErrors({ form: offlineErrorText(failure, t) });
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={t("batchPay.classes.add.title")}
      className="sm:max-w-lg"
      footer={
        <>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            {t("common.actions.cancel")}
          </Button>
          <Button onClick={() => void submit()} loading={add.isPending}>
            {t("batchPay.classes.add.submit")}
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
        <Field label={t("batchPay.preview.columns.kind")}>
          <Select value={kind} onChange={(event) => setKind(event.target.value as RoutineKind)}>
            {KINDS.map((value) => (
              <option key={value} value={value}>
                {t(kindKey(value))}
              </option>
            ))}
          </Select>
        </Field>
        <ClassDetailsFields value={details} errors={errors} onChange={change} />
        <Field label={t("batchPay.classes.edit.instructor")}>
          <Select value={instructorId} onChange={(event) => setInstructorId(event.target.value)}>
            <option value="">{t("batchPay.classes.edit.noInstructor")}</option>
            {batchInstructors.length > 0 ? (
              <optgroup label={t("batchPay.classes.add.batchInstructors")}>
                {batchInstructors.map((instructor) => (
                  <option key={instructor.id} value={instructor.id}>
                    {instructor.name}
                  </option>
                ))}
              </optgroup>
            ) : null}
            {otherInstructors.length > 0 ? (
              <optgroup label={t("batchPay.classes.add.otherInstructors")}>
                {otherInstructors.map((instructor) => (
                  <option key={instructor.id} value={instructor.id}>
                    {instructor.name}
                  </option>
                ))}
              </optgroup>
            ) : null}
          </Select>
        </Field>
      </div>
    </Dialog>
  );
}
