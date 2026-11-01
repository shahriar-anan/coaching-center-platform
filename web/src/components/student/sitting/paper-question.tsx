"use client";

import { memo } from "react";
import type { SittingQuestion } from "@/lib/api/exam/sitting";
import { formatNumber } from "@/lib/format";
import type { PaperLanguage } from "./mcq-question";
import { paperClass, paperFaceClass } from "./paper-theme";

/** The words on a paper question, already translated. Marks are formatted in the paper's digits. */
export type PaperLabels = {
  /** Read to a screen reader for a chosen option. */
  selected: string;
  /** The mark-for-review button, before and after marking. */
  mark: string;
  marked: string;
  marks: (marks: number) => string;
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
  /** The answer can no longer change (answers cannot change on this exam and the answer is saved or confirmed). */
  locked?: boolean;
  /** Set while a confirm for this question is being sent. */
  confirming?: boolean;
  labels: PaperLabels;
  /** Question number label, already localized, for example "প্রশ্ন ১". */
  questionLabel: string;
  letters: readonly string[];
  onPick: (questionId: string, optionId: string) => void;
  onFlag: (questionId: string) => void;
  /** Given on an exam whose answers cannot change. A multi-answer question then shows Confirm. */
  onConfirm?: (questionId: string) => void;
};

/**
 * One question on the printed paper: the number and stem on one line with the marks at the right end, then the
 * options as a vertical list. The bubble is the answer control (a circle for one answer, a square for "select all
 * that apply"), with a 44 px tap area; the row itself does not take taps. Memoized so that a tap re-renders only
 * the question that changed.
 */
export const PaperQuestion = memo(function PaperQuestion({
  question,
  index,
  selectedOptionIds,
  flagged,
  paperLanguage,
  editable,
  locked = false,
  confirming = false,
  labels,
  questionLabel,
  letters,
  onPick,
  onFlag,
  onConfirm,
}: Props) {
  const face = paperFaceClass(paperLanguage);
  const digits = paperLanguage === "BN" ? "bn" : "en";
  /** The paper's text size, set by the wrapper (see paper-text-size.tsx). */
  const paperFont = { fontSize: "var(--paper-font, 16px)" };
  const multi = question.multipleAnswer === true;
  const confirmable = Boolean(onConfirm) && multi && editable && !locked;

  return (
    <article id={`q-${index}`} data-question-index={index} className={`scroll-mt-40 border-b py-5 ${paperClass.rule}`}>
      <div className="flex flex-wrap items-start gap-x-4 gap-y-2">
        <p className={`flex min-w-[12rem] flex-1 items-start gap-2 leading-relaxed ${paperClass.ink}`}>
          <span className={`shrink-0 font-bold tabular-nums ${face}`} style={paperFont}>
            {formatNumber(index, digits)}.
          </span>
          <span className={`min-w-0 break-words ${face}`} style={paperFont}>
            {question.stem}
          </span>
        </p>
        <div className={`ms-auto flex shrink-0 items-center gap-4 ${paperClass.meta} ${paperClass.ink}`}>
          <span className="tabular-nums">[{labels.marks(question.marks)}]</span>
          {editable ? (
            <button
              type="button"
              aria-pressed={flagged}
              onClick={() => onFlag(question.id)}
              className="min-h-11 px-1 underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[color:var(--paper-ink)]"
            >
              <span aria-hidden="true">⚑ </span>
              {flagged ? labels.marked : labels.mark}
            </button>
          ) : null}
        </div>
      </div>
      {multi ? <p className={`mt-2 ps-7 italic ${paperClass.meta} ${paperClass.muted}`}>{labels.selectAll}</p> : null}
      <ul className="mt-3 ps-7" role={multi ? "group" : "radiogroup"} aria-label={questionLabel}>
        {question.options.map((option, position) => {
          const picked = selectedOptionIds.includes(option.id);
          const letter = letters[position] ?? "";
          return (
            <li key={option.id} className="flex items-start gap-1 py-1">
              <button
                type="button"
                role={multi ? "checkbox" : "radio"}
                aria-checked={picked}
                aria-label={`${letter} ${option.text}`}
                disabled={!editable || locked}
                onClick={() => onPick(question.id, option.id)}
                className="-ms-2 grid size-11 shrink-0 place-items-center disabled:cursor-default focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[color:var(--paper-ink)]"
              >
                <span
                  aria-hidden="true"
                  data-shape={multi ? "square" : "circle"}
                  data-chosen={picked ? "true" : undefined}
                  className={`grid size-7 place-items-center border-[1.5px] font-semibold ${paperClass.meta} ${paperClass.line} ${multi ? "" : "rounded-full"} ${
                    picked ? `${paperClass.fill} ${paperClass.onFill}` : `${paperClass.paper} ${paperClass.ink}`
                  }`}
                >
                  {letter}
                </span>
              </button>
              <span
                className={`min-w-0 flex-1 break-words pt-2.5 leading-relaxed ${paperClass.ink} ${face} ${picked ? "font-bold" : ""}`}
                style={paperFont}
              >
                {option.text}
              </span>
              {picked ? <span className="sr-only">{labels.selected}</span> : null}
            </li>
          );
        })}
      </ul>
      {confirmable ? (
        <button
          type="button"
          disabled={selectedOptionIds.length === 0 || confirming}
          onClick={() => onConfirm?.(question.id)}
          className={`ms-7 mt-3 min-h-11 border-[1.5px] px-4 font-semibold disabled:cursor-not-allowed disabled:opacity-40 ${paperClass.line} ${paperClass.paper} ${paperClass.ink} ${paperClass.body}`}
        >
          {labels.confirm}
        </button>
      ) : null}
      {locked && editable ? <p className={`ms-7 mt-3 italic ${paperClass.meta} ${paperClass.muted}`}>{labels.final}</p> : null}
    </article>
  );
});
