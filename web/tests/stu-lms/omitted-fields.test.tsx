import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import NoticesPage from "@/app/(site)/notices/page";
import { ResultPage } from "@/components/student/result/result-page";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

// The API leaves out a field that is null, so these screens must treat "missing" like "null".

const exam = {
  id: "ex-w",
  moduleId: "m-1",
  examType: "WRITTEN",
  paperLanguage: "BN",
  titleEn: "Written paper",
  titleBn: "রিটেন পত্র",
  liveAt: "2026-10-27T10:00:00Z",
  endAt: "2026-10-27T11:00:00Z",
  durationMinutes: 60,
  passPercent: 50,
  maximum: 50,
  status: "PUBLISHED",
  finallySaved: true,
  myStatus: "SUBMITTED",
};

describe("fields the API leaves out", () => {
  it("a written result that is not released shows no marks and no pass or fail", async () => {
    // No `score`, `passed` or `comment` key at all: the paper is marked but not released.
    mockApi({
      "GET /api/v1/live-exams/ex-w": exam,
      "GET /api/v1/live-exams/ex-w/result": { maximum: 50, status: "SUBMITTED" },
    });
    renderWithProviders(<ResultPage examId="ex-w" />, { user: fixtures.student, locale: "en" });

    expect(await screen.findByText("Marks obtained")).toBeInTheDocument();
    expect(document.body.textContent).not.toContain("undefined");
    expect(screen.queryByText("Not passed")).toBeNull();
  });

  it("a notice with no score or outcome shows neither, and no 'undefined'", async () => {
    mockApi({
      "GET /api/v1/me/notices": {
        content: [
          { id: "n1", kind: "EXAM_OPEN", createdAt: "2026-10-27T00:10:00Z", exam: { id: "e1", moduleId: "m1", examType: "WRITTEN", titleEn: "Written paper", titleBn: "রিটেন পত্র" } },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
    });
    renderWithProviders(<NoticesPage />, { user: fixtures.student, locale: "en" });

    expect(await screen.findByText("Written paper")).toBeInTheDocument();
    expect(document.body.textContent).not.toContain("undefined");
    expect(screen.queryByText("Not passed")).toBeNull();
  });
});
