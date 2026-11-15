import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiFile } from "@/lib/api/client";
import StaffFinancePage from "@/app/(staff)/staff/finance/page";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

// The CSV download goes through the authorised file helper. It is mocked here so the test sees the call, not a network request.
vi.mock("@/lib/api/client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api/client")>();
  return {
    ...actual,
    apiFile: vi.fn(async () => ({
      blob: new Blob(["date,source\r\n"], { type: "text/csv" }),
      fileName: "finance-entries.csv",
    })),
  };
});

const ROUTE = "/staff/finance?range=custom&from=2026-10-01&to=2026-10-31";

/** October: income 30,000 (online 20,000, offline fees 10,000), expenditure 12,000 (staff pay). */
const summary = {
  from: "2026-10-01",
  to: "2026-10-31",
  income: 30000,
  expense: 12000,
  net: 18000,
  sources: [
    { source: "ONLINE_ORDERS", direction: "INCOME", total: 20000, count: 5 },
    { source: "OFFLINE_FEES", direction: "INCOME", total: 10000, count: 2 },
    { source: "STAFF_PAY", direction: "EXPENSE", total: 12000, count: 1 },
  ],
  previous: { from: "2026-09-01", to: "2026-09-30", income: 25000, expense: 10000, net: 15000 },
  change: { income: 20, expense: 20, net: 20 },
};

const monthly = [
  {
    month: "2026-10",
    income: 30000,
    expense: 12000,
    net: 18000,
    bySource: { ONLINE_ORDERS: 20000, OFFLINE_FEES: 10000, STAFF_PAY: 12000 },
  },
];

function entry(index: number) {
  return {
    source: "ONLINE_ORDERS",
    date: "2026-10-05",
    description: `Order ${index}`,
    amount: 100,
    direction: "INCOME",
    reference: `REF-${index}`,
    id: `00000000-0000-0000-0000-${String(index).padStart(12, "0")}`,
    batchId: null,
  };
}

/** Entries: 120 in all, 50 to a page, so three pages. Page 0 holds 1 to 50. */
function entryPage(url: URL) {
  const page = Number(url.searchParams.get("page") ?? "0");
  const size = Number(url.searchParams.get("size") ?? "50");
  const total = 120;
  const start = page * size;
  const items = Array.from({ length: Math.max(0, Math.min(size, total - start)) }, (_, index) => entry(start + index + 1));
  return { items, page, size, total, income: 12000, expense: 0 };
}

function routes(overrides: Record<string, unknown> = {}) {
  return {
    "GET /api/v1/finance/summary": summary,
    "GET /api/v1/finance/monthly": monthly,
    "GET /api/v1/finance/batches": [],
    "GET /api/v1/finance/instructors": [],
    "GET /api/v1/offline-batches": [],
    "GET /api/v1/finance/entries": ({ url }: { url: URL }) => entryPage(url),
    ...overrides,
  };
}

describe("the finance page", () => {
  it("has an All entries button that opens the entries page", async () => {
    mockApi(routes());
    renderWithProviders(<StaffFinancePage />, { user: fixtures.masterAdmin, locale: "en", route: ROUTE });

    const link = await screen.findByRole("link", { name: "All entries" });
    expect(new URL(link.getAttribute("href") ?? "", "http://x").pathname).toBe("/staff/finance/entries");
  });

  beforeEach(() => {
    vi.mocked(apiFile).mockClear();
  });

  it("shows an access message to anyone who is not the Master Admin", async () => {
    mockApi(routes());
    renderWithProviders(<StaffFinancePage />, { user: fixtures.systemAdmin, locale: "en", route: ROUTE });
    expect(await screen.findByText("Finance is for the Master Admin")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Income and expenditure" })).not.toBeInTheDocument();
  });

  it("shows income, expenditure and net from the summary, with the change against the period before", async () => {
    mockApi(routes());
    renderWithProviders(<StaffFinancePage />, { user: fixtures.masterAdmin, locale: "en", route: ROUTE });

    const income = await screen.findByRole("heading", { name: "Income" });
    const incomeCard = income.closest("div.rounded-card") as HTMLElement;
    expect(within(incomeCard).getByText("৳30,000.00")).toBeInTheDocument();
    expect(within(incomeCard).getByText("20.0% on the period before")).toBeInTheDocument();
    expect(within(incomeCard).getByText("Before: ৳25,000.00")).toBeInTheDocument();

    const expense = screen.getByRole("heading", { name: "Expenditure" }).closest("div.rounded-card") as HTMLElement;
    expect(within(expense).getByText("৳12,000.00")).toBeInTheDocument();

    const net = screen.getByRole("heading", { name: "Net" }).closest("div.rounded-card") as HTMLElement;
    expect(within(net).getByText("৳18,000.00")).toHaveClass("text-ok-text");
  });

  it("shows the net in red when it is negative", async () => {
    mockApi(
      routes({
        "GET /api/v1/finance/summary": {
          ...summary,
          income: 5000,
          expense: 9000,
          net: -4000,
          change: { income: null, expense: 10, net: -50 },
        },
      }),
    );
    renderWithProviders(<StaffFinancePage />, { user: fixtures.masterAdmin, locale: "en", route: ROUTE });

    const net = (await screen.findByRole("heading", { name: "Net" })).closest("div.rounded-card") as HTMLElement;
    expect(within(net).getByText("৳-4,000.00")).toHaveClass("text-danger");
    expect(within(net).getByText("Behind by this amount")).toBeInTheDocument();
  });

  it("hides a source from the chart when its legend item is toggled, without changing the filter", async () => {
    mockApi(routes());
    renderWithProviders(<StaffFinancePage />, { user: fixtures.masterAdmin, locale: "en", route: ROUTE });

    const before = "Oct: income ৳30,000.00, expenditure ৳12,000.00, net ৳18,000.00";
    expect(await screen.findByRole("group", { name: before })).toBeInTheDocument();

    const toggle = screen.getByRole("button", { name: "Show or hide Offline fees on the chart" });
    expect(toggle).toHaveAttribute("aria-pressed", "true");
    await userEvent.click(toggle);

    expect(toggle).toHaveAttribute("aria-pressed", "false");
    expect(
      screen.getByRole("group", { name: "Oct: income ৳20,000.00, expenditure ৳12,000.00, net ৳8,000.00" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("group", { name: before })).not.toBeInTheDocument();
  });

  it("says no money was recorded when the range has none, and does not draw an empty chart", async () => {
    mockApi(
      routes({
        "GET /api/v1/finance/summary": {
          ...summary,
          income: 0,
          expense: 0,
          net: 0,
          sources: summary.sources.map((row) => ({ ...row, total: 0, count: 0 })),
          change: { income: null, expense: null, net: null },
        },
        "GET /api/v1/finance/monthly": [],
      }),
    );
    renderWithProviders(<StaffFinancePage />, { user: fixtures.masterAdmin, locale: "en", route: ROUTE });

    expect(
      await screen.findByText("No money recorded in this period. Try a wider date range."),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Show or hide/ })).not.toBeInTheDocument();
  });

  it("links a source's total to the entries page, with that source and the same dates", async () => {
    mockApi(routes());
    renderWithProviders(<StaffFinancePage />, { user: fixtures.masterAdmin, locale: "en", route: ROUTE });

    const link = (await screen.findAllByRole("link", { name: "৳20,000.00" }))[0];
    const url = new URL(link.getAttribute("href") ?? "", "http://x");
    expect(url.pathname).toBe("/staff/finance/entries");
    const href = url.searchParams;
    expect(href.get("sources")).toBe("ONLINE_ORDERS");
    expect(href.get("from")).toBe("2026-10-01");
    expect(href.get("to")).toBe("2026-10-31");
  });
});
