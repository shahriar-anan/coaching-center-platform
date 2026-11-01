import { act, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ResultPage } from "@/components/student/result/result-page";
import { formatDateTime } from "@/lib/format";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const HOUR = 60 * 60_000;

/** The student's submitted MCQ: the exam read, with no questions while the review is closed. */
const studentExam = {
  id: "ex-1",
  moduleId: "m-1",
  examType: "MCQ",
  paperLanguage: "EN",
  titleEn: "CRPC",
  titleBn: "CRPC",
  liveAt: new Date(Date.now() - HOUR).toISOString(),
  endAt: new Date(Date.now() + HOUR).toISOString(),
  durationMinutes: 40,
  passPercent: 40,
  maximum: 3,
  status: "PUBLISHED",
  finallySaved: true,
  price: null,
  questionPdf: null,
  questions: null,
  myStatus: "SUBMITTED",
  retakeAvailable: null,
  sitting: null,
};

const score = { kind: "LIVE", score: 1, maximum: 3, passed: false, status: "SUBMITTED", comment: null, evaluatedPdf: null };

const keyedQuestions = [
  {
    id: "q1",
    stem: "Question one",
    marks: 1,
    explanation: "Because the second is right.",
    options: [
      { id: "a", text: "one", correct: false, selected: true },
      { id: "b", text: "two", correct: true, selected: false },
    ],
  },
];

beforeEach(() => {
  // The clock runs with the real time, so the waits and the refreshes below still happen without a real delay.
  vi.useFakeTimers({ shouldAdvanceTime: true });
});

afterEach(() => {
  vi.useRealTimers();
});

describe("the result of a submitted MCQ before its review opens", () => {
  it("shows the score and the time the answers open, not the review", async () => {
    const opensAt = new Date(Date.now() + HOUR).toISOString();
    mockApi({
      "GET /api/v1/live-exams/ex-1": studentExam,
      "GET /api/v1/live-exams/ex-1/result": { ...score, reviewOpensAt: opensAt },
      "GET /api/v1/live-exams/ex-1/leaderboard": { rows: [] },
    });
    renderWithProviders(<ResultPage examId="ex-1" />, { user: fixtures.student, locale: "en" });

    expect(await screen.findByText("1 / 3")).toBeInTheDocument();
    expect(screen.getByText(`Your answers and the explanations open at ${formatDateTime(opensAt, "en")}.`)).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Check your answers" })).not.toBeInTheDocument();
  });

  it("switches to the review by itself when the time passes, without a reload", async () => {
    const opensAt = new Date(Date.now() + 3_000).toISOString();
    let open = false;
    const api = mockApi({
      "GET /api/v1/live-exams/ex-1": studentExam,
      "GET /api/v1/live-exams/ex-1/result": () =>
        open ? { ...score, questions: keyedQuestions } : { ...score, reviewOpensAt: opensAt },
      "GET /api/v1/live-exams/ex-1/leaderboard": { rows: [] },
    });
    renderWithProviders(<ResultPage examId="ex-1" />, { user: fixtures.student, locale: "en" });

    expect(await screen.findByText(/open at/)).toBeInTheDocument();
    const reads = () => api.calls.filter((call) => call.path === "/api/v1/live-exams/ex-1/result").length;
    const before = reads();
    expect(screen.queryByRole("heading", { name: "Check your answers" })).not.toBeInTheDocument();

    // The server now releases the review, and the page's clock passes the opening time.
    open = true;
    await act(async () => {
      vi.advanceTimersByTime(4_000);
    });

    expect(await screen.findByRole("heading", { name: "Check your answers" })).toBeInTheDocument();
    expect(screen.getByText(/Because the second is right/)).toBeInTheDocument();
    expect(screen.queryByText(/open at/)).not.toBeInTheDocument();
    expect(reads()).toBeGreaterThan(before);
  });

  it("does not ask for the result again before the opening time", async () => {
    const opensAt = new Date(Date.now() + 30 * 60_000).toISOString();
    const api = mockApi({
      "GET /api/v1/live-exams/ex-1": studentExam,
      "GET /api/v1/live-exams/ex-1/result": { ...score, reviewOpensAt: opensAt },
      "GET /api/v1/live-exams/ex-1/leaderboard": { rows: [] },
    });
    renderWithProviders(<ResultPage examId="ex-1" />, { user: fixtures.student, locale: "en" });
    expect(await screen.findByText(/open at/)).toBeInTheDocument();
    const before = api.calls.filter((call) => call.path === "/api/v1/live-exams/ex-1/result").length;

    await act(async () => {
      vi.advanceTimersByTime(10_000);
    });

    expect(api.calls.filter((call) => call.path === "/api/v1/live-exams/ex-1/result").length).toBe(before);
    expect(screen.getByText(/open at/)).toBeInTheDocument();
  });
});

describe("the result of a submitted MCQ once its review is open", () => {
  it("shows the review, and no opening note", async () => {
    mockApi({
      "GET /api/v1/live-exams/ex-1": studentExam,
      "GET /api/v1/live-exams/ex-1/result": { ...score, questions: keyedQuestions },
      "GET /api/v1/live-exams/ex-1/leaderboard": { rows: [] },
    });
    renderWithProviders(<ResultPage examId="ex-1" />, { user: fixtures.student, locale: "en" });

    expect(await screen.findByRole("heading", { name: "Check your answers" })).toBeInTheDocument();
    expect(screen.queryByText(/open at/)).not.toBeInTheDocument();
  });
});
