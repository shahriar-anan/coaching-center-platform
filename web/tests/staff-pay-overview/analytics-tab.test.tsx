import { fireEvent, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { PayAnalytics } from "@/components/staff/pay/pay-analytics";
import { PayPeoplePage } from "@/components/staff/pay/pay-people-page";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const analytics = {
  from: "2031-02",
  to: "2031-04",
  months: ["2031-02", "2031-03", "2031-04"],
  payroll: [
    { month: "2031-02", salary: 0, marking: 0, adjustments: 500, earned: 500, paid: 0 },
    { month: "2031-03", salary: 20000, marking: 100, adjustments: 0, earned: 20100, paid: 60 },
    { month: "2031-04", salary: 0, marking: 0, adjustments: 0, earned: 0, paid: 20000 },
  ],
  sheets: [
    {
      userId: "p-1",
      fullName: "Nasrin Sultana",
      total: 2,
      byMonth: [
        { month: "2031-02", sheets: 0, amount: 0 },
        { month: "2031-03", sheets: 2, amount: 100 },
        { month: "2031-04", sheets: 0, amount: 0 },
      ],
    },
  ],
  people: [
    { userId: "p-2", fullName: "Rahim Uddin", role: "SYSTEM_ADMIN", earned: 20500, paid: 20000, balance: 500 },
    { userId: "p-1", fullName: "Nasrin Sultana", role: "INSTRUCTOR", earned: 100, paid: 60, balance: 40 },
  ],
};

const overview = {
  asOf: "2026-11-05",
  owedNow: { total: 0, salaries: 0, marking: 0, adjustments: 0 },
  overdue: { total: 0, salaries: 0, marking: 0, adjustments: 0 },
  paidThisMonth: 0,
  dueNext30Days: { from: "2026-11-05", to: "2026-12-05", amount: 0, items: [] },
  owing: [],
};

/** The section under a heading, found by the heading's text. */
function sectionUnder(heading: HTMLElement): HTMLElement {
  const section = heading.closest("section");
  if (!section) {
    throw new Error("the heading is not inside a section");
  }
  return section;
}

describe("the Analytics tab on the staff pay page", () => {
  it("draws payroll by month, one bar per month, each with its salary, marking and paid figures", async () => {
    mockApi({ "GET /api/v1/staff-pay/analytics": analytics });
    renderWithProviders(<PayAnalytics />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/pay" });

    expect(await screen.findByRole("heading", { name: "Payroll by month" })).toBeInTheDocument();
    expect(
      screen.getByRole("group", { name: "Mar: salary ৳20,000.00, marking ৳100.00, paid ৳60.00" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("group", { name: "Feb: salary ৳0.00, marking ৳0.00, paid ৳0.00" })).toBeInTheDocument();
    // The legend and the table's headers both name the marks, so each label appears more than once.
    expect(screen.getAllByText("Salary").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Marking").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Paid").length).toBeGreaterThan(0);
  });

  it("lists the same payroll figures in a table, with the month that had nothing shown as zero", async () => {
    mockApi({ "GET /api/v1/staff-pay/analytics": analytics });
    renderWithProviders(<PayAnalytics />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/pay" });

    await screen.findByRole("heading", { name: "Payroll by month" });
    expect(screen.getByText("৳20,100.00")).toBeInTheDocument();
    // Rahim's paid in the people table is the same amount, so the salary cell is one of two.
    expect(screen.getAllByText("৳20,000.00", { selector: "td" }).length).toBeGreaterThan(0);
    expect(screen.getAllByText("৳0.00", { selector: "td" }).length).toBeGreaterThan(0);
  });

  it("shows the sheets published per teacher by month, with zero where none were published", async () => {
    mockApi({ "GET /api/v1/staff-pay/analytics": analytics });
    renderWithProviders(<PayAnalytics />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/pay" });

    const section = sectionUnder(await screen.findByRole("heading", { name: "Sheets published per teacher" }));
    const row = within(section)
      .getAllByRole("row")
      .find((candidate) => candidate.textContent?.includes("Nasrin Sultana"));
    expect(row).toBeDefined();
    const cells = within(row as HTMLElement).getAllByRole("cell");
    expect(cells.map((cell) => cell.textContent)).toEqual(["0", "2", "0", "2"]);
    expect(within(section).getAllByRole("link", { name: "Nasrin Sultana" })[0]).toHaveAttribute("href", "/staff/pay/p-1");
  });

  it("gives each person's earned, paid and balance for the range", async () => {
    mockApi({ "GET /api/v1/staff-pay/analytics": analytics });
    renderWithProviders(<PayAnalytics />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/pay" });

    const section = sectionUnder(await screen.findByRole("heading", { name: "Each person in this range" }));
    expect(within(section).getAllByText("৳20,500.00").length).toBeGreaterThan(0);
    expect(within(section).getAllByText("৳500.00").length).toBeGreaterThan(0);
    expect(within(section).getAllByText("৳40.00").length).toBeGreaterThan(0);
  });

  it("refuses a range that ends before it starts, and asks the server for nothing for it", async () => {
    const api = mockApi({ "GET /api/v1/staff-pay/analytics": analytics });
    renderWithProviders(<PayAnalytics />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/pay" });
    await screen.findByRole("heading", { name: "Payroll by month" });

    fireEvent.change(screen.getByLabelText("To (month)"), { target: { value: "2020-01" } });

    expect(await screen.findByRole("alert")).toHaveTextContent("The end month cannot be before the start month.");
    expect(api.calls.some((call) => call.search.includes("to=2020-01"))).toBe(false);
  });

  it("says plainly when nothing was published or paid in the range", async () => {
    mockApi({
      "GET /api/v1/staff-pay/analytics": {
        ...analytics,
        payroll: analytics.payroll.map((row) => ({ ...row, salary: 0, marking: 0, adjustments: 0, earned: 0, paid: 0 })),
        sheets: [],
        people: [],
      },
    });
    renderWithProviders(<PayAnalytics />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/pay" });

    expect(await screen.findByText("No sheets were published in this range.")).toBeInTheDocument();
    expect(screen.getByText("Nothing was earned or paid in this range.")).toBeInTheDocument();
  });

  it("shows its headings and figures in Bangla", async () => {
    mockApi({ "GET /api/v1/staff-pay/analytics": analytics });
    renderWithProviders(<PayAnalytics />, { user: fixtures.masterAdmin, locale: "bn", route: "/staff/pay" });

    expect(await screen.findByRole("heading", { name: "মাসভিত্তিক বেতন" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "শিক্ষকপ্রতি প্রকাশিত খাতা" })).toBeInTheDocument();
  });
});

describe("the staff pay page's tabs", () => {
  it("shows the overview band on People and the analytics on Analytics, and switches between them", async () => {
    mockApi({
      "GET /api/v1/staff-pay/people": [],
      "GET /api/v1/staff-pay/overview": overview,
      "GET /api/v1/staff-pay/analytics": analytics,
    });
    renderWithProviders(<PayPeoplePage />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/pay" });

    expect(await screen.findByText("Owed now")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Payroll by month" })).not.toBeInTheDocument();

    const user = userEvent.setup();
    await user.click(screen.getByRole("tab", { name: "Analytics" }));
    expect(await screen.findByRole("heading", { name: "Payroll by month" })).toBeInTheDocument();
    expect(screen.queryByText("Owed now")).not.toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "People" }));
    expect(await screen.findByText("Owed now")).toBeInTheDocument();
  });
});
