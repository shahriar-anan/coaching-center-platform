"use client";

import { useId, useRef, useState, type DragEvent } from "react";
import { FileText, Upload } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/components/ui/cn";
import { useLocale } from "@/i18n/locale-context";
import { formatNumber } from "@/lib/format";

export type FileDropValue = { name: string; size: number; uploadedAt?: string };

const PDF_MAGIC = [0x25, 0x50, 0x44, 0x46, 0x2d]; // "%PDF-"

async function startsWithPdfMagic(file: File): Promise<boolean> {
  const head = new Uint8Array(await file.slice(0, PDF_MAGIC.length).arrayBuffer());
  return PDF_MAGIC.every((byte, i) => head[i] === byte);
}

function megabytes(bytes: number, locale: "bn" | "en"): string {
  const value = Math.round((bytes / (1024 * 1024)) * 10) / 10;
  return `${formatNumber(value, locale)} ${locale === "bn" ? "এমবি" : "MB"}`;
}

/**
 * A PDF picker with drag and drop. It checks the file type by its first bytes
 * (not the name) and the size limit before `onFile` runs. Progress is shown
 * while `uploading`; `file` shows the file already on the server.
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
  accept: "pdf";
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
    if (accept === "pdf" && !(await startsWithPdfMagic(candidate))) {
      setLocalError(t("common.file.notPdf"));
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
    <div className="grid gap-2">
      <div
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        className={cn(
          "grid gap-3 rounded-card border-2 border-dashed bg-card p-5 transition-colors duration-150",
          dragging ? "border-accent" : "border-border hover:border-accent",
          shownError && "border-danger",
        )}
      >
        {file ? (
          <div className="flex items-center gap-3">
            <FileText aria-hidden="true" className="size-8 shrink-0 text-muted" />
            <div className="min-w-0 flex-1">
              <p className="truncate font-medium text-text">{file.name}</p>
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
            {file ? t("common.file.replace") : t("common.file.choose")}
          </Button>
          <span className="text-meta text-muted">{hint}</span>
        </div>
        <input
          ref={inputRef}
          id={inputId}
          type="file"
          accept="application/pdf,.pdf"
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
