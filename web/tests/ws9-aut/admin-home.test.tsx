import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AdminHome } from "@/components/staff/home/admin-home";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

describe("admin home first run and agenda (U7, UF6)", () => {
  it("shows the first three steps while there is no category, and hides them after", async () => {
    mockApi({
      "GET /api/v1/live-exam-categories": [],
      "GET /api/v1/live-exam-participation/summary": { awaitingMarking: 0, draftsAwaitingPublish: 0 },
      "GET /api/v1/live-exam-participation": { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 },
      "GET /api/v1/live-exam-orders": { content: [], page: 0, size: 1, totalElements: 0, totalPages: 0 },
    });
    const { unmount } = renderWithProviders(<AdminHome isMasterAdmin />, { user: fixtures.masterAdmin });

    expect(await screen.findByRole("heading", { name: "প্রথম পরীক্ষা তৈরি করুন" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "ক্যাটাগরি তৈরি করুন" })).toHaveAttribute("href", "/staff/categories/new");
    unmount();

    mockApi({
      "GET /api/v1/live-exam-categories": [{ id: "c1", parentId: null, titleEn: "BCS", titleBn: "বিজেএস", position: 1, archived: false, children: [], modules: [] }],
      "GET /api/v1/live-exam-participation/summary": { awaitingMarking: 0, draftsAwaitingPublish: 0 },
      "GET /api/v1/live-exam-participation": { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 },
      "GET /api/v1/live-exam-orders": { content: [], page: 0, size: 1, totalElements: 0, totalPages: 0 },
    });
    renderWithProviders(<AdminHome isMasterAdmin />, { user: fixtures.masterAdmin });
    expect(await screen.findByText("আগামী ১৪ দিনে কোনো তারিখ-দেওয়া পরীক্ষা নেই।")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "প্রথম পরীক্ষা তৈরি করুন" })).not.toBeInTheDocument();
  });

  it("groups dated exams in the next 14 days by day and leaves out later ones", async () => {
    const soon = new Date(Date.now() + 2 * 24 * 60 * 60 * 1000).toISOString();
    const later = new Date(Date.now() + 40 * 24 * 60 * 60 * 1000).toISOString();
    const row = (examId: string, titleBn: string, liveAt: string) => ({
      examId,
      moduleId: "m1",
      moduleTitleEn: "BCS",
      moduleTitleBn: "বিজেএস",
      examType: "MCQ",
      titleEn: titleBn,
      titleBn,
      liveAt,
      endAt: null,
      sitting: 12,
      submitted: 0,
      failedToSubmit: 0,
      awaitingMarking: null,
      draftsAwaitingPublish: null,
      published: null,
      retakes: 0,
    });
    mockApi({
      "GET /api/v1/live-exam-categories": [{ id: "c1", parentId: null, titleEn: "BCS", titleBn: "বিজেএস", position: 1, archived: false, children: [], modules: [] }],
      "GET /api/v1/live-exam-participation/summary": { awaitingMarking: 0, draftsAwaitingPublish: 0 },
      "GET /api/v1/live-exam-participation": {
        content: [row("a", "সিআরপিসি", soon), row("b", "দূরের পরীক্ষা", later)],
        page: 0,
        size: 100,
        totalElements: 2,
        totalPages: 1,
      },
      "GET /api/v1/live-exam-modules/m1/exams": {
        content: [{ id: "a", status: "PUBLISHED" }],
        page: 0,
        size: 100,
        totalElements: 1,
        totalPages: 1,
      },
      "GET /api/v1/live-exam-orders": { content: [], page: 0, size: 1, totalElements: 0, totalPages: 0 },
    });
    renderWithProviders(<AdminHome isMasterAdmin />, { user: fixtures.masterAdmin });

    expect(await screen.findByText("সিআরপিসি")).toBeInTheDocument();
    expect(screen.queryByText("দূরের পরীক্ষা")).not.toBeInTheDocument();
    expect(screen.getAllByText("অংশগ্রহণ ১২")[0]).toBeInTheDocument();
    const agenda = screen.getByRole("region", { name: "আগামী ১৪ দিনের পরীক্ষা" });
    expect(await within(agenda).findByText("প্রকাশিত")).toBeInTheDocument();
  });
});
