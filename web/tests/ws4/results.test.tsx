import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { LeaderboardPage, leaderboardOpensAt } from "@/components/student/result/leaderboard-page";
import { ResultPage } from "@/components/student/result/result-page";
import { ResultsHub } from "@/components/student/result/results-hub";
import { isPdf, MAX_ANSWER_BYTES } from "@/components/student/sitting/written-sitting";
import type { MyExamResult, StudentExam } from "@/lib/api/exam/sitting";
import { apiError, mockApi, renderWithProviders } from "../helpers";

// The viewer loads pdf.js. This stub asks for the file the way the viewer does, so the request can be checked.
vi.mock("@/components/pdf/pdf-viewer", () => ({
  PdfViewer: ({ load, label }: { load: () => Promise<unknown>; label?: string }) => {
    load().catch(() => undefined);
    return <p data-testid="pdf-viewer">{label}</p>;
  },
}));

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

/** One row of `GET /api/v1/me/exam-results`: the latest attempt of an exam the student has sat. */
function sat(overrides: Partial<MyExamResult> = {}): MyExamResult {
  return {
    examId: "ex-1",
    titleEn: "CRPC",
    titleBn: "সিআরপিসি",
    examType: "MCQ",
    liveAt: new Date(Date.now() - 3 * 60 * MINUTE).toISOString(),
    passPercent: 40,
    status: "RESULT_READY",
    detail: "RESULT_READY",
    kind: "LIVE",
    attempts: 1,
    submittedAt: new Date(Date.now() - 2 * 60 * MINUTE).toISOString(),
    resultVisible: true,
    score: 38,
    maximum: 50,
    passed: true,
    ...overrides,
  };
}

function page(content: MyExamResult[], pageNumber = 0, totalPages = 1) {
  return { content, page: pageNumber, size: 20, totalElements: content.length, totalPages };
}

describe("results hub", () => {
  it("lists results on the first tab, with marks and the pass pill", async () => {
    mockApi({
      "GET /api/v1/me/exam-results": page([sat()]),
    });
    renderWithProviders(<ResultsHub />, { locale: "en" });
    expect(await screen.findByText("CRPC")).toBeInTheDocument();
    expect(screen.getByText(/38 \/ 50/)).toBeInTheDocument();
    expect(screen.getAllByText(/Passed/).length).toBeGreaterThan(0);
  });

  it("the marking tab shows written scripts awaiting marks, and an empty state when there are none", async () => {
    const user = userEvent.setup();
    mockApi({
      "GET /api/v1/me/exam-results": page([]),
    });
    renderWithProviders(<ResultsHub />, { locale: "en" });
    expect(await screen.findByText("No results yet")).toBeInTheDocument();
    await user.click(screen.getByRole("tab", { name: /Being marked/ }));
    expect(screen.getByText("No scripts are waiting to be marked.")).toBeInTheDocument();
  });

  it("sends each row to the tab its visibility and status give, and shows a standalone exam", async () => {
    const user = userEvent.setup();
    mockApi({
      "GET /api/v1/me/exam-results": page([
        sat({ examId: "ex-a", titleEn: "Alpha exam", titleBn: "Alpha exam" }),
        sat({ examId: "ex-b", titleEn: "Beta exam", titleBn: "Beta exam", examType: "WRITTEN", status: "AWAITING_RESULT", detail: "AWAITING_RESULT", resultVisible: false, score: undefined, passed: undefined }),
        sat({ examId: "ex-c", titleEn: "Gamma exam", titleBn: "Gamma exam", status: "SUBMITTED", detail: "SUBMITTED", resultVisible: false, score: undefined, passed: undefined }),
        sat({ examId: "ex-d", titleEn: "Delta exam", titleBn: "Delta exam", examType: "WRITTEN", status: "FAILED_TO_SUBMIT", detail: "FAILED_TO_SUBMIT", score: 0, passed: false }),
      ]),
    });
    renderWithProviders(<ResultsHub />, { locale: "en" });

    expect(await screen.findByText("Alpha exam")).toBeInTheDocument();
    expect(screen.getByText("Delta exam")).toBeInTheDocument();
    expect(screen.queryByText("Beta exam")).not.toBeInTheDocument();
    expect(screen.queryByText("Gamma exam")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Alpha exam/ })).toHaveAttribute("href", "/exam/ex-a/result");

    // A submitted attempt with no result yet is being marked, not in progress: the answers are final.
    await user.click(screen.getByRole("tab", { name: /Being marked/ }));
    expect(screen.getByText("Beta exam")).toBeInTheDocument();
    expect(screen.getByText("Gamma exam")).toBeInTheDocument();
    expect(screen.queryByText("Alpha exam")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Gamma exam/ })).toHaveAttribute("href", "/exam/ex-c/result");

    await user.click(screen.getByRole("tab", { name: /In progress/ }));
    expect(screen.queryByText("Beta exam")).not.toBeInTheDocument();
    expect(screen.queryByText("Gamma exam")).not.toBeInTheDocument();
    expect(screen.getByText("No exams in progress.")).toBeInTheDocument();
  });

  it("loads the next page of results on Load more", async () => {
    const user = userEvent.setup();
    mockApi({
      "GET /api/v1/me/exam-results": ({ url }: { url: URL }) =>
        url.searchParams.get("page") === "1"
          ? page([sat({ examId: "ex-2", titleEn: "Second page exam", titleBn: "Second page exam" })], 1, 2)
          : page([sat({ examId: "ex-1", titleEn: "First page exam", titleBn: "First page exam" })], 0, 2),
    });
    renderWithProviders(<ResultsHub />, { locale: "en" });
    expect(await screen.findByText("First page exam")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Load more" }));
    expect(await screen.findByText("Second page exam")).toBeInTheDocument();
    expect(screen.getByText("First page exam")).toBeInTheDocument();
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

  it("an archived exam refuses the exam read, but the result still shows its title and score", async () => {
    mockApi({
      "GET /api/v1/live-exams/ex-1": apiError(404, "RESOURCE_NOT_FOUND"),
      "GET /api/v1/live-exams/ex-1/result": {
        kind: "LIVE",
        score: 38,
        maximum: 50,
        passed: true,
        status: "SUBMITTED",
        questions: null,
        comment: null,
        evaluatedPdf: null,
        examTitleEn: "Archived CRPC",
        examTitleBn: "সিআরপিসি",
        examType: "MCQ",
        passPercent: 40,
      },
    });
    renderWithProviders(<ResultPage examId="ex-1" />, { locale: "en" });
    expect(await screen.findByText("38 / 50")).toBeInTheDocument();
    expect(screen.getByText("Archived CRPC")).toBeInTheDocument();
    expect(screen.queryByText("This exam was not found")).not.toBeInTheDocument();
    expect(screen.queryByText("See the merit list")).not.toBeInTheDocument();
  });

  it("when the exam read and the result are both refused, the not-found message shows", async () => {
    mockApi({
      "GET /api/v1/live-exams/ex-1": apiError(404, "RESOURCE_NOT_FOUND"),
      "GET /api/v1/live-exams/ex-1/result": apiError(404, "RESOURCE_NOT_FOUND"),
    });
    renderWithProviders(<ResultPage examId="ex-1" />, { locale: "en" });
    expect(await screen.findByText("This exam was not found")).toBeInTheDocument();
  });
});

describe("written answer on the result page", () => {
  it("shows the submitted answer above the marked script and requests the answer PDF", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      "GET /api/v1/live-exams/ex-1": exam({ examType: "WRITTEN", myStatus: "RESULT_READY" }),
      "GET /api/v1/live-exams/ex-1/result": {
        score: 42,
        maximum: 50,
        passed: true,
        status: "RESULT_READY",
        questions: null,
        comment: null,
        evaluatedPdf: true,
        answerPdf: true,
      },
    });
    renderWithProviders(<ResultPage examId="ex-1" />, { locale: "en" });
    const answer = await screen.findByRole("heading", { name: "Your submitted answer" });
    const marked = screen.getByRole("button", { name: "View marked script" });
    expect(answer.compareDocumentPosition(marked) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();

    await user.click(screen.getByRole("button", { name: "View your answer" }));
    expect(await screen.findByTestId("pdf-viewer")).toHaveTextContent("Your submitted answer");
    await waitFor(() =>
      expect(api.calls.some((call) => call.method === "GET" && call.path === "/api/v1/live-exams/ex-1/answer-pdf")).toBe(true),
    );
  });

  it("shows the section while the script is being marked, before any mark is published", async () => {
    mockApi({
      "GET /api/v1/live-exams/ex-1": exam({ examType: "WRITTEN", myStatus: "AWAITING_RESULT" }),
      "GET /api/v1/live-exams/ex-1/result": { score: null, maximum: 50, passed: null, status: "SUBMITTED", questions: null, comment: null, evaluatedPdf: null, answerPdf: true },
    });
    renderWithProviders(<ResultPage examId="ex-1" />, { locale: "en" });
    expect(await screen.findByRole("heading", { name: "Your submitted answer" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "View marked script" })).not.toBeInTheDocument();
  });

  it("shows the section when the exam read is refused for an archived written exam", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      "GET /api/v1/live-exams/ex-1": apiError(404, "RESOURCE_NOT_FOUND"),
      "GET /api/v1/live-exams/ex-1/result": {
        kind: "LIVE",
        score: null,
        maximum: 50,
        passed: null,
        status: "SUBMITTED",
        questions: null,
        comment: null,
        evaluatedPdf: null,
        answerPdf: true,
        examTitleEn: "Archived written",
        examTitleBn: "আর্কাইভ রিটেন",
        examType: "WRITTEN",
        passPercent: 40,
      },
    });
    renderWithProviders(<ResultPage examId="ex-1" />, { locale: "en" });
    expect(await screen.findByRole("heading", { name: "Your submitted answer" })).toBeInTheDocument();
    expect(screen.queryByText("This exam was not found")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "View your answer" }));
    await waitFor(() =>
      expect(api.calls.some((call) => call.method === "GET" && call.path === "/api/v1/live-exams/ex-1/answer-pdf")).toBe(true),
    );
  });

  it("does not show the section when the attempt has no stored answer", async () => {
    mockApi({
      "GET /api/v1/live-exams/ex-1": exam({ examType: "WRITTEN", myStatus: "AWAITING_RESULT" }),
      "GET /api/v1/live-exams/ex-1/result": { score: null, maximum: 50, passed: null, status: "SUBMITTED", questions: null, comment: null, evaluatedPdf: null, answerPdf: false },
    });
    renderWithProviders(<ResultPage examId="ex-1" />, { locale: "en" });
    expect(await screen.findByText("Pending")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Your submitted answer" })).not.toBeInTheDocument();
  });

  it("does not show the section for an MCQ, even if the flag were set", async () => {
    mockApi({
      "GET /api/v1/live-exams/ex-1": exam(),
      "GET /api/v1/live-exams/ex-1/result": { score: 1, maximum: 3, passed: false, status: "SUBMITTED", comment: null, evaluatedPdf: null, questions: null, answerPdf: true },
    });
    renderWithProviders(<ResultPage examId="ex-1" />, { locale: "en" });
    expect(await screen.findByText("1 / 3")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Your submitted answer" })).not.toBeInTheDocument();
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
      "GET /api/v1/me/exam-results": page([sat({ kind: "RETAKE", score: 30, passed: false })]),
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

