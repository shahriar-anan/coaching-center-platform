"use client";

import { memo } from "react";
import type { SittingQuestion } from "@/lib/api/exam/sitting";

export type PaperLanguage = "BN" | "EN";

export type OptionLabels = {
  selected: string;
  flag: string;
  yourAnswer: string;
  correct: string;
  explanation: string;
};

type Props = {
  question: SittingQuestion;
  index: number;
  selectedOptionId: string | undefined;
  flagged: boolean;
  paperLanguage: PaperLanguage;
  /** Only true while the paper accepts taps. */
  editable: boolean;
  /** After the deadline the server sends `correct`; before it, these are null and nothing is styled. */
  reviewing: boolean;
  labels: OptionLabels;
  /** Question number label, already localized, for example "প্রশ্ন ১". */
  questionLabel: string;
  letters: readonly string[];
  onPick: (questionId: string, optionId: string) => void;
  onFlag: (questionId: string) => void;
};

/**
 * One question on the paper. Memoized so that a tap re-renders only the row that changed, which
 * keeps a 100-question paper smooth on a mid-range phone.
 */
export const McqQuestion = memo(function McqQuestion({
  question,
  index,
  selectedOptionId,
  flagged,
  paperLanguage,
  editable,
  reviewing,
  labels,
  questionLabel,
  letters,
  onPick,
  onFlag,
}: Props) {
  const stemFace = paperLanguage === "BN" ? "font-bn" : "font-latin";
  /** The paper's text size, set by the wrapper (see paper-text-size.tsx). */
  const paperFont = { fontSize: "var(--paper-font, 16px)" };

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
      <ul className="mt-3 flex flex-col gap-2" role="radiogroup" aria-label={questionLabel}>
        {question.options.map((option, position) => {
          const picked = option.id === selectedOptionId;
          const isCorrect = reviewing && option.correct === true;
          const isWrongChoice = reviewing && picked && option.correct === false;
          const border = isCorrect
            ? "border-ok"
            : isWrongChoice
              ? "border-warn"
              : picked
                ? "border-accent"
                : "border-border";
          return (
            <li key={option.id}>
              <button
                type="button"
                role="radio"
                aria-checked={picked}
                disabled={!editable}
                onClick={() => onPick(question.id, option.id)}
                className={`flex min-h-11 w-full items-center gap-3 rounded-control border-2 bg-bg px-3 py-2 text-left text-text disabled:cursor-default ${border} ${stemFace} focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent`}
              >
                <span
                  aria-hidden="true"
                  className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full border border-border text-meta"
                >
                  {letters[position] ?? ""}
                </span>
                <span className="flex-1" style={paperFont}>{option.text}</span>
                {picked && !reviewing ? <span className="text-meta font-semibold">{labels.selected}</span> : null}
                {isCorrect ? <span className="text-meta font-semibold text-ok">{labels.correct}</span> : null}
                {isWrongChoice ? <span className="text-meta font-semibold text-warn">{labels.yourAnswer}</span> : null}
              </button>
            </li>
          );
        })}
      </ul>
      {reviewing && question.explanation ? (
        <p className="mt-3 rounded-control bg-bg p-3 text-body text-muted">
          <span className="font-semibold text-text">{labels.explanation}: </span>
          {question.explanation}
        </p>
      ) : null}
    </article>
  );
});
