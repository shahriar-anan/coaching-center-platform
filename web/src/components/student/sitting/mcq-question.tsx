"use client";

import { memo } from "react";
import type { SittingQuestion } from "@/lib/api/exam/sitting";
import type { Locale } from "@/i18n/locale-context";

export type PaperLanguage = "BN" | "EN";

/**
 * The script and letters of a paper. An exam with BN or EN keeps it. An exam with no paper language,
 * or with BOTH, follows the interface language, so no language is shown anywhere.
 */
export function resolvePaperLanguage(value: "BN" | "EN" | "BOTH" | null | undefined, locale: Locale): PaperLanguage {
  if (value === "BN" || value === "EN") {
    return value;
  }
  return locale === "bn" ? "BN" : "EN";
}

export type OptionLabels = {
  selected: string;
  flag: string;
  yourAnswer: string;
  correct: string;
  explanation: string;
  selectAll: string;
  confirm: string;
  final: string;
};

type Props = {
  question: SittingQuestion;
  index: number;
  /** The chosen option ids: one for a single-answer question, any number for a multi-answer one. */
  selectedOptionIds: string[];
  flagged: boolean;
  paperLanguage: PaperLanguage;
  /** Only true while the paper accepts taps. */
  editable: boolean;
  /** After the deadline the server sends `correct`; before it, these are null and nothing is styled. */
  reviewing: boolean;
  /** The answer can no longer change (answers cannot change on this exam and the answer is saved or confirmed). */
  locked?: boolean;
  /** Set while a confirm for this question is being sent. */
  confirming?: boolean;
  labels: OptionLabels;
  /** Question number label, already localized, for example "প্রশ্ন ১". */
  questionLabel: string;
  letters: readonly string[];
  onPick: (questionId: string, optionId: string) => void;
  onFlag: (questionId: string) => void;
  /** Given on an exam whose answers cannot change. A multi-answer question then shows Confirm. */
  onConfirm?: (questionId: string) => void;
};

/**
 * One question on the paper. The answer control is the circle on the left of each option: a radio for a
 * single answer, a checkbox for "select all that apply". The row itself does not take taps. Memoized so
 * that a tap re-renders only the row that changed, which keeps a 100-question paper smooth on a phone.
 */
export const McqQuestion = memo(function McqQuestion({
  question,
  index,
  selectedOptionIds,
  flagged,
  paperLanguage,
  editable,
  reviewing,
  locked = false,
  confirming = false,
  labels,
  questionLabel,
  letters,
  onPick,
  onFlag,
  onConfirm,
}: Props) {
  const stemFace = paperLanguage === "BN" ? "font-bn" : "font-latin";
  /** The paper's text size, set by the wrapper (see paper-text-size.tsx). */
  const paperFont = { fontSize: "var(--paper-font, 16px)" };
  const multi = question.multipleAnswer === true;
  const confirmable = Boolean(onConfirm) && multi && editable && !reviewing && !locked;

  return (
    <article
      id={`q-${index}`}
      data-question-index={index}
      className="scroll-mt-40 rounded-card border border-border bg-card p-4"
    >
      <div className="flex items-start justify-between gap-3">
        <h3 className="text-label font-semibold text-muted">{questionLabel}</h3>
        {editable ? (
          <button
            type="button"
            aria-pressed={flagged}
            onClick={() => onFlag(question.id)}
            className="min-h-11 rounded-control px-3 text-meta text-text underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-accent"
          >
            {flagged ? `✓ ${labels.flag}` : labels.flag}
          </button>
        ) : null}
      </div>
      <p className={`mt-2 leading-relaxed text-text ${stemFace}`} style={paperFont}>{question.stem}</p>
      {multi ? <p className="mt-2 text-meta font-semibold text-muted">{labels.selectAll}</p> : null}
      <ul className="mt-3 flex flex-col gap-2" role={multi ? "group" : "radiogroup"} aria-label={questionLabel}>
        {question.options.map((option, position) => {
          const picked = selectedOptionIds.includes(option.id);
          const isCorrect = reviewing && option.correct === true;
          const isWrongChoice = reviewing && picked && option.correct === false;
          const border = isCorrect
            ? "border-ok"
            : isWrongChoice
              ? "border-warn"
              : picked
                ? "border-accent"
                : "border-border";
          const letter = letters[position] ?? "";
          return (
            <li
              key={option.id}
              className={`flex min-h-11 w-full items-center gap-2 rounded-control border-2 bg-bg px-2 py-1 text-text ${border} ${stemFace}`}
            >
              <button
                type="button"
                role={multi ? "checkbox" : "radio"}
                aria-checked={picked}
                aria-label={`${letter} ${option.text}`}
                disabled={!editable || locked}
                onClick={() => onPick(question.id, option.id)}
                className={`grid size-11 shrink-0 place-items-center border-2 text-meta font-semibold transition-colors disabled:cursor-default focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent ${multi ? "rounded-sm" : "rounded-full"} ${picked ? "border-accent bg-accent text-on-accent" : "border-border bg-card text-text"}`}
              >
                <span aria-hidden="true">{letter}</span>
              </button>
              <span className="min-w-0 flex-1 break-words px-1 py-2" style={paperFont}>{option.text}</span>
              {picked && !reviewing ? <span className="shrink-0 pe-2 text-meta font-semibold">{labels.selected}</span> : null}
              {isCorrect ? <span className="shrink-0 pe-2 text-meta font-semibold text-ok">{labels.correct}</span> : null}
              {isWrongChoice ? <span className="shrink-0 pe-2 text-meta font-semibold text-warn">{labels.yourAnswer}</span> : null}
            </li>
          );
        })}
      </ul>
      {confirmable ? (
        <button
          type="button"
          disabled={selectedOptionIds.length === 0 || confirming}
          onClick={() => onConfirm?.(question.id)}
          className="mt-3 min-h-11 rounded-control bg-action px-4 text-body font-medium text-on-action transition-colors disabled:cursor-not-allowed disabled:opacity-50"
        >
          {labels.confirm}
        </button>
      ) : null}
      {locked && !reviewing && editable ? <p className="mt-3 text-meta text-muted">{labels.final}</p> : null}
      {reviewing && question.explanation ? (
        <p className="mt-3 rounded-control bg-bg p-3 text-body text-muted">
          <span className="font-semibold text-text">{labels.explanation}: </span>
          {question.explanation}
        </p>
      ) : null}
    </article>
  );
});
