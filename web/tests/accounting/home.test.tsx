import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AccountingHome } from "@/components/staff/accounting/accounting-home";
import { shiftMonth } from "@/components/staff/accounting/accounting-model";
import type { Dashboard } from "@/lib/api/accounting/types";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { GROUPS, MILON_PAYEE } from "./fixtures";
import { MILON_ENTRIES } from "./fixtures-entries";

/*
 * The accounting home (accounting-plan 4.6, B15): the cards, a row per group, the payees owed the most, the latest entries, and the
 * twelve months as a chart with its figures in a table.
 */

const months = Array.from({ length: 12 }, (_, index) => ({
  month: shiftMonth("2026-11", index - 11),
  expense: "100.00",
  income: "50.00",
}));

const DASHBOARD: Dashboard = {
  today: "2026-11-27",
  owed: "2000.00",
  advance: "150.00",
  spentThisMonth: "1500.00",
  spentLastMonth: "900.00",
  incomeThisMonth: "300.00",
  groups: [
    { groupId: GROUPS[0].id, nameEn: "Offline instructors", nameBn: "অফলাইন শিক্ষক", mode: "PAYEES", direction: "EXPENSE", owed: "2000.00", thisMonth: "500.00" },
  ],
  topOwed: [MILON_PAYEE],
  latest: MILON_ENTRIES,
  months,
};

describe("accounting home", () => {
  it("shows what is owed, what went out and came in, and the groups", async () => {
    mockApi({
      "GET /api/v1/accounting/groups": GROUPS,
      "GET /api/v1/accounting/dashboard": DASHBOARD,
    });
    renderWithProviders(<AccountingHome />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/accounting" });

    expect(await screen.findByText("Owed now")).toBeInTheDocument();
    expect(screen.getByText("Advance ৳150.00")).toBeInTheDocument();
    expect(screen.getByText("Spent this month")).toBeInTheDocument();
    expect(screen.getByText("Last month ৳900.00")).toBeInTheDocument();
    expect(screen.getByText("৳1,500.00")).toBeInTheDocument();
    expect(screen.getByText("৳300.00")).toBeInTheDocument();
    // The group row and the sub-navigation both link to the group.
    const hrefs = screen.getAllByRole("link", { name: /Offline instructors/ }).map((link) => link.getAttribute("href"));
    expect(hrefs).toContain(`/staff/accounting/groups/${GROUPS[0].id}`);
  });

  it("lists the payees owed the most and the latest entries, each linked", async () => {
    mockApi({
      "GET /api/v1/accounting/groups": GROUPS,
      "GET /api/v1/accounting/dashboard": DASHBOARD,
    });
    renderWithProviders(<AccountingHome />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/accounting" });

    await screen.findAllByRole("link", { name: /Milon/ });
    const hrefs = screen.getAllByRole("link").map((link) => link.getAttribute("href"));
    expect(hrefs).toContain(`/staff/accounting/payees/${MILON_PAYEE.id}`);
    expect(hrefs).toContain("/staff/accounting/entries/e-pay");
  });

  it("charts twelve months, with the same figures in a table", async () => {
    mockApi({
      "GET /api/v1/accounting/groups": GROUPS,
      "GET /api/v1/accounting/dashboard": DASHBOARD,
    });
    renderWithProviders(<AccountingHome />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/accounting" });

    const table = await screen.findByRole("table");
    // One header row and twelve months.
    expect(within(table).getAllByRole("row")).toHaveLength(13);
    expect(within(table).getByText("November 2026")).toBeInTheDocument();
  });
});
