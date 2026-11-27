import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { EntryPage } from "@/components/staff/accounting/entries/entry-page";
import type { EntryDetail } from "@/lib/api/accounting/types";
import { ACC_ACCOUNT_IDS } from "@/lib/api/accounting/ids";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { ACCOUNTS, GROUPS, entry } from "./fixtures";
import { ANSWER_SHEET, OFFLINE_ID, OFFLINE_TYPES } from "./fixtures-entries";

/*
 * One entry (accounting-plan 4.5): Edit needs a reason and records the correction; Void needs a reason and, for a charge saved with its
 * payment, asks about the payment (ticked by default); the corrections are listed with before and after.
 */

const PAYMENT = entry({
  id: "e-pay",
  no: 1043,
  kind: "PAYMENT",
  amount: "500.00",
  date: "2026-11-20",
  accountId: ACC_ACCOUNT_IDS.BKASH,
  accountName: "bKash",
  pairedEntryId: "e-charge",
});

const CHARGE: EntryDetail = {
  ...entry({
    id: "e-charge",
    no: 1042,
    kind: "CHARGE",
    amount: "2000.00",
    date: "2026-11-10",
    accountId: null,
    accountName: null,
    typeId: ANSWER_SHEET.id,
    typeNameEn: ANSWER_SHEET.nameEn,
    typeNameBn: ANSWER_SHEET.nameBn,
    typeKind: "COUNTED",
    unitEn: "answer sheet",
    unitBn: "উত্তরপত্র",
    quantity: "40",
    unitPrice: "50.00",
    pairedEntryId: "e-pay",
    version: 3,
  }),
  paired: { ...PAYMENT, id: "e-pay" },
  edits: [
    {
      at: "2026-11-21T04:00:00Z",
      byName: "Master Admin",
      reason: "Forty sheets, not fifty",
      before: { amount: "2500.00" },
      after: { amount: "2000.00" },
    },
  ],
};

function routes(entryDetail: EntryDetail, extra: Record<string, unknown> = {}) {
  return mockApi({
    "GET /api/v1/accounting/groups": GROUPS,
    "GET /api/v1/accounting/accounts": ACCOUNTS,
    [`GET /api/v1/accounting/groups/${OFFLINE_ID}/types`]: OFFLINE_TYPES,
    [`GET /api/v1/accounting/entries/${entryDetail.id}`]: entryDetail,
    ...extra,
  });
}

function renderEntry(id: string) {
  return renderWithProviders(<EntryPage entryId={id} />, {
    user: fixtures.masterAdmin,
    locale: "en",
    route: `/staff/accounting/entries/${id}`,
  });
}

describe("entry page", () => {
  it("shows the fields, the paired entry and each correction with before and after", async () => {
    routes(CHARGE);
    renderEntry(CHARGE.id);

    expect(await screen.findByRole("heading", { name: "#1042 · Charge" })).toBeInTheDocument();
    expect(screen.getByText("40 answer sheets × ৳50.00")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /#1043/ })).toHaveAttribute("href", "/staff/accounting/entries/e-pay");
    expect(screen.getByText("Forty sheets, not fifty")).toBeInTheDocument();
    expect(screen.getByText("Amount: 2500.00 → 2000.00")).toBeInTheDocument();
  });

  it("asks for a reason before it corrects an entry, and sends nothing without one", async () => {
    const user = userEvent.setup();
    const api = routes(CHARGE, { [`PATCH /api/v1/accounting/entries/${CHARGE.id}`]: entry({ id: CHARGE.id }) });
    renderEntry(CHARGE.id);

    await user.click(await screen.findByRole("button", { name: "Edit" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));

    expect(await within(dialog).findByText("Say why you are changing this.")).toBeInTheDocument();
    expect(api.calls.some((call) => call.method === "PATCH")).toBe(false);
  });

  it("corrects an entry with its reason and the version the page read", async () => {
    const user = userEvent.setup();
    const api = routes(CHARGE, { [`PATCH /api/v1/accounting/entries/${CHARGE.id}`]: entry({ id: CHARGE.id }) });
    renderEntry(CHARGE.id);

    await user.click(await screen.findByRole("button", { name: "Edit" }));
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText(/^Reason/), "Only 38 sheets");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));

    await waitFor(() => expect(api.calls.some((call) => call.method === "PATCH")).toBe(true));
    expect(api.calls.find((call) => call.method === "PATCH")?.body).toMatchObject({
      reason: "Only 38 sheets",
      version: 3,
      quantity: "40",
      unitPrice: "50.00",
    });
  });

  it("voids a charge with its payment ticked by default, with a reason", async () => {
    const user = userEvent.setup();
    const api = routes(CHARGE, {
      [`POST /api/v1/accounting/entries/${CHARGE.id}/void`]: [entry({ id: CHARGE.id, status: "VOIDED" })],
    });
    renderEntry(CHARGE.id);

    await user.click(await screen.findByRole("button", { name: "Void" }));
    const dialog = await screen.findByRole("dialog");
    const alsoPaired = within(dialog).getByRole("switch", { name: /^Also void the payment made with it/ });
    expect(alsoPaired).toHaveAttribute("aria-checked", "true");
    await user.type(within(dialog).getByLabelText(/^Reason/), "Entered twice");
    await user.click(within(dialog).getByRole("button", { name: "Void" }));

    await waitFor(() => expect(api.calls.some((call) => call.path.endsWith("/void"))).toBe(true));
    expect(api.calls.find((call) => call.path.endsWith("/void"))?.body).toEqual({
      reason: "Entered twice",
      alsoVoidPaired: true,
    });
  });
});
