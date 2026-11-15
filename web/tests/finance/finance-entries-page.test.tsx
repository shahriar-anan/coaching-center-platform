import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiFile } from "@/lib/api/client";
import StaffFinanceEntriesPage from "@/app/(staff)/staff/finance/entries/page";
import { fixtures, mockApi, renderWithProviders, routerState } from "../helpers";

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

const ROUTE = "/staff/finance/entries?range=custom&from=2026-10-01&to=2026-10-31";

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

function entry(index: number, overrides: Record<string, unknown> = {}) {
  return {
    source: "ONLINE_ORDERS",
    date: "2026-10-05",
    description: `Order ${index}`,
    amount: 100,
    direction: "INCOME",
    reference: `REF-${index}`,
    id: `00000000-0000-0000-0000-${String(index).padStart(12, "0")}`,
    batchId: null,
    ...overrides,
  };
}

/** 120 entries, 50 to a page; the stream totals cover every stream in reach. */
function entryPage(url: URL) {
  const page = Number(url.searchParams.get("page") ?? "0");
  const size = Number(url.searchParams.get("size") ?? "50");
  const total = 120;
  const start = page * size;
  const items = Array.from({ length: Math.max(0, Math.min(size, total - start)) }, (_, index) => entry(start + index + 1));
  if (page === 0) {
    items[1] = entry(2, { source: "STAFF_PAY", direction: "EXPENSE", description: "Salary of Rahim", amount: 12000 });
  }
  return {
    items,
    page,
    size,
    total,
    income: 30000,
    expense: 12000,
    bySource: summary.sources,
  };
}

function routes() {
  return {
    "GET /api/v1/finance/summary": summary,
    "GET /api/v1/offline-batches": [],
    "GET /api/v1/finance/entries": ({ url }: { url: URL }) => entryPage(url),
  };
}

describe("the entries page", () => {
  beforeEach(() => {
    vi.mocked(apiFile).mockClear();
    routerState.replace.mockImplementation((href: string) => {
      routerState.search = href.split("?")[1] ?? "";
    });
  });

  it("shows an access message to anyone who is not the Master Admin", async () => {
    mockApi(routes());
    renderWithProviders(<StaffFinanceEntriesPage />, { user: fixtures.systemAdmin, locale: "en", route: ROUTE });

    expect(await screen.findByText("Finance is for the Master Admin")).toBeInTheDocument();
  });

  it("shows the totals, the streams with their totals, and money in and out with their signs", async () => {
    mockApi(routes());
    renderWithProviders(<StaffFinanceEntriesPage />, { user: fixtures.masterAdmin, locale: "en", route: ROUTE });

    expect(await screen.findByText("Showing 1 to 50 of 120")).toBeInTheDocument();
    expect(screen.getAllByText("৳18,000.00").length).toBeGreaterThan(0);
    expect(screen.getByRole("button", { name: /Online orders/ })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getAllByText("+৳100.00").length).toBeGreaterThan(0);
    expect(screen.getAllByText("−৳12,000.00").length).toBeGreaterThan(0);
  });

  it("turns a stream off from its chip, and Money out keeps only the expense streams", async () => {
    const user = userEvent.setup();
    mockApi(routes());
    renderWithProviders(<StaffFinanceEntriesPage />, { user: fixtures.masterAdmin, locale: "en", route: ROUTE });

    await user.click(await screen.findByRole("button", { name: /Online orders/ }));
    const off = new URL(String(routerState.replace.mock.calls.at(-1)?.[0]), "http://x").searchParams;
    expect(off.get("sources")).toBe("OFFLINE_FEES,STAFF_PAY");

    await user.click(screen.getByRole("button", { name: "Money out" }));
    const out = new URL(String(routerState.replace.mock.calls.at(-1)?.[0]), "http://x").searchParams;
    expect(out.get("sources")).toBe("STAFF_PAY");
  });

  it("pages through the entries, asking the API for the next page", async () => {
    const user = userEvent.setup();
    const api = mockApi(routes());
    const view = renderWithProviders(<StaffFinanceEntriesPage />, { user: fixtures.masterAdmin, locale: "en", route: ROUTE });
    // renderWithProviders resets the router mock, which drops the implementation set in beforeEach; the search is set again here.
    routerState.replace.mockImplementation((href: string) => {
      routerState.search = href.split("?")[1] ?? "";
    });

    await screen.findByText("Showing 1 to 50 of 120");
    await user.click(screen.getByRole("button", { name: "Next" }));
    expect(routerState.replace).toHaveBeenCalledWith(expect.stringContaining("page=1"), { scroll: true });
    view.rerender(<StaffFinanceEntriesPage />);

    await waitFor(() => {
      const pages = api.calls.filter((call) => call.path === "/api/v1/finance/entries").map((call) => call.search);
      expect(pages.some((search) => search.includes("page=1"))).toBe(true);
    });
    expect(await screen.findByText("Showing 51 to 100 of 120")).toBeInTheDocument();
  });

  it("exports exactly the filtered rows: the dates, the sources, the search and the order", async () => {
    const user = userEvent.setup();
    mockApi(routes());
    const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => undefined);
    renderWithProviders(<StaffFinanceEntriesPage />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: `${ROUTE}&sources=OFFLINE_FEES&q=Rahim&sort=amount`,
    });

    await user.click(await screen.findByRole("button", { name: "Export CSV" }));

    await waitFor(() => expect(apiFile).toHaveBeenCalledTimes(1));
    const [path] = vi.mocked(apiFile).mock.calls[0];
    const query = new URL(path, "http://x").searchParams;
    expect(path.startsWith("/api/v1/finance/entries.csv?")).toBe(true);
    expect(query.get("from")).toBe("2026-10-01");
    expect(query.get("sources")).toBe("OFFLINE_FEES");
    expect(query.get("q")).toBe("Rahim");
    expect(query.get("sort")).toBe("amount");
    click.mockRestore();
  });
});
