import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import NoticesPage from "@/app/(site)/notices/page";
import { groupFilter, parseNoticeTab, rowGroup } from "@/app/(site)/notices/_components/notice-tabs";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const base = { createdAt: "2026-11-02T04:00:00Z", readAt: null, score: null, maximum: null, passed: null, exam: null };

describe("the All tab on the notices page", () => {
  it("is parsed as a tab, and asks for every group", () => {
    expect(parseNoticeTab("ALL")).toBe("ALL");
    expect(groupFilter("ALL")).toBeNull();
    expect(groupFilter("RESULTS")).toBe("RESULTS");
  });

  it("gives each row on All its own group, and falls back to Exams without one", () => {
    expect(rowGroup({ group: "ANNOUNCEMENTS" }, "ALL")).toBe("ANNOUNCEMENTS");
    expect(rowGroup({}, "ALL")).toBe("EXAMS");
    expect(rowGroup({ group: "RESULTS" }, "EXAMS")).toBe("EXAMS");
  });

  it("shows an All tab and lists notices from every group", async () => {
    mockApi({
      "GET /api/v1/me/notices/unread-count": { count: 2, byGroup: { RESULTS: 1, EXAMS: 1, ANNOUNCEMENTS: 0 } },
      "GET /api/v1/me/notices": {
        content: [
          { ...base, id: "n1", kind: "WRITTEN_RESULT", group: "RESULTS", exam: { id: "e1", moduleId: "m1", examType: "WRITTEN", titleEn: "Paper", titleBn: "পত্র", liveAt: null, endAt: null } },
          { ...base, id: "n2", kind: "EXAM_TIME_CHANGED", group: "EXAMS", exam: { id: "e2", moduleId: "m1", examType: "MCQ", titleEn: "Quiz", titleBn: "কুইজ", liveAt: null, endAt: null } },
        ],
        page: 0,
        totalPages: 1,
      },
    });
    renderWithProviders(<NoticesPage />, { user: fixtures.student, locale: "en", route: "/notices?tab=ALL" });

    const all = await screen.findByRole("tab", { name: /All/ });
    expect(all).toBeInTheDocument();
    expect(await screen.findByText("Exam time has changed")).toBeInTheDocument();
    expect(within(screen.getByRole("list")).getAllByRole("listitem")).toHaveLength(2);
  });
});
