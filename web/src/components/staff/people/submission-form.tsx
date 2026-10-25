"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Field } from "@/components/ui/field";
import { FileDrop } from "@/components/ui/file-drop";
import { Pill } from "@/components/ui/pill";
import { Textarea } from "@/components/ui/textarea";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { PdfViewer } from "@/components/pdf/pdf-viewer";
import { isDraft } from "@/components/staff/people/people-copy";
import { ApiError } from "@/lib/api/errors";
import {
  answerPdfBlob,
  usePublishResult,
  useSaveDraft,
  type SubmissionRow,
} from "@/lib/api/exam/staff-people";
import { useFormat } from "@/lib/format/use-format";
import { useLocale } from "@/i18n/locale-context";

/** The largest marked PDF the form accepts. */
const MAX_MARKED_BYTES = 20 * 1024 * 1024;

/**
 * The marking screen for one written paper, by role. Instructor and Master Admin
 * read the answer and save a draft mark. Master Admin also publishes. System Admin
 * sees the facts only. A paper with no answer has no form at all.
 */
export function SubmissionForm({
  row,
  examTitle,
  canMark,
  canPublish,
  onSaved,
}: {
  row: SubmissionRow;
  examTitle: string;
  canMark: boolean;
  canPublish: boolean;
  /** Called after a draft saves. `saveAndNext` is true for "সেভ করে পরেরটি". */
  onSaved?: (saved: { saveAndNext: boolean }) => void;
}) {
  const { t } = useLocale();
  const { toast } = useToast();
  const save = useSaveDraft(row.id);
  const publish = usePublishResult(row.id);
  const [marks, setMarks] = useState("");
  const [comment, setComment] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [errors, setErrors] = useState<{ marks?: string; file?: string; form?: string }>({});
  const [confirmPublish, setConfirmPublish] = useState(false);

  const hasAnswer = row.answerPdf && row.status !== "FAILED_TO_SUBMIT";

  if (!hasAnswer) {
    return (
      <Card>
        <p className="text-base text-text">{t("staffPeople.submission.noAnswer")}</p>
        <Facts row={row} examTitle={examTitle} />
      </Card>
    );
  }

  async function onSave(event: FormEvent) {
    event.preventDefault();
    await saveDraft(false);
  }

  async function saveDraft(saveAndNext: boolean) {
    const next: typeof errors = {};
    const value = Number(marks);
    if (marks.trim() === "" || !Number.isInteger(value) || value < 0 || value > row.maximum) {
      next.marks = t("staffPeople.submission.marksRange", { max: String(row.maximum) });
    }
    if (!file) {
      next.file = t("staffPeople.submission.fileRequired");
    }
    setErrors(next);
    if (next.marks || next.file || !file) {
      return;
    }
    try {
      await save.mutateAsync({ marks: value, comment, file });
      setMarks("");
      setComment("");
      setFile(null);
      toast({ title: t("staffPeople.submission.saved"), tone: "ok" });
      onSaved?.({ saveAndNext });
    } catch (caught) {
      const code = caught instanceof ApiError ? caught.code : null;
      setErrors({ form: code === "VALIDATION_FAILED" ? t("staffPeople.submission.marksRange", { max: String(row.maximum) }) : t("common.error.body") });
    }
  }

  return (
    <div className="grid gap-5 lg:grid-cols-2">
      {canMark ? (
        <Card>
          <PdfViewer
            load={() => answerPdfBlob(row.id)}
            allowDownload
            fileName={`${row.studentName}-${examTitle}.pdf`}
            watermark={t("staffPeople.submission.watermark")}
          />
        </Card>
      ) : null}

      <div className="grid content-start gap-5">
        <Facts row={row} examTitle={examTitle} />

        {canMark ? (
          <Card>
            <form className="grid gap-4" onSubmit={onSave} noValidate>
              <Field label={t("staffPeople.submission.marks")} hint={t("staffPeople.submission.marksHint", { max: String(row.maximum) })} error={errors.marks} required>
                <TextInput
                  type="number"
                  inputMode="numeric"
                  min={0}
                  max={row.maximum}
                  value={marks}
                  onChange={(event) => setMarks(event.target.value)}
                  invalid={Boolean(errors.marks)}
                />
              </Field>
              <Field label={t("staffPeople.submission.comment")} hint={t("staffPeople.submission.commentHint")}>
                <Textarea value={comment} onChange={(event) => setComment(event.target.value)} rows={3} />
              </Field>
              <div className="grid gap-1">
                <span className="text-sm font-medium text-text">{t("staffPeople.submission.markedFile")}</span>
                <FileDrop
                  accept="pdf"
                  maxBytes={MAX_MARKED_BYTES}
                  file={file ? { name: file.name, size: file.size } : undefined}
                  onFile={setFile}
                  uploading={save.isPending}
                  error={errors.file}
                />
              </div>
              {errors.form ? <p className="text-sm text-danger">{errors.form}</p> : null}
              <p className="text-sm text-muted">{t("staffPeople.submission.saveNote")}</p>
              <div className="flex flex-wrap gap-2">
                <Button type="submit" loading={save.isPending}>
                  {t("staffPeople.submission.saveDraft")}
                </Button>
                <Button type="button" variant="secondary" loading={save.isPending} onClick={() => saveDraft(true)}>
                  {t("staffPeople.submission.saveAndNext")}
                </Button>
              </div>
            </form>
          </Card>
        ) : null}

        {canPublish ? (
          <Card>
            {row.published ? (
              <Pill tone="ok">{t("staffPeople.submission.published")}</Pill>
            ) : (
              <div className="grid gap-2">
                <p className="text-sm text-muted">{t("staffPeople.submission.publishNote")}</p>
                <div>
                  <Button disabled={!isDraft(row)} onClick={() => setConfirmPublish(true)}>
                    {t("staffPeople.submission.publish")}
                  </Button>
                </div>
              </div>
            )}
            <ConfirmDialog
              open={confirmPublish}
              onOpenChange={setConfirmPublish}
              title={t("staffPeople.submission.publishTitle")}
              body={t("staffPeople.submission.publishBody")}
              confirmLabel={t("staffPeople.submission.publish")}
              onConfirm={async () => {
                await publish.mutateAsync();
                setConfirmPublish(false);
                toast({ title: t("staffPeople.submission.publishedToast"), tone: "ok" });
              }}
            />
          </Card>
        ) : null}
      </div>
    </div>
  );
}

/** The facts of one paper: the same for every role that may open it. The examiner's name is shown to all staff. */
function Facts({ row, examTitle }: { row: SubmissionRow; examTitle: string }) {
  const { t } = useLocale();
  const format = useFormat();
  const yesNo = (value: boolean | null) => (value ? t("staffPeople.yesNo.yes") : t("staffPeople.yesNo.no"));
  return (
    <Card>
      <dl className="grid gap-3 sm:grid-cols-2">
        <Fact label={t("staffPeople.submission.student")} value={row.studentName} />
        <Fact label={t("staffPeople.submission.exam")} value={examTitle} />
        <Fact label={t("staffPeople.submission.status")} value={t(`staffPeople.submission.statusWord.${row.status}`)} />
        <Fact label={t("staffPeople.submission.kind")} value={t(`staffPeople.submission.kindWord.${row.kind}`)} />
        <Fact label={t("staffPeople.submission.score")} value={row.score === null ? DASH : `${format.formatNumber(row.score)} / ${format.formatNumber(row.maximum)}`} />
        <Fact label={t("staffPeople.submission.answerFile")} value={yesNo(row.answerPdf)} />
        <Fact label={t("staffPeople.submission.evaluatedFile")} value={yesNo(row.evaluatedPdf)} />
        <Fact label={t("staffPeople.submission.examiner")} value={row.evaluatorName ?? DASH} />
        <Fact label={t("staffPeople.submission.published")} value={yesNo(row.published)} />
      </dl>
    </Card>
  );
}

const DASH = "—";

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div className="grid gap-1">
      <dt className="text-sm text-muted">{label}</dt>
      <dd className="text-text">{value}</dd>
    </div>
  );
}
