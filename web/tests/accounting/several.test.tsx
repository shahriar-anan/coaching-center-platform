import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, beforeEach } from "vitest";
import { SeveralSheet } from "@/components/staff/accounting/entries/several";
import { ErrorBody, fixtures, mockApi, renderWithProviders } from "../helpers";
import { MILON_PAYEE, entry } from "./fixtures";
import { ANSWER_SHEET, EMPTY_LIVE_EXAMS, LAST_PRICES_MILON, MONTHLY, OFFLINE_GROUP, OFFLINE_TYPES, TUHIN_PAYEE } from "./fixtures-entries";

/*
 * Add for several payees (B6, accounting-plan 4.4): one type, date and note for all; a line for each ticked person, starting from their
 * last price; the total; saved together.
 */

beforeEach(() => {
  window.localStorage.clear();
});

describe("add for several payees", () => {
  it("prices each ticked person from their last price, totals the lines and saves them together", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      [`GET /api/v1/accounting/groups/${OFFLINE_GROUP.id}/types`]: OFFLINE_TYPES,
      [`GET /api/v1/accounting/groups/${OFFLINE_GROUP.id}/payees`]: [MILON_PAYEE, TUHIN_PAYEE],
      [`GET /api/v1/accounting/payees/${MILON_PAYEE.id}/last-prices`]: LAST_PRICES_MILON,
      [`GET /api/v1/accounting/payees/${TUHIN_PAYEE.id}/last-prices`]: [],
      "GET /api/v1/accounting/accounts": [],
      "GET /api/v1/live-exams": EMPTY_LIVE_EXAMS,
      "POST /api/v1/accounting/entries/many": [entry({ kind: "CHARGE" })],
    });
    renderWithProviders(<SeveralSheet group={OFFLINE_GROUP} onClose={() => undefined} />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: "/staff/accounting/groups/x",
    });

    await screen.findByRole("option", { name: "Answer sheet evaluation" });
    await user.selectOptions(screen.getByLabelText(/^Type/), ANSWER_SHEET.id);

    await user.click(screen.getByRole("checkbox", { name: "Milon" }));
    await user.type(await screen.findByLabelText(/^Quantity/), "40");
    await waitFor(() => expect(screen.getByLabelText(/^Unit price/)).toHaveValue("50.00"));

    await user.click(screen.getByRole("checkbox", { name: "Tuhin Hossain" }));
    const quantities = screen.getAllByLabelText(/^Quantity/);
    await user.type(quantities[1], "10");
    await user.type(screen.getAllByLabelText(/^Unit price/)[1], "60");

    expect(screen.getByText(/2 selected/)).toBeInTheDocument();
    expect(screen.getByText(/৳2,600\.00/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Save" }));
    await waitFor(() => expect(api.calls.some((call) => call.method === "POST")).toBe(true));
    const body = api.calls.find((call) => call.method === "POST")?.body as { entries: Record<string, unknown>[] };
    expect(body.entries).toHaveLength(2);
    expect(body.entries[0]).toMatchObject({ payeeId: MILON_PAYEE.id, typeId: ANSWER_SHEET.id, quantity: "40", unitPrice: "50.00", amount: null });
    expect(body.entries[1]).toMatchObject({ payeeId: TUHIN_PAYEE.id, quantity: "10", unitPrice: "60.00", amount: null });
  });

  it("starts a plain type's line from each person's last amount, and a new type starts the lines again", async () => {
    const user = userEvent.setup();
    mockApi({
      [`GET /api/v1/accounting/groups/${OFFLINE_GROUP.id}/types`]: OFFLINE_TYPES,
      [`GET /api/v1/accounting/groups/${OFFLINE_GROUP.id}/payees`]: [MILON_PAYEE],
      // The API leaves the empty quantity and price out.
      [`GET /api/v1/accounting/payees/${MILON_PAYEE.id}/last-prices`]: [
        ...LAST_PRICES_MILON,
        { typeId: MONTHLY.id, amount: "15000.00", date: "2026-10-31" },
      ],
      "POST /api/v1/accounting/entries/many": [entry({ kind: "CHARGE" })],
    });
    renderWithProviders(<SeveralSheet group={OFFLINE_GROUP} onClose={() => undefined} />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: "/staff/accounting/groups/x",
    });

    await screen.findByRole("option", { name: "Monthly salary" });
    await user.selectOptions(screen.getByLabelText(/^Type/), MONTHLY.id);
    await user.click(screen.getByRole("checkbox", { name: "Milon" }));
    await waitFor(() => expect(screen.getByLabelText(/^Amount/)).toHaveValue("15000.00"));

    // Another type: the line starts again from that type's last price.
    await user.selectOptions(screen.getByLabelText(/^Type/), ANSWER_SHEET.id);
    await waitFor(() => expect(screen.getByLabelText(/^Unit price/)).toHaveValue("50.00"));
  });

  it("names the person when the API refuses one line of the save", async () => {
    const user = userEvent.setup();
    mockApi({
      [`GET /api/v1/accounting/groups/${OFFLINE_GROUP.id}/types`]: OFFLINE_TYPES,
      [`GET /api/v1/accounting/groups/${OFFLINE_GROUP.id}/payees`]: [MILON_PAYEE, TUHIN_PAYEE],
      [`GET /api/v1/accounting/payees/${MILON_PAYEE.id}/last-prices`]: LAST_PRICES_MILON,
      [`GET /api/v1/accounting/payees/${TUHIN_PAYEE.id}/last-prices`]: [],
      "POST /api/v1/accounting/entries/many": () =>
        new ErrorBody(409, {
          code: "ACC_REFUSED",
          message: "accounting.errors.payee_wrong_group",
          details: { line: 1 },
        } as unknown as { code: string; message: string }),
    });
    renderWithProviders(<SeveralSheet group={OFFLINE_GROUP} onClose={() => undefined} />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: "/staff/accounting/groups/x",
    });

    await screen.findByRole("option", { name: "Answer sheet evaluation" });
    await user.selectOptions(screen.getByLabelText(/^Type/), ANSWER_SHEET.id);
    await user.click(screen.getByRole("checkbox", { name: "Milon" }));
    await user.type(await screen.findByLabelText(/^Quantity/), "40");
    await waitFor(() => expect(screen.getByLabelText(/^Unit price/)).toHaveValue("50.00"));
    await user.click(screen.getByRole("checkbox", { name: "Tuhin Hossain" }));
    await user.type(screen.getAllByLabelText(/^Quantity/)[1], "10");
    await user.type(screen.getAllByLabelText(/^Unit price/)[1], "60");
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText(/^Tuhin Hossain: /)).toBeInTheDocument();
  });

  it("saves nothing when nobody is ticked", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      [`GET /api/v1/accounting/groups/${OFFLINE_GROUP.id}/types`]: OFFLINE_TYPES,
      [`GET /api/v1/accounting/groups/${OFFLINE_GROUP.id}/payees`]: [MILON_PAYEE],
      "POST /api/v1/accounting/entries/many": [],
    });
    renderWithProviders(<SeveralSheet group={OFFLINE_GROUP} onClose={() => undefined} />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: "/staff/accounting/groups/x",
    });

    await screen.findByRole("option", { name: "Answer sheet evaluation" });
    await user.selectOptions(screen.getByLabelText(/^Type/), ANSWER_SHEET.id);
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText("Tick at least one person.")).toBeInTheDocument();
    expect(api.calls.some((call) => call.method === "POST")).toBe(false);
  });
});
