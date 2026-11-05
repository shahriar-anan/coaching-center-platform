import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { PayPeoplePage } from "@/components/staff/pay/pay-people-page";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const people = [
  {
    id: "p-1",
    fullName: "Nasrin Sultana",
    role: "INSTRUCTOR",
    status: "ACTIVE",
    deleted: false,
    balance: 1200,
    overdue: 300,
    lastPaymentOn: "2026-11-01",
    payNotSetUp: true,
  },
  {
    id: "p-2",
    fullName: "Rahim Uddin",
    role: "SYSTEM_ADMIN",
    status: "ACTIVE",
    deleted: false,
    balance: 0,
    overdue: 0,
    lastPaymentOn: null,
    payNotSetUp: false,
  },
];

describe("the Master Admin's staff pay table", () => {
  it("lists each person with a link to their page, the balance, what is overdue, and the set-up badge", async () => {
    mockApi({ "GET /api/v1/staff-pay/people": people });
    renderWithProviders(<PayPeoplePage />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/pay" });

    const [link] = await screen.findAllByRole("link", { name: "Nasrin Sultana" });
    expect(link).toHaveAttribute("href", "/staff/pay/p-1");
    expect(screen.getAllByText("Pay not set up").length).toBeGreaterThan(0);
    expect(screen.getAllByText(/1,200/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/300/).length).toBeGreaterThan(0);
    const [rahim] = screen.getAllByRole("link", { name: "Rahim Uddin" });
    expect(rahim).toHaveAttribute("href", "/staff/pay/p-2");
  });

  it("asks the API for one role, and for the owing people only, when those filters are chosen", async () => {
    const api = mockApi({ "GET /api/v1/staff-pay/people": people });
    renderWithProviders(<PayPeoplePage />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/pay" });
    await screen.findAllByRole("link", { name: "Nasrin Sultana" });

    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Teacher" }));
    await waitFor(() => expect(api.calls.some((call) => call.search.includes("role=INSTRUCTOR"))).toBe(true));

    await user.click(screen.getByRole("button", { name: "Owed money only" }));
    await waitFor(() =>
      expect(api.calls.some((call) => call.search.includes("role=INSTRUCTOR") && call.search.includes("owing=true"))).toBe(
        true,
      ),
    );
  });

  it("shows the empty state when nobody has an account yet", async () => {
    mockApi({ "GET /api/v1/staff-pay/people": [] });
    renderWithProviders(<PayPeoplePage />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/pay" });

    expect(await screen.findByText("No one to pay yet.")).toBeInTheDocument();
  });
});
