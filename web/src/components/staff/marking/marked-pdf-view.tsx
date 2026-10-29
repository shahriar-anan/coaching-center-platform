"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { PdfViewer } from "@/components/pdf/pdf-viewer";
import { useLocale } from "@/i18n/locale-context";
import { evaluatedPaperBlob } from "@/lib/api/exam/staff-marking";

/**
 * The marked PDF already uploaded for one paper, opened in a dialog with the same
 * viewer as the answer script. Shown only when the paper has a stored marked PDF.
 * The API decides who may read it.
 */
export function MarkedPdfView({ participantId, fileName }: { participantId: string; fileName: string }) {
  const { t } = useLocale();
  const [open, setOpen] = useState(false);

  return (
    <Card>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <span className="text-label font-medium text-text">{t("staffMarking.form.markedFile")}</span>
        <Button variant="secondary" size="sm" onClick={() => setOpen(true)}>
          {t("staffMarking.form.viewMarked")}
        </Button>
      </div>
      <Dialog
        open={open}
        onOpenChange={setOpen}
        title={t("staffMarking.form.markedFile")}
        className="sm:max-w-3xl"
      >
        <PdfViewer
          load={() => evaluatedPaperBlob(participantId)}
          allowDownload
          fileName={fileName}
        />
      </Dialog>
    </Card>
  );
}
