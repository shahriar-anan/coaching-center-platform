import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ExamPage } from "@/components/student/catalog/exam-page";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const offer = {
  price: 300,
  discountPercent: null,
  discountEndsAt: null,
  effectivePrice: 300,
  free: false,
  moduleOnly: false,
};

/** An ended MCQ paper with no price of its own, so the buy card has only the preview action to show. */
function exam(overrides: Record<string, unknown>) {
  return {
    id: "e1",
    moduleId: "m1",
    examType: "MCQ",
    titleEn: "Preview MCQ",
    titleBn: "Preview MCQ",
    descriptionEn: null,
    descriptionBn: null,
    rulesEn: null,
    rulesBn: null,
    paperLanguage: "EN",
    mode: "LIVE",
    phase: "ENDED",
    liveAt: "2020-01-01T00:00:00Z",
    endAt: "2020-01-01T00:20:00Z",
    durationMinutes: 20,
    price: 300,
    pricing: offer,
    packages: [],
    access: null,
    previewEnabled: false,
    ...overrides,
  };
}

function mount(examBody: Record<string, unknown>, user: typeof fixtures.student | null) {
  mockApi({
    "GET /api/v1/public/live-exams/e1": examBody,
    "GET /api/v1/public/exam-site/status": { checkoutEnabled: true },
  });
  return renderWithProviders(<ExamPage examId="e1" />, { user, locale: "en" });
}

/** The window for each phase the API sends: a window that has not opened, one that is open, and one that has ended. */
const windows: Record<string, Record<string, unknown>> = {
  UPCOMING: { liveAt: "2099-01-01T00:00:00Z", endAt: "2099-01-01T00:20:00Z" },
  LIVE: { liveAt: "2020-01-01T00:00:00Z", endAt: "2099-01-01T00:20:00Z" },
};

describe("the paper preview action on the exam page", () => {
  it("a student on a preview-on exam whose window has ended sees Preview paper, linking to the preview page", async () => {
    mount(exam({ previewEnabled: true }), fixtures.student);
    const link = await screen.findByRole("link", { name: "Preview paper" });
    expect(link).toHaveAttribute("href", "/exam/e1/preview");
  });

  it("a student on a preview-on practice exam sees Preview paper", async () => {
    mount(exam({ previewEnabled: true, mode: "PRACTICE", phase: "PRACTICE", liveAt: null, endAt: null }), fixtures.student);
    const link = await screen.findByRole("link", { name: "Preview paper" });
    expect(link).toHaveAttribute("href", "/exam/e1/preview");
  });

  it("a visitor on a preview-on exam is sent to log in, and back to the preview page", async () => {
    mount(exam({ previewEnabled: true }), null);
    const link = await screen.findByRole("link", { name: "Log in to preview the paper" });
    expect(link).toHaveAttribute("href", "/login?next=%2Fexam%2Fe1%2Fpreview");
    expect(screen.queryByRole("link", { name: "Preview paper" })).not.toBeInTheDocument();
  });

  it.each([
    { phase: "UPCOMING", who: "a student", signedIn: true },
    { phase: "UPCOMING", who: "a visitor", signedIn: false },
    { phase: "LIVE", who: "a student", signedIn: true },
    { phase: "LIVE", who: "a visitor", signedIn: false },
  ])("a $phase exam with preview on shows no preview action to $who", async ({ phase, signedIn }) => {
    mount(exam({ previewEnabled: true, phase, ...windows[phase] }), signedIn ? fixtures.student : null);
    expect(await screen.findByRole("heading", { name: "Preview MCQ", level: 1 })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Preview paper" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Log in to preview the paper" })).not.toBeInTheDocument();
  });

  it("an exam with preview off shows no preview action, for a student or a visitor", async () => {
    mount(exam({ previewEnabled: false }), fixtures.student);
    expect(await screen.findByRole("heading", { name: "Preview MCQ", level: 1 })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Preview paper" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Log in to preview the paper" })).not.toBeInTheDocument();
  });

  it("a visitor on an exam with preview off sees no preview action", async () => {
    mount(exam({ previewEnabled: false }), null);
    expect(await screen.findByRole("heading", { name: "Preview MCQ", level: 1 })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Log in to preview the paper" })).not.toBeInTheDocument();
  });
});
