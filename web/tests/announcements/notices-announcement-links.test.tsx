import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import NoticesPage from "@/app/(site)/notices/page";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const exam = {
  id: "e1",
  moduleId: "m1",
  examType: "MCQ",
  titleEn: "Exam one",
  titleBn: "পরীক্ষা এক",
  liveAt: null,
  endAt: null,
};

const base = { createdAt: "2026-11-01T04:00:00Z", readAt: "2026-11-01T05:00:00Z", score: null, maximum: null, passed: null };

const announcement = (overrides: Record<string, unknown>) => ({
  id: "ann",
  audience: "ALL",
  moduleId: null,
  examId: null,
  titleEn: null,
  titleBn: null,
  bodyEn: null,
  bodyBn: null,
  ...overrides,
});

/** An exam announcement carries the exam, so a deleted exam hides it; its row is still an announcement. */
const examNotice = {
  ...base,
  id: "n-exam",
  kind: "ANNOUNCEMENT",
  exam,
  announcement: announcement({ id: "a-exam", audience: "EXAM", examId: "e1", titleEn: "Exam postponed", bodyEn: "Moved to Friday" }),
};

const packageNotice = {
  ...base,
  id: "n-pkg",
  kind: "ANNOUNCEMENT",
  exam: null,
  announcement: announcement({ id: "a-pkg", audience: "MODULE", moduleId: "m1", titleEn: "Mock on Sunday" }),
};

const writtenPackageNotice = {
  ...base,
  id: "n-written",
  kind: "ANNOUNCEMENT",
  exam: null,
  announcement: announcement({ id: "a-written", audience: "MODULE", moduleId: "m2", titleEn: "Written batch" }),
};

describe("package and exam announcements on the Notices page (announcements plan, section 9)", () => {
  it("shows an exam announcement as an announcement, with the exam chip and an Open the exam link", async () => {
    mockApi({ "GET /api/v1/me/notices": { content: [examNotice], page: 0, totalPages: 1 } });
    renderWithProviders(<NoticesPage />, { user: fixtures.student, locale: "en" });

    expect(await screen.findByText("Exam postponed")).toBeInTheDocument();
    // The kind is no longer a line of its own: it is read out before the title, and the tab already names it.
    const heading = screen.getByRole("heading", { name: /Exam postponed/ });
    expect(heading).toHaveTextContent("Announcement from the centre");
    expect(screen.getByText("Exam one")).toBeInTheDocument();
    const links = screen.getAllByRole("link");
    expect(links).toHaveLength(1);
    expect(links[0]).toHaveAttribute("href", "/exam/e1");
    expect(links[0]).toHaveTextContent("Open the exam");
    expect(screen.queryByText("View result")).not.toBeInTheDocument();
  });

  it("shows a package announcement with the package chip and an Open the package link to its page", async () => {
    mockApi({
      "GET /api/v1/me/notices": { content: [packageNotice], page: 0, totalPages: 1 },
      "GET /api/v1/public/live-exam-modules/m1": { ...fixtures.module, id: "m1", titleEn: "BCS Prelim", mcqCount: 4, writtenCount: 0 },
    });
    renderWithProviders(<NoticesPage />, { user: fixtures.student, locale: "en" });

    expect(await screen.findByText("Mock on Sunday")).toBeInTheDocument();
    expect(await screen.findByText("BCS Prelim")).toBeInTheDocument();
    const link = screen.getByRole("link", { name: "BCS Prelim. Open the package" });
    expect(link).toHaveAttribute("href", "/mcq/m/m1");
    expect(link).toHaveTextContent("Open the package");
  });

  it("opens a written-only package on the written door", async () => {
    mockApi({
      "GET /api/v1/me/notices": { content: [writtenPackageNotice], page: 0, totalPages: 1 },
      "GET /api/v1/public/live-exam-modules/m2": { ...fixtures.module, id: "m2", titleEn: "Written course", mcqCount: 0, writtenCount: 3 },
    });
    renderWithProviders(<NoticesPage />, { user: fixtures.student, locale: "en" });

    expect(await screen.findByRole("link", { name: "Written course. Open the package" })).toHaveAttribute("href", "/written/m/m2");
  });

  it("keeps an announcement to everyone without a link", async () => {
    mockApi({
      "GET /api/v1/me/notices": {
        content: [{ ...base, id: "n-all", kind: "ANNOUNCEMENT", exam: null, announcement: announcement({ id: "a-all", titleEn: "Centre closed" }) }],
        page: 0,
        totalPages: 1,
      },
    });
    renderWithProviders(<NoticesPage />, { user: fixtures.student, locale: "en" });

    expect(await screen.findByText("Centre closed")).toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
});
