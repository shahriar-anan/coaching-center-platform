import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";
import { McqPaper } from "@/components/student/sitting/mcq-paper";
import { PaperLayer } from "@/components/student/sitting/paper-layer";
import type { StudentExam } from "@/lib/api/exam/sitting";
import { mockApi, renderWithProviders } from "../helpers";

const STARTED = "2026-11-12T06:00:00Z";
const open = (ms: number) => new Date(Date.now() + ms).toISOString();

function exam(overrides: Partial<StudentExam> = {}, selectedFor?: string): StudentExam {
  const questions = [1, 2, 3].map((n) => ({
    id: `q${n}`,
    stem: `Question stem ${n}`,
    marks: 1,
    explanation: null,
    options: ["a", "b"].map((letter) => ({
      id: `q${n}-${letter}`,
      text: `option ${letter}`,
      correct: null,
      selected: selectedFor === `q${n}-${letter}` ? true : null,
    })),
  }));
  return {
    id: "ex-1",
    moduleId: "m-1",
    examType: "MCQ",
    paperLanguage: "EN",
    titleEn: "CRPC",
    titleBn: null,
    descriptionEn: null,
    descriptionBn: null,
    rulesEn: "Read each question carefully.",
    rulesBn: null,
    liveAt: null,
    endAt: null,
    durationMinutes: 40,
    passPercent: 40,
    maximum: 3,
    status: "PUBLISHED",
    finallySaved: true,
    price: null,
    questionPdf: null,
    questions,
    myStatus: "IN_PROGRESS",
    retakeAvailable: null,
    sitting: null,
    ...overrides,
  } as StudentExam;
}

function renderPaper(e: StudentExam, props: { deadlineAt?: string; studentLine?: string; locale?: "en" | "bn" } = {}) {
  return renderWithProviders(
    <PaperLayer>
      <McqPaper
        exam={e}
        studentId="stu-1"
        studentLine={props.studentLine}
        startedAt={STARTED}
        deadlineAt={props.deadlineAt ?? open(60 * 60_000)}
        kind="LIVE"
        onClosed={() => undefined}
      />
    </PaperLayer>,
    { locale: props.locale ?? "en" },
  );
}

afterEach(() => window.localStorage.clear());

/** Class tokens that belong to the app theme or its shapes, which the paper must not use. */
const THEME_TOKEN =
  /^(?:bg|text|border|ring|outline|fill)-(?:card|accent|action|bg|border|border-control|text|text-accent|muted|warn|warn-fill|danger|ok|ok-text|on-accent|on-action|on-warn|band|on-band|field|sidebar[a-z-]*)$|^rounded-(?:card|control|sm|md|lg|xl|2xl)$|^shadow(?:-[a-z0-9]+)?$/;

describe("the printed paper", () => {
  it("uses no app theme classes inside the layer, even with the grid and the submit dialog open", async () => {
    mockApi({ "PUT /api/v1/live-exams/ex-1/answers": () => undefined });
    const user = userEvent.setup();
    renderPaper(exam({}, "q2-a"));
    await user.click(screen.getByRole("button", { name: "Questions" }));
    await user.click(screen.getByRole("button", { name: "Submit" }));
    const layer = document.querySelector("[data-paper-layer]") as HTMLElement;
    const bad: string[] = [];
    // The rules block is RichTextView, which carries the app text class itself; the paper overrides its colour on its descendants.
    const inRules = (el: Element) => el.closest("details") !== null;
    for (const el of [layer, ...Array.from(layer.querySelectorAll("*"))].filter((node) => !inRules(node))) {
      for (const token of (el.getAttribute("class") ?? "").split(/\s+/)) {
        const bare = token.replace(/^(?:[a-z-]+:)+/, "");
        if (THEME_TOKEN.test(bare)) bad.push(bare);
      }
    }
    expect(bad).toEqual([]);
  });

  it("shows the title, time, full marks, the student's line and the rules as open Instructions", () => {
    renderPaper(exam(), { studentLine: "Suman Ahmed · STU-0001" });
    expect(screen.getByRole("heading", { name: "CRPC" })).toBeInTheDocument();
    expect(screen.getByText("Time: 40 minutes · Full marks: 3")).toBeInTheDocument();
    expect(screen.getByText("Suman Ahmed · STU-0001")).toBeInTheDocument();
    const details = screen.getByText("Read each question carefully.").closest("details") as HTMLDetailsElement;
    expect(details.open).toBe(true);
    expect(within(details).getByText("Instructions")).toBeInTheDocument();
  });

  it("shows the wrong-answer note only when the exam has negative marks", () => {
    const { unmount } = renderPaper(exam({ negativeMarks: 0.25 } as Partial<StudentExam>));
    expect(screen.getByText("Wrong answer: −0.25 marks")).toBeInTheDocument();
    unmount();
    renderPaper(exam({ negativeMarks: null } as Partial<StudentExam>));
    expect(screen.queryByText(/Wrong answer/)).toBeNull();
  });

  it("numbers a Bangla paper in Bangla digits, with the marks in brackets", () => {
    renderPaper(exam({ paperLanguage: "BN" }), { locale: "bn" });
    expect(screen.getByText("১.")).toBeInTheDocument();
    expect(screen.getAllByText("[১ নম্বর]")).toHaveLength(3);
    expect(screen.getAllByRole("radio", { name: "ক option a" })[0]).toBeInTheDocument();
  });

  it("inverts the timer in the final minute only", () => {
    const { unmount } = renderPaper(exam(), { deadlineAt: open(30_000) });
    expect(document.querySelector('[data-final-minute="true"]')).toHaveClass("bg-[color:var(--paper-ink)]");
    unmount();
    renderPaper(exam());
    expect(document.querySelector('[data-final-minute="true"]')).toBeNull();
  });

  it("the grid marks answered and flagged questions", async () => {
    mockApi({ "PUT /api/v1/live-exams/ex-1/answers": () => undefined });
    const user = userEvent.setup();
    renderPaper(exam());
    await user.click(screen.getAllByRole("button", { name: "Mark" })[0]);
    await user.click(screen.getAllByRole("radio")[2]);
    await user.click(screen.getByRole("button", { name: "Questions" }));
    const nav = screen.getByRole("navigation");
    expect(nav.querySelectorAll('[data-answered="true"]')).toHaveLength(1);
    expect(nav.querySelectorAll('[data-flagged="true"]')).toHaveLength(1);
    expect(nav.querySelector('[data-flagged="true"]')).toHaveTextContent("1⚑");
  });

  it("a locked answer refuses a tap and shows the final note", async () => {
    mockApi({ "PUT /api/v1/live-exams/ex-1/answers": () => undefined });
    const user = userEvent.setup();
    renderPaper(exam({ answersChangeable: false } as Partial<StudentExam>, "q1-b"));
    await user.click(screen.getAllByRole("radio")[0]);
    expect(screen.getAllByRole("radio")[1]).toHaveAttribute("aria-checked", "true");
    expect(screen.getAllByRole("radio")[0]).toHaveAttribute("aria-checked", "false");
    expect(screen.getAllByText("Final: this answer cannot be changed.").length).toBeGreaterThan(0);
  });

  it("the submit dialog counts the answers and lists the flagged numbers, and Escape cancels it", async () => {
    mockApi({ "PUT /api/v1/live-exams/ex-1/answers": () => undefined });
    const user = userEvent.setup();
    renderPaper(exam());
    await user.click(screen.getAllByRole("button", { name: "Mark" })[0]);
    // Once question 1 is marked its button reads "Marked", so the next unmarked one is first in the list.
    await user.click(screen.getAllByRole("button", { name: "Mark" })[0]);
    await user.click(screen.getAllByRole("radio")[2]);
    await user.click(screen.getByRole("button", { name: "Submit" }));
    const dialog = await screen.findByRole("dialog");
    expect(dialog).toHaveTextContent("You answered 1 questions. 2 are unanswered.");
    expect(dialog).toHaveTextContent("Marked to review later: 1, 2");
    expect(screen.getByRole("button", { name: "Cancel" })).toHaveFocus();
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("Tab reaches the bar buttons, the answer bubbles and the Submit button, in that order", async () => {
    mockApi({ "PUT /api/v1/live-exams/ex-1/answers": () => undefined });
    const user = userEvent.setup();
    renderPaper(exam());
    const seen: string[] = [];
    for (let i = 0; i < 60; i += 1) {
      await user.tab();
      const el = document.activeElement as HTMLElement;
      seen.push(el.getAttribute("aria-label") ?? el.textContent ?? "");
      if (el.textContent === "Submit") break;
    }
    const questions = seen.indexOf("Questions");
    const bubble = seen.indexOf("A option a");
    const submit = seen.indexOf("Submit");
    expect(questions).toBeGreaterThan(-1);
    expect(questions).toBeLessThan(bubble);
    expect(bubble).toBeLessThan(submit);
  });
});
