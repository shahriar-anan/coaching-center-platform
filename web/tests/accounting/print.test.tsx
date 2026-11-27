import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { VoucherPage } from "@/components/staff/accounting/print/voucher";
import { StatementPage } from "@/components/staff/accounting/print/statement";
import { ACC_ACCOUNT_IDS } from "@/lib/api/accounting/ids";
import type { EntryDetail, Statement } from "@/lib/api/accounting/types";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { GROUPS, entry } from "./fixtures";
import { MILON_ENTRIES, payeeOf } from "./fixtures-entries";
import { MILON_PAYEE } from "./fixtures";

/*
 * The print pages (accounting-plan 4.6, B10): a voucher for one payment (A5 sheet, the amount in figures and in words, VOID across a
 * voided one), and a statement for a payee over a range (the running balance and the totals).
 */

function detail(overrides: Partial<EntryDetail> = {}): EntryDetail {
  return { ...entry({ id: "e-pay", no: 1043, kind: "PAYMENT", amount: "12500.00", reference: "BK123", accountName: "bKash", accountId: ACC_ACCOUNT_IDS.BKASH, payeeName: "Milon" }), paired: null, edits: [], ...overrides };
}

describe("voucher", () => {
  it("prints the payment: number, who, group, account and reference, and the amount in figures and words", async () => {
    mockApi({
      "GET /api/v1/accounting/groups": GROUPS,
      "GET /api/v1/accounting/entries/e-pay": detail(),
    });
    renderWithProviders(<VoucherPage entryId="e-pay" />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/accounting/entries/e-pay/voucher" });

    expect(await screen.findByText("Payment voucher")).toBeInTheDocument();
    expect(screen.getByText("#1043")).toBeInTheDocument();
    expect(screen.getByText("Milon")).toBeInTheDocument();
    expect(screen.getByText("bKash · BK123")).toBeInTheDocument();
    expect(screen.getAllByText("৳12,500.00").length).toBeGreaterThan(0);
    expect(screen.getByText("Twelve thousand five hundred taka only")).toBeInTheDocument();
    expect(screen.getByText("Received by")).toBeInTheDocument();
    expect(screen.getByText("Paid by")).toBeInTheDocument();
    expect(screen.queryByText("VOID")).not.toBeInTheDocument();
  });

  it("marks a voided voucher with VOID across it", async () => {
    mockApi({
      "GET /api/v1/accounting/groups": GROUPS,
      "GET /api/v1/accounting/entries/e-pay": detail({ status: "VOIDED", voidReason: "Duplicate", voidedAt: "2026-11-21T04:00:00Z" }),
    });
    renderWithProviders(<VoucherPage entryId="e-pay" />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/accounting/entries/e-pay/voucher" });

    expect(await screen.findByText("VOID")).toBeInTheDocument();
  });

  it("says that a charge has no voucher", async () => {
    mockApi({
      "GET /api/v1/accounting/groups": GROUPS,
      "GET /api/v1/accounting/entries/e-charge": detail({ id: "e-charge", kind: "CHARGE" }),
    });
    renderWithProviders(<VoucherPage entryId="e-charge" />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/accounting/entries/e-charge/voucher" });

    expect(await screen.findByText("A charge has no voucher. Vouchers are for payments, spending and income.")).toBeInTheDocument();
  });
});

const STATEMENT: Statement = {
  payee: payeeOf(),
  from: "2026-11-01",
  to: "2026-11-30",
  opening: "0.00",
  charged: "2500.00",
  paid: "500.00",
  closing: "2000.00",
  lines: [
    { ...MILON_ENTRIES[1], balanceAfter: "2500.00" },
    { ...MILON_ENTRIES[0], balanceAfter: "2000.00" },
  ],
};

describe("statement", () => {
  it("prints the lines with the running balance, the totals and the closing balance", async () => {
    const api = mockApi({
      "GET /api/v1/accounting/groups": GROUPS,
      [`GET /api/v1/accounting/payees/${MILON_PAYEE.id}`]: payeeOf(),
      [`GET /api/v1/accounting/payees/${MILON_PAYEE.id}/statement`]: STATEMENT,
    });
    renderWithProviders(<StatementPage payeeId={MILON_PAYEE.id} />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: `/staff/accounting/payees/${MILON_PAYEE.id}/statement?from=2026-11-01&to=2026-11-30`,
    });

    expect(await screen.findByText("Statement")).toBeInTheDocument();
    const rows = within(await screen.findByRole("table")).getAllByRole("row");
    // Header, opening, two lines, totals.
    expect(rows).toHaveLength(5);
    // The charge, and the balance after it, are both 2,500.
    expect(within(rows[2]).getAllByText("৳2,500.00")).toHaveLength(2);
    expect(within(rows[3]).getByText("৳500.00")).toBeInTheDocument();
    expect(within(rows[3]).getByText("৳2,000.00")).toBeInTheDocument();
    expect(screen.getByText("Closing balance: ৳2,000.00")).toBeInTheDocument();
    const call = api.calls.find((item) => item.path.endsWith("/statement"));
    expect(call?.search).toContain("from=2026-11-01");
    expect(call?.search).toContain("to=2026-11-30");
  });
});
