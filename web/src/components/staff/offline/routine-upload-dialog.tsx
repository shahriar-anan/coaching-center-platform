"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
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
    setErrors(problems);
    if (Object.keys(problems).length > 0 || !file) {
      return;
    }
    try {
      const view = await upload.mutateAsync({ file });
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
        <p className="text-meta text-muted">{replacing ? t("batchPay.upload.replaceNote") : t("batchPay.upload.firstNote")}</p>
      </div>
    </Dialog>
  );
}
