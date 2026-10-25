import { screen, waitFor, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { McqPaper } from "@/components/student/sitting/mcq-paper";
import type { StudentExam } from "@/lib/api/exam/sitting";
import { backupKey } from "@/components/student/sitting/selection";
import { mockApi, apiError, renderWithProviders as renderWs4 } from "../helpers";

const STUDENT = "stu-1";
const STARTED = "2026-11-12T06:00:00Z";

function exam(overrides: Partial<StudentExam> = {}, selectedFor?: string): StudentExam {
  const questions = [1, 2, 3].map((n) => ({
    id: `q${n}`,
    stem: `প্রশ্ন ${n}`,
    marks: 1,
    explanation: null,
    options: ["a", "b", "c", "d"].map((letter) => ({
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
    paperLanguage: "BN",
    titleEn: "CRPC",
    titleBn: "সিআরপিসি",
    descriptionEn: null,
    descriptionBn: null,
    rulesEn: null,
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
    ...overrides,
  };
}

function open(deadlineMs: number) {
  return new Date(Date.now() + deadlineMs).toISOString();
}

beforeEach(() => {
  window.localStorage.clear();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("MCQ paper", () => {
  it("saves the whole selection after a short pause, and the latest tap wins", async () => {
    const { calls } = mockApi({ "PUT /api/v1/live-exams/ex-1/answers": () => undefined });
    const user = userEvent.setup();
    renderWs4(
      <McqPaper exam={exam()} studentId={STUDENT} startedAt={STARTED} deadlineAt={open(60 * 60_000)} kind="LIVE" onClosed={() => {}} />,
    );
    await user.click(screen.getAllByRole("radio")[1]); // q1 option b
    await user.click(screen.getAllByRole("radio")[6]); // q2 option c
    await user.click(screen.getAllByRole("radio")[5]); // q2 option b (replaces c)
    await waitFor(() => expect(calls.some((c) => c.method === "PUT")).toBe(true));
    const puts = calls.filter((c) => c.method === "PUT");
    expect(puts.length).toBeLessThanOrEqual(2);
    const last = puts[puts.length - 1].body as { answers: { questionId: string; optionId: string }[] };
    expect(last.answers).toEqual(
      expect.arrayContaining([
        { questionId: "q1", optionId: "q1-b" },
        { questionId: "q2", optionId: "q2-b" },
      ]),
    );
    expect(last.answers).toHaveLength(2);
  });

  it("shows the selection the server already has, so a reload keeps the answers", () => {
    renderWs4(
      <McqPaper
        exam={exam({}, "q2-c")}
        studentId={STUDENT}
        startedAt={STARTED}
        deadlineAt={open(60 * 60_000)}
        kind="LIVE"
        onClosed={() => {}}
      />,
    );
    const q2Options = screen.getAllByRole("radio").slice(4, 8);
    expect(q2Options[2]).toHaveAttribute("aria-checked", "true");
    expect(screen.getByText("১/৩")).toBeInTheDocument();
  });

  it("the submit dialog counts answered and unanswered questions", async () => {
    mockApi({
      "PUT /api/v1/live-exams/ex-1/answers": () => undefined,
      "POST /api/v1/live-exams/ex-1/submit": { participantId: "p", score: 1, maximum: 3, passed: false, status: "SUBMITTED" },
    });
    const user = userEvent.setup();
    renderWs4(
      <McqPaper exam={exam()} studentId={STUDENT} startedAt={STARTED} deadlineAt={open(60 * 60_000)} kind="LIVE" onClosed={() => {}} />,
    );
    await user.click(screen.getAllByRole("radio")[1]);
    await user.click(screen.getByRole("button", { name: "জমা দিন" }));
    expect(await screen.findByRole("dialog")).toHaveTextContent("১টি প্রশ্নের উত্তর দিয়েছেন, ২টি বাকি।");
  });

  it("moves to the result when the deadline passes, without a refresh", async () => {
    mockApi({ "PUT /api/v1/live-exams/ex-1/answers": () => undefined });
    const onClosed = vi.fn();
    renderWs4(
      <McqPaper exam={exam()} studentId={STUDENT} startedAt={STARTED} deadlineAt={open(300)} kind="LIVE" onClosed={onClosed} />,
    );
    await waitFor(() => expect(onClosed).toHaveBeenCalledTimes(1), { timeout: 3000 });
  });

  it("stops taps once the server reports the sitting closed", async () => {
    mockApi({ "PUT /api/v1/live-exams/ex-1/answers": apiError(409, "DEADLINE_PASSED") });
    const user = userEvent.setup();
    renderWs4(
      <McqPaper exam={exam()} studentId={STUDENT} startedAt={STARTED} deadlineAt={open(60 * 60_000)} kind="LIVE" onClosed={() => {}} />,
    );
    await user.click(screen.getAllByRole("radio")[0]);
    await waitFor(() => expect(screen.getAllByRole("radio")[0]).toBeDisabled());
  });

  it("keeps the backup on this device and uses it when it is newer than the server", () => {
    const key = backupKey("ex-1", STUDENT, STARTED);
    window.localStorage.setItem(
      key,
      JSON.stringify({ selection: { q3: "q3-d" }, flagged: ["q1"], savedAt: Date.now() + 10_000 }),
    );
    renderWs4(
      <McqPaper exam={exam()} studentId={STUDENT} startedAt={STARTED} deadlineAt={open(60 * 60_000)} kind="LIVE" onClosed={() => {}} />,
    );
    expect(screen.getAllByRole("radio")[11]).toHaveAttribute("aria-checked", "true");
    expect(screen.getAllByRole("button", { name: "✓ পরে দেখব" })[0]).toHaveAttribute("aria-pressed", "true");
  });

  it("the later attempt is labelled as practice", () => {
    renderWs4(
      <McqPaper exam={exam()} studentId={STUDENT} startedAt={STARTED} deadlineAt={open(60 * 60_000)} kind="RETAKE" onClosed={() => {}} />,
    );
    expect(screen.getByRole("note")).toHaveTextContent("এটি অনুশীলন। এর নম্বর মেধাতালিকায় আসবে না।");
  });

  it("flags stay off the server", async () => {
    const { calls } = mockApi({ "PUT /api/v1/live-exams/ex-1/answers": () => undefined });
    const user = userEvent.setup();
    renderWs4(
      <McqPaper exam={exam()} studentId={STUDENT} startedAt={STARTED} deadlineAt={open(60 * 60_000)} kind="LIVE" onClosed={() => {}} />,
    );
    await user.click(screen.getAllByRole("button", { name: "পরে দেখব" })[0]);
    expect(screen.getAllByRole("button", { name: "✓ পরে দেখব" })[0]).toBeInTheDocument();
    expect(calls.filter((c) => c.method === "PUT")).toHaveLength(0);
    fireEvent.click(screen.getByRole("button", { name: "প্রশ্ন" }));
    expect(screen.getByRole("navigation")).toBeInTheDocument();
  });
});
