import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { EntriesPage } from "@/components/staff/accounting/entries/entries-page";
import { dhakaToday } from "@/components/staff/offline/offline-model";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { ACCOUNTS, GROUPS, MILON_PAYEE } from "./fixtures";
import { MILON_ENTRIES, OFFLINE_ID, OFFLINE_TYPES, TUHIN_PAYEE, entryPage } from "./fixtures-entries";

/*
 * The entries list (accounting-plan 4.6): the filters live in the URL, so a filtered list can be shared; the totals of the rows shown;
 * Download CSV with the same filters.
 */

function routes(extra: Record<string, unknown> = {}) {
  return mockApi({
    "GET /api/v1/accounting/groups": GROUPS,
    "GET /api/v1/accounting/accounts": ACCOUNTS,
    "GET /api/v1/offline-batches": [],
    [`GET /api/v1/accounting/groups/${OFFLINE_ID}/types`]: OFFLINE_TYPES,
    [`GET /api/v1/accounting/groups/${OFFLINE_ID}/payees`]: [MILON_PAYEE, TUHIN_PAYEE],
    "GET /api/v1/accounting/entries": entryPage(MILON_ENTRIES, {
      total: 2,
      totals: { charged: "2500.00", paid: "500.00", spent: "0.00", income: "0.00" },
    }),
    ...extra,
  });
}

describe("entries list", () => {
  it("reads the filters from the URL and asks for the entries with them", async () => {
    const api = routes();
    renderWithProviders(<EntriesPage />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: `/staff/accounting/entries?groupId=${OFFLINE_ID}&kind=PAYMENT`,
    });

    // The row shows in the table and again as a card on a phone; either is the same link.
    expect((await screen.findAllByRole("link", { name: "#1043" }, { timeout: 4000 }))[0]).toHaveAttribute("href", "/staff/accounting/entries/e-pay");
    const call = api.calls.find((item) => item.path === "/api/v1/accounting/entries");
    expect(call?.search).toContain(`groupId=${OFFLINE_ID}`);
    expect(call?.search).toContain("kind=PAYMENT");
    expect(call?.search).toContain(`from=${dhakaToday().slice(0, 7)}-01`);
  });

  it("shows the totals of the rows, and the payment with a minus sign", async () => {
    routes();
    renderWithProviders(<EntriesPage />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/accounting/entries" });

    expect((await screen.findAllByText("৳2,500.00")).length).toBeGreaterThan(0);
    expect(screen.getAllByText("৳500.00").length).toBeGreaterThan(0);
    expect((await screen.findAllByText(/−.*500\.00/)).length).toBeGreaterThan(0);
  });

  it("downloads the CSV with the same filters", async () => {
    const user = userEvent.setup();
    const api = routes({ "GET /api/v1/accounting/entries.csv": "﻿No\r\n1043\r\n" });
    renderWithProviders(<EntriesPage />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: `/staff/accounting/entries?groupId=${OFFLINE_ID}`,
    });

    await user.click(await screen.findByRole("button", { name: "Download CSV" }));
    const csv = await waitForCall(api, "/api/v1/accounting/entries.csv");
    expect(csv.search).toContain(`groupId=${OFFLINE_ID}`);
  });

  it("says who may not see it, and asks for nothing", async () => {
    const api = routes();
    renderWithProviders(<EntriesPage />, { user: fixtures.systemAdmin, locale: "en", route: "/staff/accounting/entries" });

    expect(await screen.findByText("Accounting is for the Master Admin")).toBeInTheDocument();
    expect(api.calls.some((call) => call.path === "/api/v1/accounting/entries")).toBe(false);
    expect(within(document.body).queryByRole("table")).toBeNull();
  });
});

async function waitForCall(api: { calls: { path: string; search: string }[] }, path: string) {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    const found = api.calls.find((call) => call.path === path);
    if (found) {
      return found;
    }
    await new Promise((resolve) => setTimeout(resolve, 20));
  }
  throw new Error(`no call to ${path}`);
}
