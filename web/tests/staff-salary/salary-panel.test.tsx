import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { PayPersonPage } from "@/components/staff/pay/pay-person-page";
import { salaryMonthLabel } from "@/components/staff/pay/salary-panel";
import { MyPayPage } from "@/components/staff/pay/my-pay-page";
import { MY_SALARY_PATH, paySalaryPath } from "@/lib/api/exam/staff-pay";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

// The System Admin salary (staff pay plan, phase 5): the salary card, the months list, and the Master Admin's person page.

const PERSON = "p-admin";

const summary = {
  role: "SYSTEM_ADMIN",
  summary: { charged: 60000, paid: 20000, balance: 40000, owedNow: 40000, overdue: 20000, lastPaymentOn: "2026-10-02" },
  monthlySalary: 20000,
  payDay: 5,
  nextPayDate: "2026-12-05",
  nextPayAmount: 20000,
};

const salary = {
  monthlySalary: 20000,
  payDay: 5,
  salaryStart: "2026-08-01",
  salaryEnd: null,
  nextPayDate: "2026-12-05",
  nextPayAmount: 20000,
  months: [
    { period: "2026-10", dueOn: "2026-11-05", amount: 20000, paid: 20000, uncovered: 0, status: "PAID" },
    { period: "2026-09", dueOn: "2026-10-05", amount: 20000, paid: 0, uncovered: 20000, status: "OVERDUE" },
    { period: "2026-08", dueOn: "2026-09-05", amount: 20000, paid: 0, uncovered: 20000, status: "OVERDUE" },
  ],
};

const ledger = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

describe("the salary months for My pay", () => {
  it("shows the salary, the next pay date with its amount, and each month with its due date and status", async () => {
    mockApi({
      "GET /api/v1/me/pay/summary": summary,
      "GET /api/v1/me/pay/salary": salary,
      "GET /api/v1/me/pay/ledger": ledger,
    });
    renderWithProviders(<MyPayPage />, { user: fixtures.systemAdmin, locale: "en", route: "/staff/my-pay" });

    expect(await screen.findByText("Next pay date")).toBeInTheDocument();
    expect(screen.getByText("Due on that date")).toBeInTheDocument();
    expect(screen.getAllByText("৳20,000.00").length).toBeGreaterThan(0);

    expect(await screen.findByRole("table")).toBeInTheDocument();
    const rows = screen.getAllByRole("row");
    // The header row, then the months as the API sent them (newest first).
    expect(within(rows[1]).getByText("October 2026")).toBeInTheDocument();
    expect(within(rows[1]).getByText("Paid")).toBeInTheDocument();
    expect(within(rows[2]).getByText("September 2026")).toBeInTheDocument();
    expect(within(rows[2]).getByText("Overdue")).toBeInTheDocument();
    expect(within(rows[2]).getByText("5 October 2026")).toBeInTheDocument();
  });

  it("says the amount is already paid when the month on the next pay date is settled", async () => {
    mockApi({
      "GET /api/v1/me/pay/summary": summary,
      "GET /api/v1/me/pay/salary": { ...salary, nextPayAmount: 0 },
      "GET /api/v1/me/pay/ledger": ledger,
    });
    renderWithProviders(<MyPayPage />, { user: fixtures.systemAdmin, locale: "en", route: "/staff/my-pay" });

    expect(await screen.findByText("Already paid")).toBeInTheDocument();
  });

  it("shows an empty note when no month has finished yet", async () => {
    mockApi({
      "GET /api/v1/me/pay/summary": summary,
      "GET /api/v1/me/pay/salary": { ...salary, months: [], nextPayAmount: 20000 },
      "GET /api/v1/me/pay/ledger": ledger,
    });
    renderWithProviders(<MyPayPage />, { user: fixtures.systemAdmin, locale: "en", route: "/staff/my-pay" });

    expect(await screen.findByText("No salary months yet.")).toBeInTheDocument();
  });
});

describe("the salary on the Master Admin's person page", () => {
  it("shows a System Admin's months and the next pay date", async () => {
    const api = mockApi({
      [`GET /api/v1/staff-pay/people/${PERSON}`]: {
        id: PERSON,
        fullName: "Salary Test",
        role: "SYSTEM_ADMIN",
        status: "ACTIVE",
        deleted: false,
        payNotSetUp: false,
        settings: { monthlySalary: 20000, payDay: 5, salaryStart: "2026-08-01", salaryEnd: null, sheetRate: null, effectiveSheetRate: null },
        summary: summary.summary,
        nextPayDate: "2026-12-05",
        preferredAccount: null,
      },
      [`GET /api/v1/staff-pay/people/${PERSON}/salary`]: salary,
      [`GET /api/v1/staff-pay/people/${PERSON}/payout-accounts`]: [],
      [`GET /api/v1/staff-pay/people/${PERSON}/ledger`]: ledger,
      "GET /api/v1/staff-pay/defaults": { sheetRate: 50, unpaidReminderDays: 14, salaryDueSoonDays: 3, updatedAt: "2026-11-01T00:00:00Z" },
    });
    renderWithProviders(<PayPersonPage userId={PERSON} />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: `/staff/pay/${PERSON}`,
    });

    expect(await screen.findByText("Next pay date")).toBeInTheDocument();
    expect((await screen.findAllByText("September 2026")).length).toBeGreaterThan(0);
    expect(api.calls.some((call) => call.path === `/api/v1/staff-pay/people/${PERSON}/salary`)).toBe(true);
  });
});

describe("the salary paths and month labels", () => {
  it("builds the salary paths for the Master Admin and for the signed-in System Admin", () => {
    expect(paySalaryPath("u1")).toBe("/api/v1/staff-pay/people/u1/salary");
    expect(MY_SALARY_PATH).toBe("/api/v1/me/pay/salary");
  });

  it("names the month in English and in Bangla, without the day", () => {
    expect(salaryMonthLabel("2026-10", "en")).toBe("October 2026");
    expect(salaryMonthLabel("2026-01", "en")).toBe("January 2026");
    const bangla = salaryMonthLabel("2026-10", "bn");
    expect(bangla).toContain("অক্টোবর");
    expect(bangla).toContain("২০২৬");
    expect(bangla).not.toMatch(/^[০-৯]/);
  });
});
