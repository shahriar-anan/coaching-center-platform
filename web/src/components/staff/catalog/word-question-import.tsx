"use client";

import { useRef, useState, type ChangeEvent } from "react";
import { Check } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { useToast } from "@/components/ui/toast";
import { useFormat } from "@/lib/format/use-format";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import {
  useApplyQuestionFile,
  useQuestionFilePreview,
  type QuestionFilePreview,
  type StaffExam,
} from "@/lib/api/exam/staff-catalog";
import { catalogErrorKey } from "@/components/staff/catalog/errors";
import { optionLetter } from "@/components/staff/questions/question-form";
import { EditImpactNotes, editNeedsPrompt } from "@/components/staff/catalog/edit-impact";
import { useRole } from "@/lib/auth/guards";

/**
 * Replaces the exam's questions from a client's Word file (plan: docs/exam-site/plans/mcq-docx-upload). The file is read first
 * and shown; nothing is saved until staff confirm. A file with a problem cannot be saved.
 */
export function WordQuestionImport({ exam }: { exam: StaffExam }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const format = useFormat();
  const role = useRole();
  const inputRef = useRef<HTMLInputElement>(null);
  const preview = useQuestionFilePreview(exam.id);
  const apply = useApplyQuestionFile(exam.id);
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<QuestionFilePreview | null>(null);
  const [error, setError] = useState<string | null>(null);

  function close() {
    setResult(null);
    setFile(null);
    setError(null);
  }

  async function onPick(event: ChangeEvent<HTMLInputElement>) {
    const picked = event.target.files?.[0];
    event.target.value = "";
    if (!picked) {
      return;
    }
    setError(null);
    try {
      setResult(await preview.mutateAsync(picked));
      setFile(picked);
    } catch (caught) {
      setError(t(catalogErrorKey(caught)));
    }
  }

  async function onConfirm() {
    if (!file || !result) {
      return;
    }
    setError(null);
    try {
      const saved = await apply.mutateAsync(file);
      toast({
        title: saved.staged
          ? t("staffCatalog.import.stagedSaved")
          : t("staffCatalog.import.saved", { count: format.formatNumber(saved.questions) }),
        tone: "ok",
      });
      close();
    } catch (caught) {
      setError(t(catalogErrorKey(caught)));
    }
  }

  const blocked = result !== null && result.problems.length > 0;
  const counts = result
    ? {
        current: format.formatNumber(result.currentQuestions),
        next: format.formatNumber(result.questions.length),
      }
    : null;

  return (
    <>
      <Button variant="secondary" onClick={() => inputRef.current?.click()} disabled={preview.isPending}>
        {t("staffCatalog.import.open")}
      </Button>
      <input
        ref={inputRef}
        type="file"
        accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        className="sr-only"
        tabIndex={-1}
        aria-hidden="true"
        onChange={onPick}
      />
      {/* A failed read shows here; once the dialog is open, a failed save shows inside it. */}
      {error && !result ? (
        <p role="alert" className="text-meta text-danger">
          {error}
        </p>
      ) : null}

      {result && counts ? (
        <Dialog
          open
          onOpenChange={(open) => {
            if (!open) {
              close();
            }
          }}
          title={t("staffCatalog.import.title")}
          className="max-w-2xl"
          footer={
            <div className="flex w-full flex-wrap justify-end gap-3">
              {error ? (
                <p role="alert" className="w-full text-meta text-danger">
                  {error}
                </p>
              ) : null}
              <Button variant="secondary" onClick={close} disabled={apply.isPending}>
                {t("common.cancel")}
              </Button>
              <Button onClick={onConfirm} disabled={blocked} loading={apply.isPending}>
                {result.currentQuestions > 0
                  ? t("staffCatalog.import.confirmReplace", counts)
                  : t("staffCatalog.import.confirmCreate", { next: counts.next })}
              </Button>
            </div>
          }
        >
          <div className="grid gap-4 text-body">
            {result.header.length > 0 ? (
              <div className="grid gap-1 rounded-control border border-border bg-bg p-3 text-center">
                <p className="text-meta text-muted">{t("staffCatalog.import.headerTitle")}</p>
                {result.header.map((line, index) => (
                  <p key={index} className="font-medium">
                    {line}
                  </p>
                ))}
              </div>
            ) : null}

            <p className="text-muted">
              {t("staffCatalog.import.facts", {
                questions: counts.next,
                marks: result.fullMarks === null ? "—" : format.formatNumber(result.fullMarks),
                minutes: result.durationMinutes === null ? "—" : format.formatNumber(result.durationMinutes),
              })}
            </p>

            {/* The replacement is shown before anything is saved, and confirmed with the button below (U4). */}
            <p role="status" className="rounded-control border border-warn/40 bg-warn/10 p-3 font-medium">
              {result.currentQuestions > 0
                ? t("staffCatalog.import.replaces", counts)
                : t("staffCatalog.import.creates", { next: counts.next })}
            </p>
            {editNeedsPrompt(exam) ? (
              <EditImpactNotes
                exam={exam}
                kind="replace"
                masterAdmin={role.canPublishExam}
                totalChanges={result.questions.length !== (exam.questions ?? []).reduce((sum, question) => sum + question.marks, 0)}
              />
            ) : null}

            {result.problems.length > 0 ? (
              <div className="grid gap-2 rounded-control border border-danger/40 bg-danger/10 p-3">
                <p className="font-semibold text-danger">{t("staffCatalog.import.problemsTitle")}</p>
                <ul className="grid list-disc gap-1 ps-5">
                  {result.problems.map((problem, index) => (
                    <li key={index}>
                      {t(`staffCatalog.import.problem.${problem.code}`, {
                        question: problem.question === null ? "" : format.formatNumber(problem.question),
                        minutes: result.durationMinutes === null ? "" : format.formatNumber(result.durationMinutes),
                      })}
                    </li>
                  ))}
                </ul>
              </div>
            ) : null}

            {result.warnings.length > 0 ? (
              <div className="grid gap-2 rounded-control border border-border p-3">
                <p className="font-semibold">{t("staffCatalog.import.warningsTitle")}</p>
                <ul className="grid list-disc gap-1 ps-5">
                  {result.warnings.map((code) => (
                    <li key={code}>{t(`staffCatalog.import.warning.${code}`)}</li>
                  ))}
                </ul>
              </div>
            ) : null}

            <ol className="grid max-h-[50vh] gap-3 overflow-y-auto pe-2">
              {result.questions.map((question) => (
                <li key={question.number} className="grid gap-1 rounded-control border border-border p-3">
                  <p className="font-semibold">
                    {t("staffCatalog.import.questionNumber", { number: format.formatNumber(question.number) })}
                    {". "}
                    {question.stem}
                  </p>
                  <ul className="grid gap-1">
                    {question.options.map((option, index) => (
                      <li
                        key={index}
                        className={`flex items-start gap-2 rounded-control px-2 py-1 ${option.correct ? "bg-ok/10 font-semibold text-ok-text" : ""}`}
                      >
                        {/* The file's paper is Bangla, so its letters are ক খ গ ঘ on either screen language. */}
                        <span aria-hidden="true" className="w-5 shrink-0">
                          {optionLetter(index, "bn")}.
                        </span>
                        <span className="min-w-0 flex-1">{option.text}</span>
                        {option.correct ? (
                          <span className="flex shrink-0 items-center gap-1 text-meta">
                            <Check aria-hidden="true" className="size-4" />
                            {t("staffCatalog.mcq.correctMark")}
                          </span>
                        ) : null}
                      </li>
                    ))}
                  </ul>
                  {question.explanation ? (
                    <p className="rounded-control bg-bg px-2 py-1 text-meta text-muted">
                      {t("staffCatalog.mcq.explanation", { text: question.explanation })}
                    </p>
                  ) : null}
                </li>
              ))}
            </ol>
          </div>
        </Dialog>
      ) : null}
    </>
  );
}
