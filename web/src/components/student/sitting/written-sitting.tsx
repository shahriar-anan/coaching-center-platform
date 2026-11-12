"use client";

import { useEffect, useRef, useState } from "react";
import { PdfViewer } from "@/components/pdf/pdf-viewer";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Dialog } from "@/components/ui/dialog";
import { Countdown } from "@/components/ui/countdown";
import { FileDrop, type FileDropValue } from "@/components/ui/file-drop";
import { useLocale } from "@/i18n/locale-context";
import { apiBlob } from "@/lib/api/client";
import { ApiError } from "@/lib/api/errors";
import { answerPdfPath, questionPdfPath, useUploadAnswer, type StudentExam } from "@/lib/api/exam/sitting";
import { clearStagedAnswer, loadStagedAnswer, saveStagedAnswer } from "@/lib/files/staged-answer";
import { formatNumber } from "@/lib/format";
import { useServerNow } from "@/lib/time/clock";

/** A chosen script goes in at least this long before the deadline, so the upload lands before time runs out. */
const AUTO_SEND_MS = 10_000;
/** A large script takes longer to send: one extra second per 100 KB, but never earlier than this before the deadline. */
const AUTO_SEND_MAX_MS = 5 * 60_000;

/** How long before the deadline a chosen script of this size is sent by itself. */
export function autoSendLeadMs(bytes: number): number {
  return Math.min(AUTO_SEND_MAX_MS, AUTO_SEND_MS + Math.ceil(bytes / (100 * 1024)) * 1000);
}

export const MAX_ANSWER_BYTES = 80 * 1024 * 1024;

/** Accepts a file with a `%PDF` header within its first kilobyte. A renamed image is refused here. */
export async function isPdf(file: Blob): Promise<boolean> {
  const head = new Uint8Array(await file.slice(0, 1024).arrayBuffer());
  const magic = [0x25, 0x50, 0x44, 0x46]; // "%PDF"
  for (let start = 0; start + magic.length <= head.length; start++) {
    if (magic.every((byte, i) => head[start + i] === byte)) {
      return true;
    }
  }
  return false;
}

type Props = {
  exam: StudentExam;
  deadlineAt: string;
  /** Printed at the top and bottom of every page of the question paper, for example "নাঈম হাসান · S18492017". */
  studentLine: string;
  /** Called at the deadline. `hasScript` says whether a script was stored by then. */
  onClosed: (hasScript: boolean) => void;
  /** Called once the student has confirmed and the script is stored. */
  onSubmitted: () => void;
};

/**
 * The written paper: the question PDF inline (no download), and the answer upload. The student
 * stages a file, then submits it; a later file replaces the earlier one until the deadline. A staged file
 * is kept on the phone, so a reload does not lose it.
 */
export function WrittenSitting({ exam, deadlineAt, studentLine, onClosed, onSubmitted }: Props) {
  const { t, locale } = useLocale();
  const n = (value: number) => formatNumber(value, locale);
  const [progress, setProgress] = useState(0);
  const upload = useUploadAnswer(exam.id, setProgress);
  const [pending, setPending] = useState<File | null>(null);
  const [restored, setRestored] = useState(false);
  const [stored, setStored] = useState<FileDropValue | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [closed, setClosed] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [viewing, setViewing] = useState(false);
  // The file the automatic send has already tried. A failed try is not repeated on every render; only the student retries.
  const autoSentFor = useRef<File | null>(null);
  const submitted = Boolean(stored || exam.answerStored);
  const now = useServerNow(1000);
  const deadlineMs = Date.parse(deadlineAt);

  // A script chosen before a reload (or before the phone closed the tab) is put back, unless it was already sent.
  useEffect(() => {
    if (exam.answerStored) {
      return;
    }
    let cancelled = false;
    void loadStagedAnswer(exam.id).then((file) => {
      if (!cancelled && file) {
        setPending(file);
        setRestored(true);
      }
    });
    return () => {
      cancelled = true;
    };
    // Once per exam: a file the student chooses later is saved by `choose`, not read back here.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [exam.id]);

  const choose = async (file: File) => {
    setError(null);
    if (file.size > MAX_ANSWER_BYTES) {
      setError(t("sitting.written.tooLarge"));
      return;
    }
    let pdf: boolean;
    try {
      pdf = await isPdf(file);
    } catch {
      setError(t("sitting.written.readFailed"));
      return;
    }
    if (!pdf) {
      setError(t("sitting.written.onlyPdf"));
      return;
    }
    setPending(file);
    setRestored(false);
    void saveStagedAnswer(exam.id, file);
  };

  const submit = async () => {
    if (!pending || closed) {
      return;
    }
    setError(null);
    setProgress(0);
    try {
      await upload.mutateAsync(pending);
      void clearStagedAnswer(exam.id);
      setStored({ name: pending.name, size: pending.size, uploadedAt: new Date().toISOString() });
      setPending(null);
      setRestored(false);
      onSubmitted();
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === "ANSWER_ALREADY_SUBMITTED") {
        void clearStagedAnswer(exam.id);
        onSubmitted();
        return;
      }
      if (caught instanceof ApiError && caught.code === "DEADLINE_PASSED") {
        setClosed(true);
        setError(t("sitting.written.deadlinePassed"));
        return;
      }
      // A dropped connection or a stalled upload: the file stays staged, so the student can simply try again.
      const network = caught instanceof TypeError || (caught instanceof DOMException && caught.name === "AbortError");
      setError(
        caught instanceof ApiError && caught.code === "FILE_TOO_LARGE"
          ? t("sitting.written.tooLarge")
          : network
            ? t("sitting.written.networkFailed")
            : t("sitting.written.failed"),
      );
    }
  };

  // A chosen script that was not sent yet goes in by itself as time runs out. A large one goes in earlier, as it takes longer.
  // The server refuses a late upload, so it goes early.
  useEffect(() => {
    if (
      pending &&
      !closed &&
      !upload.isPending &&
      autoSentFor.current !== pending &&
      now >= deadlineMs - autoSendLeadMs(pending.size)
    ) {
      autoSentFor.current = pending;
      void submit();
    }
  });

  const fileShown: FileDropValue | undefined =
    pending !== null ? { name: pending.name, size: pending.size } : (stored ?? undefined);

  return (
    <div className="flex flex-col gap-4 pb-28">
      <div className="sticky top-0 z-10">
        <Countdown deadline={deadlineAt} onEnd={() => onClosed(Boolean(stored || exam.answerStored))} variant="bar" warnAt={[10, 5, 1]} />
      </div>

      {/* On a phone the question paper comes first and the upload below it. From md up the order is the same (DOM order). */}
      {/* Once the script is stored the submission is final, so the question paper is no longer offered. */}
      {submitted ? null : (
        <Card className="order-1 md:order-none">
          <h2 className="text-label font-semibold text-muted">{t("sitting.written.paper")}</h2>
          <div className="mt-3">
            {exam.questionPdf ? (
              <PdfViewer
                load={() => apiBlob(questionPdfPath(exam.id))}
                studentLine={studentLine}
                allowDownload={false}
                label={t("sitting.written.paper")}
              />
            ) : (
              <p className="text-muted">{t("sitting.written.noPaper")}</p>
            )}
          </div>
        </Card>
      )}

      <Card className="order-2 md:order-none">
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
        {restored && pending ? <p className="mt-2 text-body text-muted">{t("sitting.written.restored")}</p> : null}
        {!pending && (stored || exam.answerStored) ? (
          <p className="mt-2 text-body text-ok">{stored ? t("sitting.written.stored") : t("sitting.written.storedEarlier")}</p>
        ) : null}
        {/* The chosen file, or the stored script, can be opened to check it is the right one before and after it is sent. */}
        {pending || stored || exam.answerStored ? (
          <Button variant="secondary" className="mt-3" onClick={() => setViewing(true)}>
            {t("sitting.written.viewUploaded")}
          </Button>
        ) : null}
        {/* The submit button stays off until a file is chosen; this says why, so the student does not tap a dead button. */}
        {!pending && !submitted && !closed ? (
          <p className="mt-3 text-body font-semibold text-text">{t("sitting.written.pickFirst")}</p>
        ) : null}
        <div className="mt-4 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div className="grid gap-1">
            <p className="text-body text-muted">{t("sitting.written.replaceNote")}</p>
            <p className="text-body text-muted">{t("sitting.written.autoSendNote")}</p>
          </div>
          <Button
            variant="primary"
            loading={upload.isPending}
            disabled={!pending || closed || upload.isPending}
            onClick={() => setConfirming(true)}
          >
            {upload.isPending ? `${t("sitting.written.uploading")} ${n(progress)}%` : t("sitting.written.script")}
          </Button>
        </div>
      </Card>

      <Dialog open={viewing} onOpenChange={setViewing} title={t("sitting.written.viewUploaded")} className="sm:max-w-3xl">
        {viewing ? (
          <PdfViewer
            load={() => (pending ? Promise.resolve(pending) : apiBlob(answerPdfPath(exam.id)))}
            allowDownload={false}
            label={t("sitting.written.viewUploaded")}
          />
        ) : null}
      </Dialog>

      <ConfirmDialog
        open={confirming}
        onOpenChange={setConfirming}
        title={t("sitting.written.confirmTitle")}
        body={t("sitting.written.confirmBody", { name: pending?.name ?? "" })}
        confirmLabel={t("sitting.written.confirmAction")}
        onConfirm={submit}
      />
    </div>
  );
}
