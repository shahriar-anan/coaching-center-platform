"use client";

import { useState, type FormEvent, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Field } from "@/components/ui/field";
import { FileDrop } from "@/components/ui/file-drop";
import { Pill } from "@/components/ui/pill";
import { SectionNav, type SectionNavItem } from "@/components/ui/section-nav";
import { StickyActionBar } from "@/components/ui/sticky-action-bar";
import { Textarea } from "@/components/ui/textarea";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { CommentSnippets } from "@/components/staff/marking/comment-snippets";
import { isTodayInDhaka, paperStatus, statusTone } from "@/components/staff/marking/marking-queue";
import { useLocale } from "@/i18n/locale-context";
import { ApiError } from "@/lib/api/errors";
import { usePublishPaper, useSaveMarking, type MarkingPaper } from "@/lib/api/exam/staff-marking";
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

  const saved: Marks = { marks: paper.score === null ? "" : String(paper.score), comment: paper.comment ?? "" };
  const draft = useDraftForm<Marks>(`marking:${paper.id}`, saved);
  const { marks, comment } = draft.value;
  const [file, setFile] = useState<File | null>(null);
  const [errors, setErrors] = useState<{ marks?: string; file?: string; form?: string }>({});
  const [confirmPublish, setConfirmPublish] = useState(false);

  const dirty = marks !== saved.marks || comment !== saved.comment || file !== null;
  useUnsavedChangesGuard(dirty && canMark, t("staffMarking.form.unsaved"));

  const hasAnswer = paper.answerPdf && paper.status !== "FAILED_TO_SUBMIT";
  if (!hasAnswer) {
    return (
      <Card>
        <p className="text-body text-text">{t("staffMarking.form.noAnswer")}</p>
        <Facts paper={paper} />
      </Card>
    );
  }

  const maximum = format.formatNumber(paper.maximum);
  const sections: SectionNavItem[] = [
    { id: "marks", label: t("staffMarking.sections.marks"), state: errors.marks ? "error" : paper.score === null ? "todo" : "done" },
    { id: "comment", label: t("staffMarking.sections.comment"), state: paper.comment ? "done" : "todo" },
    { id: "marked-file", label: t("staffMarking.sections.file"), state: errors.file ? "error" : paper.evaluatedPdf ? "done" : "todo" },
  ];

  function setField(patch: Partial<Marks>) {
    draft.setValue({ ...draft.value, ...patch });
  }

  async function saveDraft(saveAndNext: boolean) {
    const next: typeof errors = {};
    const value = Number(marks);
    if (marks.trim() === "" || !Number.isInteger(value) || value < 0 || value > paper.maximum) {
      next.marks = t("staffMarking.form.marksRange", { max: maximum });
    }
    if (!file) {
      next.file = t("staffMarking.form.fileRequired");
    }
    setErrors(next);
    if (next.marks || !file) {
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

  async function onSave(event: FormEvent) {
    event.preventDefault();
    await saveDraft(false);
  }

  const status = paperStatus(paper);

  return (
    <div className="grid content-start gap-5">
      <Facts paper={paper} />

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
        <div className="grid gap-5 md:grid-cols-[11rem_minmax(0,1fr)]">
          <div className="hidden md:block">
            <SectionNav sections={sections} />
          </div>
          <Card>
            <form className="grid gap-6" onSubmit={onSave} noValidate>
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
        </div>
      ) : null}

      {canPublish ? (
        <Card>
          {paper.published ? (
            <div className="grid gap-2">
              <Pill tone="ok">{t("staffMarking.publish.published")}</Pill>
              <p className="text-meta text-muted">{t("staffMarking.publish.republishNote")}</p>
            </div>
          ) : (
            <div className="grid gap-2">
              <p className="text-meta text-muted">{t("staffMarking.publish.note")}</p>
              <div>
                <Button disabled={status !== "DRAFT"} onClick={() => setConfirmPublish(true)}>
                  {t("staffMarking.publish.action")}
                </Button>
              </div>
            </div>
          )}
          <ConfirmDialog
            open={confirmPublish}
            onOpenChange={setConfirmPublish}
            title={t("staffMarking.publish.confirmTitle")}
            body={t("staffMarking.publish.confirmBody")}
            confirmLabel={t("staffMarking.publish.action")}
            onConfirm={async () => {
              await publish.mutateAsync();
              setConfirmPublish(false);
              toast({ title: t("staffMarking.publish.done"), tone: "ok" });
            }}
          />
        </Card>
      ) : null}
    </div>
  );
}

/** The facts of one paper. The examiner's name is shown to every staff member who may open the paper. */
function Facts({ paper }: { paper: MarkingPaper }) {
  const { t } = useLocale();
  const format = useFormat();
  const status = paperStatus(paper);
  const yesNo = (value: boolean | null) => (value ? t("staffMarking.yesNo.yes") : t("staffMarking.yesNo.no"));
  const examTitle = format.pickTitle({ titleEn: paper.examTitleEn, titleBn: paper.examTitleBn });
  const moduleTitle = format.pickTitle({ titleEn: paper.moduleTitleEn, titleBn: paper.moduleTitleBn });
  const submitted =
    paper.submittedAt === null
      ? DASH
      : isTodayInDhaka(paper.submittedAt)
        ? t("staffMarking.facts.today", { time: format.formatTime(paper.submittedAt) })
        : format.formatDateTime(paper.submittedAt);

  return (
    <Card>
      <dl className="grid gap-3 sm:grid-cols-2">
        <Fact label={t("staffMarking.facts.student")} value={paper.studentName} />
        <Fact label={t("staffMarking.facts.exam")} value={examTitle} />
        <Fact label={t("staffMarking.facts.module")} value={moduleTitle} />
        <Fact
          label={t("staffMarking.facts.status")}
          value={<Pill tone={statusTone(status)}>{t(`staffMarking.statusWord.${status}`)}</Pill>}
        />
        <Fact label={t("staffMarking.facts.kind")} value={t(`staffMarking.kindWord.${paper.kind}`)} />
        <Fact label={t("staffMarking.facts.submitted")} value={submitted} />
        <Fact
          label={t("staffMarking.facts.score")}
          value={paper.score === null ? DASH : `${format.formatNumber(paper.score)} / ${format.formatNumber(paper.maximum)}`}
        />
        <Fact label={t("staffMarking.facts.answerFile")} value={yesNo(paper.answerPdf)} />
        <Fact label={t("staffMarking.facts.examiner")} value={paper.evaluatorName ?? DASH} />
        <Fact label={t("staffMarking.facts.published")} value={yesNo(paper.published)} />
      </dl>
    </Card>
  );
}

const DASH = "—";

function Fact({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="grid gap-1">
      <dt className="text-meta text-muted">{label}</dt>
      <dd className="text-body text-text">{value}</dd>
    </div>
  );
}
