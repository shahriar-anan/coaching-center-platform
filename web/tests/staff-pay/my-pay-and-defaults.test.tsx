import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { MyPayPage } from "@/components/staff/pay/my-pay-page";
import { PayDefaultsPage } from "@/components/staff/pay/pay-defaults-form";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const payment = {
  id: "l-1",
  kind: "PAYMENT",
  amount: 150,
  dueOn: "2026-11-03",
  createdAt: "2026-11-03T10:00:00+06:00",
  paidOn: "2026-11-03",
  method: "BKASH",
  accountSnapshot: "bKash 017••••4821",
  reference: "TX-9",
  note: null,
  voided: false,
  voidedAt: null,
  voidReason: null,
};

const summary = (role: "INSTRUCTOR" | "SYSTEM_ADMIN", extra: Record<string, unknown> = {}) => ({
  role,
  summary: { charged: 400, paid: 250, balance: 150, owedNow: 150, overdue: 0, lastPaymentOn: "2026-11-03" },
  monthlySalary: null,
  payDay: null,
  nextPayDate: null,
  ...extra,
});

const salaryView = (extra: Record<string, unknown> = {}) => ({
  monthlySalary: 20000,
  payDay: 5,
  salaryStart: "2026-10-01",
  salaryEnd: null,
  nextPayDate: null,
  nextPayAmount: null,
  months: [{ period: "2026-10", dueOn: "2026-11-05", amount: 20000, paid: 0, uncovered: 20000, status: "DUE" }],
  ...extra,
});

describe("My pay for a teacher or a System Admin", () => {
  it("shows the totals and each payment with its method, account text and reference", async () => {
    mockApi({
      "GET /api/v1/me/pay/summary": summary("INSTRUCTOR"),
      "GET /api/v1/me/pay/ledger": { content: [payment], page: 0, size: 20, totalElements: 1, totalPages: 1 },
    });
    renderWithProviders(<MyPayPage />, { user: fixtures.instructor, locale: "en", route: "/staff/my-pay" });

    expect(await screen.findByText("Balance")).toBeInTheDocument();
    expect((await screen.findAllByText("bKash 017••••4821")).length).toBeGreaterThan(0);
    expect(screen.getAllByText("TX-9").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Payment").length).toBeGreaterThan(0);
  });

  it("shows a System Admin's salary and next pay date, and a note when the salary is not set", async () => {
    mockApi({
      "GET /api/v1/me/pay/summary": summary("SYSTEM_ADMIN", { monthlySalary: 20000, payDay: 5, nextPayDate: "2026-12-05" }),
      "GET /api/v1/me/pay/salary": salaryView({ nextPayDate: "2026-12-05", nextPayAmount: 20000 }),
      "GET /api/v1/me/pay/ledger": { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
    });
    const { unmount } = renderWithProviders(<MyPayPage />, { user: fixtures.systemAdmin, locale: "en", route: "/staff/my-pay" });

    expect(await screen.findByText(/Next pay date/)).toBeInTheDocument();
    expect(screen.getByText("Nothing recorded yet.")).toBeInTheDocument();
    unmount();

    mockApi({
      "GET /api/v1/me/pay/summary": summary("SYSTEM_ADMIN"),
      "GET /api/v1/me/pay/ledger": { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
    });
    renderWithProviders(<MyPayPage />, { user: fixtures.systemAdmin, locale: "en", route: "/staff/my-pay" });
    expect(await screen.findByText("Your salary has not been set yet. The Master Admin sets it.")).toBeInTheDocument();
  });
});

describe("the pay defaults (Master Admin)", () => {
  it("saves the sheet rate and the two reminder days together", async () => {
    const api = mockApi({
      "GET /api/v1/staff-pay/defaults": { sheetRate: 50, unpaidReminderDays: 14, salaryDueSoonDays: 3, updatedAt: "2026-11-01T00:00:00Z" },
      "PUT /api/v1/staff-pay/defaults": { sheetRate: 50, unpaidReminderDays: 10, salaryDueSoonDays: 3, updatedAt: "2026-11-05T00:00:00Z" },
    });
    renderWithProviders(<PayDefaultsPage />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/pay/settings" });

    const form = await screen.findByRole("form", { name: "Pay defaults" });
    const user = userEvent.setup();
    const unpaid = within(form).getByLabelText("Remind after (days)");
    await user.clear(unpaid);
    await user.type(unpaid, "10");
    await user.click(within(form).getByRole("button", { name: "Save defaults" }));

    await waitFor(() => expect(api.calls.some((call) => call.method === "PUT")).toBe(true));
    const sent = api.calls.find((call) => call.method === "PUT");
    expect(sent?.body).toEqual({ sheetRate: 50, unpaidReminderDays: 10, salaryDueSoonDays: 3 });
  });

  it("refuses a reminder of zero days before it asks the API", async () => {
    const api = mockApi({
      "GET /api/v1/staff-pay/defaults": { sheetRate: 50, unpaidReminderDays: 14, salaryDueSoonDays: 3, updatedAt: "2026-11-01T00:00:00Z" },
    });
    renderWithProviders(<PayDefaultsPage />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/pay/settings" });

    const form = await screen.findByRole("form", { name: "Pay defaults" });
    const user = userEvent.setup();
    const unpaid = within(form).getByLabelText("Remind after (days)");
    await user.clear(unpaid);
    await user.type(unpaid, "0");
    await user.click(within(form).getByRole("button", { name: "Save defaults" }));

    expect(await screen.findByText(/Check the number of days/)).toBeInTheDocument();
    expect(api.calls.some((call) => call.method === "PUT")).toBe(false);
  });
});
