import { describe, expect, it } from "vitest";
import {
  blankDraft,
  draftFromQuestion,
  keepOneCorrect,
  questionRequest,
  removeOptionAt,
  setMultipleAnswer,
  toggleCorrect,
  validateDraft,
  type QuestionDraft,
} from "@/components/staff/catalog/question-draft";
import type { StaffQuestion } from "@/lib/api/exam/staff-catalog";

/** A filled draft with four options; `correct` and `multipleAnswer` are set by the test. */
function filled(correct: number[], multipleAnswer: boolean): QuestionDraft {
  return {
    stem: " Capital? ",
    marks: "2",
    explanation: "",
    options: [{ text: "Dhaka" }, { text: "Khulna " }, { text: "Sylhet" }, { text: "Rangpur" }],
    correct,
    multipleAnswer,
  };
}

describe("single-answer and multiple-answer request mapping", () => {
  it("sends one correct option, and multipleAnswer false, for a single-answer draft", () => {
    const body = questionRequest(filled([0], false), 3);
    expect(body.multipleAnswer).toBe(false);
    expect(body.position).toBe(3);
    expect(body.stem).toBe("Capital?");
    expect(body.options.filter((o) => o.correct).map((o) => o.text)).toEqual(["Dhaka"]);
    expect(body.options[1].text).toBe("Khulna");
  });

  it("sends every ticked option, and multipleAnswer true, for a multiple-answer draft", () => {
    const body = questionRequest(filled([0, 2], true));
    expect(body.multipleAnswer).toBe(true);
    expect(body.options.filter((o) => o.correct).map((o) => o.text)).toEqual(["Dhaka", "Sylhet"]);
    expect("position" in body).toBe(false);
  });

  it("reads a saved question back with its correct options and its answer kind", () => {
    const saved: StaffQuestion = {
      id: "q1",
      stem: "Pick two",
      marks: 3,
      explanation: null,
      position: 1,
      multipleAnswer: true,
      options: [
        { id: "a", text: "A", correct: true, position: 1 },
        { id: "b", text: "B", correct: false, position: 2 },
        { id: "c", text: "C", correct: true, position: 3 },
      ],
    };
    expect(draftFromQuestion(saved)).toMatchObject({ correct: [0, 2], multipleAnswer: true, marks: "3" });
  });
});

describe("ticking options", () => {
  it("replaces the one correct option on a single-answer question", () => {
    expect(toggleCorrect(filled([0], false), 2).correct).toEqual([2]);
  });

  it("adds and removes options on a multiple-answer question, keeping them in order", () => {
    const added = toggleCorrect(filled([2], true), 0);
    expect(added.correct).toEqual([0, 2]);
    expect(toggleCorrect(added, 2).correct).toEqual([0]);
  });
});

describe("switching the answer kind", () => {
  it("turns multiple answers on without changing the ticks", () => {
    const { draft, keepChoice } = setMultipleAnswer(filled([1], false), true);
    expect(draft.multipleAnswer).toBe(true);
    expect(draft.correct).toEqual([1]);
    expect(keepChoice).toBe(false);
  });

  it("turns single on directly when one option is ticked", () => {
    const { draft, keepChoice } = setMultipleAnswer(filled([1], true), false);
    expect(draft.multipleAnswer).toBe(false);
    expect(draft.correct).toEqual([1]);
    expect(keepChoice).toBe(false);
  });

  it("asks which option to keep when several are ticked, and leaves the question as it is until then", () => {
    const { draft, keepChoice } = setMultipleAnswer(filled([0, 2], true), false);
    expect(keepChoice).toBe(true);
    expect(draft.multipleAnswer).toBe(true);
    expect(draft.correct).toEqual([0, 2]);
  });

  it("keeps only the chosen option once the teacher picks it", () => {
    const kept = keepOneCorrect(filled([0, 2], true), 2);
    expect(kept.multipleAnswer).toBe(false);
    expect(kept.correct).toEqual([2]);
  });
});

describe("removing an option", () => {
  it("moves the correct indexes after it down by one", () => {
    expect(removeOptionAt(filled([2, 3], true), 0).correct).toEqual([1, 2]);
  });

  it("drops a correct option that is removed", () => {
    expect(removeOptionAt(filled([1, 3], true), 1).correct).toEqual([2]);
  });
});

describe("validation", () => {
  it("needs exactly one correct option on a single-answer question", () => {
    expect(validateDraft(filled([], false)).correct).toBe("staffCatalog.question.correctRequired");
    expect(validateDraft(filled([0, 1], false)).correct).toBe("staffCatalog.question.correctRequired");
    expect(validateDraft(filled([0], false))).toEqual({});
  });

  it("needs at least one correct option on a multiple-answer question", () => {
    expect(validateDraft(filled([], true)).correct).toBe("staffCatalog.question.correctRequiredMulti");
    expect(validateDraft(filled([0, 3], true))).toEqual({});
  });

  it("refuses a correct option with no text", () => {
    const draft = filled([1], true);
    draft.options[1] = { text: "  " };
    expect(validateDraft(draft).correct).toBe("staffCatalog.question.correctRequiredMulti");
  });

  it("needs a stem, marks of at least one, and every option filled", () => {
    const blank = { ...blankDraft(), stem: "" };
    expect(validateDraft(blank).stem).toBe("staffCatalog.question.stemRequired");
    expect(validateDraft({ ...filled([0], false), marks: "0" }).marks).toBe("staffCatalog.question.marksInvalid");
    expect(validateDraft({ ...filled([0], false), options: [{ text: "A" }, { text: "" }] }).options).toBe(
      "staffCatalog.question.optionsRequired",
    );
  });
});
