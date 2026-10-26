import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ResultPage } from "@/components/student/result/result-page";
import type { StudentExam } from "@/lib/api/exam/sitting";
import { mockApi, renderWithProviders } from "../helpers";

const MINUTE = 60_000;

function writtenExam(): StudentExam {
  return {
    id: "ex-1",
    moduleId: "m-1",
    examType: "WRITTEN",
    paperLanguage: "BN",
    titleEn: "BJS",
    titleBn: "বিজেএস",
    liveAt: new Date(Date.now() - 3 * 60 * MINUTE).toISOString(),
    endAt: new Date(Date.now() - 2 * 60 * MINUTE).toISOString(),
    durationMinutes: 40,
    passPercent: 40,
    maximum: 50,
    status: "PUBLISHED",
    finallySaved: true,
    price: null,
    questionPdf: null,
    questions: null,
    myStatus: "SUBMITTED",
    retakeAvailable: null,
    sitting: null,
  } as unknown as StudentExam;
}

function renderWritten(result: Record<string, unknown>) {
  mockApi({ "GET /api/v1/live-exams/ex-1": writtenExam(), "GET /api/v1/live-exams/ex-1/result": result });
  return renderWithProviders(<ResultPage examId="ex-1" />, { locale: "bn" });
}

const base = { maximum: 50, passed: null, questions: null, comment: null, evaluatedPdf: null };

describe("written result states (ST-6 follow-up)", () => {
  it("awaiting: submitted and not marked yet, marks wait", async () => {
    renderWritten({ ...base, score: null, status: "SUBMITTED" });
    expect(await screen.findByText("আপনার খাতার মূল্যায়ন চলছে। ফলাফল প্রকাশ হলে এখানে দেখা যাবে।")).toBeInTheDocument();
    expect(screen.queryByText("শিক্ষকের মন্তব্য")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "মূল্যায়ন করা খাতা দেখুন" })).not.toBeInTheDocument();
  });

  it("published with a comment: shows the teacher's comment, no marked script when there is none", async () => {
    renderWritten({ ...base, score: 36, passed: true, status: "RESULT_READY", comment: "ভালো লিখেছেন।" });
    expect(await screen.findByText("শিক্ষকের মন্তব্য")).toBeInTheDocument();
    expect(screen.getByText("ভালো লিখেছেন।")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "মূল্যায়ন করা খাতা দেখুন" })).not.toBeInTheDocument();
  });

  it("published with the marked script: the download control opens and closes the script", async () => {
    renderWritten({
      ...base,
      score: 36,
      passed: true,
      status: "RESULT_READY",
      evaluatedPdf: { name: "marked.pdf" },
    });
    const control = await screen.findByRole("button", { name: "মূল্যায়ন করা খাতা দেখুন" });
    expect(control).toHaveAttribute("aria-expanded", "false");
    await userEvent.click(control);
    expect(screen.getByRole("button", { name: "খাতাটি বন্ধ করুন" })).toHaveAttribute("aria-expanded", "true");
  });
});
