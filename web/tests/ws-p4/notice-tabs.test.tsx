import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import NoticesPage from "@/app/(site)/notices/page";
import {
  defaultNoticeTab,
  parseNoticeTab,
  paymentHref,
  tabEdgeClass,
} from "@/app/(site)/notices/_components/notice-tabs";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const base = {
  createdAt: "2026-10-29T04:00:00Z",
  readAt: null,
  exam: { id: "e1", moduleId: "m1", examType: "MCQ", titleEn: "CRPC", titleBn: "সিআরপিসি", liveAt: null, endAt: null },
  announcement: null,
  score: null,
  maximum: null,
  passed: null,
};

describe("the notice tab rules (Phase 4, A8)", () => {
  it("reads the tab from the URL, and anything else as none", () => {
    expect(parseNoticeTab("RESULTS")).toBe("RESULTS");
    expect(parseNoticeTab("EXAMS")).toBe("EXAMS");
    expect(parseNoticeTab("ANNOUNCEMENTS")).toBe("ANNOUNCEMENTS");
    expect(parseNoticeTab("results")).toBeNull();
    expect(parseNoticeTab(null)).toBeNull();
  });

  it("opens Results if it has unread, else Exams, else Announcements, else Exams", () => {
    expect(defaultNoticeTab({ RESULTS: 1, EXAMS: 4, ANNOUNCEMENTS: 2 })).toBe("RESULTS");
    expect(defaultNoticeTab({ RESULTS: 0, EXAMS: 4, ANNOUNCEMENTS: 2 })).toBe("EXAMS");
    expect(defaultNoticeTab({ RESULTS: 0, EXAMS: 0, ANNOUNCEMENTS: 2 })).toBe("ANNOUNCEMENTS");
    expect(defaultNoticeTab({ RESULTS: 0, EXAMS: 0, ANNOUNCEMENTS: 0 })).toBe("EXAMS");
    expect(defaultNoticeTab(undefined)).toBe("EXAMS");
  });

  it("gives each tab its own edge colour", () => {
    expect(tabEdgeClass("RESULTS")).toBe("border-l-ok");
    expect(tabEdgeClass("EXAMS")).toBe("border-l-accent");
    expect(tabEdgeClass("ANNOUNCEMENTS")).toBe("border-l-muted");
  });

  it("links a purchase to its exam's payments, or to every payment when no exam is named", () => {
    expect(paymentHref({ exam: { id: "e9" } as never })).toBe("/account/payments?examId=e9");
    expect(paymentHref({ exam: null })).toBe("/account/payments");
  });

});

describe("the notices page tabs (A8)", () => {
  it("shows an unread count on each tab and opens the tab named in the URL", async () => {
    mockApi({
      "GET /api/v1/me/notices/unread-count": { count: 5, byGroup: { RESULTS: 2, EXAMS: 3, ANNOUNCEMENTS: 0 } },
      "GET /api/v1/me/notices": { content: [], page: 0, totalPages: 0 },
    });
    renderWithProviders(<NoticesPage />, { user: fixtures.student, locale: "en", route: "/notices?tab=ANNOUNCEMENTS" });

    const tabs = await screen.findByRole("tablist", { name: "Notice sections" });
    await waitFor(() => expect(within(tabs).getByRole("tab", { name: /Results/ })).toHaveTextContent("2"));
    expect(within(tabs).getByRole("tab", { name: /Exams/ })).toHaveTextContent("3");
    expect(within(tabs).getByRole("tab", { name: /Announcements/ })).toHaveAttribute("aria-selected", "true");
  });

  it("asks the API for the tab named in the URL", async () => {
    const api = mockApi({
      "GET /api/v1/me/notices/unread-count": { count: 0, byGroup: { RESULTS: 0, EXAMS: 0, ANNOUNCEMENTS: 0 } },
      "GET /api/v1/me/notices": { content: [], page: 0, totalPages: 0 },
    });
    renderWithProviders(<NoticesPage />, { user: fixtures.student, locale: "en", route: "/notices?tab=RESULTS" });

    await screen.findByText("No results yet.");
    const listCall = api.calls.find((call) => call.path === "/api/v1/me/notices");
    expect(listCall?.search).toContain("group=RESULTS");
  });

  it("marks only the open tab read when the student asks", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      "GET /api/v1/me/notices/unread-count": { count: 1, byGroup: { RESULTS: 1, EXAMS: 0, ANNOUNCEMENTS: 0 } },
      "GET /api/v1/me/notices": { content: [{ ...base, id: "n1", kind: "MCQ_SCORE", score: 7, maximum: 10, passed: true }], page: 0, totalPages: 1 },
      "POST /api/v1/me/notices/read-all": { updated: 1 },
    });
    renderWithProviders(<NoticesPage />, { user: fixtures.student, locale: "en", route: "/notices?tab=RESULTS" });

    await user.click(await screen.findByRole("button", { name: "Mark this tab as read" }));
    const marked = api.calls.find((call) => call.path === "/api/v1/me/notices/read-all");
    expect(marked?.search).toBe("?group=RESULTS");
  });

  it("shows the empty text of the tab that has nothing", async () => {
    mockApi({
      "GET /api/v1/me/notices/unread-count": { count: 0, byGroup: { RESULTS: 0, EXAMS: 0, ANNOUNCEMENTS: 0 } },
      "GET /api/v1/me/notices": { content: [], page: 0, totalPages: 0 },
    });
    renderWithProviders(<NoticesPage />, { user: fixtures.student, locale: "en", route: "/notices?tab=ANNOUNCEMENTS" });

    expect(await screen.findByText("No announcements yet.")).toBeInTheDocument();
  });

  it("puts a result's score chip and its View result link on the Results tab", async () => {
    mockApi({
      "GET /api/v1/me/notices/unread-count": { count: 0, byGroup: { RESULTS: 0, EXAMS: 0, ANNOUNCEMENTS: 0 } },
      "GET /api/v1/me/notices": {
        content: [{ ...base, id: "n1", kind: "MCQ_SCORE", score: 34, maximum: 50, passed: true }],
        page: 0,
        totalPages: 1,
      },
    });
    renderWithProviders(<NoticesPage />, { user: fixtures.student, locale: "en", route: "/notices?tab=RESULTS" });

    expect(await screen.findByText("34/50")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /View result/ })).toHaveAttribute("href", "/exam/e1/result");
  });
});
