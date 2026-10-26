"use client";

import { useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { apiBlob } from "@/lib/api/client";
import { useUploadQuestionPdf, type StaffExam } from "@/lib/api/exam/staff-catalog";
import { PdfViewer } from "@/components/pdf/pdf-viewer";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { FileDrop } from "@/components/ui/file-drop";
import { useToast } from "@/components/ui/toast";
import { catalogErrorKey, isApiError } from "@/components/staff/catalog/errors";

/** The API's limit for a question PDF (100 MiB, `MATERIAL_MAX_BYTES`). */
const QUESTION_PDF_LIMIT_MB = 100;
const MAX_PDF_BYTES = QUESTION_PDF_LIMIT_MB * 1024 * 1024;

/**
 * The one question PDF of a written exam. Staff may open the stored file to
 * check it; the student never downloads it. Replacing uploads a new file.
 */
export function WrittenPaper({ exam, locked }: { exam: StaffExam; locked: boolean }) {
  const { t } = useLocale();
  const { formatNumber } = useFormat();
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
      setError(isApiError(e, "FILE_TOO_LARGE") ? t("staffCatalog.written.tooLarge", { limit: formatNumber(QUESTION_PDF_LIMIT_MB) }) : t(catalogErrorKey(e)));
    } finally {
      setProgress(undefined);
    }
  }

  return (
    <div className="grid gap-4">
      {exam.questionPdf ? (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-card border border-border bg-card p-4">
          <div>
            <p className="font-medium">{t("staffCatalog.written.fileName")}</p>
            <p className="text-meta text-muted">{t("staffCatalog.written.uploadedNote")}</p>
          </div>
          <Button variant="secondary" onClick={() => setViewing(true)}>
            {t("staffCatalog.written.view")}
          </Button>
        </div>
      ) : (
        <p className="rounded-card border border-dashed border-border p-4 text-meta text-muted">
          {t("staffCatalog.written.missing")}
        </p>
      )}

      {locked ? (
        <p role="status" className="text-meta text-warn">
          {t("staffCatalog.written.lockedNotice")}
        </p>
      ) : (
        <div className="grid gap-2">
          <p className="text-meta text-muted">{t("staffCatalog.written.limitHint", { limit: formatNumber(QUESTION_PDF_LIMIT_MB) })}</p>
          <FileDrop
            accept="pdf"
            maxBytes={MAX_PDF_BYTES}
            onFile={onFile}
            uploading={upload.isPending}
            progress={progress}
            error={error ?? undefined}
          />
        </div>
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
    </div>
  );
}
