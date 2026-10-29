import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import NoticesPage from "@/app/(site)/notices/page";
import bn from "@/i18n/messages/bn/notices";
import { NOTICE_KINDS } from "@/app/(site)/notices/_components/notice-kind";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const base = { createdAt: "2026-11-01T04:00:00Z", readAt: null, score: null, maximum: null, passed: null };

const exam = { id: "e1", moduleId: "m1", examType: "MCQ", titleEn: "Exam one", titleBn: "পরীক্ষা এক", liveAt: null, endAt: null };

/** One row per kind: the exam (or none) the API sends with it, and the link the row should have. */
const rows: Record<(typeof NOTICE_KINDS)[number], { notice: Record<string, unknown>; href: string | null }> = {
  EXAM_REMINDER: { notice: { exam }, href: "/exam/e1" },
  EXAM_STARTING_SOON: { notice: { exam }, href: "/exam/e1" },
  EXAM_OPEN: { notice: { exam }, href: "/exam/e1" },
  PURCHASE_GRANTED: { notice: { exam }, href: "/exam/e1" },
  EXAM_PUBLISHED: { notice: { exam }, href: "/exam/e1" },
  RETAKE_AVAILABLE: { notice: { exam }, href: "/exam/e1" },
  ANNOUNCEMENT: {
    notice: {
      exam: null,
      announcement: { id: "a1", titleEn: "Closed", titleBn: "বন্ধ", bodyEn: null, bodyBn: null, audience: "ALL", moduleId: null },
    },
    href: null,
  },
  MCQ_SCORE: { notice: { exam, score: 7, maximum: 10, passed: true }, href: "/exam/e1/result" },
  WRITTEN_RESULT: { notice: { exam, score: 30, maximum: 50, passed: false }, href: "/exam/e1/result" },
};

function renderOne(kind: string, extra: Record<string, unknown>) {
  mockApi({
    "GET /api/v1/me/notices": {
      content: [{ ...base, id: "n1", kind, ...extra }],
      page: 0,
      totalPages: 1,
    },
  });
  return renderWithProviders(<NoticesPage />, { user: fixtures.student, locale: "bn" });
}

describe("each notice kind: its label and where its row opens (S-002, S-004, S-017)", () => {
  it("has a row case for every kind the API sends", () => {
    expect(Object.keys(rows).sort()).toEqual([...NOTICE_KINDS].sort());
  });

  it.each(NOTICE_KINDS)("%s shows its Bangla label and links where it should", async (kind) => {
    const { notice, href } = rows[kind];
    renderOne(kind, notice);

    // A score row puts "· 7/10" after the label in the same line, so match the label as a part.
    expect(await screen.findByText(bn.kind[kind], { exact: false })).toBeInTheDocument();
    const links = screen.queryAllByRole("link");
    if (href === null) {
      expect(links).toHaveLength(0);
    } else if (kind === "PURCHASE_GRANTED") {
      // A purchase row also has "View payment" (Phase 4, A8), after the row's own link.
      expect(links).toHaveLength(2);
      expect(links[0]).toHaveAttribute("href", href);
      expect(links[1]).toHaveAttribute("href", `/account/payments?examId=${exam.id}`);
    } else {
      expect(links).toHaveLength(1);
      expect(links[0]).toHaveAttribute("href", href);
    }
  });

  it("PURCHASE_GRANTED with no exam named opens my modules", async () => {
    renderOne("PURCHASE_GRANTED", { exam: null });

    // With no exam the label is the row title, and also the kind line under it.
    expect((await screen.findAllByText(bn.kind.PURCHASE_GRANTED)).length).toBeGreaterThan(0);
    const links = screen.getAllByRole("link");
    expect(links[0]).toHaveAttribute("href", "/my-modules");
    expect(links[1]).toHaveAttribute("href", "/account/payments");
  });

  it("an unknown kind shows the generic label and has no link", async () => {
    renderOne("SOMETHING_NEW", { exam: null });

    expect(await screen.findByText(bn.kind.other)).toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
});
