import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { VoucherPage } from "@/components/staff/accounting/print/voucher";
import { ACC_ACCOUNT_IDS } from "@/lib/api/accounting/ids";
import type { EntryDetail } from "@/lib/api/accounting/types";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { GROUPS, entry } from "./fixtures";

/*
 * A voucher for an amount-only payment does not print a "Quantity × price" line with nothing in it. The line is for counted
 * entries only.
 */
function payment(overrides: Partial<EntryDetail> = {}): EntryDetail {
  return {
    ...entry({ id: "e-pay", no: 1044, kind: "PAYMENT", amount: "1000.00", reference: "QA-1", accountName: "bKash", accountId: ACC_ACCOUNT_IDS.BKASH, payeeName: "Milon" }),
    paired: null,
    edits: [],
    ...overrides,
  };
}

describe("voucher rows", () => {
  it("leaves out the quantity line for an amount-only payment", async () => {
    mockApi({
      "GET /api/v1/accounting/groups": GROUPS,
      "GET /api/v1/accounting/entries/e-pay": payment(),
    });
    renderWithProviders(<VoucherPage entryId="e-pay" />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/accounting/entries/e-pay/voucher" });

    expect(await screen.findByText("Payment voucher")).toBeInTheDocument();
    expect(screen.queryByText("Quantity × price")).not.toBeInTheDocument();
    expect(screen.queryByText("Type")).not.toBeInTheDocument();
  });
});
