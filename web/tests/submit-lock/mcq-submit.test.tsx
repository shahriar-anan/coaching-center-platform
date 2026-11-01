import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { McqPaper } from "@/components/student/sitting/mcq-paper";
import type { StudentExam } from "@/lib/api/exam/sitting";
import { apiError, mockApi, renderWithProviders } from "../helpers";

const STARTED = new Date(Date.now() - 60_000).toISOString();
const DEADLINE = new Date(Date.now() + 60 * 60_000).toISOString();

function exam(): StudentExam {
  const questions = [1, 2].map((n) => ({
    id: `q${n}`,
    stem: `প্রশ্ন ${n}`,
    marks: 1,
    explanation: null,
    options: ["a", "b"].map((letter) => ({ id: `q${n}-${letter}`, text: `option ${letter}`, correct: null, selected: null })),
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
    maximum: 2,
    status: "PUBLISHED",
    finallySaved: true,
    price: null,
    questionPdf: null,
    questions,
    myStatus: "IN_PROGRESS",
    retakeAvailable: null,
    sitting: null,
  } as unknown as StudentExam;
}

/** Taps one answer and submits the paper through its dialog. */
async function answerAndSubmit(onSubmitted: () => void, onClosed = () => undefined) {
  const user = userEvent.setup();
  renderWithProviders(
    <McqPaper exam={exam()} studentId="stu-1" startedAt={STARTED} deadlineAt={DEADLINE} kind="LIVE" onClosed={onClosed} onSubmitted={onSubmitted} />,
    { locale: "bn" },
  );
  await user.click(screen.getAllByRole("radio")[0]);
  await user.click(screen.getByRole("button", { name: "জমা দিন" }));
  await user.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "জমা দিন" }));
  return user;
}

describe("a submitted MCQ paper is final", () => {
  it("reports the submit once, and takes no more taps after it", async () => {
    mockApi({
      "PUT /api/v1/live-exams/ex-1/answers": () => undefined,
      "POST /api/v1/live-exams/ex-1/submit": { participantId: "p", score: 1, maximum: 2, passed: false, status: "SUBMITTED" },
    });
    const onSubmitted = vi.fn();
    const user = await answerAndSubmit(onSubmitted);

    await waitFor(() => expect(onSubmitted).toHaveBeenCalledTimes(1));
    const radios = screen.getAllByRole("radio");
    for (const radio of radios) {
      expect(radio).toBeDisabled();
    }
    await user.click(radios[1]);
    expect(radios[1]).toHaveAttribute("aria-checked", "false");
  });

  it("a failed submit leaves the paper open and does not report a submit", async () => {
    mockApi({
      "PUT /api/v1/live-exams/ex-1/answers": () => undefined,
      "POST /api/v1/live-exams/ex-1/submit": apiError(500, "INTERNAL_ERROR"),
    });
    const onSubmitted = vi.fn();
    await answerAndSubmit(onSubmitted);

    expect(await screen.findByRole("alert")).toHaveTextContent("জমা দেওয়া যায়নি। আবার চেষ্টা করুন।");
    expect(onSubmitted).not.toHaveBeenCalled();
    expect(screen.getAllByRole("radio")[1]).not.toBeDisabled();
  });
});
