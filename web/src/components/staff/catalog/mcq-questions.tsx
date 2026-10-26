"use client";

import { useState } from "react";
import dynamic from "next/dynamic";
import { ArrowDown, ArrowUp, Pencil, Trash2 } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useDeleteQuestion, useUpdateQuestion, type StaffExam, type StaffQuestion } from "@/lib/api/exam/staff-catalog";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { IconButton } from "@/components/ui/icon-button";
import { Skeleton } from "@/components/ui/skeleton";
import { useToast } from "@/components/ui/toast";
import { catalogErrorKey } from "@/components/staff/catalog/errors";
import { PasteQuestions } from "@/components/staff/catalog/paste-questions";

/** The editor is only needed when a question is opened, so it loads on demand. */
const QuestionEditor = dynamic(
  () => import("@/components/staff/catalog/question-editor").then((m) => m.QuestionEditor),
  { ssr: false, loading: () => <Skeleton variant="card" /> },
);

function shortStem(stem: string) {
  return stem.length > 48 ? `${stem.slice(0, 48)}…` : stem;
}

/**
 * The MCQ paper as staff see it: every question with its options and the one
 * correct mark, a running total, and add, edit, reorder and delete. Nothing
 * here is shown to students.
 */
export function McqQuestions({ exam, locked, canDelete }: { exam: StaffExam; locked: boolean; canDelete: boolean }) {
  const { t, locale } = useLocale();
  const { formatNumber } = useFormat();
  const { toast } = useToast();
  const questions = [...(exam.questions ?? [])].sort((a, b) => a.position - b.position);
  const total = questions.reduce((sum, q) => sum + q.marks, 0);
  // A question with no correct option cannot be marked: the summary links to the first one.
  const missing = questions.filter((q) => !q.options.some((o) => o.correct));
  const missingCorrect = missing.length;
  const firstMissingCorrect = missing[0]?.id;
  const update = useUpdateQuestion(exam.id);
  const remove = useDeleteQuestion(exam.id);
  const [editing, setEditing] = useState<{ question?: StaffQuestion } | null>(null);
  const [pasting, setPasting] = useState(false);
  const [deleting, setDeleting] = useState<StaffQuestion | null>(null);
  const [moveError, setMoveError] = useState<string | null>(null);
  const nextPosition = questions.length === 0 ? 1 : Math.max(...questions.map((q) => q.position)) + 1;

  async function move(index: number, direction: -1 | 1) {
    const other = questions[index + direction];
    const current = questions[index];
    if (!other || !current) {
      return;
    }
    setMoveError(null);
    try {
      await update.mutateAsync({ questionId: current.id, body: { position: other.position } });
      await update.mutateAsync({ questionId: other.id, body: { position: current.position } });
    } catch (e) {
      setMoveError(t(catalogErrorKey(e)));
    }
  }

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-label font-medium tabular-nums" aria-live="polite">
          {t("staffCatalog.mcq.summary", { count: formatNumber(questions.length), total: formatNumber(total) })}
          {missingCorrect > 0 ? (
            <>
              {" · "}
              <a href={`#question-${firstMissingCorrect}`} className="text-warn underline-offset-2 hover:underline">
                {t("staffCatalog.mcq.missingCorrect", { n: formatNumber(missingCorrect) })}
              </a>
            </>
          ) : null}
        </p>
        {!locked ? (
          <Button variant="secondary" onClick={() => setPasting(true)}>
            {t("staffCatalog.paste.open")}
          </Button>
        ) : null}
      </div>
      <PasteQuestions exam={exam} nextPosition={nextPosition} open={pasting} onOpenChange={setPasting} />

      {questions.length === 0 ? (
        <EmptyState title={t("staffCatalog.mcq.emptyTitle")} body={t("staffCatalog.mcq.emptyBody")} />
      ) : (
        <ol className="grid gap-3">
          {questions.map((question, index) => (
            <li key={question.id} id={`question-${question.id}`} className="grid scroll-mt-24 gap-3 rounded-card border border-border bg-card p-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <p className="text-sm text-muted">
                    {t("staffCatalog.mcq.questionNumber", { n: formatNumber(index + 1) })} · {t("staffCatalog.mcq.marksOf", { marks: formatNumber(question.marks) })}
                  </p>
                  <p className="mt-1 font-medium">{question.stem}</p>
                </div>
                {!locked ? (
                  <div className="flex items-center gap-1">
                    <IconButton label={t("staffCatalog.mcq.moveUp")} icon={<ArrowUp className="size-4" />} disabled={index === 0 || update.isPending} onClick={() => move(index, -1)} />
                    <IconButton label={t("staffCatalog.mcq.moveDown")} icon={<ArrowDown className="size-4" />} disabled={index === questions.length - 1 || update.isPending} onClick={() => move(index, 1)} />
                    <IconButton label={t("staffCatalog.mcq.edit")} icon={<Pencil className="size-4" />} onClick={() => setEditing({ question })} />
                    {canDelete ? (
                      <IconButton label={t("staffCatalog.mcq.delete")} icon={<Trash2 className="size-4" />} onClick={() => setDeleting(question)} />
                    ) : null}
                  </div>
                ) : null}
              </div>
              <ul className="grid gap-1.5">
                {[...question.options]
                  .sort((a, b) => a.position - b.position)
                  .map((option, i) => (
                    <li key={option.id} className="flex items-start gap-2 text-[15px]">
                      <span aria-hidden="true" className="w-6 shrink-0 text-muted">
                        {locale === "bn" ? ["ক", "খ", "গ", "ঘ", "ঙ", "চ"][i] : String.fromCharCode(65 + i)}
                      </span>
                      <span className={option.correct ? "font-semibold text-ok" : undefined}>{option.text}</span>
                      {option.correct ? <span className="text-sm text-ok">· {t("staffCatalog.mcq.correctMark")}</span> : null}
                    </li>
                  ))}
              </ul>
              {question.explanation ? <p className="text-sm text-muted">{t("staffCatalog.mcq.explanation", { text: question.explanation })}</p> : null}
            </li>
          ))}
        </ol>
      )}

      {moveError ? (
        <p role="alert" className="text-sm text-danger">
          {moveError}
        </p>
      ) : null}

      {!locked ? (
        <div>
          <Button onClick={() => setEditing({})}>{t("staffCatalog.mcq.add")}</Button>
        </div>
      ) : null}

      {editing ? (
        <QuestionEditor
          examId={exam.id}
          question={editing.question}
          nextPosition={nextPosition}
          open
          onOpenChange={(open) => !open && setEditing(null)}
        />
      ) : null}

      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => !open && setDeleting(null)}
        title={t("staffCatalog.mcq.deleteTitle", { stem: shortStem(deleting?.stem ?? "") })}
        body={t("staffCatalog.mcq.deleteBody")}
        confirmLabel={t("staffCatalog.mcq.deleteConfirm")}
        tone="danger"
        onConfirm={async () => {
          if (!deleting) {
            return;
          }
          try {
            await remove.mutateAsync(deleting.id);
            setDeleting(null);
            toast({ title: t("staffCatalog.mcq.deleted"), tone: "ok" });
          } catch (e) {
            throw new Error(t(catalogErrorKey(e)));
          }
        }}
      />
    </div>
  );
}
