"use client";

import { useEffect, useState } from "react";
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
import {
  answerDraftPath,
  answerPdfPath,
  questionPdfPath,
  useSaveAnswerDraft,
  useSubmitExam,
  type AnswerDraft,
  type StudentExam,
} from "@/lib/api/exam/sitting";
import { clearStagedAnswer } from "@/lib/files/staged-answer";
import { formatDateTime, formatNumber } from "@/lib/format";

export const MAX_ANSWER_BYTES = 80 * 1024 * 1024;

/** Waits before the automatic retries of a dropped upload. Two retries, then the student is asked to try again. */
const RETRY_DELAYS_MS = [2_000, 5_000];

/**
 * A copy of the chosen file held in memory. On a phone the picked file is only a pointer to the original: if the original is re-saved,
 * synced or its access lapses while the exam runs, every upload of the pointer fails at once, and Try again fails the same way
 * (written-upload incident, 15 Nov 2026). The copy is read once, when the student chooses it.
 */
export async function snapshot(file: File): Promise<File> {
  const bytes = await file.arrayBuffer();
  return new File([bytes], file.name, { type: "application/pdf", lastModified: file.lastModified });
}

function isNetworkError(caught: unknown): boolean {
  return caught instanceof TypeError || (caught instanceof DOMException && caught.name === "AbortError");
}

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
  /** Called at the deadline. `hasScript` says whether a script (or a draft the server submits) was stored by then. */
  onClosed: (hasScript: boolean) => void;
  /** Called once the student has submitted. */
  onSubmitted: () => void;
};

/**
 * The written paper: the question PDF inline (no download), and the answer script (docs/exam-site/written-draft-submission-plan.md).
 * Choosing a PDF uploads it to the server as a draft at once; Replace uploads another over it. Submit makes the draft the answer script,
 * and nothing replaces it after. When the time ends the server submits the last draft by itself, so the page does not need to stay open.
 */
export function WrittenSitting({ exam, deadlineAt, studentLine, onClosed, onSubmitted }: Props) {
  const { t, locale } = useLocale();
  const n = (value: number) => formatNumber(value, locale);
  const [progress, setProgress] = useState(0);
  const saveDraft = useSaveAnswerDraft(exam.id, setProgress);
  const submitExam = useSubmitExam(exam.id);
  // The server's draft: the one the exam read carried, then the one each upload answers.
  const [draft, setDraft] = useState<AnswerDraft | null>(exam.answerDraft ?? null);
  // A chosen file whose upload failed: it stays on screen with Try again, and Submit waits for it.
  const [failed, setFailed] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [closed, setClosed] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [viewing, setViewing] = useState(false);
  const submitted = Boolean(exam.answerStored);

  // A file the old page kept in the browser is no longer used: the draft lives on the server.
  useEffect(() => {
    void clearStagedAnswer(exam.id);
  }, [exam.id]);

  const upload = async (file: File) => {
    setError(null);
    setProgress(0);
    try {
      // A dropped connection is retried by itself (twice), so a weak signal does not need the student to notice and tap.
      let saved: AnswerDraft | null = null;
      for (let attempt = 0; saved === null; attempt += 1) {
        try {
          saved = await saveDraft.mutateAsync(file);
        } catch (caught) {
          if (!isNetworkError(caught) || attempt >= RETRY_DELAYS_MS.length || Date.now() >= Date.parse(deadlineAt)) {
            throw caught;
          }
          setError(t("sitting.written.retrying", { attempt: n(attempt + 2), total: n(RETRY_DELAYS_MS.length + 1) }));
          await new Promise((resolve) => window.setTimeout(resolve, RETRY_DELAYS_MS[attempt]));
          setProgress(0);
        }
      }
      setError(null);
      setDraft(saved);
      setFailed(null);
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === "ANSWER_ALREADY_SUBMITTED") {
        onSubmitted();
        return;
      }
      if (caught instanceof ApiError && caught.code === "DEADLINE_PASSED") {
        setClosed(true);
        setError(t("sitting.written.deadlinePassed"));
        return;
      }
      // The server could not open this PDF (for example it needs a password): sending it again will not help, so Try again is not
      // offered; the student chooses another file.
      if (caught instanceof ApiError && caught.code === "VALIDATION_FAILED") {
        setFailed(null);
        setError(t("sitting.written.badPdf"));
        return;
      }
      setFailed(file);
      const network = isNetworkError(caught);
      setError(
        caught instanceof ApiError && caught.code === "FILE_TOO_LARGE"
          ? t("sitting.written.tooLarge")
          : network
            ? t("sitting.written.networkFailed")
            : t("sitting.written.failed"),
      );
    }
  };

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
    let copy: File;
    try {
      copy = await snapshot(file);
    } catch {
      setError(t("sitting.written.readFailed"));
      return;
    }
    await upload(copy);
  };

  const submit = async () => {
    if (!draft || closed) {
      return;
    }
    setError(null);
    try {
      await submitExam.mutateAsync();
      onSubmitted();
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === "DEADLINE_PASSED") {
        setClosed(true);
        setError(t("sitting.written.deadlinePassed"));
        return;
      }
      if (caught instanceof ApiError && caught.code === "NO_ANSWER_SCRIPT") {
        setDraft(null);
        setError(t("sitting.written.uploadFirst"));
        return;
      }
      throw new Error(t("sitting.written.submitFailed"));
    }
  };

  const shown: FileDropValue | undefined = failed
    ? { name: failed.name, size: failed.size }
    : draft
      ? {
          name: draft.fileName,
          size: draft.byteSize,
          uploadedAt: draft.uploadedAt ? formatDateTime(draft.uploadedAt, locale) : undefined,
        }
      : undefined;
  const busy = saveDraft.isPending || submitExam.isPending;

  return (
    <div className="flex flex-col gap-4 pb-28">
      <div className="sticky top-0 z-10">
        <Countdown deadline={deadlineAt} onEnd={() => onClosed(Boolean(draft || exam.answerStored))} variant="bar" warnAt={[10, 5, 1]} />
      </div>

      {/* On a phone the question paper comes first and the upload below it. From md up the order is the same (DOM order). */}
      {/* Once the script is submitted it is final, so the question paper is no longer offered. */}
      {submitted ? null : (
        <Card className="order-1 md:order-none">
          <h2 className="text-label font-semibold text-muted">{t("sitting.written.paper")}</h2>
          <div className="mt-3">
            {exam.questionPdf ? (
              <PdfViewer load={() => apiBlob(questionPdfPath(exam.id))} studentLine={studentLine} allowDownload={false} label={t("sitting.written.paper")} />
            ) : (
              <p className="text-muted">{t("sitting.written.noPaper")}</p>
            )}
          </div>
        </Card>
      )}

      <Card className="order-2 md:order-none">
        <h2 className="text-label font-semibold text-muted">{t("sitting.written.script")}</h2>
        <p className="mt-1 text-body text-muted">{t("sitting.written.instruction")}</p>
        <p className="mt-1 text-body text-muted">{t("sitting.written.autoSendNote")}</p>
        {!closed ? (
          <div className="mt-3">
            <FileDrop
              accept="pdf"
              maxBytes={MAX_ANSWER_BYTES}
              file={shown}
              onFile={(file) => void choose(file)}
              uploading={saveDraft.isPending}
              progress={saveDraft.isPending ? progress : undefined}
              error={error ?? undefined}
            />
          </div>
        ) : (
          <p role="alert" className="mt-3 text-body text-danger">
            {t("sitting.written.deadlinePassed")}
          </p>
        )}
        {submitted ? <p className="mt-2 text-body text-ok">{t("sitting.written.storedEarlier")}</p> : null}
        {draft && !failed && !saveDraft.isPending && !submitted ? (
          <p className="mt-2 text-body text-ok">{t("sitting.written.draftSaved")}</p>
        ) : null}
        {failed && !saveDraft.isPending && !closed ? (
          <Button variant="secondary" className="mt-3" onClick={() => void upload(failed)}>
            {t("sitting.written.tryAgain")}
          </Button>
        ) : null}
        {/* A replacement that did not upload leaves the earlier file on the server: Submit still sends that one, and says so. */}
        {failed && draft && !saveDraft.isPending && !closed && !submitted ? (
          <p className="mt-2 text-body text-text">{t("sitting.written.earlierStillSaved", { name: draft.fileName })}</p>
        ) : null}
        {draft || submitted ? (
          <Button variant="secondary" className="mt-3" onClick={() => setViewing(true)}>
            {t("sitting.written.viewUploaded")}
          </Button>
        ) : null}
        {!draft && !submitted && !closed && !saveDraft.isPending ? (
          <p className="mt-3 text-body font-semibold text-text">{t("sitting.written.pickFirst")}</p>
        ) : null}
        <div className="mt-4 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-body text-muted">{t("sitting.written.replaceNote")}</p>
          <Button
            variant="primary"
            loading={submitExam.isPending}
            disabled={!draft || closed || busy}
            onClick={() => setConfirming(true)}
          >
            {saveDraft.isPending ? `${t("sitting.written.uploading")} ${n(progress)}%` : t("sitting.written.script")}
          </Button>
        </div>
      </Card>

      <Dialog open={viewing} onOpenChange={setViewing} title={t("sitting.written.viewUploaded")} className="sm:max-w-3xl">
        {viewing ? (
          <PdfViewer
            load={() => apiBlob(draft ? answerDraftPath(exam.id) : answerPdfPath(exam.id))}
            allowDownload={false}
            label={t("sitting.written.viewUploaded")}
          />
        ) : null}
      </Dialog>

      <ConfirmDialog
        open={confirming}
        onOpenChange={setConfirming}
        title={t("sitting.written.confirmTitle")}
        body={t("sitting.written.confirmBody", { name: draft?.fileName ?? "" })}
        confirmLabel={t("sitting.written.confirmAction")}
        onConfirm={submit}
      />
    </div>
  );
}
