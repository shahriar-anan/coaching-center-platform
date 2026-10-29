import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { outcomeOf, positionKey } from "@/components/student/result/result-page";
import { McqQuestion, resolvePaperLanguage, type OptionLabels } from "@/components/student/sitting/mcq-question";
import { answerErrorKey, answerPayload, normaliseSelection, toggleOption } from "@/components/student/sitting/selection";
import type { SittingPosition, SittingQuestion } from "@/lib/api/exam/sitting";
import { fixtures, renderWithProviders } from "../helpers";

const labels: OptionLabels = {
  selected: "Selected",
  flag: "Flag",
  yourAnswer: "Your answer",
  correct: "Correct",
  explanation: "Explanation",
  selectAll: "Select all that apply.",
  confirm: "Confirm this answer",
  final: "Final: this answer cannot be changed.",
};

const multiQuestion: SittingQuestion = {
  id: "q1",
  stem: "Which are prime?",
  marks: 1,
  multipleAnswer: true,
  explanation: "2 and 3 are prime.",
  options: [
    { id: "a", text: "2", correct: null, selected: null },
    { id: "b", text: "3", correct: null, selected: null },
    { id: "c", text: "4", correct: null, selected: null },
  ],
};

function renderQuestion(props: Partial<Parameters<typeof McqQuestion>[0]> = {}) {
  return renderWithProviders(
    <McqQuestion
      question={multiQuestion}
      index={1}
      selectedOptionIds={[]}
      flagged={false}
      paperLanguage="EN"
      editable
      reviewing={false}
      labels={labels}
      questionLabel="Question 1"
      letters={["A", "B", "C"]}
      onPick={() => undefined}
      onFlag={() => undefined}
      {...props}
    />,
    { user: fixtures.masterAdmin },
  );
}

describe("selection: taps", () => {
  it("a single-answer question keeps one option: the same tap clears it, another replaces it", () => {
    const first = toggleOption({}, "q1", "a");
    expect(first).toEqual({ q1: ["a"] });
    expect(toggleOption(first, "q1", "a")).toEqual({});
    expect(toggleOption(first, "q1", "b")).toEqual({ q1: ["b"] });
  });

  it("a multi-answer question toggles each option on its own", () => {
    const one = toggleOption({}, "q1", "a", true);
    const two = toggleOption(one, "q1", "b", true);
    expect(two).toEqual({ q1: ["a", "b"] });
    expect(toggleOption(two, "q1", "a", true)).toEqual({ q1: ["b"] });
    expect(toggleOption({ q1: ["b"] }, "q1", "b", true)).toEqual({});
  });
});

describe("selection: the save body", () => {
  const selection = { q1: ["a", "b"], q2: ["c"] };
  const multi = new Set(["q1"]);

  it("sends every question when answers can change", () => {
    expect(answerPayload(selection, multi, true)).toEqual([
      { questionId: "q1", optionIds: ["a", "b"] },
      { questionId: "q2", optionIds: ["c"] },
    ]);
  });

  it("leaves a multi-answer question out of the bulk save when answers cannot change, so it is not confirmed early", () => {
    expect(answerPayload(selection, multi, false)).toEqual([{ questionId: "q2", optionIds: ["c"] }]);
  });

  it("reads an old one-option backup as a one-item list", () => {
    expect(normaliseSelection({ q1: "a", q2: ["b", "c"], q3: 5, q4: [] })).toEqual({ q1: ["a"], q2: ["b", "c"] });
  });
});

describe("the answer control", () => {
  it("a multi-answer question shows checkboxes under a select-all line", () => {
    renderQuestion();

    expect(screen.getByText("Select all that apply.")).toBeInTheDocument();
    expect(screen.getAllByRole("checkbox")).toHaveLength(3);
    expect(screen.queryAllByRole("radio")).toHaveLength(0);
  });

  it("a single-answer question shows radio circles", () => {
    renderQuestion({ question: { ...multiQuestion, multipleAnswer: false } });

    expect(screen.getAllByRole("radio")).toHaveLength(3);
    expect(screen.queryAllByRole("checkbox")).toHaveLength(0);
  });

  it("a tap on the circle passes the question and the option", async () => {
    const onPick = vi.fn();
    renderQuestion({ onPick });

    await userEvent.click(screen.getByRole("checkbox", { name: "A 2" }));

    expect(onPick).toHaveBeenCalledWith("q1", "a");
  });

  it("Confirm sends the question once options are chosen, and is disabled before", async () => {
    const onConfirm = vi.fn();
    const { unmount } = renderQuestion({ onConfirm, selectedOptionIds: [] });
    expect(screen.getByRole("button", { name: "Confirm this answer" })).toBeDisabled();
    unmount();

    renderQuestion({ onConfirm, selectedOptionIds: ["a", "b"] });
    await userEvent.click(screen.getByRole("button", { name: "Confirm this answer" }));

    expect(onConfirm).toHaveBeenCalledWith("q1");
  });

  it("a confirmed question disables every option and says it is final", () => {
    renderQuestion({ locked: true, selectedOptionIds: ["a", "b"], onConfirm: () => undefined });

    for (const box of screen.getAllByRole("checkbox")) {
      expect(box).toBeDisabled();
    }
    expect(screen.getByText("Final: this answer cannot be changed.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Confirm this answer" })).toBeNull();
  });

  it("the save error for an answer that cannot change has its own message", () => {
    expect(answerErrorKey("ANSWER_LOCKED")).toBe("sitting.paper.answerLocked");
    expect(answerErrorKey("SOMETHING_ELSE")).toBeNull();
    expect(answerErrorKey(undefined)).toBeNull();
  });
});

describe("review marking", () => {
  const withChoices = (chosen: string[], correct: string[]): SittingQuestion => ({
    ...multiQuestion,
    explanation: "Explained",
    options: ["a", "b", "c"].map((id) => ({
      id,
      text: id,
      correct: correct.includes(id),
      selected: chosen.includes(id),
    })),
  });

  it("a multi-answer question is right only when every correct option, and nothing else, is chosen", () => {
    expect(outcomeOf(withChoices(["a", "b"], ["a", "b"]))).toBe("right");
    expect(outcomeOf(withChoices(["a"], ["a", "b"]))).toBe("wrong");
    expect(outcomeOf(withChoices(["a", "c"], ["a", "b"]))).toBe("wrong");
    expect(outcomeOf(withChoices([], ["a", "b"]))).toBe("skipped");
  });

  it("a question with no key yet is withheld, not right or wrong", () => {
    const withheld = { ...withChoices(["a"], []), explanation: null, options: withChoices(["a"], []).options.map((o) => ({ ...o, correct: null })) };
    expect(outcomeOf(withheld)).toBe("withheld");
  });

  it("each position type has its message key, and below the pass mark there is no rank", () => {
    const at = (type: SittingPosition["type"], belowPass = false): SittingPosition => ({ type, rank: 3, of: 20, belowPass });
    expect(positionKey(at("CURRENT"))).toBe("result.position.current");
    expect(positionKey(at("BOARD"))).toBe("result.position.board");
    expect(positionKey(at("RELATIVE"))).toBe("result.position.relative");
    expect(positionKey(at("BOARD", true))).toBe("result.position.belowPass");
  });
});

describe("paper language", () => {
  it("BN and EN keep their paper; no language or BOTH follows the interface", () => {
    expect(resolvePaperLanguage("BN", "en")).toBe("BN");
    expect(resolvePaperLanguage("EN", "bn")).toBe("EN");
    expect(resolvePaperLanguage("BOTH", "bn")).toBe("BN");
    expect(resolvePaperLanguage(null, "en")).toBe("EN");
    expect(resolvePaperLanguage(null, "bn")).toBe("BN");
  });
});
