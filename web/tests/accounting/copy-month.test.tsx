import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { CopyMonthSheet, draftInput } from "@/components/staff/accounting/entries/copy-month";
import type { CopyDraft } from "@/lib/api/accounting/types";
import { dhakaToday } from "@/components/staff/offline/offline-model";
import { shiftMonth, monthOf } from "@/components/staff/accounting/accounting-model";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { MILON_PAYEE, entry } from "./fixtures";
import { ANSWER_SHEET, OFFLINE_GROUP, OFFLINE_TYPES, OTHER_AMOUNT, TUHIN_PAYEE } from "./fixtures-entries";

/*
 * Copy last month (B7, accounting-plan 4.4): last month's charges, ticked; a draft that already looks added this month starts unticked;
 * the copy saves through the several call without the copy-only fields.
 */

beforeEach(() => {
  window.localStorage.clear();
});

const today = dhakaToday();

function drafts(): CopyDraft[] {
  return [
    {
      kind: "CHARGE" as const,
      groupId: OFFLINE_GROUP.id,
      payeeId: MILON_PAYEE.id,
      typeId: ANSWER_SHEET.id,
      date: today,
      period: null,
      quantity: "40",
      unitPrice: "50.00",
      amount: "2000.00",
      sourceEntryId: "e-old-1",
      alreadyThisMonth: false,
    },
    {
      kind: "CHARGE" as const,
      groupId: OFFLINE_GROUP.id,
      payeeId: TUHIN_PAYEE.id,
      typeId: ANSWER_SHEET.id,
      date: today,
      period: null,
      quantity: "10",
      unitPrice: "60.00",
      amount: "600.00",
      sourceEntryId: "e-old-2",
      alreadyThisMonth: true,
    },
  ];
}

describe("copy last month", () => {
  it("asks for last month, ticks what is new and leaves out what already looks added", async () => {
    const api = mockApi({
      [`GET /api/v1/accounting/groups/${OFFLINE_GROUP.id}/copy-source`]: drafts(),
      [`GET /api/v1/accounting/groups/${OFFLINE_GROUP.id}/types`]: OFFLINE_TYPES,
      [`GET /api/v1/accounting/groups/${OFFLINE_GROUP.id}/payees`]: [MILON_PAYEE, TUHIN_PAYEE],
      "POST /api/v1/accounting/entries/many": [entry({ kind: "CHARGE" })],
    });
    renderWithProviders(<CopyMonthSheet group={OFFLINE_GROUP} onClose={() => undefined} />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: "/staff/accounting/groups/x",
    });

    expect(await screen.findByText("Looks already added this month")).toBeInTheDocument();
    const switches = await screen.findAllByRole("switch");
    expect(switches[0]).toHaveAttribute("aria-checked", "true");
    expect(switches[1]).toHaveAttribute("aria-checked", "false");

    const copy = api.calls.find((call) => call.path.endsWith("/copy-source"));
    expect(copy?.search).toContain(`month=${shiftMonth(monthOf(today), -1)}`);
  });

  it("saves the ticked drafts through the several call, without the copy-only fields", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      [`GET /api/v1/accounting/groups/${OFFLINE_GROUP.id}/copy-source`]: drafts(),
      [`GET /api/v1/accounting/groups/${OFFLINE_GROUP.id}/types`]: OFFLINE_TYPES,
      [`GET /api/v1/accounting/groups/${OFFLINE_GROUP.id}/payees`]: [MILON_PAYEE, TUHIN_PAYEE],
      "POST /api/v1/accounting/entries/many": [entry({ kind: "CHARGE" })],
    });
    renderWithProviders(<CopyMonthSheet group={OFFLINE_GROUP} onClose={() => undefined} />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: "/staff/accounting/groups/x",
    });

    await screen.findByText("Looks already added this month");
    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(api.calls.some((call) => call.method === "POST")).toBe(true));
    const body = api.calls.find((call) => call.method === "POST")?.body as { entries: Record<string, unknown>[] };
    expect(body.entries).toHaveLength(1);
    expect(body.entries[0]).toMatchObject({ payeeId: MILON_PAYEE.id, quantity: "40", unitPrice: "50.00", amount: null });
    expect(body.entries[0]).not.toHaveProperty("sourceEntryId");
    expect(body.entries[0]).not.toHaveProperty("alreadyThisMonth");
  });
});

describe("copy last month: what counts as counted", () => {
  it("takes a plain type's draft as an amount even when it carries a quantity, and a counted one as quantity times price", async () => {
    const user = userEvent.setup();
    const plain: CopyDraft = {
      kind: "CHARGE",
      groupId: OFFLINE_GROUP.id,
      payeeId: MILON_PAYEE.id,
      typeId: OTHER_AMOUNT.id,
      date: today,
      period: null,
      quantity: "2",
      unitPrice: null,
      amount: "700.00",
      description: "Other work",
      sourceEntryId: "e-old-3",
      alreadyThisMonth: false,
    };
    const api = mockApi({
      [`GET /api/v1/accounting/groups/${OFFLINE_GROUP.id}/copy-source`]: [drafts()[0], plain],
      [`GET /api/v1/accounting/groups/${OFFLINE_GROUP.id}/types`]: OFFLINE_TYPES,
      [`GET /api/v1/accounting/groups/${OFFLINE_GROUP.id}/payees`]: [MILON_PAYEE, TUHIN_PAYEE],
      "POST /api/v1/accounting/entries/many": [entry({ kind: "CHARGE" })],
    });
    renderWithProviders(<CopyMonthSheet group={OFFLINE_GROUP} onClose={() => undefined} />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: "/staff/accounting/groups/x",
    });

    await screen.findAllByRole("switch");
    await waitFor(() => expect(screen.getAllByLabelText(/^Amount/)).toHaveLength(1));
    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(api.calls.some((call) => call.method === "POST")).toBe(true));
    const body = api.calls.find((call) => call.method === "POST")?.body as { entries: Record<string, unknown>[] };
    expect(body.entries[0]).toMatchObject({ quantity: "40", unitPrice: "50.00", amount: null });
    expect(body.entries[1]).toMatchObject({ typeId: OTHER_AMOUNT.id, amount: "700.00", unitPrice: null });
  });
});

describe("copy draft helper", () => {
  it("strips the two copy-only fields and keeps the rest", () => {
    const input = draftInput(drafts()[0] as CopyDraft);
    expect(input).not.toHaveProperty("sourceEntryId");
    expect(input).not.toHaveProperty("alreadyThisMonth");
    expect(input).toMatchObject({ kind: "CHARGE", payeeId: MILON_PAYEE.id, quantity: "40" });
  });
});
