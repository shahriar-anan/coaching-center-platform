import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ResultsHub } from "@/components/student/result/results-hub";
import type { MyExamResult } from "@/lib/api/exam/sitting";
import en from "@/i18n/messages/en/reviews";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

/** One sitting that has a result, as `GET /api/v1/me/exam-results` lists it. */
function sat(overrides: Partial<MyExamResult> = {}): MyExamResult {
  return {
    examId: "ex-1",
    titleEn: "CRPC",
    titleBn: "সিআরপিসি",
    examType: "MCQ",
    liveAt: "2026-10-20T04:00:00Z",
    passPercent: 40,
    status: "RESULT_READY",
    detail: "RESULT_READY",
    kind: "LIVE",
    attempts: 1,
    submittedAt: "2026-10-20T05:00:00Z",
    resultVisible: true,
    score: 38,
    maximum: 50,
    passed: true,
    ...overrides,
  };
}

function page(content: MyExamResult[]) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

describe("results hub", () => {
  it("shows no Rate action on the list; rating is on the exam's result page", async () => {
    mockApi({
      "GET /api/v1/me/exam-results": page([sat({ examId: "ex-1", titleEn: "CRPC" }), sat({ examId: "ex-2", titleEn: "Evidence Act" })]),
      "GET /api/v1/me/reviews": [],
    });
    renderWithProviders(<ResultsHub />, { user: fixtures.student, locale: "en" });

    expect(await screen.findByText("Evidence Act")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: en.rate })).not.toBeInTheDocument();
  });
});
