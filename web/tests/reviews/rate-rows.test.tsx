import { screen, within } from "@testing-library/react";
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

describe("results hub Rate action", () => {
  it("offers Rate on a result the student has not reviewed, linking to its result page", async () => {
    mockApi({
      "GET /api/v1/me/exam-results": page([sat({ examId: "ex-1", titleEn: "CRPC" })]),
      "GET /api/v1/me/reviews": [],
    });
    renderWithProviders(<ResultsHub />, { user: fixtures.student, locale: "en" });

    expect(await screen.findByText("CRPC")).toBeInTheDocument();
    expect(await screen.findByRole("link", { name: en.rate })).toHaveAttribute("href", "/exam/ex-1/result");
  });

  it("hides Rate on a result already reviewed, and keeps it on the others", async () => {
    mockApi({
      "GET /api/v1/me/exam-results": page([
        sat({ examId: "ex-1", titleEn: "CRPC" }),
        sat({ examId: "ex-2", titleEn: "Evidence Act" }),
      ]),
      "GET /api/v1/me/reviews": [
        {
          kind: "exam",
          targetId: "ex-1",
          targetTitleEn: "CRPC",
          targetTitleBn: "সিআরপিসি",
          review: { id: "r1", rating: 4, comment: null, status: "APPROVED", hideReason: null, createdAt: "", updatedAt: "" },
        },
      ],
    });
    renderWithProviders(<ResultsHub />, { user: fixtures.student, locale: "en" });

    const row = (await screen.findByText("Evidence Act")).closest("li");
    expect(row).not.toBeNull();
    expect(within(row as HTMLElement).getByRole("link", { name: en.rate })).toHaveAttribute("href", "/exam/ex-2/result");
    expect(screen.getAllByRole("link", { name: en.rate })).toHaveLength(1);
  });
});
