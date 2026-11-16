import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { InstructorPayments } from "@/components/staff/instructor-pay/instructor-payments-page";
import { InstructorDetail } from "@/components/staff/instructor-pay/instructor-detail";
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

    const milon = await screen.findByRole("link", { name: /Milon/ });
    expect(within(milon).getByText(/3 classes/)).toBeInTheDocument();
    expect(within(milon).getByText(/৳1,500\.00 due/)).toBeInTheDocument();

    const tuhin = screen.getByRole("link", { name: /Tuhin Hossain/ });
    expect(within(tuhin).getByText(/1 class\b/)).toBeInTheDocument();
    expect(within(tuhin).getByText(/৳400\.00 due/)).toBeInTheDocument();
  });

  it("opens an instructor's own page from their group (17 Nov)", async () => {
    payRoutes();
    renderWithProviders(<InstructorPayments />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: "/staff/offline-instructor-payments",
    });

    expect(await screen.findByRole("link", { name: /Milon/ })).toHaveAttribute("href", `/staff/offline-instructors/${MILON.id}`);
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
    // 17 Nov: classes are settled on the instructor's own page.
    const user = userEvent.setup();
    payRoutes({ [`GET /api/v1/offline-instructors/${MILON.id}/other-payments`]: [] });
    renderWithProviders(<InstructorDetail id={MILON.id} />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: `/staff/offline-instructors/${MILON.id}`,
    });

    expect((await screen.findAllByRole("button", { name: "Mark not taken" })).length).toBeGreaterThan(0);
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

describe("a class with no rate, on the instructor's page (17 Nov)", () => {
  it("says the batch has no rate, and Set rate saves the instructor's rate in that batch", async () => {
    const user = userEvent.setup();
    const noRate = { ...HELD_ROWS[0], amount: null, rateOverride: null } as (typeof HELD_ROWS)[number];
    const api = mockApi({
      "GET /api/v1/offline-batches": [BATCH],
      "GET /api/v1/offline-batches/batch-1/classes": [noRate],
      "GET /api/v1/offline-instructors": [MILON, TUHIN],
      [`GET /api/v1/offline-instructors/${MILON.id}/other-payments`]: [],
      [`PUT /api/v1/offline-batches/batch-1/rates/${MILON.id}`]: { instructorId: MILON.id, name: MILON.name, rate: 450 },
    });
    renderWithProviders(<InstructorDetail id={MILON.id} />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: `/staff/offline-instructors/${MILON.id}`,
    });

    expect((await screen.findAllByText("No rate set for this batch")).length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: "Record payment" })).toBeNull();
    await user.click(screen.getAllByRole("button", { name: "Set rate" })[0] as HTMLElement);

    const dialog = await screen.findByRole("dialog", { name: "Set the class rate" });
    await user.type(within(dialog).getByLabelText(/Amount per class/), "450");
    await user.click(within(dialog).getByRole("button", { name: "Save rate" }));

    await waitFor(() =>
      expect(api.calls.find((call) => call.method === "PUT" && call.path.endsWith(`/rates/${MILON.id}`))?.body).toEqual({ rate: "450" }),
    );
  });
});

describe("a class with its own rate, on the instructor's page (17 Nov)", () => {
  it("is tagged Own rate, and a batch rate set from it removes its own rate so it follows the batch", async () => {
    const user = userEvent.setup();
    const own = { ...HELD_ROWS[0], amount: 350, rateOverride: 350 } as (typeof HELD_ROWS)[number];
    const api = mockApi({
      "GET /api/v1/offline-batches": [BATCH],
      "GET /api/v1/offline-batches/batch-1/classes": [own, HELD_ROWS[1], HELD_ROWS[2]],
      "GET /api/v1/offline-instructors": [MILON, TUHIN],
      [`GET /api/v1/offline-instructors/${MILON.id}/other-payments`]: [],
      [`PUT /api/v1/offline-batches/batch-1/rates/${MILON.id}`]: { instructorId: MILON.id, name: MILON.name, rate: 450 },
      [`PATCH /api/v1/offline-classes/${own.id}`]: { ...own, amount: 450, rateOverride: null },
    });
    renderWithProviders(<InstructorDetail id={MILON.id} />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: `/staff/offline-instructors/${MILON.id}`,
    });

    expect((await screen.findAllByText("Own rate")).length).toBeGreaterThan(0);
    const ownRow = screen.getAllByText("Own rate")[0]?.closest("tr") as HTMLElement;
    await user.click(within(ownRow).getByRole("button", { name: "Change rate" }));

    const dialog = await screen.findByRole("dialog", { name: "Set the class rate" });
    // A class with its own rate opens on "This class only"; choosing the batch says what changes.
    expect(within(dialog).getByLabelText("This class only")).toBeChecked();
    await user.click(within(dialog).getByLabelText(/Every class of Milon/));
    expect(within(dialog).getByText("Unpaid classes that get this rate: 3")).toBeInTheDocument();
    expect(within(dialog).getByText(/own rate is removed/)).toBeInTheDocument();
    await user.clear(within(dialog).getByLabelText(/Amount per class/));
    await user.type(within(dialog).getByLabelText(/Amount per class/), "450");
    await user.click(within(dialog).getByRole("button", { name: "Save rate" }));

    await waitFor(() =>
      expect(api.calls.find((call) => call.method === "PATCH" && call.path.endsWith(`/offline-classes/${own.id}`))?.body).toEqual({
        version: own.version,
        rateOverride: null,
      }),
    );
    expect(api.calls.some((call) => call.method === "PUT" && call.path.endsWith(`/rates/${MILON.id}`))).toBe(true);
  });
});

describe("the waiting tab shows the rate beside a recorded payment (17 Nov)", () => {
  it("says the rate per class and how far the payment is from it", async () => {
    const paid = { ...WAITING_ROWS[0], amount: 400 } as (typeof WAITING_ROWS)[number];
    mockApi({
      "GET /api/v1/offline-batches": [BATCH],
      "GET /api/v1/offline-batches/batch-1/classes": [paid],
      "GET /api/v1/offline-classes/unsettled": unsettledPage([paid]),
      "GET /api/v1/offline-classes/unsettled/counts": { held: 0, waitingApproval: 1, heldAmount: 0 },
      "GET /api/v1/offline-instructors": [MILON, TUHIN],
    });
    renderWithProviders(<InstructorPayments />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: "/staff/offline-instructor-payments?tab=waiting",
    });

    expect(await screen.findByText(/Rate per class: ৳400\.00/)).toBeInTheDocument();
    expect(screen.getByText("৳100.00 more than the rate")).toBeInTheDocument();
  });
});
