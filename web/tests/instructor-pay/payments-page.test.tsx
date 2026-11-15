import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { InstructorPayments } from "@/components/staff/instructor-pay/instructor-payments-page";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { BATCH, HELD_ROWS, MILON, TUHIN, WAITING_ROWS, unsettledPage } from "./fixtures";

const COUNTS = { held: 4, waitingApproval: 2, heldAmount: 1900 };

function payRoutes(extra: Record<string, unknown> = {}) {
  const rows = [...HELD_ROWS, ...WAITING_ROWS];
  return mockApi({
    "GET /api/v1/offline-batches": [BATCH],
    "GET /api/v1/offline-batches/batch-1/classes": rows,
    "GET /api/v1/offline-classes/unsettled": unsettledPage(rows),
    "GET /api/v1/offline-classes/unsettled/counts": COUNTS,
    "GET /api/v1/offline-instructors": [MILON, TUHIN],
    ...extra,
  });
}

describe("instructor payments page: grouping", () => {
  it("groups the held classes by instructor, with the name, the count and the amount due", async () => {
    payRoutes();
    renderWithProviders(<InstructorPayments />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: "/staff/offline-instructor-payments",
    });

    const milon = await screen.findByRole("button", { name: /Milon/ });
    expect(within(milon).getByText(/3 classes/)).toBeInTheDocument();
    expect(within(milon).getByText(/৳1,500\.00 due/)).toBeInTheDocument();

    const tuhin = screen.getByRole("button", { name: /Tuhin Hossain/ });
    expect(within(tuhin).getByText(/1 class\b/)).toBeInTheDocument();
    expect(within(tuhin).getByText(/৳400\.00 due/)).toBeInTheDocument();
  });

  it("opens a group to its classes, each with Record payment and Mark not taken", async () => {
    const user = userEvent.setup();
    payRoutes();
    renderWithProviders(<InstructorPayments />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: "/staff/offline-instructor-payments",
    });

    await user.click(await screen.findByRole("button", { name: /Milon/ }));
    expect(screen.getByText("Evidence Act")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Record payment" })).toHaveLength(3);
    expect(screen.getAllByRole("button", { name: "Mark not taken" })).toHaveLength(3);
  });

  it("shows the empty state when nothing is held", async () => {
    mockApi({
      "GET /api/v1/offline-batches": [BATCH],
      "GET /api/v1/offline-batches/batch-1/classes": [],
      "GET /api/v1/offline-classes/unsettled": unsettledPage([]),
      "GET /api/v1/offline-classes/unsettled/counts": { held: 0, waitingApproval: 0, heldAmount: 0 },
      "GET /api/v1/offline-instructors": [],
    });
    renderWithProviders(<InstructorPayments />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: "/staff/offline-instructor-payments",
    });

    expect(await screen.findByText("Nothing to settle.")).toBeInTheDocument();
    expect(screen.getByText("Classes appear here after their time has passed.")).toBeInTheDocument();
  });
});

describe("record payment dialog", () => {
  it("prefills the amount with the class's due amount", async () => {
    const user = userEvent.setup();
    payRoutes();
    renderWithProviders(<InstructorPayments />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: "/staff/offline-instructor-payments",
    });

    await user.click(await screen.findByRole("button", { name: /Milon/ }));
    await user.click(screen.getAllByRole("button", { name: "Record payment" })[0] as HTMLElement);

    const dialog = await screen.findByRole("dialog", { name: "Record payment" });
    expect(within(dialog).getByLabelText(/Amount paid/)).toHaveValue("500");
    expect(within(dialog).getByText("Due: ৳500.00")).toBeInTheDocument();
  });
});

describe("approvals (Master Admin)", () => {
  it("lists the selected rows in one confirmation before it calls the API", async () => {
    const user = userEvent.setup();
    const api = payRoutes({
      "POST /api/v1/offline-class-payments/approve-many": [{ id: "pay-1", ok: true, message: null }],
      "POST /api/v1/offline-class-decisions/approve-many": [{ id: "dec-1", ok: true, message: null }],
    });
    renderWithProviders(<InstructorPayments />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: "/staff/offline-instructor-payments?tab=waiting",
    });

    await user.click(await screen.findByRole("checkbox", { name: /Select Evidence Act by Milon/ }));
    await user.click(screen.getByRole("checkbox", { name: /Select Contract Act by Milon/ }));
    await user.click(screen.getByRole("button", { name: "Approve 2" }));

    const dialog = await screen.findByRole("dialog", { name: "Approve 2 entries?" });
    expect(within(dialog).getByText(/Evidence Act/)).toBeInTheDocument();
    expect(within(dialog).getByText(/Contract Act/)).toBeInTheDocument();
    // Nothing is sent until the person confirms.
    expect(api.calls.some((call) => call.path.includes("approve-many"))).toBe(false);

    await user.click(within(dialog).getByRole("button", { name: "Approve 2" }));

    await waitFor(() => expect(api.calls.some((call) => call.path.endsWith("offline-class-payments/approve-many"))).toBe(true));
    const payments = api.calls.find((call) => call.path.endsWith("offline-class-payments/approve-many"));
    const decisions = api.calls.find((call) => call.path.endsWith("offline-class-decisions/approve-many"));
    expect(payments?.body).toEqual({ ids: ["pay-1"] });
    expect(decisions?.body).toEqual({ ids: ["dec-1"] });
  });

  it("gives staff no approve buttons on the waiting tab, and says the entries are theirs", async () => {
    payRoutes();
    renderWithProviders(<InstructorPayments />, {
      user: fixtures.systemAdmin,
      locale: "en",
      route: "/staff/offline-instructor-payments?tab=waiting",
    });

    expect(await screen.findByText("Your entries waiting")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Approve" })).toBeNull();
    expect(screen.queryByRole("checkbox")).toBeNull();
  });
});
