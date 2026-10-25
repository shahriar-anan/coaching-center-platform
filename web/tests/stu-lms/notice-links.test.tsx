import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import NoticesPage from "@/app/(site)/notices/page";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const exam = (id: string) => ({
  id,
  moduleId: "m1",
  examType: "MCQ",
  titleEn: `Exam ${id}`,
  titleBn: `পরীক্ষা ${id}`,
  liveAt: null,
  endAt: null,
});

const base = { createdAt: "2026-11-01T04:00:00Z", readAt: null, score: null, maximum: null, passed: null };

const notices = [
  { ...base, id: "n1", kind: "EXAM_OPEN", exam: exam("e1") },
  { ...base, id: "n2", kind: "PURCHASE_GRANTED", exam: null },
  { ...base, id: "n3", kind: "ANNOUNCEMENT", exam: null },
  { ...base, id: "n4", kind: "EXAM_PUBLISHED", exam: exam("e2") },
  { ...base, id: "n5", kind: "SOMETHING_NEW", exam: null },
];

describe("notice rows open the right page (S-004, S-017)", () => {
  it("exam kinds open the exam offer, a purchase opens my modules, an announcement and an unknown kind have no link", async () => {
    mockApi({ "GET /api/v1/me/notices": { content: notices, page: 0, totalPages: 1 } });
    renderWithProviders(<NoticesPage />, { user: fixtures.student });

    expect(await screen.findByText("পরীক্ষা e1")).toBeInTheDocument();
    const hrefs = screen.getAllByRole("link").map((link) => link.getAttribute("href"));
    expect(hrefs).toContain("/exam/e1");
    expect(hrefs).toContain("/exam/e2");
    expect(hrefs).toContain("/my-modules");
    expect(hrefs.filter((href) => href === "/my-modules")).toHaveLength(1);
    expect(screen.getAllByText("প্রশাসকের ঘোষণা").length).toBeGreaterThan(0);
    expect(screen.getAllByText("নোটিশ").length).toBeGreaterThan(0);
  });
});
