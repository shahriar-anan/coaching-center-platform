import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { MyPayPage } from "@/components/staff/pay/my-pay-page";
import { MyExamsSection } from "@/components/staff/pay/my-exams-section";
import { formatMoney } from "@/lib/format";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

/** The API's own shape: a plain list with examRate, effectiveRate, marked and published (the page translates it). */
const rows = [
  { examId: "e-1", titleEn: "Muslim Law", titleBn: "মুসলিম আইন", examRate: 60, effectiveRate: 60, marked: 12, published: 9 },
  { examId: "e-2", titleEn: "", titleBn: "পারিবারিক আইন", effectiveRate: 50, marked: 3, published: 0 },
];

const summary = (role: "INSTRUCTOR" | "SYSTEM_ADMIN") => ({
  role,
  summary: { charged: 0, paid: 0, balance: 0, owedNow: 0, overdue: 0, lastPaymentOn: null },
  monthlySalary: null,
  payDay: null,
  nextPayDate: null,
  nextPayAmount: null,
});

const emptyLedger = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

describe("My exams for an instructor", () => {
  it("lists each assigned exam with its rate per sheet, marked and published counts", async () => {
    mockApi({ "GET /api/v1/me/pay/exams": rows });
    renderWithProviders(<MyExamsSection />, { user: fixtures.instructor, locale: "en" });

    const table = await screen.findByRole("table");
    const first = within(table).getByText("Muslim Law").closest("tr");
    expect(first).not.toBeNull();
    expect(within(first as HTMLElement).getByText(formatMoney(60, "en") as string)).toBeInTheDocument();
    expect(within(first as HTMLElement).getByText("12")).toBeInTheDocument();
    expect(within(first as HTMLElement).getByText("9")).toBeInTheDocument();
  });

  it("uses the Bangla title when the English one is blank", async () => {
    mockApi({ "GET /api/v1/me/pay/exams": rows });
    renderWithProviders(<MyExamsSection />, { user: fixtures.instructor, locale: "en" });

    const table = await screen.findByRole("table");
    expect(within(table).getByText("পারিবারিক আইন")).toBeInTheDocument();
  });

  it("says so when no exam is assigned yet", async () => {
    mockApi({ "GET /api/v1/me/pay/exams": [] });
    renderWithProviders(<MyExamsSection />, { user: fixtures.instructor, locale: "en" });

    expect(await screen.findByText("No exams assigned to you yet.")).toBeInTheDocument();
  });

  it("is shown on My pay for an instructor", async () => {
    mockApi({
      "GET /api/v1/me/pay/summary": summary("INSTRUCTOR"),
      "GET /api/v1/me/pay/ledger": emptyLedger,
      "GET /api/v1/me/pay/marking": {
        sheetRate: 50,
        totals: { marked: 0, waiting: 0, ready: 0, published: 0, earned: 0, expected: 0 },
        exams: [],
      },
      "GET /api/v1/me/pay/exams": rows,
    });
    renderWithProviders(<MyPayPage />, { user: fixtures.instructor, locale: "en", route: "/staff/my-pay" });

    expect(await screen.findByRole("heading", { name: "My exams" })).toBeInTheDocument();
  });

  it("is hidden on My pay for a System Admin, and its route is never called", async () => {
    const api = mockApi({
      "GET /api/v1/me/pay/summary": summary("SYSTEM_ADMIN"),
      "GET /api/v1/me/pay/ledger": emptyLedger,
    });
    renderWithProviders(<MyPayPage />, { user: fixtures.systemAdmin, locale: "en", route: "/staff/my-pay" });

    expect(await screen.findByText("Balance")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "My exams" })).not.toBeInTheDocument();
    expect(api.calls.some((call) => call.path === "/api/v1/me/pay/exams")).toBe(false);
  });
});
