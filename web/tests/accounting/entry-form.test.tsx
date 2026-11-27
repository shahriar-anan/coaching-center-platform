import { fireEvent, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { EntryForm } from "@/components/staff/accounting/entries/entry-form";
import { dhakaToday } from "@/components/staff/offline/offline-model";
import { ACC_ACCOUNT_IDS } from "@/lib/api/accounting/ids";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { ACCOUNTS, MILON_PAYEE, entry } from "./fixtures";
import { ANSWER_SHEET, EMPTY_LIVE_EXAMS, LAST_PRICES_MILON, MONTHLY, OFFLINE_TYPES, OTHER_AMOUNT, OFFLINE_GROUP } from "./fixtures-entries";

/*
 * The entry form (accounting-plan 4.4, W2): one form for the four kinds. Counted types show the line live and start from the last price;
 * a charge can be paid now; a type that needs a description or a month says so; a future date is refused.
 */

function routes(extra: Record<string, unknown> = {}) {
  return mockApi({
    "GET /api/v1/accounting/groups/a0000000-0000-4000-8000-000000000001/types": OFFLINE_TYPES,
    "GET /api/v1/accounting/accounts": ACCOUNTS,
    "GET /api/v1/offline-batches": [],
    "GET /api/v1/live-exams": EMPTY_LIVE_EXAMS,
    [`GET /api/v1/accounting/payees/${MILON_PAYEE.id}/last-prices`]: LAST_PRICES_MILON,
    "POST /api/v1/accounting/entries": [entry({ id: "e-new", kind: "CHARGE", amount: "2000.00" })],
    ...extra,
  });
}

function renderCharge(onSaved = () => undefined) {
  return renderWithProviders(
    <EntryForm kind="CHARGE" group={OFFLINE_GROUP} payee={MILON_PAYEE} onSaved={onSaved} onCancel={() => undefined} />,
    { user: fixtures.masterAdmin, locale: "en", route: "/staff/accounting" },
  );
}

beforeEach(() => {
  window.localStorage.clear();
});

describe("entry form: counted types", () => {
  it("shows the counted line live: 40 answer sheets at 50 is 2,000", async () => {
    const user = userEvent.setup();
    routes();
    renderCharge();

    await screen.findByRole("option", { name: "Answer sheet evaluation" });
    await user.selectOptions(screen.getByLabelText(/^Type/), ANSWER_SHEET.id);
    await user.type(screen.getByLabelText(/^Quantity/), "40");
    await user.clear(screen.getByLabelText(/^Unit price/));
    await user.type(screen.getByLabelText(/^Unit price/), "50");

    expect(screen.getByText("40 answer sheets × ৳50.00 = ৳2,000.00")).toBeInTheDocument();
    // The amount is computed, so there is no amount field for a counted type.
    expect(screen.queryByLabelText(/^Amount/)).not.toBeInTheDocument();
  });

  it("starts a plain type's amount from the payee's last amount for it, such as a monthly salary", async () => {
    const user = userEvent.setup();
    // The API leaves the empty quantity and price out of the answer.
    routes({
      [`GET /api/v1/accounting/payees/${MILON_PAYEE.id}/last-prices`]: [
        ...LAST_PRICES_MILON,
        { typeId: MONTHLY.id, amount: "15000.00", date: "2026-10-31" } as unknown as (typeof LAST_PRICES_MILON)[number],
      ],
    });
    renderCharge();

    await screen.findByRole("option", { name: "Monthly salary" });
    await user.selectOptions(screen.getByLabelText(/^Type/), MONTHLY.id);
    await waitFor(() => expect(screen.getByLabelText(/^Amount/)).toHaveValue("15000.00"));
    await user.clear(screen.getByLabelText(/^Amount/));
    await user.type(screen.getByLabelText(/^Amount/), "16000");
    expect(screen.getByLabelText(/^Amount/)).toHaveValue("16000");
  });

  it("starts the unit price from the payee's last price for that type", async () => {
    const user = userEvent.setup();
    routes();
    renderCharge();

    await screen.findByRole("option", { name: "Answer sheet evaluation" });
    await user.selectOptions(screen.getByLabelText(/^Type/), ANSWER_SHEET.id);
    await waitFor(() => expect(screen.getByLabelText(/^Unit price/)).toHaveValue("50.00"));
  });

  it("sends a counted charge with quantity and price, and the server computes the amount", async () => {
    const user = userEvent.setup();
    let saved = false;
    const api = routes();
    renderCharge(() => {
      saved = true;
    });

    await screen.findByRole("option", { name: "Answer sheet evaluation" });
    await user.selectOptions(screen.getByLabelText(/^Type/), ANSWER_SHEET.id);
    await user.type(screen.getByLabelText(/^Quantity/), "40");
    await waitFor(() => expect(screen.getByLabelText(/^Unit price/)).toHaveValue("50.00"));
    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(saved).toBe(true));
    const post = api.calls.find((call) => call.method === "POST" && call.path === "/api/v1/accounting/entries");
    expect(post?.body).toMatchObject({
      kind: "CHARGE",
      groupId: OFFLINE_GROUP.id,
      payeeId: MILON_PAYEE.id,
      typeId: ANSWER_SHEET.id,
      date: dhakaToday(),
      quantity: "40",
      unitPrice: "50.00",
      amount: null,
      accountId: null,
      paidNow: null,
    });
  });
});

describe("entry form: paid now", () => {
  it("adds the payment in the same step, with the charge amount as the default", async () => {
    const user = userEvent.setup();
    const api = routes();
    renderCharge();

    await screen.findByRole("option", { name: "Answer sheet evaluation" });
    await user.selectOptions(screen.getByLabelText(/^Type/), ANSWER_SHEET.id);
    await user.type(screen.getByLabelText(/^Quantity/), "40");
    await waitFor(() => expect(screen.getByLabelText(/^Unit price/)).toHaveValue("50.00"));
    await user.click(screen.getByRole("switch", { name: /^Paid now/ }));

    expect(screen.getByLabelText(/^Paid now: amount/)).toHaveValue("2000.00");
    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(api.calls.some((call) => call.method === "POST")).toBe(true));
    expect(api.calls.find((call) => call.method === "POST")?.body).toMatchObject({
      paidNow: { accountId: ACC_ACCOUNT_IDS.CASH, amount: "2000.00", reference: null },
    });
  });

  it("keeps the paid-now amount in step with the charge until the person types their own", async () => {
    const user = userEvent.setup();
    routes();
    renderCharge();

    await screen.findByRole("option", { name: "Answer sheet evaluation" });
    await user.selectOptions(screen.getByLabelText(/^Type/), ANSWER_SHEET.id);
    await user.type(screen.getByLabelText(/^Quantity/), "40");
    await waitFor(() => expect(screen.getByLabelText(/^Unit price/)).toHaveValue("50.00"));
    await user.click(screen.getByRole("switch", { name: /^Paid now/ }));
    expect(screen.getByLabelText(/^Paid now: amount/)).toHaveValue("2000.00");

    // Two more sheets after the switch: the payment follows.
    await user.clear(screen.getByLabelText(/^Quantity/));
    await user.type(screen.getByLabelText(/^Quantity/), "42");
    expect(screen.getByLabelText(/^Paid now: amount/)).toHaveValue("2100.00");

    // A typed part payment stays as typed.
    await user.clear(screen.getByLabelText(/^Paid now: amount/));
    await user.type(screen.getByLabelText(/^Paid now: amount/), "1000");
    await user.clear(screen.getByLabelText(/^Quantity/));
    await user.type(screen.getByLabelText(/^Quantity/), "44");
    expect(screen.getByLabelText(/^Paid now: amount/)).toHaveValue("1000");
  });

  it("refuses a paid-now amount that is more than the charge, before any write", async () => {
    const user = userEvent.setup();
    const api = routes();
    renderCharge();

    await screen.findByRole("option", { name: "Answer sheet evaluation" });
    await user.selectOptions(screen.getByLabelText(/^Type/), ANSWER_SHEET.id);
    await user.type(screen.getByLabelText(/^Quantity/), "40");
    await waitFor(() => expect(screen.getByLabelText(/^Unit price/)).toHaveValue("50.00"));
    await user.click(screen.getByRole("switch", { name: /^Paid now/ }));
    await user.clear(screen.getByLabelText(/^Paid now: amount/));
    await user.type(screen.getByLabelText(/^Paid now: amount/), "2500");
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText("The payment cannot be more than the charge.")).toBeInTheDocument();
    expect(api.calls.some((call) => call.method === "POST")).toBe(false);
  });
});

describe("entry form: what a type needs", () => {
  it("asks for a description and sends nothing until it is there", async () => {
    const user = userEvent.setup();
    const api = routes();
    renderCharge();

    await screen.findByRole("option", { name: "Other" });
    await user.selectOptions(screen.getByLabelText(/^Type/), OTHER_AMOUNT.id);
    await user.type(screen.getByLabelText(/^Amount/), "300");
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText("Say what it was for.")).toBeInTheDocument();
    expect(api.calls.some((call) => call.method === "POST")).toBe(false);
  });

  it("shows the month field for a type that needs one, starting from the date's month", async () => {
    const user = userEvent.setup();
    routes();
    renderCharge();

    await screen.findByRole("option", { name: "Monthly salary" });
    await user.selectOptions(screen.getByLabelText(/^Type/), "a0000000-0000-4000-8000-000000000119");
    expect(await screen.findByLabelText(/^Month/)).toHaveValue(dhakaToday().slice(0, 7));
  });

  it("refuses a future date before any write", async () => {
    const user = userEvent.setup();
    const api = routes();
    renderCharge();

    await screen.findByRole("option", { name: "Answer sheet evaluation" });
    await user.selectOptions(screen.getByLabelText(/^Type/), ANSWER_SHEET.id);
    await user.type(screen.getByLabelText(/^Quantity/), "1");
    await waitFor(() => expect(screen.getByLabelText(/^Unit price/)).toHaveValue("50.00"));
    fireEvent.change(screen.getByLabelText(/^Date/), { target: { value: "2099-01-01" } });
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText("The date cannot be in the future.")).toBeInTheDocument();
    expect(api.calls.some((call) => call.method === "POST")).toBe(false);
  });
});

describe("entry form: a payment", () => {
  it("records a payment with its money account and sends the account as the last one used", async () => {
    const user = userEvent.setup();
    const api = routes({ "POST /api/v1/accounting/entries": [entry({ kind: "PAYMENT", amount: "500.00" })] });
    renderWithProviders(
      <EntryForm kind="PAYMENT" group={OFFLINE_GROUP} payee={MILON_PAYEE} onSaved={() => undefined} onCancel={() => undefined} />,
      { user: fixtures.masterAdmin, locale: "en", route: "/staff/accounting" },
    );

    await screen.findByRole("option", { name: "bKash" });
    await user.selectOptions(screen.getByLabelText(/^Money account/), ACC_ACCOUNT_IDS.BKASH);
    await user.type(screen.getByLabelText(/^Amount/), "500");
    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(api.calls.some((call) => call.method === "POST")).toBe(true));
    expect(api.calls.find((call) => call.method === "POST")?.body).toMatchObject({
      kind: "PAYMENT",
      payeeId: MILON_PAYEE.id,
      typeId: null,
      accountId: ACC_ACCOUNT_IDS.BKASH,
      amount: "500.00",
    });
    expect(window.localStorage.getItem("cc-acc-last-account")).toBe(ACC_ACCOUNT_IDS.BKASH);
  });
});

