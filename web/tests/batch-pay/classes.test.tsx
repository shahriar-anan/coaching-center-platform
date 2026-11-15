import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { BatchClasses } from "@/components/staff/offline/batch-classes";
import { dhakaToday } from "@/components/staff/offline/offline-model";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { classRow, milon, rahim } from "./fixtures";

vi.mock("@/i18n/messages", async (importOriginal) => {
  const mod = await importOriginal<typeof import("@/i18n/messages")>();
  const { withBatchPay } = await import("./catalog");
  return withBatchPay(mod);
});

type Call = { method: string; path: string; body: unknown };

function setup() {
  return mockApi({
    "GET /api/v1/offline-batches/b1/classes": [
      classRow({ id: "c1", subject: "Physics", status: "HELD", instructorId: "i1", instructorName: "Milon Hossain", amount: 1500 }),
      classRow({
        id: "c2",
        subject: "Chemistry",
        status: "PAID",
        instructorId: "i2",
        instructorName: "Rahim Sir",
        amount: 800,
        payment: { id: "p1", instructorId: "i2", amount: 800, method: "CASH", reference: null, paidOn: "2026-11-02", note: null, status: "APPROVED" },
      }),
      classRow({ id: "c3", subject: "Biology", status: "SCHEDULED", instructorId: "i1", instructorName: "Milon Hossain", amount: 1500 }),
    ],
    "GET /api/v1/offline-instructors": [milon, rahim],
    "POST /api/v1/offline-classes/c1/payment": () => classRow({ id: "c1", status: "PAID_PENDING" }),
    "POST /api/v1/offline-classes/c1/not-taken": () => classRow({ id: "c1", status: "NOT_TAKEN_PENDING" }),
  });
}

/** The list item that holds a class, found by its subject. */
async function rowOf(subject: string): Promise<HTMLElement> {
  const row = (await screen.findByText(subject)).closest("li");
  if (!row) {
    throw new Error(`no row for ${subject}`);
  }
  return row;
}

describe("classes tab (plan 8.1)", () => {
  it("offers Record payment and Mark not taken on a held class", async () => {
    setup();
    renderWithProviders(<BatchClasses batchId="b1" />, { user: fixtures.masterAdmin, locale: "en" });

    const held = await rowOf("Physics");
    expect(await within(held).findByRole("button", { name: "Record payment" })).toBeInTheDocument();
    expect(within(held).getByRole("button", { name: "Mark not taken" })).toBeInTheDocument();
  });

  it("shows a paid class read only", async () => {
    setup();
    renderWithProviders(<BatchClasses batchId="b1" />, { user: fixtures.masterAdmin, locale: "en" });

    const paid = await rowOf("Chemistry");
    expect(await within(paid).findByText("Read only")).toBeInTheDocument();
    expect(within(paid).queryByRole("button", { name: "Record payment" })).not.toBeInTheDocument();
    expect(within(paid).queryByRole("button", { name: "Mark not taken" })).not.toBeInTheDocument();
  });

  it("opens the payment dialog prefilled with the due amount and today's date", async () => {
    const user = userEvent.setup();
    setup();
    renderWithProviders(<BatchClasses batchId="b1" />, { user: fixtures.masterAdmin, locale: "en" });

    await user.click(await within(await rowOf("Physics")).findByRole("button", { name: "Record payment" }));

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByLabelText(/Amount paid/)).toHaveValue("1500");
    expect(within(dialog).getByLabelText(/Paid on/)).toHaveValue(dhakaToday());
  });

  it("asks for a note when the amount differs from the due amount", async () => {
    const user = userEvent.setup();
    const api = setup();
    renderWithProviders(<BatchClasses batchId="b1" />, { user: fixtures.masterAdmin, locale: "en" });

    await user.click(await within(await rowOf("Physics")).findByRole("button", { name: "Record payment" }));
    const dialog = await screen.findByRole("dialog");
    const amount = within(dialog).getByLabelText(/Amount paid/);
    await user.clear(amount);
    await user.type(amount, "1200");

    expect(within(dialog).getByText(/300(\.00)? less than the due amount/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Record payment" }));

    expect(await within(dialog).findByText("Say why the amount differs from the due amount.")).toBeInTheDocument();
    expect((api.calls as Call[]).some((call) => call.path.endsWith("/payment"))).toBe(false);
  });

  it("sends the not-taken reason from a one-tap choice", async () => {
    const user = userEvent.setup();
    const api = setup();
    renderWithProviders(<BatchClasses batchId="b1" />, { user: fixtures.masterAdmin, locale: "en" });

    await user.click(await within(await rowOf("Physics")).findByRole("button", { name: "Mark not taken" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Holiday" }));
    expect(within(dialog).getByLabelText(/Reason/)).toHaveValue("Holiday");
    await user.click(within(dialog).getByRole("button", { name: "Mark not taken" }));

    const post = (api.calls as Call[]).find((call) => call.path === "/api/v1/offline-classes/c1/not-taken");
    expect(post?.body).toEqual({ reason: "Holiday" });
  });

  it("refuses a not-taken mark without a reason", async () => {
    const user = userEvent.setup();
    const api = setup();
    renderWithProviders(<BatchClasses batchId="b1" />, { user: fixtures.masterAdmin, locale: "en" });

    await user.click(await within(await rowOf("Physics")).findByRole("button", { name: "Mark not taken" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Mark not taken" }));

    expect(await within(dialog).findByText("Enter the reason.")).toBeInTheDocument();
    expect((api.calls as Call[]).some((call) => call.path.endsWith("/not-taken"))).toBe(false);
  });
});
