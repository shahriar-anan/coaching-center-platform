"use client";

import { useState, type FormEvent, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Field } from "@/components/ui/field";
import { FileDrop } from "@/components/ui/file-drop";
import { Pill } from "@/components/ui/pill";
import { Tabs } from "@/components/ui/tabs";
import { cn } from "@/components/ui/cn";
import { StickyActionBar } from "@/components/ui/sticky-action-bar";
import { Textarea } from "@/components/ui/textarea";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { CommentSnippets } from "@/components/staff/marking/comment-snippets";
import { isTodayInDhaka, paperStatus, statusTone, statusWordKey } from "@/components/staff/marking/marking-queue";
import { MarkedPdfView } from "@/components/staff/marking/marked-pdf-view";
import { useLocale } from "@/i18n/locale-context";
import { ApiError } from "@/lib/api/errors";
import { usePublishPaper, useSaveMarking, useSetReady, type MarkingPaper } from "@/lib/api/exam/staff-marking";
import { isMissing } from "@/lib/api/missing";
import { useFormat } from "@/lib/format/use-format";
import { useDraftForm } from "@/lib/hooks/use-draft-form";
import { useUnsavedChangesGuard } from "@/lib/hooks/use-unsaved-changes-guard";

/** The largest marked PDF the form accepts. The server's limit for answer and marked PDFs is 80 MiB. */
const MAX_MARKED_BYTES = 80 * 1024 * 1024;
const MAX_MARKED_MB = 80;

type Marks = { marks: string; comment: string };

/**
 * The marking form for one written paper. Instructor and Master Admin save a draft
 * mark; Master Admin also publishes. System Admin sees the facts only. A paper with no
 * answer has no form. The marks and comment are kept as a draft until a save succeeds.
 */
export function SubmissionForm({
  paper,
  canMark,
  canPublish,
  onSaved,
}: {
  paper: MarkingPaper;
  canMark: boolean;
  canPublish: boolean;
  /** Called after a draft saves. `saveAndNext` is true for "সেভ করে পরেরটি". */
  onSaved?: (saved: { saveAndNext: boolean }) => void;
}) {
  const { t } = useLocale();
  const format = useFormat();
  const { toast } = useToast();
  const save = useSaveMarking(paper.id);
  const publish = usePublishPaper(paper.id);
  const setReady = useSetReady(paper.id);

  const saved: Marks = { marks: (paper.score ?? null) === null ? "" : String(paper.score), comment: paper.comment ?? "" };
  const draft = useDraftForm<Marks>(`marking:${paper.id}`, saved);
  const { marks, comment } = draft.value;
  const [file, setFile] = useState<File | null>(null);
  const [errors, setErrors] = useState<{ marks?: string; file?: string; form?: string }>({});
  const [confirmPublish, setConfirmPublish] = useState(false);
  // The grading panel is three tabs, so nothing needs a long scroll. A marker starts on the marks.
  const [panel, setPanel] = useState<"mark" | "details" | "release">(canMark ? "mark" : "details");

  const dirty = marks !== saved.marks || comment !== saved.comment || file !== null;
  useUnsavedChangesGuard(dirty && canMark, t("staffMarking.form.unsaved"));

  const hasAnswer = paper.answerPdf && paper.status !== "FAILED_TO_SUBMIT";
  if (!hasAnswer) {
    // The note and the facts are one card each; the facts card is not nested in the note's card.
    return (
      <div className="grid gap-4">
        <Card>
          <p className="text-body text-text">{t("staffMarking.form.noAnswer")}</p>
        </Card>
        <StatusLine paper={paper} />
        <Facts paper={paper} />
      </div>
    );
  }

  const maximum = format.formatNumber(paper.maximum);

  function setField(patch: Partial<Marks>) {
    draft.setValue({ ...draft.value, ...patch });
  }

  async function saveDraft(saveAndNext: boolean) {
    const next: typeof errors = {};
    const value = Number(marks);
    if (marks.trim() === "" || !Number.isInteger(value) || value < 0 || value > paper.maximum) {
      next.marks = t("staffMarking.form.marksRange", { max: maximum });
    }
    // A new marked PDF is needed only when none is stored yet; otherwise a save keeps the stored one.
    const needsFile = !file && !paper.evaluatedPdf;
    if (needsFile) {
      next.file = t("staffMarking.form.fileRequired");
    }
    setErrors(next);
    if (next.marks || needsFile) {
      return;
    }
    try {
      await save.mutateAsync({ marks: value, comment, file });
      draft.clear();
      setFile(null);
      toast({ title: t("staffMarking.form.saved"), tone: "ok" });
      onSaved?.({ saveAndNext });
    } catch (caught) {
      setErrors({ form: saveErrorText(caught) });
    }
  }

  function saveErrorText(caught: unknown): string {
    if (caught instanceof ApiError) {
      if (caught.code === "FILE_TOO_LARGE") {
        return t("staffMarking.form.fileTooLarge", { max: format.formatNumber(MAX_MARKED_MB) });
      }
      if (caught.code === "VALIDATION_FAILED") {
        return t("staffMarking.form.marksRange", { max: maximum });
      }
    }
    return t("common.error.body");
  }

  async function toggleReady() {
    try {
      await setReady.mutateAsync(!paper.readyForRelease);
      toast({
        title: t(paper.readyForRelease ? "staffMarking.form.clearedReady" : "staffMarking.form.markedReady"),
        tone: "ok",
      });
    } catch (caught) {
      setErrors({ form: saveErrorText(caught) });
    }
  }

  async function onSave(event: FormEvent) {
    event.preventDefault();
    await saveDraft(false);
  }

  const status = paperStatus(paper);

  return (
    <div className="grid content-start gap-4">
      <StatusLine paper={paper} />
      <Tabs
        ariaLabel={t("staffMarking.panel.label")}
        tabs={[
          ...(canMark ? [{ value: "mark", label: t("staffMarking.panel.mark") }] : []),
          { value: "details", label: t("staffMarking.panel.details") },
          ...(canMark || canPublish ? [{ value: "release", label: t("staffMarking.panel.release") }] : []),
        ]}
        value={panel}
        onChange={(value) => setPanel(value as typeof panel)}
      />

      {/* Every panel stays mounted and only the chosen one shows, so a typed mark or a chosen file is kept when you switch tabs. */}
      <div className={cn("grid content-start gap-4", panel !== "mark" && "hidden")}>
      {paper.evaluatedPdf ? (
        <MarkedPdfView
          participantId={paper.id}
          fileName={`${paper.studentName}-${format.pickTitle({ titleEn: paper.examTitleEn, titleBn: paper.examTitleBn })}-marked.pdf`}
        />
      ) : null}

      {draft.hasDraft ? (
        <Card>
          <div className="grid gap-3">
            <p className="text-meta text-muted">{t("staffMarking.form.draftFound")}</p>
            <div className="flex flex-wrap gap-2">
              <Button type="button" variant="secondary" onClick={draft.restore}>
                {t("staffMarking.form.restoreDraft")}
              </Button>
              <Button type="button" variant="ghost" onClick={draft.clear}>
                {t("staffMarking.form.discardDraft")}
              </Button>
            </div>
          </div>
        </Card>
      ) : null}

      {canMark ? (
        <Card>
          <form className="grid gap-6" onSubmit={onSave} noValidate>
              {/* Said before the fields, so the marker knows the file rule before starting. */}
              <p className="text-meta text-muted">{t("staffMarking.form.markedFileRule")}</p>
              <section id="marks" className="grid gap-4 scroll-mt-24">
                <Field
                  label={t("staffMarking.form.marks")}
                  hint={t("staffMarking.form.marksHint", { max: maximum })}
                  error={errors.marks}
                  required
                >
                  <TextInput
                    type="number"
                    inputMode="numeric"
                    min={0}
                    max={paper.maximum}
                    value={marks}
                    onChange={(event) => setField({ marks: event.target.value })}
                    invalid={Boolean(errors.marks)}
                  />
                </Field>
              </section>

              <section id="comment" className="grid gap-3 scroll-mt-24">
                <Field label={t("staffMarking.form.comment")} hint={t("staffMarking.form.commentHint")}>
                  <Textarea value={comment} onChange={(event) => setField({ comment: event.target.value })} rows={3} />
                </Field>
                <CommentSnippets
                  comment={comment}
                  onInsert={(text) => setField({ comment: comment.trim() ? `${comment.trim()} ${text}` : text })}
                />
              </section>

              <section id="marked-file" className="grid gap-2 scroll-mt-24">
                <span className="text-label font-medium text-text">{t("staffMarking.form.markedFile")}</span>
                {paper.evaluatedPdf ? null : (
                  <span className="text-meta text-danger">{t("staffMarking.form.markedFileRequired")}</span>
                )}
                {paper.evaluatedFileName ? (
                  <p className="text-meta text-text">
                    {t("staffMarking.form.storedFile", { name: paper.evaluatedFileName })}
                  </p>
                ) : null}
                <FileDrop
                  accept="pdf"
                  maxBytes={MAX_MARKED_BYTES}
                  file={file ? { name: file.name, size: file.size } : undefined}
                  onFile={setFile}
                  uploading={save.isPending}
                  error={errors.file}
                />
                <p className="text-meta text-muted">
                  {t("staffMarking.form.fileLimit", { max: format.formatNumber(MAX_MARKED_MB) })}
                </p>
              </section>

              {errors.form ? <p className="text-meta text-danger">{errors.form}</p> : null}
              <p className="text-meta text-muted">{t("staffMarking.form.saveNote")}</p>

              <StickyActionBar>
                <Button type="submit" loading={save.isPending}>
                  {t("staffMarking.form.saveDraft")}
                </Button>
                <Button type="button" variant="secondary" loading={save.isPending} onClick={() => saveDraft(true)}>
                  {t("staffMarking.form.saveAndNext")}
                </Button>
              </StickyActionBar>
            </form>
        </Card>
      ) : null}
      </div>

      <div className={cn("grid content-start gap-4", panel !== "details" && "hidden")}>
        <Facts paper={paper} />
        {!canPublish && !paper.published ? (
          <p className="text-meta text-muted">{t("staffMarking.publish.instructorNote")}</p>
        ) : null}
      </div>

      {/* Release: the ready flag and the publish action live in one card, so it is clear which one releases. */}
      <div className={cn(panel !== "release" && "hidden")}>
      <Card>
        <div className="grid gap-4">
          <h2 className="text-card-title font-semibold text-text">{t("staffResults.release.title")}</h2>

          {canMark && paper.status !== "FAILED_TO_SUBMIT" && paper.score !== null ? (
            <div className="grid gap-2">
              <p className="text-meta text-muted">{t("staffMarking.form.readyNote")}</p>
              <div>
                <Button variant="secondary" loading={setReady.isPending} onClick={() => toggleReady()}>
                  {paper.readyForRelease ? t("staffMarking.form.clearReady") : t("staffMarking.form.markReady")}
                </Button>
              </div>
            </div>
          ) : null}

          {canPublish && paper.published ? (
            <div>
              <Pill tone="ok">{t("staffMarking.publish.published")}</Pill>
            </div>
          ) : null}

          {canPublish && !paper.published ? (
            <div className="grid gap-2 border-t border-border pt-4">
              <p className="text-meta text-muted">
                {paper.releasePending ? t("staffMarking.publish.republishNote") : t("staffMarking.publish.note")}
              </p>
              <div>
                <Button disabled={status !== "DRAFT"} onClick={() => setConfirmPublish(true)}>
                  {paper.releasePending ? t("staffMarking.publish.releaseChange") : t("staffMarking.publish.action")}
                </Button>
              </div>
            </div>
          ) : null}

        </div>
        {canPublish ? (
          <ConfirmDialog
            open={confirmPublish}
            onOpenChange={setConfirmPublish}
            title={t("staffMarking.publish.confirmTitle")}
            body={t("staffMarking.publish.confirmBody")}
            confirmLabel={paper.releasePending ? t("staffMarking.publish.releaseChange") : t("staffMarking.publish.action")}
            onConfirm={async () => {
              await publish.mutateAsync();
              setConfirmPublish(false);
              toast({ title: t("staffMarking.publish.done"), tone: "ok" });
            }}
          />
        ) : null}
      </Card>
      </div>
    </div>
  );
}

/** The facts of one paper. The examiner's name is shown to every staff member who may open the paper. */
function Facts({ paper }: { paper: MarkingPaper }) {
  const { t } = useLocale();
  const format = useFormat();
  const yesNo = (value: boolean | null) => (value ? t("staffMarking.yesNo.yes") : t("staffMarking.yesNo.no"));
  const examTitle = format.pickTitle({ titleEn: paper.examTitleEn, titleBn: paper.examTitleBn });
  const moduleTitle = format.pickTitle({ titleEn: paper.moduleTitleEn, titleBn: paper.moduleTitleBn });
  const submitted =
    isMissing(paper.submittedAt)
      ? DASH
      : isTodayInDhaka(paper.submittedAt)
        ? t("staffMarking.facts.today", { time: format.formatTime(paper.submittedAt) })
        : format.formatDateTime(paper.submittedAt);

  return (
    <Card>
      <dl className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-2">
        <Fact label={t("staffMarking.facts.exam")} value={examTitle} />
        <Fact label={t("staffMarking.facts.module")} value={moduleTitle} />
        <Fact label={t("staffMarking.facts.kind")} value={t(`staffMarking.kindWord.${paper.kind}`)} />
        <Fact label={t("staffMarking.facts.submitted")} value={submitted} />
        <Fact label={t("staffMarking.facts.answerFile")} value={yesNo(paper.answerPdf)} />
        <Fact label={t("staffMarking.facts.examiner")} value={paper.evaluatorName ?? DASH} />
        <Fact
          label={t("staffMarking.facts.published")}
          value={paper.releasePending ? `${yesNo(true)} · ${t("staffMarking.facts.changeWaiting")}` : yesNo(paper.published)}
        />
      </dl>
    </Card>
  );
}

/** Status and score, always in view above the grading tabs. The student's name is the page title. */
function StatusLine({ paper }: { paper: MarkingPaper }) {
  const { t } = useLocale();
  const format = useFormat();
  const status = paperStatus(paper);
  const wordKey = statusWordKey({ status, releasePending: paper.releasePending, readyForRelease: paper.readyForRelease });
  const score =
    isMissing(paper.score) || paper.status === "FAILED_TO_SUBMIT"
      ? DASH
      : `${format.formatNumber(paper.score)} / ${format.formatNumber(paper.maximum)}`;
  return (
    <Card>
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="grid gap-1">
          <span className="text-meta text-muted">{t("staffMarking.facts.status")}</span>
          <Pill tone={statusTone(status)}>{t(`staffMarking.statusWord.${wordKey}`)}</Pill>
        </div>
        <div className="grid gap-1 text-end">
          <span className="text-meta text-muted">{t("staffMarking.facts.score")}</span>
          <span className="text-section font-semibold tabular-nums text-text">{score}</span>
        </div>
      </div>
    </Card>
  );
}

const DASH = "—";

function Fact({ label, value }: { label: string; value: ReactNode }) {
  return (
    <>
      <dt className="text-meta text-muted">{label}</dt>
      <dd className="min-w-0 wrap-break-word text-meta text-text">{value}</dd>
    </>
  );
}
