import { screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ResultPage } from "@/components/student/result/result-page";
import { WrittenSitting } from "@/components/student/sitting/written-sitting";
import bn from "@/i18n/messages/bn/sitting";
import en from "@/i18n/messages/en/sitting";
import type { StudentExam } from "@/lib/api/exam/sitting";
import { mockApi, renderWithProviders } from "../helpers";

const MINUTE = 60_000;

vi.mock("@/lib/api/exam/sitting", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api/exam/sitting")>();
  return { ...actual, useUploadAnswer: () => ({ mutateAsync: vi.fn(), isPending: false }) };
});

const BN_LATE = "খাতা জমা দিতে ভুলে গেলেও শেষ সময়ে সর্বশেষ আপলোড করা খাতাটি জমা হবে। কোনো খাতা আপলোড না হলে পরীক্ষা শেষ করা যাবে না।";
const EN_LATE =
  "Even if you forget to submit, the last script you uploaded before the deadline will count. If no script is uploaded, the exam cannot be finished.";

describe("written paper copy (S-024)", () => {
  it("the late-upload sentence is in both languages, and the instruction no longer asks for a submit click", () => {
    expect(bn.written.lateUpload).toBe(BN_LATE);
    expect(en.written.lateUpload).toBe(EN_LATE);
    expect(bn.written.instruction).not.toContain("জমা দিন");
    expect(en.written.instruction).not.toMatch(/submit/i);
  });

  it("the paper page shows the late-upload sentence", () => {
    renderWithProviders(
      <WrittenSitting
        exam={{ id: "ex-w", questionPdf: null } as unknown as StudentExam}
        deadlineAt={new Date(Date.now() + 60 * MINUTE).toISOString()}
        watermark="নাঈম"
        onClosed={() => undefined}
      />,
      { locale: "bn" },
    );
    expect(screen.getByText(BN_LATE)).toBeInTheDocument();
  });

  it("a written exam that could not finish says so", async () => {
    const exam = {
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
      myStatus: "FAILED_TO_SUBMIT",
      retakeAvailable: null,
      sitting: null,
    } as unknown as StudentExam;
    mockApi({
      "GET /api/v1/live-exams/ex-1": exam,
      "GET /api/v1/live-exams/ex-1/result": {
        score: 0,
        maximum: 50,
        passed: false,
        status: "FAILED_TO_SUBMIT",
        comment: null,
        evaluatedPdf: null,
        questions: null,
      },
    });
    renderWithProviders(<ResultPage examId="ex-1" />, { locale: "bn" });
    expect(await screen.findByText("পরীক্ষা শেষ করা যায়নি।")).toBeInTheDocument();
    expect(screen.getByText("সময়ের মধ্যে খাতা জমা হয়নি, তাই নম্বর ০।")).toBeInTheDocument();
  });
});
