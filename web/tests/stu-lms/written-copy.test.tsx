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
  return {
    ...actual,
    useSaveAnswerDraft: () => ({ mutateAsync: vi.fn(), isPending: false }),
    useSubmitExam: () => ({ mutateAsync: vi.fn(), isPending: false }),
  };
});

const BN_FINAL = "জমা দেওয়ার পর আর খাতা বদলানো যাবে না।";
const BN_AUTO = "সময় শেষ হলে সবশেষে আপলোড করা ফাইলটি নিজে থেকে জমা হয়ে যাবে, এই পাতা বন্ধ থাকলেও।";

describe("written paper copy (written-draft plan)", () => {
  it("says, in both languages, that the script is final after submit and that the server submits the last upload", () => {
    expect(bn.written.replaceNote).toBe(BN_FINAL);
    expect(en.written.replaceNote).toBe("After you submit, the script cannot be changed.");
    expect(bn.written.autoSendNote).toBe(BN_AUTO);
    expect(en.written.autoSendNote).toMatch(/last file you uploaded is submitted for you/);
    expect(en.written.instruction).toMatch(/replace it until you submit or the time ends/);
  });

  it("the paper page shows both sentences", () => {
    renderWithProviders(
      <WrittenSitting
        exam={{ id: "ex-w", questionPdf: null } as unknown as StudentExam}
        deadlineAt={new Date(Date.now() + 60 * MINUTE).toISOString()}
        studentLine="নাঈম"
        onClosed={() => undefined}
        onSubmitted={() => undefined}
      />,
      { locale: "bn" },
    );
    expect(screen.getByText(BN_FINAL)).toBeInTheDocument();
    expect(screen.getByText(BN_AUTO)).toBeInTheDocument();
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
