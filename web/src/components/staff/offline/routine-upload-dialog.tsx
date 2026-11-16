"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { routineFileProblem, useUploadRoutine } from "@/lib/api/exam/offline-batch-pay";
import { offlineErrorText } from "./offline-error";

/**
 * Uploads a routine (plan 8.1, changed on 13 Nov 2026). Only the file is asked: the routine counts from today. Its earlier dates still
 * become classes, and a new routine replaces the classes from today onward only. A file that is not a PDF or is over 20 MB is refused here,
 * before the upload. On success the preview opens: nothing is saved to the classes until it is confirmed.
 */
export function RoutineUploadDialog({
  batchId,
  open,
  onOpenChange,
  replacing,
}: {
  batchId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** True when the batch already has a routine, so the dialog says the new one replaces it from today. */
  replacing: boolean;
}) {
  const { t } = useLocale();
  const router = useRouter();
  const { toast } = useToast();
  const upload = useUploadRoutine(batchId);
  const [file, setFile] = useState<File | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  // 17 Nov: an exam batch's routine (Exam, Date, Exam Topics, Time) has no times, so the exam time is asked here.
  const [examRoutine, setExamRoutine] = useState(false);
  const [examStartsAt, setExamStartsAt] = useState("16:00");
  const [examMinutes, setExamMinutes] = useState("120");

  async function submit() {
    const problems: Record<string, string> = {};
    if (!file) {
      problems.file = t("batchPay.upload.errors.file");
    } else {
      const problem = await routineFileProblem(file);
      if (problem === "notPdf") {
        problems.file = t("batchPay.upload.errors.notPdf");
      } else if (problem === "tooLarge") {
        problems.file = t("batchPay.upload.errors.tooLarge");
      }
    }
    const minutes = Number(examMinutes);
    if (examRoutine && !/^\d{2}:\d{2}$/.test(examStartsAt)) {
      problems.examStartsAt = t("batchPay.upload.errors.examTime");
    }
    if (examRoutine && !(Number.isInteger(minutes) && minutes >= 10 && minutes <= 600)) {
      problems.examMinutes = t("batchPay.upload.errors.examMinutes");
    }
    setErrors(problems);
    if (Object.keys(problems).length > 0 || !file) {
      return;
    }
    try {
      const view = await upload.mutateAsync(examRoutine ? { file, examStartsAt, examMinutes: minutes } : { file });
      toast({ title: t("batchPay.upload.uploaded"), tone: "ok" });
      onOpenChange(false);
      router.push(`/staff/offline-batches/${batchId}/routines/${view.id}`);
    } catch (error) {
      setErrors({ form: offlineErrorText(error, t) });
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={replacing ? t("batchPay.upload.titleNew") : t("batchPay.upload.title")}
      footer={
        <>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            {t("common.actions.cancel")}
          </Button>
          <Button onClick={() => void submit()} loading={upload.isPending}>
            {t("batchPay.upload.submit")}
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
        <Field label={t("batchPay.upload.file")} hint={t("batchPay.upload.fileHint")} error={errors.file} required>
          <input
            type="file"
            accept="application/pdf,.pdf"
            className="block w-full min-w-0 text-body text-text file:mr-3 file:rounded-control file:border file:border-border-control file:bg-card file:px-3 file:py-2 file:text-label"
            onChange={(event) => setFile(event.target.files?.[0] ?? null)}
          />
        </Field>
        <label className="inline-flex min-h-11 cursor-pointer items-center gap-2 text-body text-text">
          <input type="checkbox" className="size-5 accent-accent" checked={examRoutine} onChange={(event) => setExamRoutine(event.target.checked)} />
          {t("batchPay.upload.examRoutine")}
        </label>
        {examRoutine ? (
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label={t("batchPay.upload.examStartsAt")} error={errors.examStartsAt} required>
              <TextInput type="time" value={examStartsAt} onChange={(event) => setExamStartsAt(event.target.value)} invalid={Boolean(errors.examStartsAt)} />
            </Field>
            <Field label={t("batchPay.upload.examMinutes")} error={errors.examMinutes} required>
              <TextInput inputMode="numeric" value={examMinutes} onChange={(event) => setExamMinutes(event.target.value)} invalid={Boolean(errors.examMinutes)} />
            </Field>
            <p className="text-meta text-muted sm:col-span-2">{t("batchPay.upload.examNote")}</p>
          </div>
        ) : null}
        <p className="text-meta text-muted">{replacing ? t("batchPay.upload.replaceNote") : t("batchPay.upload.firstNote")}</p>
      </div>
    </Dialog>
  );
}
