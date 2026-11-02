"use client";

import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import type { StaffExam } from "@/lib/api/exam/staff-catalog";
import { optionLetter } from "@/components/staff/questions/question-form";

/**
 * The MCQ paper as the exam page shows it: every question with its options and the one correct mark, and the running total.
 * Changes are made on the question editor page, which has the navigator, the editor and the student preview.
 */
export function McqQuestions({ exam }: { exam: StaffExam }) {
  const { t, locale } = useLocale();
  const { formatNumber } = useFormat();
  const questions = [...(exam.questions ?? [])].sort((a, b) => a.position - b.position);
  const total = questions.reduce((sum, question) => sum + question.marks, 0);
  // A question with no correct option cannot be marked: the summary says how many.
  const missingCorrect = questions.filter((question) => !question.options.some((option) => option.correct)).length;

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-label font-medium tabular-nums" aria-live="polite">
          {t("staffCatalog.mcq.summary", { count: formatNumber(questions.length), total: formatNumber(total) })}
          {missingCorrect > 0 ? <> · {t("staffCatalog.mcq.missingCorrect", { n: formatNumber(missingCorrect) })}</> : null}
        </p>
        <Button variant="secondary" href={`/staff/exams/${exam.id}/questions`}>
          {t("staffCatalog.questionPage.open")}
        </Button>
      </div>

      {questions.length === 0 ? (
        <EmptyState title={t("staffCatalog.mcq.emptyTitle")} body={t("staffCatalog.mcq.emptyBody")} />
      ) : (
        <ol className="grid gap-3">
          {questions.map((question, index) => (
            <li key={question.id} className="grid gap-3 rounded-card border border-border bg-card shadow-[var(--card-shadow)] p-4">
              <div>
                <p className="text-meta text-muted">
                  {t("staffCatalog.mcq.questionNumber", { n: formatNumber(index + 1) })} · {t("staffCatalog.mcq.marksOf", { marks: formatNumber(question.marks) })}
                </p>
                <p className="mt-1 font-medium">{question.stem}</p>
                {question.multipleAnswer ? <span className="mt-1 inline-block rounded-control bg-field px-2 py-0.5 text-meta">{t("staffCatalog.mcq.multipleTag")}</span> : null}
              </div>
              <ul className="grid gap-1.5">
                {[...question.options]
                  .sort((a, b) => a.position - b.position)
                  .map((option, i) => (
                    <li key={option.id} className="flex items-start gap-2 text-body">
                      <span aria-hidden="true" className="w-6 shrink-0 text-muted">
                        {optionLetter(i, locale)}
                      </span>
                      <span className={option.correct ? "font-semibold text-ok" : undefined}>{option.text}</span>
                      {option.correct ? <span className="text-meta text-ok">· {t("staffCatalog.mcq.correctMark")}</span> : null}
                    </li>
                  ))}
              </ul>
              {question.explanation ? <p className="text-meta text-muted">{t("staffCatalog.mcq.explanation", { text: question.explanation })}</p> : null}
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}
