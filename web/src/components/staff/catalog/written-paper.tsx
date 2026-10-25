"use client";

import { useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { apiBlob } from "@/lib/api/client";
import { useUploadQuestionPdf, type StaffExam } from "@/lib/api/exam/staff-catalog";
import { PdfViewer } from "@/components/pdf/pdf-viewer";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { FileDrop } from "@/components/ui/file-drop";
import { useToast } from "@/components/ui/toast";
import { catalogErrorKey } from "@/components/staff/catalog/errors";

const MAX_PDF_BYTES = 20 * 1024 * 1024;

/**
 * The one question PDF of a written exam. Staff may open the stored file to
 * check it; the student never downloads it. Replacing uploads a new file.
 */
export function WrittenPaper({ exam, locked }: { exam: StaffExam; locked: boolean }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const upload = useUploadQuestionPdf(exam.id);
  const [viewing, setViewing] = useState(false);
  const [progress, setProgress] = useState<number | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);

  async function onFile(file: File) {
    setError(null);
    setProgress(0);
    try {
      await upload.mutateAsync({ file, onProgress: setProgress });
      toast({ title: t("staffCatalog.written.uploaded"), tone: "ok" });
    } catch (e) {
      setError(t(catalogErrorKey(e)));
    } finally {
      setProgress(undefined);
    }
  }

  return (
    <section id="exam-question-pdf" aria-labelledby="written-paper-heading" className="grid max-w-3xl gap-4">
      <h2 id="written-paper-heading" className="text-base font-semibold">
        {t("staffCatalog.written.heading")}
      </h2>

      {exam.questionPdf ? (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-card border border-border bg-card p-4">
          <div>
            <p className="font-medium">{t("staffCatalog.written.fileName")}</p>
            <p className="text-sm text-muted">{t("staffCatalog.written.uploadedNote")}</p>
          </div>
          <Button variant="secondary" onClick={() => setViewing(true)}>
            {t("staffCatalog.written.view")}
          </Button>
        </div>
      ) : (
        <p className="rounded-card border border-dashed border-border p-4 text-sm text-muted">
          {t("staffCatalog.written.missing")}
        </p>
      )}

      {locked ? (
        <p role="status" className="text-sm text-warn">
          {t("staffCatalog.written.lockedNotice")}
        </p>
      ) : (
        <FileDrop
          accept="pdf"
          maxBytes={MAX_PDF_BYTES}
          onFile={onFile}
          uploading={upload.isPending}
          progress={progress}
          error={error ?? undefined}
        />
      )}

      <Dialog open={viewing} onOpenChange={setViewing} title={t("staffCatalog.written.viewTitle")}>
        {viewing ? (
          <PdfViewer
            load={() => apiBlob(`/api/v1/live-exams/${exam.id}/question-pdf`)}
            watermark={t("staffCatalog.written.staffWatermark")}
            allowDownload={false}
            fileName={exam.titleBn}
          />
        ) : null}
      </Dialog>
    </section>
  );
}
