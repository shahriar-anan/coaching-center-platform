import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import AccountPage from "@/app/(staff)/staff/account/page";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const BANK_ID = "b1000000-0000-4000-8000-000000000001";
const WALLET_ID = "b2000000-0000-4000-8000-000000000002";
const LIST = "GET /api/v1/me/payout-accounts";

/** The owner's list as the API sends it: full numbers, and the masked form a list shows. */
const bankAccount = {
  id: BANK_ID,
  kind: "BANK",
  accountName: "Nasrin Sultana",
  accountNumber: "1234567890",
  bankName: "Sonali Bank",
  branch: "Dhanmondi",
  routingNumber: "200262123",
  walletNumber: null,
  maskedNumber: "•••• 7890",
  preferred: true,
  createdAt: "2026-11-01T10:00:00+06:00",
  updatedAt: "2026-11-01T10:00:00+06:00",
};

const walletAccount = {
  id: WALLET_ID,
  kind: "BKASH",
  accountName: null,
  accountNumber: null,
  bankName: null,
  branch: null,
  routingNumber: null,
  walletNumber: "+8801712345678",
  maskedNumber: "•••• 5678",
  preferred: false,
  createdAt: "2026-11-02T10:00:00+06:00",
  updatedAt: "2026-11-02T10:00:00+06:00",
};

describe("the payout accounts section on the staff account page", () => {
  it("lists the owner's accounts with the numbers masked, preferred first", async () => {
    mockApi({ [LIST]: [bankAccount, walletAccount] });
    renderWithProviders(<AccountPage />, { user: fixtures.instructor, locale: "en", route: "/staff/account" });

    expect(await screen.findByRole("heading", { name: "Payout accounts" })).toBeInTheDocument();
    expect(await screen.findByText("Sonali Bank")).toBeInTheDocument();
    expect(screen.getByText("•••• 7890")).toBeInTheDocument();
    expect(screen.getByText("•••• 5678")).toBeInTheDocument();
    expect(screen.getByText("Preferred")).toBeInTheDocument();
    expect(screen.queryByText("1234567890")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Make preferred" })).toBeInTheDocument();
  });

  it("is not shown to the Master Admin, who is not paid through it, and asks for no accounts", async () => {
    const api = mockApi({});
    renderWithProviders(<AccountPage />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/account" });

    expect(await screen.findByRole("heading", { name: "Change password" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Payout accounts" })).not.toBeInTheDocument();
    expect(api.calls.some((call) => call.path === "/api/v1/me/payout-accounts")).toBe(false);
  });

  it("shows the not-set-yet invitation when the owner has no accounts", async () => {
    mockApi({ [LIST]: [] });
    renderWithProviders(<AccountPage />, { user: fixtures.systemAdmin, locale: "en", route: "/staff/account" });

    expect(await screen.findByText("Not set yet")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add account" })).toBeEnabled();
  });

  it("stops adding at five accounts", async () => {
    const five = [0, 1, 2, 3, 4].map((i) => ({ ...walletAccount, id: `c000000${i}-0000-4000-8000-00000000000${i}`, preferred: i === 0 }));
    mockApi({ [LIST]: five });
    renderWithProviders(<AccountPage />, { user: fixtures.instructor, locale: "en", route: "/staff/account" });

    expect(await screen.findByText("You can keep up to 5 payout accounts. Remove one to add another.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add account" })).toBeDisabled();
  });

  it("adds a bank account after the checks pass, sending the numbers without spaces", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      [LIST]: [],
      "POST /api/v1/me/payout-accounts": () => ({ ...bankAccount, preferred: true }),
    });
    renderWithProviders(<AccountPage />, { user: fixtures.instructor, locale: "en", route: "/staff/account" });

    await user.click(await screen.findByRole("button", { name: "Add account" }));
    const dialog = await screen.findByRole("dialog", { name: "Add a payout account" });

    await user.click(within(dialog).getByRole("button", { name: "Save" }));
    expect(within(dialog).getAllByText("This is needed.").length).toBeGreaterThan(0);
    expect(api.calls.some((call) => call.method === "POST")).toBe(false);

    await user.type(within(dialog).getByLabelText(/Account name/), "Nasrin Sultana");
    await user.type(within(dialog).getByLabelText(/Account number/), "1234-5678 90");
    await user.type(within(dialog).getByLabelText(/Bank name/), "Sonali Bank");
    await user.type(within(dialog).getByLabelText(/Branch/), "Dhanmondi");
    await user.type(within(dialog).getByLabelText(/Routing number/), "200262123");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));

    await waitFor(() => {
      const sent = api.calls.find((call) => call.method === "POST");
      expect(sent?.body).toEqual({
        kind: "BANK",
        accountName: "Nasrin Sultana",
        accountNumber: "1234567890",
        bankName: "Sonali Bank",
        branch: "Dhanmondi",
        routingNumber: "200262123",
      });
    });
  });

  it("adds a bKash number with only the wallet fields", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      [LIST]: [],
      "POST /api/v1/me/payout-accounts": () => walletAccount,
    });
    renderWithProviders(<AccountPage />, { user: fixtures.systemAdmin, locale: "en", route: "/staff/account" });

    await user.click(await screen.findByRole("button", { name: "Add account" }));
    const dialog = await screen.findByRole("dialog", { name: "Add a payout account" });
    await user.selectOptions(within(dialog).getByLabelText(/Account type/), "BKASH");
    await user.type(within(dialog).getByLabelText(/Mobile number/), "01712345678");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));

    await waitFor(() => {
      const sent = api.calls.find((call) => call.method === "POST");
      expect(sent?.body).toEqual({ kind: "BKASH", walletNumber: "01712345678" });
    });
  });

  it("makes another account preferred with one call", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      [LIST]: [bankAccount, walletAccount],
      [`POST /api/v1/me/payout-accounts/${WALLET_ID}/preferred`]: { ...walletAccount, preferred: true },
    });
    renderWithProviders(<AccountPage />, { user: fixtures.instructor, locale: "en", route: "/staff/account" });

    await user.click(await screen.findByRole("button", { name: "Make preferred" }));
    await waitFor(() =>
      expect(api.calls.some((call) => call.method === "POST" && call.path.endsWith(`/${WALLET_ID}/preferred`))).toBe(true),
    );
  });

  it("removes an account only after the owner confirms", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      [LIST]: [bankAccount],
      [`DELETE /api/v1/me/payout-accounts/${BANK_ID}`]: () => undefined,
    });
    renderWithProviders(<AccountPage />, { user: fixtures.instructor, locale: "en", route: "/staff/account" });

    await user.click(await screen.findByRole("button", { name: "Remove" }));
    const dialog = await screen.findByRole("dialog", { name: "Remove this payout account?" });
    expect(api.calls.some((call) => call.method === "DELETE")).toBe(false);

    await user.click(within(dialog).getByRole("button", { name: "Remove" }));
    await waitFor(() => expect(api.calls.some((call) => call.method === "DELETE")).toBe(true));
  });
});
