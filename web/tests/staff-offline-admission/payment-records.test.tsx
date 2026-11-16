import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { PaymentRecordsPage, periodRange } from "@/components/staff/payment-records/payment-records-page";
import type { PaymentEdit, PaymentRow } from "@/lib/api/exam/payment-records";
import { apiError, fixtures, mockApi, renderWithProviders } from "../helpers";

function row(overrides: Partial<PaymentRow> = {}): PaymentRow {
  return {
    kind: "FEE",
    id: "pay-1",
    paidOn: "2026-11-10",
    amount: "3000.00",
    method: "CASH",
    reference: "CASH-20261110",
    status: "APPROVED",
    receiptNo: "OF-2026-0001",
    personName: "Rahim Uddin",
    batchId: "batch-1",
    batchNameEn: "BJS Evening",
    batchNameBn: "বিজেএস সান্ধ্য",
    recordedByName: "Staff One",
    recordedAt: "2026-11-10T05:00:00Z",
    direction: "INCOME",
    itemType: "BATCH",
    editable: true,
    ...overrides,
  };
}

function edit(overrides: Partial<PaymentEdit> = {}): PaymentEdit {
  return {
    id: "edit-1",
    kind: "FEE",
    paymentId: "pay-1",
    status: "PENDING",
    before: { amount: "3000.00", paidOn: "2026-11-10", method: "CASH", reference: "CASH-20261110" },
    after: { amount: "3500.00", paidOn: "2026-11-10", method: "CASH", reference: "CASH-20261110" },
    reason: "Typed the wrong amount",
    personName: "Rahim Uddin",
    requestedByName: "Staff One",
    requestedAt: "2026-11-11T05:00:00Z",
    ...overrides,
  };
}

function routes(extra: Record<string, unknown> = {}) {
  return mockApi({
    "GET /api/v1/payment-records": [
      row(),
      row({ kind: "CLASS", id: "cls-1", personName: "Ayesha Khan", amount: 400, receiptNo: null, instructorId: "ins-1", correctedAt: "2026-11-12T05:00:00Z" }),
      row({ id: "pay-2", personName: "Karim Ali", amount: 1500, pendingEdit: { id: "edit-2", after: row().amount as never, reason: "x", requestedAt: "2026-11-11T05:00:00Z" } as never }),
    ],
    "GET /api/v1/payment-records/edits": ({ url }: { url: URL }) =>
      url.searchParams.get("status") === "PENDING" ? [edit()] : [edit({ id: "edit-3", status: "REJECTED", decisionReason: "Receipt says 3000", decidedByName: "Master" })],
    "GET /api/v1/offline-batches": [{ id: "batch-1", nameEn: "BJS Evening", nameBn: "বিজেএস সান্ধ্য" }],
    "GET /api/v1/offline-instructors": [{ id: "ins-1", name: "Ayesha Khan", phone: null, note: null, aliases: [], archived: false }],
    ...extra,
  });
}

describe("payment records period", () => {
  it("works out today, this month and last month in Dhaka dates", () => {
    expect(periodRange("today", "2026-11-15")).toEqual({ from: "2026-11-15", to: "2026-11-15" });
    expect(periodRange("month", "2026-11-15")).toEqual({ from: "2026-11-01", to: "2026-11-15" });
    expect(periodRange("last", "2026-01-15")).toEqual({ from: "2025-12-01", to: "2025-12-31" });
    expect(periodRange("last", "2026-03-15")).toEqual({ from: "2026-02-01", to: "2026-02-28" });
    expect(periodRange("custom", "2026-03-15")).toBeNull();
  });
});

describe("payment records page (payment-records-edit-plan.md)", () => {
  it("lists payments with their badges and asks the API with this month's dates and the filters", async () => {
    const api = routes();
    renderWithProviders(<PaymentRecordsPage />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/payment-records?kind=CLASS&instructorId=ins-1" });

    const corrected = (await screen.findAllByText("Ayesha Khan")).map((node) => node.closest("tr")).find(Boolean) as HTMLElement;
    expect(within(corrected).getByText("Corrected")).toBeInTheDocument();
    const waiting = (await screen.findAllByText("Karim Ali"))[0]?.closest("tr") as HTMLElement;
    expect(within(waiting).getByText("Correction waiting")).toBeInTheDocument();
    expect(within(waiting).queryByRole("button", { name: "Edit" })).not.toBeInTheDocument();

    const list = api.calls.find((call) => call.path === "/api/v1/payment-records");
    const search = new URLSearchParams(list?.search);
    expect(search.get("kind")).toBe("CLASS");
    expect(search.get("instructorId")).toBe("ins-1");
    expect(search.get("from")).toMatch(/^\d{4}-\d{2}-01$/);
  });

  it("sends a staff correction with its reason and says it waits for the Master Admin", async () => {
    const api = routes({ "POST /api/v1/payment-records/fee/pay-1/edits": edit() });
    renderWithProviders(<PaymentRecordsPage />, { user: fixtures.systemAdmin, locale: "en", route: "/staff/payment-records" });

    const first = (await screen.findAllByText("Rahim Uddin"))[0]?.closest("tr") as HTMLElement;
    fireEvent.click(within(first).getByRole("button", { name: "Edit" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/waits for the Master Admin/)).toBeInTheDocument();

    fireEvent.click(within(dialog).getByRole("button", { name: "Send for finalizing" }));
    expect(await within(dialog).findByText("Say why the payment is being corrected.")).toBeInTheDocument();

    fireEvent.change(within(dialog).getByLabelText(/Amount/), { target: { value: "3500" } });
    fireEvent.change(within(dialog).getByLabelText(/Why is it being corrected/), { target: { value: "Typed the wrong amount" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Send for finalizing" }));

    await waitFor(() => expect(api.calls.some((call) => call.method === "POST")).toBe(true));
    const post = api.calls.find((call) => call.method === "POST");
    expect(post?.body).toMatchObject({ amount: "3500", reason: "Typed the wrong amount", reference: "CASH-20261110" });
  });

  it("refuses to send a correction that changes nothing", async () => {
    const api = routes();
    renderWithProviders(<PaymentRecordsPage />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/payment-records" });

    const first = (await screen.findAllByText("Rahim Uddin"))[0]?.closest("tr") as HTMLElement;
    fireEvent.click(within(first).getByRole("button", { name: "Edit" }));
    const dialog = await screen.findByRole("dialog");
    fireEvent.change(within(dialog).getByLabelText(/Why is it being corrected/), { target: { value: "Check" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Save correction" }));

    expect(await within(dialog).findByText("Nothing is changed yet.")).toBeInTheDocument();
    expect(api.calls.some((call) => call.method === "POST")).toBe(false);
  });

  it("shows the API's limit on the amount in the page's words", async () => {
    const refusal = apiError(400, "VALIDATION_FAILED");
    Object.assign(refusal.body, { fieldErrors: [{ field: "amount", message: "The most this payment can be is BDT 9000.00." }] });
    routes({ "POST /api/v1/payment-records/fee/pay-1/edits": refusal });
    renderWithProviders(<PaymentRecordsPage />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/payment-records" });
    const first = (await screen.findAllByText("Rahim Uddin"))[0]?.closest("tr") as HTMLElement;
    fireEvent.click(within(first).getByRole("button", { name: "Edit" }));
    const dialog = await screen.findByRole("dialog");
    fireEvent.change(within(dialog).getByLabelText(/Amount/), { target: { value: "13000" } });
    fireEvent.change(within(dialog).getByLabelText(/Why is it being corrected/), { target: { value: "More" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Save correction" }));

    expect(await within(dialog).findByText(/The most this payment can be is .*9,000\.00/)).toBeInTheDocument();
  });

  it("gives the Master Admin Finalize and Reject on a waiting correction, with the old and new values", async () => {
    const api = routes({ "POST /api/v1/payment-edits/edit-1/finalize": edit({ status: "FINALIZED" }) });
    renderWithProviders(<PaymentRecordsPage />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/payment-records?tab=waiting" });

    const waiting = (await screen.findAllByText("Rahim Uddin"))[0]?.closest("tr") as HTMLElement;
    expect(within(waiting).getByText(/Typed the wrong amount/)).toBeInTheDocument();
    expect(within(waiting).getByText(/3,000\.00/)).toBeInTheDocument();
    expect(within(waiting).getByText(/3,500\.00/)).toBeInTheDocument();
    fireEvent.click(within(waiting).getByRole("button", { name: "Finalize" }));
    await waitFor(() => expect(api.calls.some((call) => call.path === "/api/v1/payment-edits/edit-1/finalize")).toBe(true));
    expect(within(waiting).getByRole("button", { name: "Reject" })).toBeInTheDocument();
  });

  it("shows staff their waiting correction with no Finalize button", async () => {
    routes();
    renderWithProviders(<PaymentRecordsPage />, { user: fixtures.systemAdmin, locale: "en", route: "/staff/payment-records?tab=waiting" });

    expect((await screen.findAllByText("Rahim Uddin")).length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: "Finalize" })).not.toBeInTheDocument();
  });

  it("lists decided corrections in the history with the rejection reason", async () => {
    routes();
    renderWithProviders(<PaymentRecordsPage />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/payment-records?tab=history" });

    expect((await screen.findAllByText(/Receipt says 3000/)).length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: "Finalize" })).not.toBeInTheDocument();
  });
});

describe("every money stream on one page (16 Nov)", () => {
  it("lists online purchases and staff pay read-only, and sums money in and out", async () => {
    mockApi({
      "GET /api/v1/payment-records": [
        row({ id: "fee-1", amount: "3000.00" }),
        row({
          kind: "ONLINE",
          id: "ord-1",
          amount: "450.00",
          method: "ONLINE",
          reference: "TRAN-1",
          itemType: "PACKAGE",
          itemNameEn: "BJS Full Package",
          editable: false,
          personName: "Karim Ali",
        }),
        row({ kind: "STAFF", id: "pay-9", amount: "1000.00", direction: "EXPENSE", itemType: "STAFF", editable: false, personName: "Sadia" }),
      ],
      "GET /api/v1/payment-records/edits": [],
      "GET /api/v1/offline-batches": [],
      "GET /api/v1/offline-instructors": [],
    });
    renderWithProviders(<PaymentRecordsPage />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/payment-records" });

    const online = (await screen.findAllByText("Karim Ali"))[0]?.closest("tr") as HTMLElement;
    expect(within(online).getByText(/Package: BJS Full Package/)).toBeInTheDocument();
    expect(within(online).getByText("Online (gateway)")).toBeInTheDocument();
    expect(within(online).queryByRole("button", { name: "Edit" })).toBeNull();
    expect(within(online).getByRole("link", { name: "Payments" })).toHaveAttribute("href", "/staff/payments");
    const staff = (await screen.findAllByText("Sadia"))[0]?.closest("tr") as HTMLElement;
    expect(within(staff).getByRole("link", { name: "Staff and online instructor pay" })).toHaveAttribute("href", "/staff/pay");
    expect(screen.getByText(/money in .*3,450\.00.* money out .*1,000\.00.* net .*2,450\.00/)).toBeInTheDocument();
  });

  it("shows only the sources of money out when Money out is chosen, and asks the API for them", async () => {
    const api = routes();
    renderWithProviders(<PaymentRecordsPage />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/payment-records?direction=EXPENSE" });

    const options = (await screen.findByLabelText("Source")).querySelectorAll("option");
    expect([...options].map((option) => option.textContent)).toEqual(["All sources", "Offline instructor pay", "Other instructor payment", "Staff and online instructor pay"]);
    await waitFor(() => expect(api.calls.some((call) => call.path === "/api/v1/payment-records" && call.search.includes("direction=EXPENSE"))).toBe(true));
  });
});
