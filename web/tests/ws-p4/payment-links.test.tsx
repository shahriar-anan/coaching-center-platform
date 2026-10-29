import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import { PurchaseResult } from "@/components/student/purchase/purchase-result";
import { pendingTarget } from "@/lib/api/exam/purchase";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const MEMO_KEY = "exam-portal.order.tran-1";

beforeEach(() => {
  window.sessionStorage.clear();
});

describe("the links on the purchase result (Phase 4, S4.6)", () => {
  it("offers View payment and Download receipt on a paid order, and links to that order's row", async () => {
    window.sessionStorage.setItem(
      MEMO_KEY,
      JSON.stringify({ scope: "MODULE", examId: null, moduleId: "m1", door: "mcq", title: "BCS Preliminary", orderId: "order-1" }),
    );
    mockApi({ "GET /api/v1/live-exam-orders/status": { status: "PAID", examName: "BCS Preliminary" } });
    renderWithProviders(<PurchaseResult />, { user: fixtures.student, locale: "en", route: "/purchase/result?tranId=tran-1" });

    expect(await screen.findByRole("link", { name: "View payment" })).toHaveAttribute("href", "/account/payments?order=order-1");
    expect(screen.getByRole("button", { name: "Download receipt" })).toBeInTheDocument();
  });

  it("offers View payment to the whole list when the order is not known here, with no receipt button", async () => {
    window.sessionStorage.setItem(MEMO_KEY, JSON.stringify({ scope: "EXAM", examId: "e1", moduleId: "m1", door: "mcq", title: "CRPC Mock" }));
    mockApi({ "GET /api/v1/live-exam-orders/status": { status: "PAID", examName: "CRPC Mock" } });
    renderWithProviders(<PurchaseResult />, { user: fixtures.student, locale: "en", route: "/purchase/result?tranId=tran-1" });

    expect(await screen.findByRole("link", { name: "View payment" })).toHaveAttribute("href", "/account/payments");
    expect(screen.queryByRole("button", { name: "Download receipt" })).not.toBeInTheDocument();
  });

  it("offers no receipt on a pending payment", async () => {
    window.sessionStorage.setItem(MEMO_KEY, JSON.stringify({ scope: "MODULE", examId: null, moduleId: "m1", door: "mcq", title: "BCS", orderId: "order-1" }));
    mockApi({ "GET /api/v1/live-exam-orders/status": { status: "PENDING", examName: "BCS" } });
    renderWithProviders(<PurchaseResult />, { user: fixtures.student, locale: "en", route: "/purchase/result?tranId=tran-1" });

    expect(await screen.findByText("Confirming your payment…")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Download receipt" })).not.toBeInTheDocument();
  });
});

describe("the target a pending order is paid with again (Phase 4, S4.6)", () => {
  it("posts the package for a package order, and the exam for an exam order", async () => {
    await expect(pendingTarget({ orderId: "o1", scope: "MODULE", moduleId: "m1", examId: null })).resolves.toEqual({ moduleId: "m1" });
    await expect(pendingTarget({ orderId: "o2", scope: "EXAM", moduleId: "m1", examId: "e1" })).resolves.toEqual({ examId: "e1" });
  });

  it("reads the exams of a bundle order and posts them", async () => {
    const { calls } = mockApi({ "GET /api/v1/live-exam-orders/o3": { examIds: ["e1", "e2"] } });
    await expect(pendingTarget({ orderId: "o3", scope: "BUNDLE", moduleId: null, examId: null })).resolves.toEqual({ examIds: ["e1", "e2"] });
    expect(calls.some((call) => call.path === "/api/v1/live-exam-orders/o3")).toBe(true);
  });
});
