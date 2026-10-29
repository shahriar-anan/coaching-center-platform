import { act, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ExamPage } from "@/components/student/catalog/exam-page";
import { ModulePage } from "@/components/student/catalog/module-page";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const MODULE_ID = fixtures.module.id;
const EXAM_ID = "eeeeeeee-0000-0000-0000-000000000009";

const offer = (price: number | null, effective: number | null) => ({
  price,
  discountPercent: null,
  discountEndsAt: null,
  effectivePrice: effective,
  free: false,
  moduleOnly: false,
});

const pkg = (access: Record<string, unknown> | null) => ({
  ...fixtures.module,
  price: 2500,
  pricing: offer(2500, 2500),
  coverUrl: null,
  favourite: false,
  access,
});

const exam = (access: Record<string, unknown> | null) => ({
  id: EXAM_ID,
  moduleId: MODULE_ID,
  examType: "MCQ",
  titleEn: "CPC Full Mock",
  titleBn: "CPC Full Mock",
  descriptionEn: "A full mock paper.",
  descriptionBn: null,
  rulesEn: null,
  rulesBn: null,
  paperLanguage: "EN",
  liveAt: "2099-01-01T00:00:00Z",
  endAt: "2099-01-01T00:40:00Z",
  durationMinutes: 40,
  price: 400,
  pricing: offer(400, 400),
  packages: [],
  favourite: false,
  access,
});

const order = (overrides: Record<string, unknown>) => ({
  orderId: "order-p",
  tranId: "tran-p",
  status: "PENDING",
  amount: 2500,
  currency: "BDT",
  scope: "MODULE",
  moduleId: MODULE_ID,
  moduleTitleEn: "BCS Preliminary",
  moduleTitleBn: "BCS Preliminary",
  examId: null,
  examTitleEn: null,
  examTitleBn: null,
  paymentChannel: null,
  createdAt: "2026-10-20T06:00:00.000Z",
  listPrice: null,
  discountPercent: null,
  paidAt: null,
  receiptNo: null,
  ...overrides,
});

const emptyPage = { content: [], page: 0, size: 1, totalElements: 0, totalPages: 0 };

/** Answers the open-order query: a pending order for the package or the exam the query names, and nothing else. */
function openOrders(pending: { moduleId?: string; examId?: string; row: Record<string, unknown> }) {
  return (req: { url: URL }) => {
    const status = req.url.searchParams.get("status");
    const moduleId = req.url.searchParams.get("moduleId");
    const examId = req.url.searchParams.get("examId");
    const match = status === "PENDING" && ((pending.moduleId && moduleId === pending.moduleId) || (pending.examId && examId === pending.examId));
    return match ? { content: [pending.row], page: 0, size: 1, totalElements: 1, totalPages: 1 } : emptyPage;
  };
}

describe("the package page on the shared layout (Phase 4, S4.3 and S4.4)", () => {
  it("shows the bought date and the payment link in the buy card, and the Wish List mark in the header", async () => {
    mockApi({
      [`GET /api/v1/public/live-exam-modules/${MODULE_ID}`]: pkg({ state: "MODULE", buyable: false, blockedReason: null }),
      [`GET /api/v1/public/live-exam-modules/${MODULE_ID}/exams`]: { ...emptyPage, content: [exam(null)] },
      [`GET /api/v1/live-exam-modules/${MODULE_ID}/exams`]: emptyPage,
      "GET /api/v1/public/live-exam-categories": fixtures.categoryTree,
      "GET /api/v1/public/exam-site/status": { checkoutEnabled: true },
      "GET /api/v1/me/live-exam-access": {
        modules: [{ moduleId: MODULE_ID, via: "MODULE", orderId: "order-1", grantedAt: "2026-10-01T06:00:00.000Z", endsAt: null }],
        exams: [],
      },
      "GET /api/v1/me/live-exam-orders": emptyPage,
    });
    renderWithProviders(<ModulePage door="mcq" moduleId={MODULE_ID} />, { user: fixtures.student, locale: "en" });

    const heading = await screen.findByRole("heading", { level: 1, name: "BCS Preliminary" });
    const card = screen.getByRole("region", { name: "Buy whole package" });
    const link = await within(card).findByRole("link", { name: "View payment" });
    expect(link).toHaveAttribute("href", "/account/payments?order=order-1");
    expect(within(card).getByText(/Purchased/)).toBeInTheDocument();
    // The Wish List mark is in the header, and it is one icon button. The buy card comes after the tabs in the document.
    const mark = within(heading.parentElement as HTMLElement).getByRole("button", { name: "Wish List" });
    expect(mark).toHaveClass("absolute", "size-11");
    expect(heading.compareDocumentPosition(card) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(screen.getByRole("tablist").compareDocumentPosition(card) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    // The tabs wrap rather than scroll, so the About tab is never cut off at 320 px (S4.4).
    expect(screen.getByRole("tablist")).toHaveClass("flex-wrap");
  });

  it("shows Payment waiting with Continue payment and Cancel for an open order, asking only for this package's open orders", async () => {
    const { calls } = mockApi({
      [`GET /api/v1/public/live-exam-modules/${MODULE_ID}`]: pkg({ state: "NONE", buyable: true, blockedReason: null }),
      [`GET /api/v1/public/live-exam-modules/${MODULE_ID}/exams`]: { ...emptyPage, content: [exam(null)] },
      [`GET /api/v1/live-exam-modules/${MODULE_ID}/exams`]: emptyPage,
      "GET /api/v1/public/live-exam-categories": fixtures.categoryTree,
      "GET /api/v1/public/exam-site/status": { checkoutEnabled: true },
      "GET /api/v1/me/live-exam-access": { modules: [], exams: [] },
      "GET /api/v1/me/live-exam-orders": openOrders({ moduleId: MODULE_ID, row: order({}) }),
    });
    renderWithProviders(<ModulePage door="mcq" moduleId={MODULE_ID} />, { user: fixtures.student, locale: "en" });

    const card = await screen.findByRole("region", { name: "Buy whole package" });
    expect(await within(card).findByText("Payment waiting")).toBeInTheDocument();
    expect(within(card).getByRole("button", { name: "Continue payment" })).toBeInTheDocument();
    expect(within(card).getByRole("button", { name: "Cancel" })).toBeInTheDocument();
    expect(calls.some((call) => call.path === "/api/v1/me/live-exam-orders" && call.search.includes("status=PENDING") && call.search.includes(`moduleId=${MODULE_ID}`))).toBe(true);
  });

  it("gives a visitor a Wish List link to log in, and no payment line", async () => {
    mockApi({
      [`GET /api/v1/public/live-exam-modules/${MODULE_ID}`]: pkg(null),
      [`GET /api/v1/public/live-exam-modules/${MODULE_ID}/exams`]: { ...emptyPage, content: [exam(null)] },
      "GET /api/v1/public/live-exam-categories": fixtures.categoryTree,
      "GET /api/v1/public/exam-site/status": { checkoutEnabled: true },
    });
    renderWithProviders(<ModulePage door="mcq" moduleId={MODULE_ID} />, { locale: "en" });

    const heading = await screen.findByRole("heading", { level: 1, name: "BCS Preliminary" });
    const link = within(heading.parentElement as HTMLElement).getByRole("link", { name: "Wish List" });
    expect(link).toHaveAttribute("href", expect.stringContaining("/login"));
    expect(screen.queryByText("Payment waiting")).not.toBeInTheDocument();
  });
});

describe("the exam page on the shared layout (Phase 4, S4.3 and S4.4)", () => {
  it("puts the header, then the details, then the buy card, and shows the purchase line for a bought exam", async () => {
    mockApi({
      [`GET /api/v1/public/live-exams/${EXAM_ID}`]: exam({ state: "EXAM", buyable: false, blockedReason: null }),
      [`GET /api/v1/public/live-exam-modules/${MODULE_ID}`]: pkg(null),
      "GET /api/v1/me/live-exam-access": {
        modules: [],
        exams: [{ examId: EXAM_ID, moduleId: MODULE_ID, via: "EXAM", orderId: "order-e", grantedAt: "2026-10-05T06:00:00.000Z", endsAt: null }],
      },
      "GET /api/v1/me/live-exam-orders": emptyPage,
      "GET /api/v1/public/exam-site/status": { checkoutEnabled: true },
    });
    renderWithProviders(<ExamPage examId={EXAM_ID} />, { user: fixtures.student, locale: "en" });

    const heading = await screen.findByRole("heading", { level: 1, name: "CPC Full Mock" });
    const card = screen.getByRole("region", { name: "CPC Full Mock" });
    const details = screen.getByText("Duration").closest("dl") as HTMLElement;
    expect(heading.compareDocumentPosition(details) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(details.compareDocumentPosition(card) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(await within(card).findByRole("link", { name: "View payment" })).toHaveAttribute("href", "/account/payments?order=order-e");
  });

  it("shows Payment waiting for an open order on the exam", async () => {
    mockApi({
      [`GET /api/v1/public/live-exams/${EXAM_ID}`]: exam({ state: "NONE", buyable: true, blockedReason: null }),
      [`GET /api/v1/public/live-exam-modules/${MODULE_ID}`]: pkg(null),
      "GET /api/v1/me/live-exam-access": { modules: [], exams: [] },
      "GET /api/v1/me/live-exam-orders": openOrders({
        examId: EXAM_ID,
        row: order({ orderId: "order-x", scope: "EXAM", moduleId: null, examId: EXAM_ID, examTitleEn: "CPC Full Mock" }),
      }),
      "GET /api/v1/public/exam-site/status": { checkoutEnabled: true },
    });
    renderWithProviders(<ExamPage examId={EXAM_ID} />, { user: fixtures.student, locale: "en" });

    const card = await screen.findByRole("region", { name: "CPC Full Mock" });
    expect(await within(card).findByText("Payment waiting")).toBeInTheDocument();
    expect(within(card).getByRole("button", { name: "Continue payment" })).toBeInTheDocument();
  });
});

describe("the exam cover fallback (Phase 4, E1 and S4.4)", () => {
  const packageRef = { id: MODULE_ID, titleEn: "BCS Preliminary", titleBn: "BCS Preliminary" };
  const coverPackage = { ...pkg(null), coverUrl: "/api/v1/public/live-exam-modules/pkg/cover" };

  it("shows the exam's own cover first, then the package cover, then the typed placeholder", async () => {
    mockApi({
      [`GET /api/v1/public/live-exams/${EXAM_ID}`]: { ...exam(null), coverUrl: "/api/v1/public/live-exams/own/cover", packages: [packageRef] },
      [`GET /api/v1/public/live-exam-modules/${MODULE_ID}`]: coverPackage,
      "GET /api/v1/public/exam-site/status": { checkoutEnabled: true },
    });
    renderWithProviders(<ExamPage examId={EXAM_ID} />, { locale: "en" });
    const own = await screen.findByRole("img", { name: "Cover of CPC Full Mock" });
    expect(own.getAttribute("src")).toContain("/api/v1/public/live-exams/own/cover");
  });

  it("falls back to the first package's cover when the exam has none", async () => {
    mockApi({
      [`GET /api/v1/public/live-exams/${EXAM_ID}`]: { ...exam(null), coverUrl: null, packages: [packageRef] },
      [`GET /api/v1/public/live-exam-modules/${MODULE_ID}`]: coverPackage,
      "GET /api/v1/public/exam-site/status": { checkoutEnabled: true },
    });
    renderWithProviders(<ExamPage examId={EXAM_ID} />, { locale: "en" });
    const fallback = await screen.findByRole("img", { name: "Cover of BCS Preliminary" });
    expect(fallback.getAttribute("src")).toContain("/api/v1/public/live-exam-modules/pkg/cover");
  });

  it("shows the typed placeholder, with no image, when neither has a cover", async () => {
    mockApi({
      [`GET /api/v1/public/live-exams/${EXAM_ID}`]: { ...exam(null), coverUrl: null, packages: [packageRef] },
      [`GET /api/v1/public/live-exam-modules/${MODULE_ID}`]: { ...pkg(null), coverUrl: null },
      "GET /api/v1/public/exam-site/status": { checkoutEnabled: true },
    });
    renderWithProviders(<ExamPage examId={EXAM_ID} />, { locale: "en" });
    await screen.findByRole("heading", { level: 1, name: "CPC Full Mock" });
    expect(screen.queryByRole("img", { name: /Cover of/ })).not.toBeInTheDocument();
  });
});

type FakeObserver = { callback: IntersectionObserverCallback; targets: Element[] };
let observers: FakeObserver[] = [];

/** A stand-in for the browser's observer: the test reports the card on screen or off it. */
class FakeIntersectionObserver {
  private readonly entry: FakeObserver;
  constructor(callback: IntersectionObserverCallback) {
    this.entry = { callback, targets: [] };
    observers.push(this.entry);
  }
  observe(target: Element) {
    this.entry.targets.push(target);
  }
  unobserve() {}
  disconnect() {}
  takeRecords() {
    return [];
  }
}

function report(target: Element, isIntersecting: boolean) {
  const observer = observers.find((o) => o.targets.includes(target));
  act(() => {
    observer?.callback([{ isIntersecting, target } as IntersectionObserverEntry], {} as IntersectionObserver);
  });
}

describe("the phone bar follows the buy card (Phase 4, S4.3)", () => {
  beforeEach(() => {
    observers = [];
    vi.stubGlobal("IntersectionObserver", FakeIntersectionObserver);
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("slides the bar in only while the buy card is off screen, and away again when the card comes back", async () => {
    mockApi({
      [`GET /api/v1/public/live-exam-modules/${MODULE_ID}`]: pkg(null),
      [`GET /api/v1/public/live-exam-modules/${MODULE_ID}/exams`]: { ...emptyPage, content: [exam(null)] },
      "GET /api/v1/public/live-exam-categories": fixtures.categoryTree,
      "GET /api/v1/public/exam-site/status": { checkoutEnabled: true },
    });
    renderWithProviders(<ModulePage door="mcq" moduleId={MODULE_ID} />, { locale: "en" });

    const card = await screen.findByRole("region", { name: "Buy whole package" });
    await waitFor(() => expect(observers.some((o) => o.targets.includes(card))).toBe(true));
    // The bar stays hidden until the observer says the card is off screen.
    // The bar is the div with an explicit region role; the card is a section, so the two never match the same query.
    const bar = () => document.querySelector<HTMLElement>('[role="region"][aria-label="Buy whole package"]') as HTMLElement;
    expect(bar()).toHaveAttribute("aria-hidden", "true");
    expect(bar()).toHaveClass("invisible", "translate-y-full", "motion-reduce:transition-none");

    report(card, false);
    expect(bar()).toHaveAttribute("aria-hidden", "false");
    expect(bar()).toHaveClass("visible", "translate-y-0");

    report(card, true);
    expect(bar()).toHaveAttribute("aria-hidden", "true");
    expect(bar()).toHaveClass("invisible", "translate-y-full");
  });
});
