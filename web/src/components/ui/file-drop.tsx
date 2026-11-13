"use client";

import { useId, useRef, useState, type DragEvent } from "react";
import { FileText, Upload } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/components/ui/cn";
import { useLocale } from "@/i18n/locale-context";
import { formatNumber } from "@/lib/format";

export type FileDropValue = { name: string; size: number; uploadedAt?: string };

const PDF_MAGIC = [0x25, 0x50, 0x44, 0x46, 0x2d]; // "%PDF-"
const JPEG_MAGIC = [0xff, 0xd8, 0xff];
const PNG_MAGIC = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];

/** A PDF header within the first kilobyte (readers accept a few bytes of junk before it). Throws if the phone cannot read the file. */
async function hasPdfHeader(file: File): Promise<boolean> {
  const head = new Uint8Array(await file.slice(0, 1024).arrayBuffer());
  for (let start = 0; start + PDF_MAGIC.length <= head.length; start++) {
    if (PDF_MAGIC.every((byte, i) => head[start + i] === byte)) {
      return true;
    }
  }
  return false;
}

/** The first bytes of a JPEG, a PNG or a WebP file: a phone photo of the paper. */
async function hasImageHeader(file: File): Promise<boolean> {
  const head = new Uint8Array(await file.slice(0, 16).arrayBuffer());
  const starts = (magic: number[]) => magic.every((byte, i) => head[i] === byte);
  const webp = String.fromCharCode(...head.slice(0, 4)) === "RIFF" && String.fromCharCode(...head.slice(8, 12)) === "WEBP";
  return starts(JPEG_MAGIC) || starts(PNG_MAGIC) || webp;
}

/** A file size in KB below one megabyte (so a small PDF is not shown as 0 MB), and in MB above it. */
function megabytes(bytes: number, locale: "bn" | "en"): string {
  if (bytes < 1024 * 1024) {
    const kilobytes = Math.max(1, Math.round(bytes / 1024));
    return `${formatNumber(kilobytes, locale)} ${locale === "bn" ? "কেবি" : "KB"}`;
  }
  const value = Math.round((bytes / (1024 * 1024)) * 10) / 10;
  return `${formatNumber(value, locale)} ${locale === "bn" ? "এমবি" : "MB"}`;
}

/**
 * A PDF picker with drag and drop. It checks the file type by its first bytes
 * (not the name) and the size limit before `onFile` runs. Progress is shown
 * while `uploading`; `file` shows the file already on the server. `accept="pdf-or-image"` also takes a JPEG, PNG or WebP photo
 * of the paper.
 */
export function FileDrop({
  accept,
  maxBytes,
  file,
  onFile,
  uploading,
  progress,
  error,
  id,
}: {
  accept: "pdf" | "pdf-or-image";
  maxBytes: number;
  file?: FileDropValue;
  onFile: (file: File) => void;
  uploading?: boolean;
  progress?: number;
  error?: string;
  id?: string;
}) {
  const { t, locale } = useLocale();
  const reactId = useId();
  const inputId = id ?? `file-${reactId}`;
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  const [localError, setLocalError] = useState<string | null>(null);
  const shownError = error ?? localError;

  async function take(candidate: File | undefined) {
    if (!candidate || uploading) {
      return;
    }
    let pdf: boolean;
    try {
      pdf = (await hasPdfHeader(candidate)) || (accept === "pdf-or-image" && (await hasImageHeader(candidate)));
    } catch {
      // The phone could not read the file (a cloud or messaging app that has not downloaded it). Say so, rather than fail silently.
      setLocalError(t("common.file.readFailed"));
      return;
    }
    if (!pdf) {
      setLocalError(t(accept === "pdf-or-image" ? "common.file.notPdfOrImage" : "common.file.notPdf"));
      return;
    }
    if (candidate.size > maxBytes) {
      setLocalError(t("common.file.tooLarge", { max: megabytes(maxBytes, locale) }));
      return;
    }
    setLocalError(null);
    onFile(candidate);
  }

  function onDrop(e: DragEvent<HTMLDivElement>) {
    e.preventDefault();
    setDragging(false);
    void take(e.dataTransfer.files[0]);
  }

  const hint = t("common.file.limit", { max: megabytes(maxBytes, locale) });

  return (
    <div className="grid min-w-0 gap-2">
      <div
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        className={cn(
          // One column that can shrink, so a long file name is cut short rather than pushing the box wider than the card.
          "grid min-w-0 grid-cols-[minmax(0,1fr)] gap-3 rounded-card border-2 border-dashed bg-card p-5 transition-colors duration-150",
          dragging ? "border-accent" : "border-border-control hover:border-accent",
          shownError && "border-danger",
        )}
      >
        {file ? (
          <div className="flex items-center gap-3">
            <FileText aria-hidden="true" className="size-8 shrink-0 text-muted" />
            <div className="min-w-0 flex-1">
              <p className="truncate font-medium text-text" title={file.name}>{file.name}</p>
              <p className="text-meta text-muted">
                {megabytes(file.size, locale)}
                {file.uploadedAt ? ` · ${t("common.file.uploadedAt", { time: file.uploadedAt })}` : ""}
              </p>
            </div>
          </div>
        ) : (
          <div className="flex items-center gap-3 text-muted">
            <Upload aria-hidden="true" className="size-6 shrink-0" />
            <p className="text-body">
              {t("common.file.drop")} <span className="sr-only">{t("common.file.choose")}</span>
            </p>
          </div>
        )}
        {uploading ? (
          <div className="grid gap-1.5">
            <div
              role="progressbar"
              aria-valuemin={0}
              aria-valuemax={100}
              aria-valuenow={progress ?? 0}
              aria-label={t("common.file.uploading", { percent: formatNumber(progress ?? 0, locale) })}
              className="h-2 overflow-hidden rounded-full bg-border"
            >
              <div className="h-full bg-action transition-[width] duration-200" style={{ width: `${progress ?? 0}%` }} />
            </div>
            <p className="text-meta text-muted">
              {t("common.file.uploading", { percent: formatNumber(progress ?? 0, locale) })}
            </p>
          </div>
        ) : null}
        <div className="flex flex-wrap items-center gap-2">
          <Button variant="secondary" disabled={uploading} onClick={() => inputRef.current?.click()}>
            {file ? t("common.file.replace") : t(accept === "pdf-or-image" ? "common.file.pdfOrImage" : "common.file.choose")}
          </Button>
          <span className="text-meta text-muted">{hint}</span>
        </div>
        <input
          ref={inputRef}
          id={inputId}
          type="file"
          accept={accept === "pdf-or-image" ? "application/pdf,.pdf,image/jpeg,image/png,image/webp" : "application/pdf,.pdf"}
          className="sr-only"
          tabIndex={-1}
          onChange={(e) => {
            void take(e.target.files?.[0]);
            e.target.value = "";
          }}
        />
      </div>
      {shownError ? (
        <p role="alert" className="text-meta text-danger">
          {shownError}
        </p>
      ) : null}
    </div>
  );
}
