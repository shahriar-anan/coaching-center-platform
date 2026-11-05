import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { MyPayPage } from "@/components/staff/pay/my-pay-page";
import { PayPersonPage } from "@/components/staff/pay/pay-person-page";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

// Instructor marking pay (staff pay plan, phase 4): the cards and the per-exam table on My pay and on the Master Admin's
// person page, and who is asked for them.

const PERSON = "p-1";

const marking = {
  sheetRate: 50,
  totals: { marked: 3, waiting: 2, ready: 1, published: 1, earned: 50, expected: 100 },
  exams: [
    {
      examId: "e-1",
      titleEn: "Muslim Law",
      titleBn: "মুসলিম আইন",
      marked: 3,
      waiting: 2,
      ready: 1,
      published: 1,
      earned: 50,
      expected: 100,
    },
  ],
};

const emptyLedger = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

const summary = (role: "INSTRUCTOR" | "SYSTEM_ADMIN") => ({
  role,
  summary: { charged: 50, paid: 0, balance: 50, owedNow: 50, overdue: 0, lastPaymentOn: null },
  monthlySalary: null,
  payDay: null,
  nextPayDate: null,
});

const personDetail = (role: "INSTRUCTOR" | "SYSTEM_ADMIN") => ({
  id: PERSON,
  fullName: "Nasrin Sultana",
  role,
  status: "ACTIVE",
  deleted: false,
  payNotSetUp: false,
  settings: { monthlySalary: null, payDay: null, salaryStart: null, salaryEnd: null, sheetRate: null, effectiveSheetRate: 50 },
  summary: { charged: 50, paid: 0, balance: 50, owedNow: 50, overdue: 0, lastPaymentOn: null },
  nextPayDate: null,
  preferredAccount: null,
});

describe("marking pay on My pay", () => {
  it("shows the marking figures, the rate and a row per exam for an instructor", async () => {
    mockApi({
      "GET /api/v1/me/pay/summary": summary("INSTRUCTOR"),
      "GET /api/v1/me/pay/ledger": emptyLedger,
      "GET /api/v1/me/pay/marking": marking,
    });
    renderWithProviders(<MyPayPage />, { user: fixtures.instructor, locale: "en", route: "/staff/my-pay" });

    expect(await screen.findByRole("heading", { name: "Marking" })).toBeInTheDocument();
    expect(screen.getByText(/Rate per sheet now:/)).toBeInTheDocument();
    expect(screen.getAllByText("Waiting to be published").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Ready for release").length).toBeGreaterThan(0);
    // The exam title shows in the per-exam table and in another row, so check that it is there, not that it is unique.
    expect(screen.getAllByText("Muslim Law").length).toBeGreaterThan(0);
  });

  it("uses the Bengali labels in Bengali", async () => {
    mockApi({
      "GET /api/v1/me/pay/summary": summary("INSTRUCTOR"),
      "GET /api/v1/me/pay/ledger": emptyLedger,
      "GET /api/v1/me/pay/marking": marking,
    });
    renderWithProviders(<MyPayPage />, { user: fixtures.instructor, locale: "bn", route: "/staff/my-pay" });

    expect(await screen.findByRole("heading", { name: "মার্কিং" })).toBeInTheDocument();
    expect(screen.getAllByText("প্রকাশের অপেক্ষায়").length).toBeGreaterThan(0);
    expect(screen.getAllByText("মুসলিম আইন").length).toBeGreaterThan(0);
  });

  it("does not ask for marking figures when the person is not an instructor", async () => {
    const api = mockApi({
      "GET /api/v1/me/pay/summary": summary("SYSTEM_ADMIN"),
      "GET /api/v1/me/pay/ledger": emptyLedger,
    });
    renderWithProviders(<MyPayPage />, { user: fixtures.systemAdmin, locale: "en", route: "/staff/my-pay" });

    expect(await screen.findByText("Your salary has not been set yet. The Master Admin sets it.")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Marking" })).toBeNull();
    expect(api.calls.some((call) => call.path === "/api/v1/me/pay/marking")).toBe(false);
  });
});

describe("marking pay on one person's page (Master Admin)", () => {
  it("shows the same figures for an instructor", async () => {
    mockApi({
      [`GET /api/v1/staff-pay/people/${PERSON}`]: personDetail("INSTRUCTOR"),
      [`GET /api/v1/staff-pay/people/${PERSON}/payout-accounts`]: [],
      [`GET /api/v1/staff-pay/people/${PERSON}/ledger`]: emptyLedger,
      [`GET /api/v1/staff-pay/people/${PERSON}/marking`]: marking,
      "GET /api/v1/staff-pay/defaults": {
        sheetRate: 50,
        unpaidReminderDays: 14,
        salaryDueSoonDays: 3,
        updatedAt: "2026-11-01T00:00:00Z",
      },
    });
    renderWithProviders(<PayPersonPage userId={PERSON} />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: `/staff/pay/${PERSON}`,
    });

    expect((await screen.findAllByText("Muslim Law")).length).toBeGreaterThan(0);
    expect(screen.getByRole("heading", { name: "Marking" })).toBeInTheDocument();
  });

  it("does not ask for marking figures for a System Admin", async () => {
    const api = mockApi({
      [`GET /api/v1/staff-pay/people/${PERSON}`]: personDetail("SYSTEM_ADMIN"),
      [`GET /api/v1/staff-pay/people/${PERSON}/payout-accounts`]: [],
      [`GET /api/v1/staff-pay/people/${PERSON}/ledger`]: emptyLedger,
      "GET /api/v1/staff-pay/defaults": {
        sheetRate: 50,
        unpaidReminderDays: 14,
        salaryDueSoonDays: 3,
        updatedAt: "2026-11-01T00:00:00Z",
      },
    });
    renderWithProviders(<PayPersonPage userId={PERSON} />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: `/staff/pay/${PERSON}`,
    });

    expect(await screen.findByRole("heading", { name: "Nasrin Sultana" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Marking" })).toBeNull();
    expect(api.calls.some((call) => call.path.endsWith("/marking"))).toBe(false);
  });
});
