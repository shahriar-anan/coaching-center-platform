"use client";

import { useState } from "react";
import { PdfViewer } from "@/components/pdf/pdf-viewer";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Countdown } from "@/components/ui/countdown";
import { FileDrop, type FileDropValue } from "@/components/ui/file-drop";
import { useLocale } from "@/i18n/locale-context";
import { apiBlob } from "@/lib/api/client";
import { ApiError } from "@/lib/api/errors";
import { questionPdfPath, useUploadAnswer, type StudentExam } from "@/lib/api/exam/sitting";
import { formatNumber } from "@/lib/format";

export const MAX_ANSWER_BYTES = 80 * 1024 * 1024;

/** Accepts only a file whose first bytes are `%PDF`. A renamed image is refused here. */
export async function isPdf(file: Blob): Promise<boolean> {
  const head = new Uint8Array(await file.slice(0, 4).arrayBuffer());
  return head.length === 4 && head[0] === 0x25 && head[1] === 0x50 && head[2] === 0x44 && head[3] === 0x46;
}

type Props = {
  exam: StudentExam;
  deadlineAt: string;
  /** Shown diagonally over the question paper, for example "নাঈম হাসান · S18492017". */
  watermark: string;
  onClosed: () => void;
};

/**
 * The written paper: the question PDF inline (no download), and the answer upload. The student
 * stages a file, then submits it; a later file replaces the earlier one until the deadline.
 */
export function WrittenSitting({ exam, deadlineAt, watermark, onClosed }: Props) {
  const { t, locale } = useLocale();
  const n = (value: number) => formatNumber(value, locale);
  const [progress, setProgress] = useState(0);
  const upload = useUploadAnswer(exam.id, setProgress);
  const [pending, setPending] = useState<File | null>(null);
  const [stored, setStored] = useState<FileDropValue | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [closed, setClosed] = useState(false);

  const choose = async (file: File) => {
    setError(null);
    if (file.size > MAX_ANSWER_BYTES) {
      setError(t("sitting.written.tooLarge"));
      return;
    }
    if (!(await isPdf(file))) {
      setError(t("sitting.written.onlyPdf"));
      return;
    }
    setPending(file);
  };

  const submit = async () => {
    if (!pending || closed) {
      return;
    }
    setError(null);
    setProgress(0);
    try {
      await upload.mutateAsync(pending);
      setStored({ name: pending.name, size: pending.size, uploadedAt: new Date().toISOString() });
      setPending(null);
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === "DEADLINE_PASSED") {
        setClosed(true);
        setError(t("sitting.written.deadlinePassed"));
        return;
      }
      setError(
        caught instanceof ApiError && caught.code === "FILE_TOO_LARGE"
          ? t("sitting.written.tooLarge")
          : t("sitting.written.failed"),
      );
    }
  };

  const fileShown: FileDropValue | undefined =
    pending !== null ? { name: pending.name, size: pending.size } : (stored ?? undefined);

  return (
    <div className="flex flex-col gap-4 pb-28">
      <div className="sticky top-0 z-10">
        <Countdown deadline={deadlineAt} onEnd={onClosed} variant="bar" warnAt={[10, 5, 1]} />
      </div>

      <Card>
        <h2 className="text-label font-semibold text-muted">{t("sitting.written.paper")}</h2>
        <div className="mt-3">
          {exam.questionPdf ? (
            <PdfViewer
              load={() => apiBlob(questionPdfPath(exam.id))}
              watermark={watermark}
              allowDownload={false}
              label={t("sitting.written.paper")}
            />
          ) : (
            <p className="text-muted">{t("sitting.written.noPaper")}</p>
          )}
        </div>
      </Card>

      <Card>
        <h2 className="text-label font-semibold text-muted">{t("sitting.written.script")}</h2>
        <p className="mt-1 text-body text-muted">{t("sitting.written.instruction")}</p>
        <p className="mt-1 text-body text-muted">{t("sitting.written.lateUpload")}</p>
        {!closed ? (
          <div className="mt-3">
            <FileDrop
              accept="pdf"
              maxBytes={MAX_ANSWER_BYTES}
              file={fileShown}
              onFile={choose}
              uploading={upload.isPending}
              progress={upload.isPending ? progress : undefined}
              error={error ?? undefined}
            />
          </div>
        ) : (
          <p role="alert" className="mt-3 text-body text-danger">
            {t("sitting.written.deadlinePassed")}
          </p>
        )}
        {stored && !pending ? <p className="mt-2 text-body text-ok">{t("sitting.written.stored")}</p> : null}
        <div className="mt-4 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-body text-muted">{t("sitting.written.replaceNote")}</p>
          <Button
            variant="primary"
            loading={upload.isPending}
            disabled={!pending || closed || upload.isPending}
            onClick={submit}
          >
            {upload.isPending ? `${t("sitting.written.uploading")} ${n(progress)}%` : t("sitting.written.script")}
          </Button>
        </div>
      </Card>
    </div>
  );
}
