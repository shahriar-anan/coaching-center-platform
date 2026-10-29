import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import PaymentsPage from "@/app/(site)/account/payments/page";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const page = (content: unknown[]) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 });

function order(overrides: Record<string, unknown> = {}) {
  return {
    orderId: "order-1",
    tranId: "tran-1",
    status: "PAID",
    amount: 300,
    currency: "BDT",
    scope: "EXAM",
    moduleId: "m1",
    moduleTitleEn: "Module",
    moduleTitleBn: "Module",
    examId: "e1",
    examTitleEn: "Evidence paper",
    examTitleBn: "Evidence paper",
    paymentChannel: "BKASH",
    receiptEmail: null,
    createdAt: "2026-10-20T04:00:00Z",
    paidAt: "2026-10-20T04:05:00Z",
    receiptNo: "RCPT-2026-000001",
    listPrice: null,
    discountPercent: null,
    ...overrides,
  };
}

/** The payment history with one order open in its detail, for a student whose account email is verified or not. */
function openDetail(row: Record<string, unknown>, verified: boolean) {
  mockApi({
    "GET /api/v1/me/live-exam-orders": page([row]),
    "GET /api/v1/me/live-exam-access": { modules: [], exams: [] },
  });
  renderWithProviders(<PaymentsPage />, {
    user: { ...fixtures.student, emailVerifiedAt: verified ? "2026-10-01T00:00:00Z" : null },
    locale: "en",
    route: "/account/payments?order=order-1",
  });
}

describe("the receipt line in the payment history detail (Phase 4B)", () => {
  it("says where the receipt was sent when an address was typed for the order", async () => {
    openDetail(order({ receiptEmail: "reader@example.com" }), false);
    expect(await screen.findByText("Receipt sent to: reader@example.com")).toBeInTheDocument();
  });

  it("says the receipt went to the account email when none was typed and the account email is verified", async () => {
    openDetail(order(), true);
    expect(await screen.findByText("Receipt sent to your account email")).toBeInTheDocument();
  });

  it("says there is no receipt email when none was typed and the account email is not verified", async () => {
    openDetail(order(), false);
    expect(await screen.findByText("No receipt email")).toBeInTheDocument();
  });

  it("shows no receipt line for an order that is not paid", async () => {
    openDetail(order({ status: "PENDING", paidAt: null, receiptNo: null }), true);
    await screen.findAllByText("Evidence paper");
    expect(screen.queryByText(/Receipt sent to|No receipt email/)).not.toBeInTheDocument();
  });
});
