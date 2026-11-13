import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AdmissionQueue } from "@/components/staff/offline/admission-queue";
import { useOfflineAttention } from "@/lib/api/exam/offline-admin";
import type { Role } from "@/lib/api/exam/types";
import { fixtures, mockApi, renderWithProviders, routerState } from "../helpers";
import { counts, page, queueRow } from "./fixtures";

/** The queue answers each state with its rows. The counts come from the counts endpoint, one request for every tab and card. */
function queueRoutes(rowsFor: Record<string, unknown[]> = {}) {
  return mockApi({
    "GET /api/v1/offline-admissions": ({ url }: { url: URL }) => {
      const status = url.searchParams.get("status") ?? "";
      return page(rowsFor[status] ?? []);
    },
    "GET /api/v1/offline-admissions/counts": counts(),
    "GET /api/v1/offline-batches": [],
  });
}

/** Shows the two nav badge numbers the shell reads: admissions waiting, then payments waiting. */
function AttentionProbe({ role }: { role: Role }) {
  const attention = useOfflineAttention(role);
  return <p>{`${attention.admissions} ${attention.offlineFees}`}</p>;
}

describe("the admissions queue tabs (plan 6.3)", () => {
  it("opens on Ready for the Master Admin, with the counts on every tab", async () => {
    queueRoutes();
    renderWithProviders(<AdmissionQueue />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/admissions" });

    const ready = await screen.findByRole("tab", { name: /^Ready/ });
    expect(ready).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("tab", { name: /^Submitted/ })).toHaveAttribute("aria-selected", "false");
    expect(await within(screen.getByRole("tab", { name: /^Submitted/ })).findByText("3")).toBeInTheDocument();
    expect(await within(screen.getByRole("tab", { name: /^Onboarded/ })).findByText("5")).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: /^Changes requested/ })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: /^Closed/ })).toBeInTheDocument();
  });

  it("opens on Submitted for staff", async () => {
    queueRoutes();
    renderWithProviders(<AdmissionQueue />, { user: fixtures.systemAdmin, locale: "en", route: "/staff/admissions" });

    expect(await screen.findByRole("tab", { name: /^Submitted/ })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("tab", { name: /^Ready/ })).toHaveAttribute("aria-selected", "false");
  });

  it("keeps the tab in the URL: a tab named there is the one that opens", async () => {
    queueRoutes();
    renderWithProviders(<AdmissionQueue />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: "/staff/admissions?tab=ONBOARDED",
    });

    expect(await screen.findByRole("tab", { name: /^Onboarded/ })).toHaveAttribute("aria-selected", "true");
  });

  it("says what to do next when the tab is empty", async () => {
    queueRoutes();
    renderWithProviders(<AdmissionQueue />, {
      user: fixtures.systemAdmin,
      locale: "en",
      route: "/staff/admissions?tab=CLOSED",
    });

    expect(await screen.findByText("No closed admissions")).toBeInTheDocument();
    expect(screen.getByText(/Admissions that have ended or been closed/)).toBeInTheDocument();
  });
});

describe("the dashboard cards (plan 5)", () => {
  it("shows the four cards to the Master Admin, each with its count", async () => {
    queueRoutes();
    renderWithProviders(<AdmissionQueue />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/admissions" });

    const review = await screen.findByRole("link", { name: /Forms to review/ });
    expect(await within(review).findByText("3")).toBeInTheDocument();
    expect(await within(screen.getByRole("link", { name: /Ready to onboard/ })).findByText("2")).toBeInTheDocument();
    expect(await within(screen.getByRole("link", { name: /Payments to approve/ })).findByText("4")).toBeInTheDocument();
    expect(await within(screen.getByRole("link", { name: /Overdue fees/ })).findByText("2")).toBeInTheDocument();
  });

  it("leaves out the two Master Admin cards for a System Admin", async () => {
    queueRoutes();
    renderWithProviders(<AdmissionQueue />, { user: fixtures.systemAdmin, locale: "en", route: "/staff/admissions" });

    expect(await screen.findByRole("link", { name: /Forms to review/ })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Overdue fees/ })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Ready to onboard/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Payments to approve/ })).not.toBeInTheDocument();
  });
});

describe("the money of each row comes from the server, not from a schedule read (plan 4.3)", () => {
  it("shows the flag, the owed total and the next due the row carries, and reads no schedule", async () => {
    const api = queueRoutes({
      ONBOARDED: [
        queueRow({
          id: "adm-2",
          fullName: "Karim Ali",
          status: "ONBOARDED",
          flags: ["OVERDUE"],
          owedTotal: 6000,
          nextDueOn: "2026-12-09",
        }),
      ],
    });
    renderWithProviders(<AdmissionQueue />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/admissions?tab=ONBOARDED" });

    const row = (await screen.findAllByText("Karim Ali"))[0]?.closest("tr") as HTMLElement;
    expect(within(row).getByText("Overdue")).toBeInTheDocument();
    expect(within(row).getByText(/Owed/)).toHaveTextContent("6,000.00");
    expect(within(row).getByText(/Next due/)).toBeInTheDocument();
    expect(api.calls.some((call) => call.path.endsWith("/fees"))).toBe(false);
  });

  it("shows Paid up for an onboarded row that owes nothing, and no money at all for a row still in review", async () => {
    queueRoutes({
      ONBOARDED: [queueRow({ id: "adm-3", fullName: "Sumi Akter", status: "ONBOARDED", flags: [], owedTotal: 0, nextDueOn: null })],
      SUBMITTED: [queueRow({ id: "adm-4", fullName: "Jamal Hossain", status: "SUBMITTED", flags: [], owedTotal: 0, nextDueOn: null })],
    });
    renderWithProviders(<AdmissionQueue />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/admissions?tab=ONBOARDED" });

    const paid = (await screen.findAllByText("Sumi Akter"))[0]?.closest("tr") as HTMLElement;
    expect(within(paid).getByText("Paid up")).toBeInTheDocument();
  });
});

describe("the money filter is applied by the server (plan 4.3)", () => {
  it("sends the filter from the URL, and shows the rows the server returns", async () => {
    const api = queueRoutes({ SUBMITTED: [queueRow({ id: "adm-5", fullName: "Nasir Uddin", flags: ["PENDING_APPROVAL"] })] });
    renderWithProviders(<AdmissionQueue />, {
      user: fixtures.systemAdmin,
      locale: "en",
      route: "/staff/admissions?tab=SUBMITTED&flag=pending-approval",
    });

    expect((await screen.findAllByText("Nasir Uddin")).length).toBeGreaterThan(0);
    await waitFor(() =>
      expect(
        api.calls.some(
          (call) => call.path === "/api/v1/offline-admissions" && new URLSearchParams(call.search).get("flag") === "pending-approval",
        ),
      ).toBe(true),
    );
  });

  it("puts the picked money filter in the URL, which is what makes the server answer again", async () => {
    const user = userEvent.setup();
    queueRoutes({ SUBMITTED: [] });
    renderWithProviders(<AdmissionQueue />, { user: fixtures.systemAdmin, locale: "en", route: "/staff/admissions?tab=SUBMITTED" });

    await user.selectOptions(await screen.findByLabelText("Money"), "overdue");

    const urls = [...routerState.push.mock.calls, ...routerState.replace.mock.calls].map((args) => String(args[0]));
    expect(urls.some((url) => new URLSearchParams(url.split("?")[1] ?? "").get("flag") === "overdue")).toBe(true);
  });
});

describe("the nav badges read the counts endpoint (plan 5)", () => {
  it("counts the forms to review and the ready ones, and the payments waiting, for the Master Admin", async () => {
    mockApi({ "GET /api/v1/offline-admissions/counts": counts() });
    renderWithProviders(<AttentionProbe role="MASTER_ADMIN" />, { user: fixtures.masterAdmin, locale: "en" });

    // Submitted 3 plus Ready 2 is 5 admissions; 4 payments wait for approval.
    expect(await screen.findByText("5 4")).toBeInTheDocument();
  });

  it("counts only the forms to review for a System Admin, who approves no payments", async () => {
    mockApi({ "GET /api/v1/offline-admissions/counts": counts() });
    renderWithProviders(<AttentionProbe role="SYSTEM_ADMIN" />, { user: fixtures.systemAdmin, locale: "en" });

    expect(await screen.findByText("3 0")).toBeInTheDocument();
  });

  it("asks for no count at all from a role that does not review admissions", async () => {
    const api = mockApi({ "GET /api/v1/offline-admissions/counts": counts() });
    renderWithProviders(<AttentionProbe role="INSTRUCTOR" />, { user: fixtures.systemAdmin, locale: "en" });

    expect(await screen.findByText("0 0")).toBeInTheDocument();
    expect(api.calls.some((call) => call.path === "/api/v1/offline-admissions/counts")).toBe(false);
  });
});

describe("the row's next action (staff never see Onboard)", () => {
  it("gives the Master Admin an Onboard action on a ready row", async () => {
    queueRoutes({ READY: [queueRow({ status: "READY" })] });
    renderWithProviders(<AdmissionQueue />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/admissions?tab=READY" });

    expect((await screen.findAllByText("Rahim Uddin")).length).toBeGreaterThan(0);
    expect(screen.getAllByRole("link", { name: "Onboard" }).length).toBeGreaterThan(0);
  });

  it("gives a System Admin no Onboard action, only the line that the Master Admin is to onboard", async () => {
    queueRoutes({ READY: [queueRow({ status: "READY" })] });
    renderWithProviders(<AdmissionQueue />, { user: fixtures.systemAdmin, locale: "en", route: "/staff/admissions?tab=READY" });

    expect((await screen.findAllByText("Rahim Uddin")).length).toBeGreaterThan(0);
    expect(screen.queryByRole("link", { name: "Onboard" })).not.toBeInTheDocument();
    expect(screen.getAllByText(/Waiting for the Master Admin to onboard/).length).toBeGreaterThan(0);
  });

  it("gives staff a Review action on a submitted row", async () => {
    queueRoutes({ SUBMITTED: [queueRow()] });
    renderWithProviders(<AdmissionQueue />, { user: fixtures.systemAdmin, locale: "en", route: "/staff/admissions" });

    await waitFor(() => expect(screen.getAllByRole("link", { name: "Review" }).length).toBeGreaterThan(0));
  });
});
