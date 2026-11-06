"use client";

import { useRef, useState } from "react";
import { FileText } from "lucide-react";
import { apiBlob } from "@/lib/api/client";
import { ApiError } from "@/lib/api/errors";
import { useRemoveRoutinePdf, useUploadRoutinePdf, type StaffModule } from "@/lib/api/exam/staff-catalog";
import { useLocale, type Locale } from "@/i18n/locale-context";
import { formatDate, formatNumber } from "@/lib/format";
import { useToast } from "@/components/ui/toast";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Dialog } from "@/components/ui/dialog";
import { PdfViewer } from "@/components/pdf/pdf-viewer";

/** The routine PDF's largest size. The browser checks type and size first; the server checks the bytes and size again. */
export const ROUTINE_PDF_MAX_BYTES = 20 * 1024 * 1024;

const KB = 1024;
const MB = 1024 * 1024;

function sizeLabel(bytes: number, t: (key: string, params?: Record<string, string | number>) => string, locale: Locale) {
  if (bytes < MB) {
    return t("common.file.sizeKb", { size: formatNumber(Math.max(1, Math.round(bytes / KB)), locale) });
  }
  return t("common.file.sizeMb", { size: formatNumber(bytes / MB, locale, { decimals: 1 }) });
}

/**
 * The package's routine PDF on the staff details screen. Everyone who can open the package sees the file; only the
 * cover's managers (`canManage`) upload, replace or remove it. The change applies at once, as the cover does.
 */
export function RoutinePdfUploader({ module, canManage }: { module: StaffModule; canManage: boolean }) {
  const { t, locale } = useLocale();
  const { toast } = useToast();
  const input = useRef<HTMLInputElement>(null);
  const upload = useUploadRoutinePdf(module.id);
  const remove = useRemoveRoutinePdf(module.id);
  const [error, setError] = useState<string | null>(null);
  const [viewing, setViewing] = useState(false);
  const [confirmRemove, setConfirmRemove] = useState(false);
  const pdf = module.routinePdf ?? null;
  const busy = upload.isPending || remove.isPending;

  async function onChange(file: File | undefined) {
    if (!file) return;
    setError(null);
    const isPdf = file.type === "application/pdf" || file.name.toLowerCase().endsWith(".pdf");
    if (!isPdf) {
      setError(t("staffCatalog.routinePdf.wrongType"));
      if (input.current) input.current.value = "";
      return;
    }
    if (file.size > ROUTINE_PDF_MAX_BYTES) {
      setError(t("staffCatalog.routinePdf.tooLarge"));
      if (input.current) input.current.value = "";
      return;
    }
    try {
      await upload.mutateAsync(file);
      toast({ title: t("staffCatalog.routinePdf.saved"), tone: "ok" });
    } catch (err) {
      setError(mapError(err, t));
    } finally {
      if (input.current) input.current.value = "";
    }
  }

  return (
    <section aria-labelledby="routine-pdf-heading" className="grid max-w-3xl gap-4 rounded-card border border-border bg-card shadow-[var(--card-shadow)] p-5">
      <h2 id="routine-pdf-heading" className="text-card-title font-semibold">
        {t("staffCatalog.routinePdf.heading")}
      </h2>
      {pdf ? (
        <div className="flex min-w-0 items-center gap-3">
          <span
            aria-hidden="true"
            className="flex size-11 shrink-0 items-center justify-center rounded-control bg-accent/15 text-text-accent"
          >
            <FileText className="size-5" />
          </span>
          <div className="min-w-0">
            <p className="break-words font-medium text-text">{pdf.fileName}</p>
            <p className="text-meta text-muted">
              {`${sizeLabel(pdf.byteSize, t, locale)} · ${t("staffCatalog.routinePdf.uploaded", {
                date: formatDate(pdf.updatedAt, locale, { year: "auto" }),
              })}`}
            </p>
          </div>
        </div>
      ) : (
        <p className="text-meta text-muted">{t("staffCatalog.routinePdf.none")}</p>
      )}
      {canManage ? <p className="text-meta text-muted">{t("staffCatalog.routinePdf.hint")}</p> : null}
      <div className="flex flex-wrap gap-2">
        {canManage ? (
          <>
            <input
              ref={input}
              type="file"
              accept="application/pdf"
              className="sr-only"
              aria-label={pdf ? t("staffCatalog.routinePdf.replace") : t("staffCatalog.routinePdf.upload")}
              onChange={(event) => void onChange(event.target.files?.[0])}
              disabled={busy}
            />
            <Button variant="secondary" loading={upload.isPending} disabled={busy} onClick={() => input.current?.click()}>
              {pdf ? t("staffCatalog.routinePdf.replace") : t("staffCatalog.routinePdf.upload")}
            </Button>
            {pdf ? (
              <Button variant="ghost" loading={remove.isPending} disabled={busy} onClick={() => setConfirmRemove(true)}>
                {t("staffCatalog.routinePdf.remove")}
              </Button>
            ) : null}
          </>
        ) : null}
        {pdf ? (
          <Button variant="secondary" onClick={() => setViewing(true)}>
            {t("staffCatalog.routinePdf.view")}
          </Button>
        ) : null}
      </div>
      {error ? (
        <p role="alert" className="text-meta text-danger">
          {error}
        </p>
      ) : null}
      {pdf ? (
        <Dialog open={viewing} onOpenChange={setViewing} title={pdf.fileName}>
          {viewing ? (
            <PdfViewer
              load={() => apiBlob(`/api/v1/live-exam-modules/${module.id}/routine-pdf`)}
              allowDownload
              fileName={pdf.fileName}
              label={pdf.fileName}
            />
          ) : null}
        </Dialog>
      ) : null}
      {canManage ? (
        <ConfirmDialog
          open={confirmRemove}
          onOpenChange={setConfirmRemove}
          title={t("staffCatalog.routinePdf.removeTitle")}
          body={t("staffCatalog.routinePdf.removeBody")}
          confirmLabel={t("staffCatalog.routinePdf.removeConfirm")}
          tone="danger"
          onConfirm={async () => {
            setError(null);
            try {
              await remove.mutateAsync();
              toast({ title: t("staffCatalog.routinePdf.removed"), tone: "ok" });
            } catch (err) {
              setError(mapError(err, t));
            }
          }}
        />
      ) : null}
    </section>
  );
}

function mapError(err: unknown, t: (key: string) => string): string {
  const code = err instanceof ApiError ? err.code : null;
  if (code === "FILE_TOO_LARGE") return t("staffCatalog.routinePdf.tooLarge");
  if (code === "VALIDATION_FAILED") return t("staffCatalog.routinePdf.wrongType");
  return t("staffCatalog.routinePdf.failed");
}
