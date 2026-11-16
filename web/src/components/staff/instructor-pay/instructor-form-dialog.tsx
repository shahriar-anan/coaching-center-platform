"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { Textarea } from "@/components/ui/textarea";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { ApiError } from "@/lib/api/errors";
import { offlineErrorText } from "@/components/staff/offline/offline-error";
import { useSaveInstructor, type OfflineInstructor } from "@/lib/api/exam/instructor-pay";

/**
 * Adds an offline instructor, or edits one. An instructor is a name kept for the record: they have no account and never log in (plan
 * F5). Editing sends the version the screen read, so an older screen cannot overwrite a newer edit. Aliases are the other names the
 * routine uses for the same person (G4).
 */
export function InstructorFormDialog({
  instructor,
  open,
  onOpenChange,
  onSaved,
}: {
  /** The instructor being edited, or null to add one. */
  instructor: OfflineInstructor | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Called with the saved instructor, so a page can open a new one (17 Nov). */
  onSaved?: (saved: OfflineInstructor) => void;
}) {
  const { t } = useLocale();
  const { toast } = useToast();
  const save = useSaveInstructor();
  const [name, setName] = useState(instructor?.name ?? "");
  const [phone, setPhone] = useState(instructor?.phone ?? "");
  const [note, setNote] = useState(instructor?.note ?? "");
  const [aliases, setAliases] = useState((instructor?.aliases ?? []).join("\n"));
  const [errors, setErrors] = useState<Record<string, string>>({});

  async function submit() {
    const problems: Record<string, string> = {};
    if (name.trim() === "") {
      problems.name = t("instructorPay.instructors.errors.name");
    }
    setErrors(problems);
    if (Object.keys(problems).length > 0) {
      return;
    }
    const body = instructor
      ? {
          name: name.trim(),
          phone: phone.trim() || null,
          note: note.trim() || null,
          aliases: aliases
            .split("\n")
            .map((line) => line.trim())
            .filter(Boolean),
          version: instructor.version,
        }
      : { name: name.trim(), phone: phone.trim() || null, note: note.trim() || null };
    try {
      const saved = await save.mutateAsync({ id: instructor?.id, body });
      toast({ title: t("instructorPay.instructors.saved"), tone: "ok" });
      onOpenChange(false);
      onSaved?.(saved);
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
      title={instructor ? t("instructorPay.instructors.editTitle") : t("instructorPay.instructors.addTitle")}
      description={t("instructorPay.instructors.formNote")}
      footer={
        <>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            {t("common.actions.cancel")}
          </Button>
          <Button onClick={() => void submit()} loading={save.isPending}>
            {t("instructorPay.instructors.save")}
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
        <Field label={t("instructorPay.instructors.name")} error={errors.name ?? errors.instructorName} required>
          <TextInput value={name} onChange={(event) => setName(event.target.value)} invalid={Boolean(errors.name)} />
        </Field>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label={t("instructorPay.instructors.phone")} hint={t("instructorPay.instructors.phoneHint")} error={errors.phone}>
            <TextInput inputMode="tel" value={phone} onChange={(event) => setPhone(event.target.value)} />
          </Field>
          <Field label={t("instructorPay.instructors.note")} error={errors.note}>
            <TextInput value={note} onChange={(event) => setNote(event.target.value)} />
          </Field>
        </div>
        {instructor ? (
          <Field label={t("instructorPay.instructors.aliases")} hint={t("instructorPay.instructors.aliasesHint")} error={errors.aliases}>
            <Textarea rows={3} value={aliases} onChange={(event) => setAliases(event.target.value)} />
          </Field>
        ) : null}
      </div>
    </Dialog>
  );
}
