import { Suspense } from "react";
import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ExamsPage } from "@/components/staff/exams/exams-page";
import { fixtures, renderWithProviders } from "../helpers";
import { mockApi } from "../helpers/mock-api";

const pageOf = (content: unknown[]) => ({ content, page: 0, size: 200, totalElements: content.length, totalPages: 1 });

const examRow = {
  id: "exam-1",
  moduleId: "pkg-1",
  examType: "MCQ",
  paperLanguage: null,
  titleEn: "Weekly quiz",
  titleBn: "সাপ্তাহিক কুইজ",
  liveAt: null,
  endAt: null,
  durationMinutes: 30,
  maximum: 50,
  mode: "LIVE",
  status: "PUBLISHED",
  price: null,
  finallySaved: true,
  questionPdf: null,
  questions: null,
  packages: [{ id: "pkg-1", titleEn: "BJS Full", titleBn: "বিজেএস" }],
  pendingChanges: true,
  free: false,
};

function renderPage(exams: unknown[]) {
  mockApi({
    "GET /api/v1/live-exams": pageOf(exams),
    "GET /api/v1/live-exam-modules": pageOf([{ id: "pkg-1", titleEn: "BJS Full", titleBn: "বিজেএস", status: "PUBLISHED" }]),
    "GET /api/v1/live-exam-categories": [],
    "GET /api/v1/live-exam-pending-changes": { exams: 1, packages: 0, items: [] },
  });
  return renderWithProviders(
    <Suspense fallback={null}>
      <ExamsPage />
    </Suspense>,
    { user: fixtures.masterAdmin, locale: "en" },
  );
}

describe("the Exams page", () => {
  it("shows the empty state when there are no exams", async () => {
    renderPage([]);

    expect(await screen.findByText("No exams yet")).toBeInTheDocument();
  });

  it("groups an exam under its package, with an Open button and the pending dot", async () => {
    renderPage([examRow]);

    expect(await screen.findByRole("heading", { level: 2, name: "BJS Full" })).toBeInTheDocument();
    expect(screen.getByText("Weekly quiz")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Open" })).toHaveAttribute("href", "/staff/exams/exam-1");
    expect(screen.getByRole("link", { name: "Open package" })).toHaveAttribute("href", "/staff/modules/pkg-1");
    // The group and the row each show the pending dot.
    expect(screen.getAllByRole("img", { name: "Has changes waiting" })).toHaveLength(2);
  });
});
