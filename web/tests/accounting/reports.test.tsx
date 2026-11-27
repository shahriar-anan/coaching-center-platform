import { fireEvent, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ReportsPage, monthsBetween } from "@/components/staff/accounting/reports/reports-page";
import { csvCell, toCsv } from "@/components/staff/accounting/csv";
import { shiftMonth, monthOf } from "@/components/staff/accounting/accounting-model";
import { dhakaToday } from "@/components/staff/offline/offline-model";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { GROUPS, MILON_PAYEE } from "./fixtures";
import { ACC_GROUP_IDS } from "@/lib/api/accounting/ids";

/*
 * The reports (accounting-plan 4.6): owed now, spending and income by month (at most 36 months), and a group summary; each prints and
 * downloads CSV built in the browser with a byte order mark.
 */

const thisMonth = monthOf(dhakaToday());
const lastTwelve = Array.from({ length: 12 }, (_, index) => shiftMonth(thisMonth, index - 11));

describe("reports", () => {
  it("shows owed now: the total, and each group with its people", async () => {
    mockApi({
      "GET /api/v1/accounting/groups": GROUPS,
      "GET /api/v1/accounting/reports/owed": {
        total: "2000.00",
        advance: "0.00",
        groups: [
          {
            groupId: ACC_GROUP_IDS.OFFLINE_INSTRUCTORS,
            nameEn: "Offline instructors",
            nameBn: "অফলাইন শিক্ষক",
            owed: "2000.00",
            advance: "0.00",
            payees: [MILON_PAYEE],
          },
        ],
      },
    });
    renderWithProviders(<ReportsPage />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/accounting/reports" });

    expect((await screen.findAllByText("৳2,000.00")).length).toBeGreaterThan(0);
    expect(screen.getAllByRole("link", { name: "Milon" })[0]).toHaveAttribute("href", `/staff/accounting/payees/${MILON_PAYEE.id}`);
  });

  it("shows spending by month, a column for each month, and asks for the months on the URL range", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      "GET /api/v1/accounting/groups": GROUPS,
      "GET /api/v1/accounting/reports/owed": { total: "0.00", advance: "0.00", groups: [] },
      "GET /api/v1/accounting/reports/monthly": {
        months: lastTwelve,
        rows: [
          {
            groupId: ACC_GROUP_IDS.OFFLINE_INSTRUCTORS,
            nameEn: "Offline instructors",
            nameBn: "অফলাইন শিক্ষক",
            direction: "EXPENSE",
            amounts: lastTwelve.map(() => "100.00"),
            total: "1200.00",
          },
        ],
        expense: lastTwelve.map(() => "100.00"),
        income: lastTwelve.map(() => "0.00"),
      },
    });
    renderWithProviders(<ReportsPage />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/accounting/reports" });

    await user.click(await screen.findByRole("tab", { name: "By month" }));
    expect(await screen.findByRole("columnheader", { name: "November 2026" })).toBeInTheDocument();
    const call = api.calls.find((item) => item.path === "/api/v1/accounting/reports/monthly");
    expect(call?.search).toContain(`from=${lastTwelve[0]}`);
    expect(call?.search).toContain(`to=${thisMonth}`);
  });

  it("refuses a range of more than 36 months before it asks", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      "GET /api/v1/accounting/groups": GROUPS,
      "GET /api/v1/accounting/reports/owed": { total: "0.00", advance: "0.00", groups: [] },
      "GET /api/v1/accounting/reports/monthly": { months: [], rows: [], expense: [], income: [] },
    });
    renderWithProviders(<ReportsPage />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/accounting/reports" });

    await user.click(await screen.findByRole("tab", { name: "By month" }));
    const from = await screen.findByLabelText(/^From month/);
    fireEvent.change(from, { target: { value: "2020-01" } });

    expect((await screen.findAllByText(/A range covers at most 36 months/)).length).toBeGreaterThan(0);
    expect(api.calls.some((item) => item.path === "/api/v1/accounting/reports/monthly" && item.search.includes("from=2020-01"))).toBe(false);
  });

  it("asks for a group before the summary", async () => {
    const user = userEvent.setup();
    mockApi({
      "GET /api/v1/accounting/groups": GROUPS,
      "GET /api/v1/accounting/reports/owed": { total: "0.00", advance: "0.00", groups: [] },
    });
    renderWithProviders(<ReportsPage />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/accounting/reports" });

    await user.click(await screen.findByRole("tab", { name: "Group summary" }));
    expect(await screen.findByText("Choose a group and a date range to see its summary.")).toBeInTheDocument();
  });
});

describe("report helpers", () => {
  it("counts the months of a range, both ends included", () => {
    expect(monthsBetween("2026-01", "2026-12")).toBe(12);
    expect(monthsBetween("2026-11", "2026-11")).toBe(1);
    expect(monthsBetween("2026-12", "2026-01")).toBe(-10);
    expect(monthsBetween("", "2026-01")).toBe(0);
  });

  it("quotes a cell with a comma, a quote or a line break, and doubles the quotes", () => {
    expect(csvCell("plain")).toBe("plain");
    expect(csvCell("a, b")).toBe('"a, b"');
    expect(csvCell('say "hi"')).toBe('"say ""hi"""');
    expect(csvCell(null)).toBe("");
    expect(csvCell(40)).toBe("40");
  });

  it("starts the file with a byte order mark and ends lines with CRLF", () => {
    const text = toCsv([
      ["Group", "Amount"],
      ["অফলাইন শিক্ষক", "2000.00"],
    ]);
    expect(text.charCodeAt(0)).toBe(0xfeff);
    expect(text).toBe("﻿Group,Amount\r\nঅফলাইন শিক্ষক,2000.00\r\n");
  });
});
