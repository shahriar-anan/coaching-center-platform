import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ExamPage } from "@/components/student/catalog/exam-page";
import { ResultPage } from "@/components/student/result/result-page";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const EXAM_ID = "ex-retake";
const PUBLIC_EXAM = `/api/v1/public/live-exams/${EXAM_ID}`;
const OWN_EXAM = `/api/v1/live-exams/${EXAM_ID}`;
const RESULT = `/api/v1/live-exams/${EXAM_ID}/result`;

function exam(retakeAvailable: boolean) {
  return {
    id: EXAM_ID,
    moduleId: null,
    titleEn: "Mock one",
    titleBn: "মক ওয়ান",
    descriptionEn: null,
    descriptionBn: null,
    examType: "MCQ",
    status: "PUBLISHED",
    phase: "ENDED",
    liveAt: "2026-10-01T04:00:00Z",
    endAt: "2026-10-01T05:00:00Z",
    durationMinutes: 40,
    price: 100,
    pricing: { price: 100, discountPercent: null, discountEndsAt: null, effectivePrice: 100, free: false, moduleOnly: false },
    access: { state: "GRANTED", buyable: false, blockedReason: null, owned: true, via: "PURCHASE" },
    packages: [],
    ratingAverage: null,
    ratingCount: 0,
    myStatus: "SUBMITTED",
    retakeAvailable: retakeAvailable,
  };
}

const result = {
  examId: EXAM_ID,
  examTitleEn: "Mock one",
  examTitleBn: "মক ওয়ান",
  examType: "MCQ",
  kind: "LIVE",
  status: "SUBMITTED",
  score: 8,
  maximum: 10,
  passed: true,
  questions: [],
};

describe("a finished MCQ offers a retake on its result page only", () => {
  it("shows Retake exam on the result page when the server offers a retake", async () => {
    mockApi({
      [`GET ${OWN_EXAM}`]: exam(true),
      [`GET ${RESULT}`]: result,
      [`GET /api/v1/live-exams/${EXAM_ID}/leaderboard`]: { rows: [] },
    });
    renderWithProviders(<ResultPage examId={EXAM_ID} />, { user: fixtures.student, locale: "en" });

    const retake = await screen.findByRole("link", { name: "Retake exam" });
    expect(retake).toHaveAttribute("href", `/exam/${EXAM_ID}/sit?start=retake`);
  });

  it("hides the retake on the result page when none is offered", async () => {
    mockApi({
      [`GET ${OWN_EXAM}`]: exam(false),
      [`GET ${RESULT}`]: result,
      [`GET /api/v1/live-exams/${EXAM_ID}/leaderboard`]: { rows: [] },
    });
    renderWithProviders(<ResultPage examId={EXAM_ID} />, { user: fixtures.student, locale: "en" });

    await screen.findByRole("heading", { name: "Mock one" });
    expect(screen.queryByRole("link", { name: "Retake exam" })).not.toBeInTheDocument();
  });

  it("the exam page itself does not offer a retake", async () => {
    mockApi({
      [`GET ${PUBLIC_EXAM}`]: exam(true),
      [`GET ${OWN_EXAM}`]: exam(true),
      "GET /api/v1/public/live-exam-categories": fixtures.categoryTree,
      "GET /api/v1/public/exam-site/status": { checkoutEnabled: true },
    });
    renderWithProviders(<ExamPage examId={EXAM_ID} />, { user: fixtures.student, locale: "en" });

    await screen.findByRole("heading", { name: "Mock one" });
    expect(screen.queryByRole("button", { name: "Retake exam" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Retake exam" })).not.toBeInTheDocument();
  });
});
