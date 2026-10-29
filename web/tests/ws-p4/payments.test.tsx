import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import PaymentsPage from "@/app/(site)/account/payments/page";
import {
  actionsFor,
  hasDiscount,
  hasFilters,
  itemHref,
  NO_FILTERS,
  parseScopeFilter,
  parseStatusFilter,
  statusKey,
  toOrderFilter,
} from "@/app/(site)/account/payments/_components/payment-model";
import { orderFilterQuery } from "@/lib/api/exam/account";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const order = (overrides: Record<string, unknown> = {}) => ({
  orderId: "o1",
  tranId: "TRAN-1",
  status: "PAID",
  amount: 1500,
  currency: "BDT",
  scope: "MODULE",
  moduleId: "m1",
  moduleTitleEn: "BJS package",
  moduleTitleBn: "বিজেএস প্যাকেজ",
  examId: null,
  examTitleEn: null,
  examTitleBn: null,
  paymentChannel: "bKash",
  createdAt: "2026-10-28T04:00:00Z",
  listPrice: 2000,
  discountPercent: 25,
  paidAt: "2026-10-28T04:05:00Z",
  receiptNo: "RCPT-2026-000123",
  ...overrides,
});

describe("payment filters and actions (Phase 4, S4.6)", () => {
  it("reads the status and type from the URL and drops anything it does not know", () => {
    expect(parseStatusFilter("PENDING")).toBe("PENDING");
    expect(parseStatusFilter("PAYING")).toBe("");
    expect(parseScopeFilter("BUNDLE")).toBe("BUNDLE");
    expect(parseScopeFilter("ANY")).toBe("");
  });

  it("maps the chosen filters to the API query, leaving unset ones out", () => {
    const filters = { ...NO_FILTERS, status: "PAID" as const, from: "2026-10-01", examId: "e9" };
    expect(orderFilterQuery(toOrderFilter(filters))).toBe("&status=PAID&from=2026-10-01&examId=e9");
    expect(orderFilterQuery(toOrderFilter(NO_FILTERS))).toBe("");
    expect(hasFilters(NO_FILTERS)).toBe(false);
    expect(hasFilters(filters)).toBe(true);
  });

  it("gives each status its own actions", () => {
    expect(actionsFor("PAID")).toEqual(["downloadPdf", "printReceipt", "open"]);
    expect(actionsFor("PENDING")).toEqual(["continue", "cancel"]);
    expect(actionsFor("FAILED")).toEqual(["buyAgain"]);
    expect(actionsFor("CANCELLED")).toEqual(["buyAgain"]);
  });

  it("links an exam order to the exam and a package or bundle order to the package", () => {
    expect(itemHref({ scope: "EXAM", moduleId: null, examId: "e1" })).toBe("/exam/e1");
    expect(itemHref({ scope: "MODULE", moduleId: "m1", examId: null })).toBe("/mcq/m/m1");
    expect(itemHref({ scope: "BUNDLE", moduleId: "m1", examId: null })).toBe("/mcq/m/m1");
  });

  it("shows a discount only when the list price is above the amount paid", () => {
    expect(hasDiscount({ listPrice: 2000, discountPercent: 25, amount: 1500 })).toBe(true);
    expect(hasDiscount({ listPrice: 1500, discountPercent: null, amount: 1500 })).toBe(false);
  });

  it("names the status key for each word", () => {
    expect(statusKey("PENDING")).toBe("account.payments.statusPending");
    expect(statusKey("CANCELLED")).toBe("account.payments.statusCancelled");
  });
});

describe("payment history page (S4.6)", () => {
  it("opens the order named in ?order= with its receipt actions", async () => {
    mockApi({
      "GET /api/v1/me/live-exam-orders": { content: [order()], page: 0, totalPages: 1 },
      "GET /api/v1/me/live-exam-orders/summary": { totalPaid: 1500, currency: "BDT", paidCount: 1, pendingCount: 0 },
      "GET /api/v1/me/live-exam-access": { exams: [], modules: [] },
    });
    renderWithProviders(<PaymentsPage />, { user: fixtures.student, locale: "en", route: "/account/payments?order=o1" });

    await screen.findByRole("heading", { name: "Order details" });
    const section = document.body;
    expect(within(section).getByRole("button", { name: "Download PDF" })).toBeInTheDocument();
    expect(within(section).getByRole("link", { name: "Print receipt" })).toHaveAttribute(
      "href",
      "/account/payments/receipt?order=o1",
    );
    expect(within(section).queryByRole("button", { name: "Cancel order" })).not.toBeInTheDocument();
    expect(screen.getAllByText("RCPT-2026-000123").length).toBeGreaterThan(0);
  });

  it("offers Continue payment and Cancel order for an order awaiting payment", async () => {
    mockApi({
      "GET /api/v1/me/live-exam-orders": { content: [order({ status: "PENDING", paidAt: null, receiptNo: null })], page: 0, totalPages: 1 },
      "GET /api/v1/me/live-exam-orders/summary": { totalPaid: 0, currency: "BDT", paidCount: 0, pendingCount: 1 },
      "GET /api/v1/me/live-exam-access": { exams: [], modules: [] },
    });
    renderWithProviders(<PaymentsPage />, { user: fixtures.student, locale: "en", route: "/account/payments?order=o1" });

    await screen.findByRole("heading", { name: "Order details" });
    const panel = document.body;
    expect(within(panel).getByRole("button", { name: "Continue payment" })).toBeInTheDocument();
    expect(within(panel).getByRole("button", { name: "Cancel order" })).toBeInTheDocument();
    expect(within(panel).queryByRole("button", { name: "Download PDF" })).not.toBeInTheDocument();
  });

  it("labels a bundle as a whole package, not as an exam", async () => {
    mockApi({
      "GET /api/v1/me/live-exam-orders": { content: [order({ scope: "BUNDLE" })], page: 0, totalPages: 1 },
      "GET /api/v1/me/live-exam-orders/summary": { totalPaid: 1500, currency: "BDT", paidCount: 1, pendingCount: 0 },
      "GET /api/v1/me/live-exam-access": { exams: [], modules: [] },
    });
    renderWithProviders(<PaymentsPage />, { user: fixtures.student, locale: "en", route: "/account/payments" });

    expect((await screen.findAllByText("Whole package")).length).toBeGreaterThan(0);
    expect(screen.queryByRole("cell", { name: "Exam" })).not.toBeInTheDocument();
  });
});
