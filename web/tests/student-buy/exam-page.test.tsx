import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ExamPage } from "@/components/student/catalog/exam-page";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const offer = (price: number | null, effective: number | null, extra: Record<string, unknown> = {}) => ({
  price,
  discountPercent: null,
  discountEndsAt: null,
  effectivePrice: effective,
  free: false,
  moduleOnly: false,
  ...extra,
});

const exam = (overrides: Record<string, unknown>) => ({
  id: "e1",
  moduleId: "m1",
  examType: "MCQ",
  titleEn: "CPC MCQ",
  titleBn: "CPC MCQ",
  descriptionEn: null,
  descriptionBn: null,
  rulesEn: null,
  rulesBn: null,
  paperLanguage: "EN",
  liveAt: "2099-01-01T00:00:00Z",
  endAt: "2099-01-01T00:20:00Z",
  durationMinutes: 20,
  price: null,
  pricing: offer(null, null),
  packages: [],
  access: null,
  ...overrides,
});

const pkg = {
  id: "m1",
  titleEn: "Package one",
  titleBn: "Package one",
  descriptionEn: null,
  descriptionBn: null,
  categoryId: "c1",
  position: 1,
  price: 1000,
  mcqCount: 3,
  writtenCount: 0,
  coverUrl: null,
  pricing: offer(1000, 1000, { moduleOnly: false }),
  access: null,
};

function mount(examBody: Record<string, unknown>, user: typeof fixtures.student | null) {
  mockApi({
    "GET /api/v1/public/live-exams/e1": examBody,
    "GET /api/v1/public/live-exam-modules/m1": pkg,
    "GET /api/v1/public/exam-site/status": { checkoutEnabled: true },
  });
  return renderWithProviders(<ExamPage examId="e1" />, { user, locale: "en" });
}

describe("the exam detail purchase options (S2.3)", () => {
  it("a visitor on a paid exam sees Buy this exam at the charged price, after any discount", async () => {
    mount(exam({ price: 400, pricing: offer(400, 300, { discountPercent: 25, discountEndsAt: "2099-01-01T00:00:00Z" }) }), null);
    const button = await screen.findByRole("link", { name: /Buy this exam/ });
    expect(button).toHaveTextContent("300");
    expect(button).toHaveAttribute("href", expect.stringContaining("/login"));
  });

  it("a visitor on a free exam is offered Free, log in to start", async () => {
    mount(exam({ pricing: offer(null, null, { free: true }) }), null);
    expect(await screen.findByRole("link", { name: "Free, log in to start" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Buy this exam/ })).not.toBeInTheDocument();
  });

  it("an exam with no price of its own, sold only in a package, has no buy button and shows its package cards", async () => {
    mount(exam({ pricing: offer(null, null, { moduleOnly: true }), packages: [{ id: "m1", titleEn: "Package one", titleBn: "Package one" }] }), fixtures.student);
    expect(await screen.findByText("Package one")).toBeInTheDocument();
    expect(screen.getByText("Packages with this exam")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Buy this exam/ })).not.toBeInTheDocument();
    const link = screen.getByRole("link", { name: /Package one/ });
    expect(link).toHaveAttribute("href", "/mcq/m/m1");
    expect(within(link.closest(".group") as HTMLElement).getByText("3 exams", { exact: false })).toBeInTheDocument();
  });

  it("an exam in no package shows no packages section", async () => {
    mount(exam({ pricing: offer(400, 400), price: 400, packages: [] }), fixtures.student);
    expect(await screen.findByRole("button", { name: /Buy this exam/ })).toBeInTheDocument();
    expect(screen.queryByText("Packages with this exam")).not.toBeInTheDocument();
  });
});

describe("the exam detail access and price states (S2.5)", () => {
  it("a student who holds the exam sees Access until the end date", async () => {
    mount(
      exam({
        price: 400,
        pricing: offer(400, 400),
        access: { state: "EXAM", buyable: false, blockedReason: "ALREADY_HAS_ACCESS", expiresAt: "2099-12-28T12:00:00Z" },
      }),
      fixtures.student,
    );
    expect(await screen.findByText(/Access until 28 Dec/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Buy this exam/ })).not.toBeInTheDocument();
  });

  it("an expired grant says the access ended on that date, and the student can buy again", async () => {
    mount(
      exam({
        price: 400,
        pricing: offer(400, 400),
        access: { state: "NONE", buyable: true, blockedReason: null, expiresAt: "2020-11-28T12:00:00Z" },
      }),
      fixtures.student,
    );
    expect(await screen.findByText(/Your access ended on 28 November 2020/)).toBeInTheDocument();
    expect(await screen.findByRole("button", { name: /Buy this exam/ })).toBeEnabled();
  });

  it("a free exam a student holds says Free, start on its start button when it is open", async () => {
    mount(
      exam({
        liveAt: "2020-01-01T00:00:00Z",
        endAt: "2099-01-01T00:00:00Z",
        pricing: offer(null, null, { free: true }),
        access: { state: "FREE", buyable: false, blockedReason: "ALREADY_HAS_ACCESS", expiresAt: null },
      }),
      fixtures.student,
    );
    expect(await screen.findByText("Free")).toBeInTheDocument();
    expect(screen.queryByText(/Access until/)).not.toBeInTheDocument();
  });

  it("a discounted price shows the list price struck through and the charged price", async () => {
    const { container } = mount(exam({ price: 400, pricing: offer(400, 300, { discountPercent: 25, discountEndsAt: "2099-11-30T17:59:59Z" }) }), null);
    expect(await screen.findByRole("link", { name: /Buy this exam/ })).toHaveTextContent("300");
    expect(container.querySelector("s")?.textContent).toContain("400");
    expect(screen.getAllByText(/25% off/).length).toBeGreaterThan(0);
  });
});

describe("a written paper bought after its window ended, never sat (D20)", () => {
  function mountOwned(examBody: Record<string, unknown>, mine: Record<string, unknown>) {
    mockApi({
      "GET /api/v1/public/live-exams/e1": examBody,
      "GET /api/v1/public/live-exam-modules/m1": pkg,
      "GET /api/v1/public/exam-site/status": { checkoutEnabled: true },
      "GET /api/v1/live-exams/e1": { ...examBody, ...mine },
    });
    return renderWithProviders(<ExamPage examId="e1" />, { user: fixtures.student, locale: "en" });
  }

  const ended = {
    examType: "WRITTEN",
    titleEn: "CPC written",
    liveAt: "2020-01-01T00:00:00Z",
    endAt: "2020-01-01T00:20:00Z",
    price: 500,
    pricing: offer(500, 500),
    access: { state: "EXAM", buyable: false, blockedReason: "ALREADY_HAS_ACCESS", expiresAt: null },
  };

  it("shows the one-attempt note and a Start the paper button that starts a live attempt", async () => {
    mountOwned(ended, { myStatus: "MISSED", myStatusDetail: "ARCHIVED_OPEN", retakeAvailable: false });
    expect(await screen.findByText("The live window has ended. You can take this paper once now.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Start the paper" })).toBeEnabled();
    expect(screen.queryByRole("button", { name: "Practice attempt" })).not.toBeInTheDocument();
  });

  it("keeps the missed message when the paper cannot be sat any more", async () => {
    mountOwned(ended, { myStatus: "MISSED", myStatusDetail: "EXPIRED", retakeAvailable: false });
    expect(await screen.findByText("The time for this exam has ended.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Start the paper" })).not.toBeInTheDocument();
  });
});
