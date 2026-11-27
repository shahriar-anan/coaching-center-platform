import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { GroupPage } from "@/components/staff/accounting/group-page";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { ACCOUNTS, GROUPS, LECTURE, MILON_PAYEE } from "./fixtures";
import { OFFLINE_ID, PERSONAL_GROUP, TUHIN_PAYEE, entryPage } from "./fixtures-entries";
import { entry } from "./fixtures";

/*
 * The group page (accounting-plan 4.6): a Payees group lists its people with their balances and adds one; a Log group lists its entries
 * with the total for the range.
 */

function renderGroup(groupId: string) {
  return renderWithProviders(<GroupPage groupId={groupId} />, {
    user: fixtures.masterAdmin,
    locale: "en",
    route: `/staff/accounting/groups/${groupId}`,
  });
}

describe("group page: a Payees group", () => {
  it("lists the people with their balances, each linked to their page", async () => {
    mockApi({
      "GET /api/v1/accounting/groups": GROUPS,
      [`GET /api/v1/accounting/groups/${OFFLINE_ID}/payees`]: [MILON_PAYEE, TUHIN_PAYEE],
    });
    renderGroup(OFFLINE_ID);

    const milon = (await screen.findAllByRole("link", { name: "Milon" }))[0];
    expect(milon).toHaveAttribute("href", `/staff/accounting/payees/${MILON_PAYEE.id}`);
    expect(screen.getAllByText("Owed ৳2,000.00").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Settled").length).toBeGreaterThan(0);
  });

  it("adds a person with no phone, note or other spellings, and posts it once", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      "GET /api/v1/accounting/groups": GROUPS,
      [`GET /api/v1/accounting/groups/${OFFLINE_ID}/payees`]: [MILON_PAYEE],
      [`POST /api/v1/accounting/groups/${OFFLINE_ID}/payees`]: { ...MILON_PAYEE, id: "p-rahim", name: "Rahim" },
    });
    renderGroup(OFFLINE_ID);

    await user.click(await screen.findByRole("button", { name: "Add person" }));
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText(/^Name/), "Rahim");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));

    await waitFor(() => expect(api.calls.filter((call) => call.method === "POST")).toHaveLength(1));
    expect(api.calls.find((call) => call.method === "POST")?.body).toEqual({
      name: "Rahim",
      phone: null,
      note: null,
      aliases: [],
      userId: null,
    });
  });

  it("refuses an empty name before any write", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      "GET /api/v1/accounting/groups": GROUPS,
      [`GET /api/v1/accounting/groups/${OFFLINE_ID}/payees`]: [],
    });
    renderGroup(OFFLINE_ID);

    await user.click(await screen.findByRole("button", { name: "Add person" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));

    expect(within(dialog).getByLabelText(/^Name/)).toHaveAttribute("aria-invalid", "true");
    expect(api.calls.some((call) => call.method === "POST")).toBe(false);
  });
});

describe("group page: a Log group", () => {
  it("lists the entries of the range with the total of its spending", async () => {
    mockApi({
      "GET /api/v1/accounting/groups": GROUPS,
      [`GET /api/v1/accounting/groups/${PERSONAL_GROUP.id}/types`]: [],
      "GET /api/v1/accounting/accounts": ACCOUNTS,
      "GET /api/v1/accounting/entries": entryPage(
        [
          entry({
            id: "e-food",
            kind: "SPEND",
            groupId: PERSONAL_GROUP.id,
            groupNameEn: "Personal",
            payeeId: null,
            payeeName: null,
            typeId: LECTURE.id,
            typeNameEn: "Food",
            typeNameBn: "খাবার",
            typeKind: "AMOUNT",
            amount: "1200.00",
            counterparty: "Agora",
            accountId: null,
            accountName: null,
          }),
        ],
        { totals: { charged: "0.00", paid: "0.00", spent: "1200.00", income: "0.00" } },
      ),
    });
    renderGroup(PERSONAL_GROUP.id);

    expect((await screen.findAllByText("Agora")).length).toBeGreaterThan(0);
    // The total and the row both show it.
    expect(screen.getAllByText("৳1,200.00").length).toBeGreaterThanOrEqual(2);
    expect(screen.getByRole("button", { name: "Add spending" })).toBeInTheDocument();
  });
});
