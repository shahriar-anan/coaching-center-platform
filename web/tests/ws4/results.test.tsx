import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { LeaderboardPage, leaderboardOpensAt } from "@/components/student/result/leaderboard-page";
import { ResultPage } from "@/components/student/result/result-page";
import { ResultsHub } from "@/components/student/result/results-hub";
import { isPdf, MAX_ANSWER_BYTES } from "@/components/student/sitting/written-sitting";
import type { StudentExam } from "@/lib/api/exam/sitting";
import { mockApi, renderWithProviders } from "../helpers";

const MINUTE = 60_000;

function exam(overrides: Partial<StudentExam> = {}): StudentExam {
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
    maximum: 3,
    status: "PUBLISHED",
    finallySaved: true,
    price: null,
    questionPdf: null,
    questions: null,
    myStatus: "RESULT_READY",
    retakeAvailable: null,
    sitting: null,
    ...overrides,
  };
}

describe("results hub", () => {
  it("lists results on the first tab, with marks and the pass pill", async () => {
    mockApi({
      "GET /api/v1/live-exam-modules": { content: [{ id: "m-1", titleEn: "BJS", titleBn: "বিজেএস" }], page: 0, size: 50, totalElements: 1, totalPages: 1 },
      "GET /api/v1/live-exam-modules/m-1/exams": {
        content: [{ id: "ex-1", moduleId: "m-1", titleEn: "CRPC", titleBn: "সিআরপিসি", examType: "MCQ", liveAt: exam().liveAt, myStatus: "RESULT_READY" }],
        page: 0,
        size: 50,
        totalElements: 1,
        totalPages: 1,
      },
      "GET /api/v1/live-exams/ex-1/result": { score: 38, maximum: 50, passed: true, status: "RESULT_READY", questions: null, comment: null, evaluatedPdf: null },
    });
    renderWithProviders(<ResultsHub />, { locale: "en" });
    expect(await screen.findByText("CRPC")).toBeInTheDocument();
    expect(screen.getByText(/38 \/ 50/)).toBeInTheDocument();
    expect(screen.getAllByText(/Passed/).length).toBeGreaterThan(0);
  });

  it("the marking tab shows written scripts awaiting marks, and an empty state when there are none", async () => {
    const user = userEvent.setup();
    mockApi({
      "GET /api/v1/live-exam-modules": { content: [{ id: "m-1", titleEn: "BJS", titleBn: "বিজেএস" }], page: 0, size: 50, totalElements: 1, totalPages: 1 },
      "GET /api/v1/live-exam-modules/m-1/exams": { content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 },
    });
    renderWithProviders(<ResultsHub />, { locale: "en" });
    expect(await screen.findByText("No results yet")).toBeInTheDocument();
    await user.click(screen.getByRole("tab", { name: /Being marked/ }));
    expect(screen.getByText("No scripts are waiting to be marked.")).toBeInTheDocument();
  });
});

describe("leaderboard", () => {
  it("is closed until five minutes after the window, and says when it opens", async () => {
    mockApi({
      "GET /api/v1/live-exams/ex-1": exam({ endAt: new Date(Date.now() + 10 * MINUTE).toISOString() }),
      "GET /api/v1/live-exams/ex-1/leaderboard": { rows: [] },
    });
    renderWithProviders(<LeaderboardPage examId="ex-1" />, { locale: "bn" });
    expect(await screen.findByText(/মেধাতালিকা প্রকাশ হবে/)).toBeInTheDocument();
  });

  it("is undated when the exam has no date", async () => {
    mockApi({ "GET /api/v1/live-exams/ex-1": exam({ liveAt: null, endAt: null }) });
    renderWithProviders(<LeaderboardPage examId="ex-1" />, { locale: "en" });
    expect(await screen.findByText("The exam date is not set yet, so there is no merit list.")).toBeInTheDocument();
  });

  it("shares a rank between ties and skips the next", async () => {
    mockApi({
      "GET /api/v1/live-exams/ex-1": exam(),
      "GET /api/v1/live-exams/ex-1/leaderboard": {
        rows: [
          { name: "Asha", rank: 1, score: 48, maximum: 50, passed: true },
          { name: "Rafi", rank: 2, score: 45, maximum: 50, passed: true },
          { name: "Nadia", rank: 2, score: 45, maximum: 50, passed: true },
          { name: "Tanvir", rank: 4, score: 40, maximum: 50, passed: true },
        ],
      },
    });
    renderWithProviders(<LeaderboardPage examId="ex-1" />, { locale: "en" });
    const rows = await screen.findAllByRole("row");
    const cells = rows.slice(1).map((row) => within(row).getAllByRole("cell")[0].textContent);
    expect(cells).toEqual(["1", "2", "2", "4"]);
  });

  it("the opening time is five minutes after the end", () => {
    const end = "2026-11-12T06:40:00.000Z";
    expect(leaderboardOpensAt(end, "2026-11-12T06:00:00.000Z")).toBe(Date.parse(end) + 5 * MINUTE);
    expect(leaderboardOpensAt(null, null)).toBeNull();
  });
});

describe("result page", () => {
  it("MCQ after the deadline: shows the review with the student's answer and the correct one", async () => {
    mockApi({
      "GET /api/v1/live-exams/ex-1": exam(),
      "GET /api/v1/live-exams/ex-1/result": {
        score: 1,
        maximum: 3,
        passed: false,
        status: "SUBMITTED",
        comment: null,
        evaluatedPdf: null,
        questions: [
          {
            id: "q1",
            stem: "প্রশ্ন ১",
            marks: 1,
            explanation: "ব্যাখ্যা",
            options: [
              { id: "a", text: "one", correct: false, selected: true },
              { id: "b", text: "two", correct: true, selected: false },
            ],
          },
        ],
      },
    });
    renderWithProviders(<ResultPage examId="ex-1" />, { locale: "bn" });
    expect(await screen.findByText("আপনার উত্তর")).toBeInTheDocument();
    expect(screen.getByText("সঠিক উত্তর")).toBeInTheDocument();
    expect(screen.getByText("ব্যাখ্যা:", { exact: false })).toBeInTheDocument();
  });

  it("written, failed to submit: the mark is zero and the result says so", async () => {
    mockApi({
      "GET /api/v1/live-exams/ex-1": exam({ examType: "WRITTEN", myStatus: "FAILED_TO_SUBMIT" }),
      "GET /api/v1/live-exams/ex-1/result": { score: 0, maximum: 50, passed: false, status: "FAILED_TO_SUBMIT", questions: null, comment: null, evaluatedPdf: null },
    });
    renderWithProviders(<ResultPage examId="ex-1" />, { locale: "bn" });
    expect(await screen.findByText("জমা হয়নি")).toBeInTheDocument();
    expect(screen.getByText("০")).toBeInTheDocument();
  });

  it("written, stored but not published: marks and rank wait, and no comment or marked script shows", async () => {
    mockApi({
      "GET /api/v1/live-exams/ex-1": exam({ examType: "WRITTEN", myStatus: "AWAITING_RESULT" }),
      "GET /api/v1/live-exams/ex-1/result": { score: null, maximum: 50, passed: null, status: "SUBMITTED", questions: null, comment: null, evaluatedPdf: null },
    });
    renderWithProviders(<ResultPage examId="ex-1" />, { locale: "en" });
    expect(await screen.findByText("Pending")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "View marked script" })).not.toBeInTheDocument();
  });

  it("written, published: shows the comment and the marked-script control", async () => {
    mockApi({
      "GET /api/v1/live-exams/ex-1": exam({ examType: "WRITTEN", myStatus: "RESULT_READY" }),
      "GET /api/v1/live-exams/ex-1/result": { score: 42, maximum: 50, passed: true, status: "RESULT_READY", questions: null, comment: "ভালো লিখেছেন।", evaluatedPdf: true },
    });
    renderWithProviders(<ResultPage examId="ex-1" />, { locale: "bn" });
    expect(await screen.findByText("ভালো লিখেছেন।")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "মূল্যায়ন করা খাতা দেখুন" })).toBeInTheDocument();
  });
});

describe("own row, rank and kind from the API", () => {
  it("highlights the student's own leaderboard row", async () => {
    mockApi({
      "GET /api/v1/live-exams/ex-1": exam(),
      "GET /api/v1/live-exams/ex-1/leaderboard": {
        rows: [
          { name: "Asha", rank: 1, score: 48, maximum: 50, passed: true, mine: false },
          { name: "Rafi", rank: 2, score: 45, maximum: 50, passed: true, mine: true },
        ],
      },
    });
    renderWithProviders(<LeaderboardPage examId="ex-1" />, { locale: "en" });
    // The table shows each row as a desktop row and as a phone card, so the marker can appear twice.
    expect((await screen.findAllByText("Rafi · You")).length).toBeGreaterThan(0);
    expect(screen.queryByText("Asha · You")).not.toBeInTheDocument();
  });

  it("gives the student's rank on the result page from their own row", async () => {
    mockApi({
      "GET /api/v1/live-exams/ex-1": exam(),
      "GET /api/v1/live-exams/ex-1/leaderboard": {
        rows: [
          { name: "Asha", rank: 1, score: 48, maximum: 50, passed: true, mine: false },
          { name: "Rafi", rank: 2, score: 45, maximum: 50, passed: true, mine: true },
        ],
      },
      "GET /api/v1/live-exams/ex-1/result": { kind: "LIVE", score: 45, maximum: 50, passed: true, status: "SUBMITTED", questions: null, comment: null, evaluatedPdf: null },
    });
    renderWithProviders(<ResultPage examId="ex-1" />, { locale: "en" });
    expect(await screen.findByText("2")).toBeInTheDocument();
  });

  it("labels a later attempt as practice on the result page", async () => {
    mockApi({
      "GET /api/v1/live-exams/ex-1": exam(),
      "GET /api/v1/live-exams/ex-1/leaderboard": { rows: [] },
      "GET /api/v1/live-exams/ex-1/result": { kind: "RETAKE", score: 30, maximum: 50, passed: false, status: "SUBMITTED", questions: null, comment: null, evaluatedPdf: null },
    });
    renderWithProviders(<ResultPage examId="ex-1" />, { locale: "en" });
    expect(await screen.findByText("Practice attempt · not on the merit list")).toBeInTheDocument();
  });

  it("the results hub labels a later attempt as practice", async () => {
    mockApi({
      "GET /api/v1/live-exam-modules": { content: [{ id: "m-1", titleEn: "BJS", titleBn: "বিজেএস" }], page: 0, size: 50, totalElements: 1, totalPages: 1 },
      "GET /api/v1/live-exam-modules/m-1/exams": {
        content: [{ id: "ex-1", moduleId: "m-1", titleEn: "CRPC", titleBn: "সিআরপিসি", examType: "MCQ", liveAt: exam().liveAt, myStatus: "RESULT_READY" }],
        page: 0,
        size: 50,
        totalElements: 1,
        totalPages: 1,
      },
      "GET /api/v1/live-exams/ex-1/result": { kind: "RETAKE", score: 30, maximum: 50, passed: false, status: "RESULT_READY", questions: null, comment: null, evaluatedPdf: null },
    });
    renderWithProviders(<ResultsHub />, { locale: "en" });
    expect(await screen.findByText("Practice attempt · not on the merit list")).toBeInTheDocument();
  });
});

describe("written answer checks", () => {
  it("accepts a PDF and refuses an image renamed to .pdf", async () => {
    expect(await isPdf(new Blob(["%PDF-1.7 rest"], { type: "application/pdf" }))).toBe(true);
    expect(await isPdf(new Blob([new Uint8Array([0x89, 0x50, 0x4e, 0x47])], { type: "image/png" }))).toBe(false);
  });

  it("the answer limit is 80 MB", () => {
    expect(MAX_ANSWER_BYTES).toBe(80 * 1024 * 1024);
  });
});

