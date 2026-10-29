import type { QuestionInput, StaffQuestion } from "@/lib/api/exam/staff-catalog";

export const MIN_OPTIONS = 2;
export const MAX_OPTIONS = 6;

/**
 * A question as the editor holds it. `correct` lists the indexes of the correct options: exactly one for a
 * single-answer question, one or more for a multiple-answer one (NR-01, plan 7.5).
 */
export type QuestionDraft = {
  stem: string;
  marks: string;
  explanation: string;
  options: { text: string }[];
  correct: number[];
  multipleAnswer: boolean;
};

/** The message key of each problem the editor shows under a field. */
export type DraftErrors = Partial<Record<"stem" | "marks" | "options" | "correct", string>>;

export function blankDraft(): QuestionDraft {
  return {
    stem: "",
    marks: "1",
    explanation: "",
    options: [{ text: "" }, { text: "" }, { text: "" }, { text: "" }],
    correct: [],
    multipleAnswer: false,
  };
}

/** The draft of a saved question. Its correct options come from the server; the answer kind follows the question. */
export function draftFromQuestion(question: StaffQuestion): QuestionDraft {
  const options = [...question.options].sort((a, b) => a.position - b.position);
  const correct = options.flatMap((option, index) => (option.correct ? [index] : []));
  return {
    stem: question.stem,
    marks: String(question.marks),
    explanation: question.explanation ?? "",
    options: options.map((option) => ({ text: option.text })),
    correct,
    multipleAnswer: question.multipleAnswer ?? correct.length > 1,
  };
}

/**
 * Ticks or unticks an option. On a single-answer question the new choice replaces the old one, so there is always
 * exactly one correct option; on a multiple-answer question it is added or removed.
 */
export function toggleCorrect(draft: QuestionDraft, index: number): QuestionDraft {
  if (!draft.multipleAnswer) {
    return { ...draft, correct: [index] };
  }
  const correct = draft.correct.includes(index) ? draft.correct.filter((i) => i !== index) : [...draft.correct, index].sort((a, b) => a - b);
  return { ...draft, correct };
}

/** Removes an option and moves the correct indexes after it down by one. A removed correct option is no longer correct. */
export function removeOptionAt(draft: QuestionDraft, index: number): QuestionDraft {
  const correct = draft.correct.filter((i) => i !== index).map((i) => (i > index ? i - 1 : i));
  return { ...draft, options: draft.options.filter((_, i) => i !== index), correct };
}

/**
 * Turning on "Select all that apply" just changes the answer kind. Turning it off with one correct option keeps that
 * option. Turning it off with several ticked does not change anything yet: `keepChoice` asks the teacher which one to
 * keep (see keepOneCorrect).
 */
export function setMultipleAnswer(draft: QuestionDraft, on: boolean): { draft: QuestionDraft; keepChoice: boolean } {
  if (on) {
    return { draft: { ...draft, multipleAnswer: true }, keepChoice: false };
  }
  if (draft.correct.length > 1) {
    return { draft, keepChoice: true };
  }
  return { draft: { ...draft, multipleAnswer: false, correct: draft.correct.slice(0, 1) }, keepChoice: false };
}

/** The single correct option the teacher keeps when a multiple-answer question becomes single-answer. */
export function keepOneCorrect(draft: QuestionDraft, index: number): QuestionDraft {
  return { ...draft, multipleAnswer: false, correct: [index] };
}

/**
 * The problems with a draft, as message keys. A single-answer question needs exactly one correct option, and a
 * multiple-answer one at least one. Every correct option must have text.
 */
export function validateDraft(draft: QuestionDraft): DraftErrors {
  const errors: DraftErrors = {};
  if (!draft.stem.trim()) {
    errors.stem = "staffCatalog.question.stemRequired";
  }
  const marks = Number(draft.marks);
  if (!Number.isInteger(marks) || marks < 1) {
    errors.marks = "staffCatalog.question.marksInvalid";
  }
  const filled = draft.options.filter((option) => option.text.trim() !== "");
  if (filled.length < MIN_OPTIONS || filled.length !== draft.options.length) {
    errors.options = "staffCatalog.question.optionsRequired";
  }
  const correctFilled = draft.correct.every((index) => draft.options[index]?.text.trim());
  const countOk = draft.multipleAnswer ? draft.correct.length >= 1 : draft.correct.length === 1;
  if (!countOk || !correctFilled) {
    errors.correct = draft.multipleAnswer ? "staffCatalog.question.correctRequiredMulti" : "staffCatalog.question.correctRequired";
  }
  return errors;
}

/** The request body of a valid draft. Options are trimmed; `correct` is set per option. */
export function questionRequest(draft: QuestionDraft, position?: number): QuestionInput {
  const correct = new Set(draft.correct);
  return {
    stem: draft.stem.trim(),
    marks: Number(draft.marks),
    explanation: draft.explanation.trim() || null,
    ...(position === undefined ? {} : { position }),
    multipleAnswer: draft.multipleAnswer,
    options: draft.options.map((option, index) => ({ text: option.text.trim(), correct: correct.has(index) })),
  };
}
