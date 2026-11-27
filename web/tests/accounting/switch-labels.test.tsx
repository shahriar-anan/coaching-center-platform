import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { EntryForm } from "@/components/staff/accounting/entries/entry-form";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { ACCOUNTS, MILON_PAYEE } from "./fixtures";
import { ANSWER_SHEET, EMPTY_LIVE_EXAMS, LAST_PRICES_MILON, OFFLINE_TYPES, OFFLINE_GROUP } from "./fixtures-entries";

/*
 * The switches of the accounting pages carry their words on screen, not only for screen readers. The paid-now switch of a
 * charge is the one a person must find: without its words it is a bare toggle.
 */
describe("accounting switches", () => {
  it("writes the paid-now words beside its switch on a charge", async () => {
    const user = userEvent.setup();
    mockApi({
      "GET /api/v1/accounting/groups/a0000000-0000-4000-8000-000000000001/types": OFFLINE_TYPES,
      "GET /api/v1/accounting/accounts": ACCOUNTS,
      "GET /api/v1/offline-batches": [],
      "GET /api/v1/live-exams": EMPTY_LIVE_EXAMS,
      [`GET /api/v1/accounting/payees/${MILON_PAYEE.id}/last-prices`]: LAST_PRICES_MILON,
    });
    renderWithProviders(
      <EntryForm kind="CHARGE" group={OFFLINE_GROUP} payee={MILON_PAYEE} onSaved={() => undefined} onCancel={() => undefined} />,
      { user: fixtures.masterAdmin, locale: "en", route: "/staff/accounting" },
    );
    await screen.findByRole("option", { name: "Answer sheet evaluation" });
    await user.selectOptions(screen.getByLabelText(/^Type/), ANSWER_SHEET.id);

    expect(screen.getByText("Paid now")).toBeInTheDocument();
    expect(screen.getByRole("switch", { name: /Paid now/ })).toBeInTheDocument();
  });
});
