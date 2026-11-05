import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { PayPersonPage } from "@/components/staff/pay/pay-person-page";
import { apiError, fixtures, mockApi, renderWithProviders } from "../helpers";

const PERSON = "p-1";

const detail = {
  id: PERSON,
  fullName: "Nasrin Sultana",
  role: "INSTRUCTOR",
  status: "ACTIVE",
  deleted: false,
  payNotSetUp: false,
  settings: { monthlySalary: null, payDay: null, salaryStart: null, salaryEnd: null, sheetRate: null, effectiveSheetRate: 50 },
  summary: { charged: 1000, paid: 400, balance: 600, owedNow: 600, overdue: 0, lastPaymentOn: "2026-11-01" },
  nextPayDate: null,
  preferredAccount: null,
};

const accounts = [
  {
    id: "a-1",
    kind: "BKASH",
    accountName: null,
    accountNumber: null,
    bankName: null,
    branch: null,
    routingNumber: null,
    walletNumber: "01712344821",
    preferred: true,
  },
];

const paymentRow = {
  id: "l-1",
  kind: "PAYMENT",
  amount: 400,
  dueOn: "2026-11-01",
  createdAt: "2026-11-01T10:00:00+06:00",
  paidOn: "2026-11-01",
  method: "BKASH",
  accountSnapshot: "bKash 017••••4821",
  reference: "TX-1",
  note: null,
  voided: false,
  voidedAt: null,
  voidReason: null,
};

const ledger = { content: [paymentRow], page: 0, size: 20, totalElements: 1, totalPages: 1 };

function routes(extra: Record<string, unknown> = {}) {
  return {
    [`GET /api/v1/staff-pay/people/${PERSON}`]: detail,
    [`GET /api/v1/staff-pay/people/${PERSON}/payout-accounts`]: accounts,
    [`GET /api/v1/staff-pay/people/${PERSON}/ledger`]: ledger,
    "GET /api/v1/staff-pay/defaults": { sheetRate: 50, unpaidReminderDays: 14, salaryDueSoonDays: 3, updatedAt: "2026-11-01T00:00:00Z" },
    ...extra,
  };
}

function renderPerson(extra: Record<string, unknown> = {}) {
  const api = mockApi(routes(extra));
  renderWithProviders(<PayPersonPage userId={PERSON} />, { user: fixtures.masterAdmin, locale: "en", route: `/staff/pay/${PERSON}` });
  return api;
}

describe("one person's pay (Master Admin)", () => {
  it("shows the summary, the payout account as read only with its number, and the ledger", async () => {
    renderPerson();

    expect(await screen.findByRole("heading", { name: "Nasrin Sultana" })).toBeInTheDocument();
    expect(screen.getByText("Payout accounts")).toBeInTheDocument();
    expect(screen.getByText("Wallet number")).toBeInTheDocument();
    expect(screen.getByText("Preferred")).toBeInTheDocument();
    expect((await screen.findAllByText("bKash 017••••4821")).length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: "Delete" })).toBeNull();
  });

  it("opens the payment form with the balance as the amount, and asks for the advance tick above it", async () => {
    const api = renderPerson({
      [`POST /api/v1/staff-pay/people/${PERSON}/payments`]: { ...paymentRow, id: "l-2", amount: 700 },
    });
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Record payment" }));
    const form = await screen.findByRole("form", { name: "Record a payment" });
    const amount = within(form).getByLabelText("Amount") as HTMLInputElement;
    expect(amount.value).toBe("600.00");

    await user.clear(amount);
    await user.type(amount, "700");
    await user.click(within(form).getByRole("button", { name: "Record payment" }));
    expect(await screen.findByText("That is more than the balance. Tick the advance box to record it anyway.")).toBeInTheDocument();
    expect(api.calls.some((call) => call.method === "POST" && call.path.endsWith("/payments"))).toBe(false);

    await user.click(within(form).getByRole("checkbox"));
    await user.click(within(form).getByRole("button", { name: "Record payment" }));
    await waitFor(() => expect(api.calls.some((call) => call.method === "POST" && call.path.endsWith("/payments"))).toBe(true));
    const sent = api.calls.find((call) => call.method === "POST" && call.path.endsWith("/payments"));
    expect(sent?.body).toMatchObject({ amount: 700, method: "BANK", allowAdvance: true });
  });

  it("voids a payment only with a reason, and shows the API answer when it is refused", async () => {
    mockApi({
      ...routes(),
      "POST /api/v1/staff-pay/ledger/l-1/void": apiError(409, "LEDGER_ALREADY_VOIDED", "already"),
    });
    renderWithProviders(<PayPersonPage userId={PERSON} />, { user: fixtures.masterAdmin, locale: "en", route: `/staff/pay/${PERSON}` });
    const user = userEvent.setup();

    const [voidButton] = await screen.findAllByRole("button", { name: "Void" });
    await user.click(voidButton);
    const dialog = await screen.findByRole("dialog", { name: "Void this entry?" });
    await user.click(within(dialog).getByRole("button", { name: "Void entry" }));
    expect(await within(dialog).findByText("Say why: the reason is required.")).toBeInTheDocument();

    await user.type(within(dialog).getByRole("textbox"), "Typed twice");
    await user.click(within(dialog).getByRole("button", { name: "Void entry" }));
    expect(await screen.findByText("This entry is already voided.")).toBeInTheDocument();
  });
});
