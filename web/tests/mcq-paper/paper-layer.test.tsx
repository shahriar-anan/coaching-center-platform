import { screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { PaperLayer } from "@/components/student/sitting/paper-layer";
import { PaperQuestion, type PaperLabels } from "@/components/student/sitting/paper-question";
import { renderWithProviders } from "../helpers";

const labels: PaperLabels = {
  selected: "Selected",
  mark: "Mark",
  marked: "Marked",
  marks: (marks) => `${marks} marks`,
  selectAll: "Select all that apply.",
  confirm: "Confirm this answer",
  final: "Final: this answer cannot be changed.",
};

const question = {
  id: "q1",
  stem: "Who issues a warrant?",
  marks: 5,
  explanation: null,
  options: [
    { id: "a", text: "Police", correct: null, selected: null },
    { id: "b", text: "A magistrate", correct: null, selected: null },
  ],
};

describe("the paper layer", () => {
  it("covers the page, and the site shell behind it is inert and hidden while it is open", () => {
    const { unmount } = renderWithProviders(
      <>
        <div data-surface="site">
          <header>
            <button type="button">Site menu</button>
          </header>
        </div>
        <PaperLayer>
          <p>Paper text</p>
        </PaperLayer>
      </>,
      { locale: "en" },
    );
    const shell = document.querySelector('[data-surface="site"]') as HTMLElement;
    expect(shell).toHaveAttribute("inert");
    expect(shell).toHaveAttribute("aria-hidden", "true");

    const layer = screen.getByText("Paper text").closest("[data-paper-layer]") as HTMLElement;
    expect(layer.parentElement).toBe(document.body);
    expect(layer).toHaveClass("fixed", "inset-0", "z-30");
    expect(layer.style.getPropertyValue("--paper-bg")).toBe("#ffffff");
    expect(layer.style.getPropertyValue("--paper-ink")).toBe("#000000");
    expect(layer.style.colorScheme).toBe("light");
    expect(screen.getByRole("link", { name: "Skip to the questions" })).toHaveAttribute("href", "#paper-questions");

    unmount();
    expect(shell).not.toHaveAttribute("inert");
    expect(shell).not.toHaveAttribute("aria-hidden");
    expect(document.querySelector("[data-paper-layer]")).toBeNull();
  });

  it("does not touch the history, so the browser Back button still works", () => {
    const push = vi.spyOn(window.history, "pushState");
    const replace = vi.spyOn(window.history, "replaceState");
    renderWithProviders(<PaperLayer><p>Paper text</p></PaperLayer>, { locale: "en" });
    expect(push).not.toHaveBeenCalled();
    expect(replace).not.toHaveBeenCalled();
    push.mockRestore();
    replace.mockRestore();
  });
});

describe("one question on the paper", () => {
  const renderQuestion = (props: Partial<Parameters<typeof PaperQuestion>[0]> = {}) =>
    renderWithProviders(
      <PaperLayer>
        <PaperQuestion
          question={question}
          index={1}
          selectedOptionIds={[]}
          flagged={false}
          paperLanguage="EN"
          editable
          labels={labels}
          questionLabel="Question 1"
          letters={["A", "B"]}
          onPick={() => undefined}
          onFlag={() => undefined}
          {...props}
        />
      </PaperLayer>,
      { locale: "en" },
    );

  it("a chosen option has aria-checked, a filled bubble and bold text", () => {
    renderQuestion({ selectedOptionIds: ["b"] });
    const chosen = screen.getByRole("radio", { name: "B A magistrate" });
    expect(chosen).toHaveAttribute("aria-checked", "true");
    expect(chosen.querySelector('[data-chosen="true"]')).toHaveClass("bg-[color:var(--paper-ink)]");
    expect(screen.getByText("A magistrate")).toHaveClass("font-bold");
    expect(screen.getByText("Police")).not.toHaveClass("font-bold");
  });

  it("a multi-answer question uses checkboxes, square bubbles and the select-all line", () => {
    renderQuestion({ question: { ...question, multipleAnswer: true } });
    expect(screen.getAllByRole("checkbox")).toHaveLength(2);
    expect(screen.queryAllByRole("radio")).toHaveLength(0);
    expect(screen.getAllByRole("checkbox")[0].querySelector("[data-shape]")).toHaveAttribute("data-shape", "square");
    expect(screen.getByText("Select all that apply.")).toBeInTheDocument();
  });

  it("numbers and letters follow a Bangla paper, and an English paper uses the serif face", () => {
    renderQuestion({ paperLanguage: "BN", questionLabel: "প্রশ্ন ১", letters: ["ক", "খ"], index: 12 });
    expect(screen.getByText("১২.")).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "ক Police" })).toBeInTheDocument();
  });

  it("an English paper sets the stem in the serif face", () => {
    renderQuestion();
    expect(screen.getByText("1.")).toBeInTheDocument();
    expect(screen.getByText("Who issues a warrant?")).toHaveClass("font-[family-name:var(--paper-serif)]");
  });

  it("shows the marks in square brackets and a Mark button that toggles the flag", () => {
    const onFlag = vi.fn();
    renderQuestion({ onFlag });
    expect(screen.getByText("[5 marks]")).toBeInTheDocument();
    screen.getByRole("button", { name: "Mark" }).click();
    expect(onFlag).toHaveBeenCalledWith("q1");
  });

  it("a locked answer refuses taps and shows the final note", () => {
    const onPick = vi.fn();
    renderQuestion({ locked: true, selectedOptionIds: ["a"], onPick });
    const police = screen.getByRole("radio", { name: "A Police" });
    expect(police).toBeDisabled();
    police.click();
    expect(onPick).not.toHaveBeenCalled();
    expect(screen.getByText("Final: this answer cannot be changed.")).toBeInTheDocument();
  });
});
