import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { PayeePage, balanceEffect, runningBalances } from "@/components/staff/accounting/payee-page";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { GROUPS } from "./fixtures";
import { MILON_ENTRIES, entryPage, payeeOf } from "./fixtures-entries";
import { MILON_PAYEE } from "./fixtures";

/*
 * One payee (accounting-plan 4.6): the balance, the entries with a running balance (newest first), the buttons, the details and the
 * archive warning for a balance that is not zero.
 */

function renderPayee() {
  return renderWithProviders(<PayeePage payeeId={MILON_PAYEE.id} />, {
    user: fixtures.masterAdmin,
    locale: "en",
    route: `/staff/accounting/payees/${MILON_PAYEE.id}`,
  });
}

describe("payee page", () => {
  it("shows the balance and the entries, newest first, with the balance after each", async () => {
    mockApi({
      "GET /api/v1/accounting/groups": GROUPS,
      [`GET /api/v1/accounting/payees/${MILON_PAYEE.id}`]: payeeOf(),
      "GET /api/v1/accounting/entries": entryPage(MILON_ENTRIES, { total: 2 }),
    });
    renderPayee();

    expect(await screen.findByRole("heading", { name: "Milon" })).toBeInTheDocument();
    expect(screen.getAllByText("Owed ৳2,000.00").length).toBeGreaterThan(0);
    // The payment takes 500 away: after it 2,000; before it, after the charge, 2,500.
    expect((await screen.findAllByText(/−.*500\.00/)).length).toBeGreaterThan(0);
    expect((await screen.findAllByText("৳2,000.00")).length).toBeGreaterThan(0);
    expect((await screen.findAllByText("৳2,500.00")).length).toBeGreaterThan(0);
    // The charge reads as a counted line.
    expect((await screen.findAllByText("50 answer sheets × ৳50.00")).length).toBeGreaterThan(0);
  });

  it("shows the details, the note and the link to the staff pay of a linked user", async () => {
    const user = userEvent.setup();
    mockApi({
      "GET /api/v1/accounting/groups": GROUPS,
      [`GET /api/v1/accounting/payees/${MILON_PAYEE.id}`]: payeeOf({ userId: "user-7", userName: "Nasrin", staffPayBalance: "800.00" }),
      "GET /api/v1/accounting/entries": entryPage([]),
    });
    renderPayee();

    await user.click(await screen.findByRole("tab", { name: "Details" }));
    expect(screen.getByText("bKash 017…, pays on Fridays")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Also on Staff pay: balance ৳800.00" })).toHaveAttribute("href", "/staff/pay/user-7");
  });

  it("asks before archiving a payee who is still owed, with the amount", async () => {
    const user = userEvent.setup();
    mockApi({
      "GET /api/v1/accounting/groups": GROUPS,
      [`GET /api/v1/accounting/payees/${MILON_PAYEE.id}`]: payeeOf(),
      "GET /api/v1/accounting/entries": entryPage(MILON_ENTRIES, { total: 2 }),
      [`POST /api/v1/accounting/payees/${MILON_PAYEE.id}/archive`]: { ...payeeOf(), archived: true },
    });
    renderPayee();

    await user.click(await screen.findByRole("button", { name: "Archive" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Milon is still owed ৳2,000.00. Archive anyway?")).toBeInTheDocument();
  });
});

describe("running balance helpers", () => {
  it("counts a charge up and a payment down, and a voided entry not at all", () => {
    expect(balanceEffect({ kind: "CHARGE", status: "ACTIVE", amount: "2500.00" })).toBe(2500);
    expect(balanceEffect({ kind: "PAYMENT", status: "ACTIVE", amount: "500.00" })).toBe(-500);
    expect(balanceEffect({ kind: "CHARGE", status: "VOIDED", amount: "2500.00" })).toBe(0);
    expect(balanceEffect({ kind: "CHARGE", status: "ACTIVE", amount: "-500.00" })).toBe(-500);
  });

  it("works back from the balance now, newest first", () => {
    expect(runningBalances("2000.00", MILON_ENTRIES)).toEqual(["2000.00", "2500.00"]);
  });
});
