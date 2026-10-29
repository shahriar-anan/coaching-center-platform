import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
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
    // The Wish List mark is in the header, before the buy card, and it is one icon button.
    const mark = within(heading.parentElement as HTMLElement).getByRole("button", { name: "Wish List" });
    expect(mark).toHaveClass("absolute", "size-11");
    expect(heading.compareDocumentPosition(card) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
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
  it("puts the header, then the buy card, then the details, and shows the purchase line for a bought exam", async () => {
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
    expect(heading.compareDocumentPosition(card) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(card.compareDocumentPosition(details) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
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
