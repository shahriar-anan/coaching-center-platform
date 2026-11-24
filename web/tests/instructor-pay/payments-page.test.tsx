import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { InstructorPayments } from "@/components/staff/instructor-pay/instructor-payments-page";
import { InstructorDetail } from "@/components/staff/instructor-pay/instructor-detail";
import { dhakaToday } from "@/components/staff/offline/offline-model";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { BATCH, CARDS, MILON, OLD_CLASS_PAYMENT, SUMMARY, TUHIN, list, payment } from "./fixtures";

/*
 * Instructor pay made simple (24 Nov, instructor-pay-simple-plan.md): the Instructor payments page, and one instructor's page with
 * Record payment and Payment history.
 */

function detailRoutes(extra: Record<string, unknown> = {}) {
  return mockApi({
    "GET /api/v1/offline-instructors": [MILON, TUHIN],
    "GET /api/v1/offline-batches": [BATCH],
    [`GET /api/v1/offline-instructors/${MILON.id}/pay-summary`]: SUMMARY,
    [`GET /api/v1/offline-instructors/${MILON.id}/payments`]: list([payment(), OLD_CLASS_PAYMENT]),
    ...extra,
  });
}

function renderDetail(tab = "") {
  return renderWithProviders(<InstructorDetail id={MILON.id} />, {
    user: fixtures.masterAdmin,
    locale: "en",
    route: `/staff/offline-instructors/${MILON.id}${tab ? `?tab=${tab}` : ""}`,
  });
}

describe("instructor payments page", () => {
  it("shows the cards and every payment, from this month to start with, each linked to its instructor", async () => {
    const api = mockApi({
      "GET /api/v1/offline-instructors": [MILON, TUHIN],
      "GET /api/v1/offline-batches": [BATCH],
      "GET /api/v1/offline-instructor-payments/cards": CARDS,
      "GET /api/v1/offline-instructor-payments": list([payment(), payment({ id: "pay-2", instructorId: TUHIN.id, instructorName: TUHIN.name })]),
    });
    renderWithProviders(<InstructorPayments />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/offline-instructor-payments" });

    expect(await screen.findByText(/Paid this month · 2 payments/)).toBeInTheDocument();
    expect((await screen.findAllByRole("link", { name: TUHIN.name })).length).toBeGreaterThan(0);
    expect(screen.getAllByRole("link", { name: MILON.name })[0]).toHaveAttribute("href", `/staff/offline-instructors/${MILON.id}`);
    // Nothing is recorded, edited or voided here.
    expect(screen.queryByRole("button", { name: "Void" })).not.toBeInTheDocument();
    const call = api.calls.find((item) => item.path === "/api/v1/offline-instructor-payments");
    expect(call?.search).toContain(`from=${dhakaToday().slice(0, 7)}-01`);
  });
});

describe("an instructor's page: Record payment (D7)", () => {
  it("shows the summary and starts on Record payment with today's date", async () => {
    detailRoutes();
    renderDetail();

    expect(await screen.findByRole("tab", { name: "Record payment" })).toHaveAttribute("aria-selected", "true");
    expect(await screen.findByText("Total paid")).toBeInTheDocument();
    expect(screen.getByLabelText(/^Date/)).toHaveValue(dhakaToday());
    expect(screen.getAllByText(BATCH.nameEn).length).toBeGreaterThan(0);
  });

  it("asks for a description for Other and sends nothing until it is there", async () => {
    const user = userEvent.setup();
    const api = detailRoutes();
    renderDetail();

    await user.selectOptions(await screen.findByLabelText(/^Type/), "OTHER");
    await user.type(screen.getByLabelText(/^Amount/), "300");
    await user.click(screen.getByRole("button", { name: "Record payment" }));

    expect(await screen.findByText("Say what the payment is for.")).toBeInTheDocument();
    expect(api.calls.some((call) => call.method === "POST")).toBe(false);
  });

  it("records a lecture payment with the number of classes, at once", async () => {
    const user = userEvent.setup();
    const api = detailRoutes({ [`POST /api/v1/offline-instructors/${MILON.id}/payments`]: payment() });
    renderDetail();

    await user.selectOptions(await screen.findByLabelText(/^Type/), "LECTURE");
    await user.type(screen.getByLabelText(/^Number of classes/), "4");
    await user.type(screen.getByLabelText(/^Amount/), "2000");
    await user.selectOptions(screen.getByLabelText(/^Batch/), BATCH.id);
    await user.click(screen.getByRole("button", { name: "Record payment" }));

    await waitFor(() =>
      expect(api.calls.find((call) => call.method === "POST")?.body).toEqual({
        paidOn: dhakaToday(),
        kind: "LECTURE",
        classCount: 4,
        amount: "2000",
        method: "CASH",
        reference: null,
        batchId: BATCH.id,
        description: null,
        note: null,
      }),
    );
    expect(await screen.findByText("Payment recorded.")).toBeInTheDocument();
  });
});

describe("an instructor's page: Payment history", () => {
  it("lists the new payments and the class payments from before", async () => {
    detailRoutes();
    renderDetail("history");

    // The table is drawn twice (a table, and cards for a phone), so each text is found at least once.
    expect((await screen.findAllByText("4 lectures")).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Class: Evidence Act on/).length).toBeGreaterThan(0);
    expect(screen.getByText(/Total paid in this list/)).toBeInTheDocument();
  });

  it("corrects a class payment from before with its money fields only, and a reason", async () => {
    const user = userEvent.setup();
    const api = detailRoutes({ "PATCH /api/v1/offline-instructor-payments/CLASS/old-1": { ...OLD_CLASS_PAYMENT, amount: 450 } });
    renderDetail("history");

    const row = (await screen.findAllByText(/Class: Evidence Act on/))[0].closest("tr") ?? document.body;
    await user.click(within(row as HTMLElement).getByRole("button", { name: "Edit" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/made for a class before 24 Nov 2026/)).toBeInTheDocument();
    expect(within(dialog).queryByLabelText(/^Type/)).not.toBeInTheDocument();

    const amount = within(dialog).getByLabelText(/^Amount/);
    await user.clear(amount);
    await user.type(amount, "450");
    await user.click(within(dialog).getByRole("button", { name: "Save correction" }));
    expect(await within(dialog).findByText("Say why the payment is corrected.")).toBeInTheDocument();

    await user.type(within(dialog).getByLabelText(/Why is it corrected/), "Paid less");
    await user.click(within(dialog).getByRole("button", { name: "Save correction" }));
    await waitFor(() =>
      expect(api.calls.find((call) => call.method === "PATCH")?.body).toEqual({
        paidOn: OLD_CLASS_PAYMENT.paidOn,
        amount: "450",
        method: "CASH",
        reference: null,
        note: null,
        editReason: "Paid less",
      }),
    );
  });

  it("voids a payment with a reason", async () => {
    const user = userEvent.setup();
    const api = detailRoutes({ "POST /api/v1/offline-instructor-payments/OTHER/pay-1/void": payment({ status: "VOIDED" }) });
    renderDetail("history");

    const row = (await screen.findAllByText("4 lectures"))[0].closest("tr") ?? document.body;
    await user.click(within(row as HTMLElement).getByRole("button", { name: "Void" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Void payment" }));
    expect(await within(dialog).findByText("Give a reason.")).toBeInTheDocument();

    await user.type(within(dialog).getByLabelText(/^Reason/), "Entered twice");
    await user.click(within(dialog).getByRole("button", { name: "Void payment" }));
    await waitFor(() =>
      expect(api.calls.find((call) => call.method === "POST" && call.path.endsWith("/void"))?.body).toEqual({ reason: "Entered twice" }),
    );
  });
});
