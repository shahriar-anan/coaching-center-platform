import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { FeeApprovals } from "@/components/staff/offline/fee-approvals";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { overdueRow, page, payment } from "./fixtures";

const PENDING = [
  payment({ id: "pay-1", name: "Rahim Uddin", amount: 3000 }),
  payment({ id: "pay-2", name: "Karim Ali", amount: 1500, method: "BKASH", reference: "TX123" }),
];

/** Pending payments for the Master Admin's queue; the recorded list for staff gets everything. */
function feeRoutes() {
  return mockApi({
    "GET /api/v1/offline-fee-payments": ({ url }: { url: URL }) => {
      const status = url.searchParams.get("status");
      return status === "PENDING" ? PENDING : [...PENDING, payment({ id: "pay-3", status: "APPROVED", receiptNo: "R-1" })];
    },
  });
}

describe("fee approvals (plan 6.3)", () => {
  it("lists each pending payment with Approve and Reject in its row for the Master Admin", async () => {
    feeRoutes();
    renderWithProviders(<FeeApprovals />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/offline-fees" });

    const first = (await screen.findAllByText("Rahim Uddin"))[0]?.closest("tr");
    expect(first).not.toBeNull();
    expect(within(first as HTMLElement).getByRole("button", { name: "Approve" })).toBeInTheDocument();
    expect(within(first as HTMLElement).getByRole("button", { name: "Reject" })).toBeInTheDocument();

    const second = (await screen.findAllByText("Karim Ali"))[0]?.closest("tr") as HTMLElement;
    expect(within(second).getByRole("button", { name: "Approve" })).toBeInTheDocument();
    expect(within(second).getByRole("button", { name: "Reject" })).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Approve" })).toHaveLength(4);
  });

  it("shows the batch under each student's name, from the payment row itself", async () => {
    feeRoutes();
    renderWithProviders(<FeeApprovals />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/offline-fees" });

    const first = (await screen.findAllByText("Rahim Uddin"))[0]?.closest("tr") as HTMLElement;
    expect(within(first).getByText("BJS Evening")).toBeInTheDocument();
  });

  it("gives staff their own payments with no approve or reject buttons", async () => {
    feeRoutes();
    renderWithProviders(<FeeApprovals />, { user: fixtures.systemAdmin, locale: "en", route: "/staff/offline-fees" });

    expect(await screen.findByText("Payments I recorded")).toBeInTheDocument();
    expect((await screen.findAllByText("Rahim Uddin")).length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Reject" })).not.toBeInTheDocument();
  });
});

describe("the Overdue tab reads the server's overdue list (plan 6.3)", () => {
  it("shows one row per admission with its days late and what it owes in all, and reads no schedule", async () => {
    const api = mockApi({
      "GET /api/v1/offline-admissions/overdue": page([
        overdueRow(),
        overdueRow({ admissionId: "adm-2", studentId: "stu-2", name: "Karim Ali", batchNameEn: null, amount: 1500, daysLate: 4, owedTotal: 1500 }),
      ]),
    });
    renderWithProviders(<FeeApprovals />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/offline-fees?tab=overdue" });

    const first = (await screen.findAllByText("Rahim Uddin"))[0]?.closest("tr") as HTMLElement;
    expect(within(first).getByText("BJS Evening")).toBeInTheDocument();
    expect(within(first).getByText("12")).toBeInTheDocument();
    expect(within(first).getByText(/6,000\.00/)).toBeInTheDocument();

    const second = (await screen.findAllByText("Karim Ali"))[0]?.closest("tr") as HTMLElement;
    expect(within(second).getByText("4")).toBeInTheDocument();

    expect(api.calls.some((call) => call.path === "/api/v1/offline-admissions/overdue")).toBe(true);
    expect(api.calls.some((call) => call.path.endsWith("/fees"))).toBe(false);
  });

  it("says nothing is overdue when the server returns no rows", async () => {
    mockApi({ "GET /api/v1/offline-admissions/overdue": page([]) });
    renderWithProviders(<FeeApprovals />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/offline-fees?tab=overdue" });

    expect(await screen.findByText("Nothing is overdue")).toBeInTheDocument();
  });
});
