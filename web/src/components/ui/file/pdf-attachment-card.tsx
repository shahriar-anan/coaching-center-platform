"use client";

import { useState } from "react";
import { FileText } from "lucide-react";
import { PdfViewer } from "@/components/pdf/pdf-viewer";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { apiBlob } from "@/lib/api/client";
import { apiUrl } from "@/lib/config";
import { useLocale, type Locale } from "@/i18n/locale-context";
import { formatNumber } from "@/lib/format";

const KB = 1024;
const MB = 1024 * 1024;

/** Whole kilobytes below 1 MB, one decimal place of megabytes from 1 MB up. */
function sizeLabel(bytes: number, t: (key: string, params?: Record<string, string | number>) => string, locale: Locale) {
  if (bytes < MB) {
    return t("common.file.sizeKb", { size: formatNumber(Math.max(1, Math.round(bytes / KB)), locale) });
  }
  return t("common.file.sizeMb", { size: formatNumber(bytes / MB, locale, { decimals: 1 }) });
}

export type PdfAttachmentCardProps = {
  /** The file's path from the API, for example `/api/v1/public/...?v=...`. Null renders nothing. */
  url: string | null;
  /** The name shown on the card. Falls back to `title` when missing. */
  fileName: string | null;
  /** The size in bytes, or null to leave the size out. */
  byteSize: number | null;
  /** The dialog title, the fallback name and the accessible name of the viewer. */
  title: string;
};

/**
 * A PDF attached to a page: the name and size, a View button that opens the viewer in a dialog, and a Download link.
 * The download link is a plain `<a download>`, so it works without JavaScript.
 */
export function PdfAttachmentCard({ url, fileName, byteSize, title }: PdfAttachmentCardProps) {
  const { t, locale } = useLocale();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [opening, setOpening] = useState(false);
  const [openFailed, setOpenFailed] = useState(false);

  if (!url) {
    return null;
  }

  const source = url;
  const displayName = fileName?.trim() ? fileName.trim() : title;
  // The plain link needs the API base URL; apiBlob adds it itself, so `source` stays a path for the viewer.
  const downloadHref = apiUrl(`${source}${source.includes("?") ? "&" : "?"}download=1`);
  const size = byteSize === null ? null : sizeLabel(byteSize, t, locale);

  // The viewer fetches the file itself when the dialog opens, so this wrapper reports that fetch to the card.
  const load = async (): Promise<Blob> => {
    setOpening(true);
    setOpenFailed(false);
    try {
      return await apiBlob(source);
    } catch (error) {
      setOpenFailed(true);
      throw error;
    } finally {
      setOpening(false);
    }
  };

  return (
    <div className="flex flex-col gap-3 rounded-card border border-border bg-card p-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex min-w-0 items-center gap-3">
        <span
          aria-hidden="true"
          className="flex size-11 shrink-0 items-center justify-center rounded-control bg-accent/15 text-text-accent"
        >
          <FileText className="size-5" />
        </span>
        <div className="min-w-0">
          <p className="text-meta text-muted">
            {t("common.file.pdf")}
            {size ? ` · ${size}` : ""}
          </p>
          <p className="break-words text-body font-medium text-text">{displayName}</p>
        </div>
      </div>
      <div className="flex flex-col gap-2 sm:flex-row sm:shrink-0">
        <Button
          type="button"
          variant="secondary"
          onClick={() => {
            setDialogOpen(true);
          }}
          aria-label={`${t("common.file.view")}: ${title}`}
        >
          {opening ? t("common.file.opening") : t("common.file.view")}
        </Button>
        <a
          href={downloadHref}
          download
          aria-label={`${t("common.file.download")}: ${displayName}`}
          className="inline-flex min-h-11 items-center justify-center gap-2 rounded-control border border-border-control bg-card px-5 text-label text-text transition-colors duration-150 hover:bg-bg active:bg-border focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
        >
          {t("common.file.download")}
        </a>
      </div>
      {openFailed ? (
        <p role="alert" className="text-meta text-danger sm:basis-full">
          {t("common.file.openFailed")}
        </p>
      ) : null}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen} title={title}>
        <PdfViewer load={load} allowDownload fileName={displayName} label={title} />
      </Dialog>
    </div>
  );
}
