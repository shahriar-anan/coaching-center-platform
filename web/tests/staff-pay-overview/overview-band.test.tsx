import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { PayOverviewBand } from "@/components/staff/pay/pay-overview-band";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const overview = {
  asOf: "2026-11-05",
  owedNow: { total: 20050, salaries: 20000, marking: 50, adjustments: 0 },
  overdue: { total: 50, salaries: 0, marking: 50, adjustments: 0 },
  paidThisMonth: 60,
  dueNext30Days: {
    from: "2026-11-05",
    to: "2026-12-05",
    amount: 20000,
    items: [{ userId: "p-2", fullName: "Rahim Uddin", dueOn: "2026-11-15", amount: 20000 }],
  },
  owing: [
    {
      userId: "p-2",
      fullName: "Rahim Uddin",
      role: "SYSTEM_ADMIN",
      owedNow: { total: 20000, salaries: 20000, marking: 0, adjustments: 0 },
      overdue: { total: 0, salaries: 0, marking: 0, adjustments: 0 },
      nextDueOn: "2026-11-15",
    },
    {
      userId: "p-1",
      fullName: "Nasrin Sultana",
      role: "INSTRUCTOR",
      owedNow: { total: 50, salaries: 0, marking: 50, adjustments: 0 },
      overdue: { total: 50, salaries: 0, marking: 50, adjustments: 0 },
      nextDueOn: "2026-11-02",
    },
  ],
};

describe("the Master Admin's overview band", () => {
  it("shows the four figures: owed now, overdue, paid this month and salary due in thirty days", async () => {
    mockApi({ "GET /api/v1/staff-pay/overview": overview });
    renderWithProviders(<PayOverviewBand />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/pay" });

    expect(await screen.findByText("Owed now")).toBeInTheDocument();
    expect(screen.getByText("৳20,050.00")).toBeInTheDocument();
    expect(screen.getByText("৳50.00", { selector: "dd" })).toBeInTheDocument();
    expect(screen.getByText("Paid this month")).toBeInTheDocument();
    expect(screen.getByText("৳60.00")).toBeInTheDocument();
    expect(screen.getByText("Salaries due in 30 days")).toBeInTheDocument();
    expect(screen.getByText("৳20,000.00", { selector: "dd" })).toBeInTheDocument();
    expect(screen.getByText(/Salaries ৳20,000\.00 · Marking ৳50\.00/)).toBeInTheDocument();
  });

  it("lists who is owed next, most overdue first, each linking to the person's page", async () => {
    mockApi({ "GET /api/v1/staff-pay/overview": overview });
    renderWithProviders(<PayOverviewBand />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/pay" });

    const rahim = await screen.findByRole("link", { name: "Rahim Uddin" });
    expect(rahim).toHaveAttribute("href", "/staff/pay/p-2");
    const nasrin = screen.getByRole("link", { name: "Nasrin Sultana" });
    expect(nasrin).toHaveAttribute("href", "/staff/pay/p-1");
    expect(screen.getByText("৳50.00 overdue")).toBeInTheDocument();
    expect(screen.getByText(/Next due 15 Nov/)).toBeInTheDocument();
  });

  it("says nobody is owed when the list is empty", async () => {
    mockApi({
      "GET /api/v1/staff-pay/overview": {
        ...overview,
        owedNow: { total: 0, salaries: 0, marking: 0, adjustments: 0 },
        overdue: { total: 0, salaries: 0, marking: 0, adjustments: 0 },
        dueNext30Days: { ...overview.dueNext30Days, amount: 0, items: [] },
        owing: [],
      },
    });
    renderWithProviders(<PayOverviewBand />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/pay" });

    expect(await screen.findByText("Nobody is owed money right now.")).toBeInTheDocument();
  });

  it("shows an error with a retry, and not a blank band, when the overview cannot load", async () => {
    mockApi({});
    renderWithProviders(<PayOverviewBand />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/pay" });

    expect(await screen.findByRole("alert")).toBeInTheDocument();
  });

  it("shows the same figures in Bangla with the Bangla labels", async () => {
    mockApi({ "GET /api/v1/staff-pay/overview": overview });
    renderWithProviders(<PayOverviewBand />, { user: fixtures.masterAdmin, locale: "bn", route: "/staff/pay" });

    expect(await screen.findByText("এখন পাওনা")).toBeInTheDocument();
    expect(screen.getByText("মেয়াদোত্তীর্ণ")).toBeInTheDocument();
    expect(screen.getByText("এই মাসে পরিশোধ")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Rahim Uddin" })).toBeInTheDocument();
    expect(screen.getAllByText(/৳/).length).toBeGreaterThan(0);
    expect(screen.queryByText("Owed now")).not.toBeInTheDocument();
  });
});
