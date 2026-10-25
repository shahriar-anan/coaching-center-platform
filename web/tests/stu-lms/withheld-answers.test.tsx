import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ResultPage, isWithheld, outcomeOf } from "@/components/student/result/result-page";
import type { StudentExam } from "@/lib/api/exam/sitting";
import { mockApi, renderWithProviders } from "../helpers";

const MINUTE = 60_000;

function exam(): StudentExam {
  return {
    id: "ex-1",
    moduleId: "m-1",
    examType: "MCQ",
    paperLanguage: "BN",
    titleEn: "CRPC",
    titleBn: "সিআরপিসি",
    descriptionEn: null,
    descriptionBn: null,
    rulesEn: null,
    rulesBn: null,
    liveAt: new Date(Date.now() - 3 * 60 * MINUTE).toISOString(),
    endAt: new Date(Date.now() - 2 * 60 * MINUTE).toISOString(),
    durationMinutes: 40,
    passPercent: 40,
    maximum: 2,
    status: "PUBLISHED",
    finallySaved: true,
    price: null,
    questionPdf: null,
    questions: null,
    myStatus: "RESULT_READY",
    retakeAvailable: null,
    sitting: null,
  } as StudentExam;
}

/** q1 was answered (option a) and q2 was not answered; both are withheld or both released. */
function result(released: boolean) {
  const correctA = released ? false : null;
  const correctB = released ? true : null;
  const explanation = released ? "ব্যাখ্যা" : null;
  return {
    score: released ? 0 : null,
    maximum: 2,
    passed: released ? false : null,
    status: "SUBMITTED",
    comment: null,
    evaluatedPdf: null,
    questions: [
      {
        id: "q1",
        stem: "প্রথম প্রশ্ন",
        marks: 1,
        explanation,
        options: [
          { id: "a", text: "one", correct: correctA, selected: true },
          { id: "b", text: "two", correct: correctB, selected: false },
        ],
      },
      {
        id: "q2",
        stem: "দ্বিতীয় প্রশ্ন",
        marks: 1,
        explanation,
        options: [
          { id: "c", text: "three", correct: correctA, selected: null },
          { id: "d", text: "four", correct: correctB, selected: null },
        ],
      },
    ],
  };
}

function renderResult(released: boolean) {
  mockApi({
    "GET /api/v1/live-exams/ex-1": exam(),
    "GET /api/v1/live-exams/ex-1/result": result(released),
  });
  return renderWithProviders(<ResultPage examId="ex-1" />, { locale: "bn" });
}

describe("withheld answers on the MCQ review (S-018)", () => {
  it("the helper: withheld needs no correct flag and no explanation", () => {
    expect(isWithheld({ explanation: null, options: [{ correct: null }] })).toBe(true);
    expect(isWithheld({ explanation: "x", options: [{ correct: null }] })).toBe(false);
    expect(isWithheld({ explanation: null, options: [{ correct: false }] })).toBe(false);
  });

  it("an answered question with withheld answers is neither right nor wrong on the client", () => {
    const question = { explanation: null, options: [{ selected: true, correct: null }, { selected: false, correct: null }] };
    expect(outcomeOf(question)).toBe("withheld");
    expect(outcomeOf({ explanation: null, options: [{ selected: null, correct: null }] })).toBe("skipped");
  });

  it("withheld: shows the copy once, no correct marker, and no right or wrong filter", async () => {
    renderResult(false);
    expect(await screen.findByText("উত্তর এখনো প্রকাশ হয়নি। প্রশাসক উত্তর প্রকাশ করলে এখানে দেখা যাবে।")).toBeInTheDocument();
    expect(screen.queryByText("সঠিক উত্তর")).not.toBeInTheDocument();
    expect(screen.getByRole("tab", { name: /উত্তর দেননি/ })).toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: /^ভুল/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: /^সঠিক/ })).not.toBeInTheDocument();
    expect(screen.getByText("সঠিক ও ভুলের হিসাব উত্তর প্রকাশ হলে দেখা যাবে।")).toBeInTheDocument();
  });

  it("withheld: the not-answered filter still works", async () => {
    renderResult(false);
    await userEvent.click(await screen.findByRole("tab", { name: /উত্তর দেননি/ }));
    expect(screen.getByText("দ্বিতীয় প্রশ্ন")).toBeInTheDocument();
    expect(screen.queryByText("প্রথম প্রশ্ন")).not.toBeInTheDocument();
  });

  it("released: no withheld copy; the right and wrong filters show and work", async () => {
    renderResult(true);
    expect((await screen.findAllByText("সঠিক উত্তর")).length).toBeGreaterThan(0);
    expect(screen.queryByText("উত্তর এখনো প্রকাশ হয়নি। প্রশাসক উত্তর প্রকাশ করলে এখানে দেখা যাবে।")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("tab", { name: /^ভুল/ }));
    expect(screen.getByText("প্রথম প্রশ্ন")).toBeInTheDocument();
    expect(screen.queryByText("দ্বিতীয় প্রশ্ন")).not.toBeInTheDocument();
  });
});
