import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { MyPayPage } from "@/components/staff/pay/my-pay-page";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const ledger = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

const summary = (role: "INSTRUCTOR" | "SYSTEM_ADMIN") => ({
  role,
  summary: { charged: 0, paid: 0, balance: 0, owedNow: 0, overdue: 0, lastPaymentOn: null },
  monthlySalary: null,
  payDay: null,
  nextPayDate: null,
});

describe("the My pay header link to payment details", () => {
  it("links a System Admin and an instructor to the payout accounts section", async () => {
    const people = [
      { user: fixtures.systemAdmin, role: "SYSTEM_ADMIN" as const },
      { user: fixtures.instructor, role: "INSTRUCTOR" as const },
    ];
    for (const { user, role } of people) {
      mockApi({
        "GET /api/v1/me/pay/summary": summary(role),
        "GET /api/v1/me/pay/ledger": ledger,
      });
      const { unmount } = renderWithProviders(<MyPayPage />, { user, locale: "en", route: "/staff/my-pay" });

      const link = await screen.findByRole("link", { name: "Update payment details" });
      expect(link).toHaveAttribute("href", "/staff/account");
      unmount();
    }
  });

  it("does not show the link to the Master Admin", async () => {
    // The link follows the signed-in role, not the summary's role, so the summary here is only a placeholder.
    mockApi({
      "GET /api/v1/me/pay/summary": summary("INSTRUCTOR"),
      "GET /api/v1/me/pay/ledger": ledger,
    });
    renderWithProviders(<MyPayPage />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/my-pay" });

    await screen.findByRole("heading", { name: "My pay" });
    expect(screen.queryByRole("link", { name: "Update payment details" })).not.toBeInTheDocument();
  });
});
