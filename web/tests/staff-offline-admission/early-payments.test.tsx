import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AdmissionDetailView } from "@/components/staff/offline/admission-detail";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { ADMISSION_ID, detail, page } from "./fixtures";

/*
 * Payments before onboarding (instalment-plan-and-early-payments.md, E3, E4): once the setup has a batch and instalments, the admission page
 * shows a Payments card with the planned instalments and Record payment. An approved payment offers its receipt as soon as it is recorded.
 */

const PLAN = [
  { seq: 1, dueOn: "2026-12-09", amount: 4000 },
  { seq: 2, dueOn: "2027-01-09", amount: 3000 },
  { seq: 3, dueOn: "2027-02-09", amount: 2000 },
];

const APPROVED = {
  id: "pay-1",
  admissionId: ADMISSION_ID,
  amount: 4500,
  method: "CASH",
  reference: "CASH-20261115",
  paidOn: "2026-11-15",
  status: "APPROVED",
  receiptNo: "RCPT-2026-000042",
  recordedBy: "m1",
  recordedByName: "Master",
  recordedAt: "2026-11-15T10:00:00Z",
};

function renderDetail(user: typeof fixtures.masterAdmin | typeof fixtures.systemAdmin, payments: unknown[] = [], extra: Record<string, unknown> = {}) {
  const api = mockApi({
    [`GET /api/v1/offline-admissions/${ADMISSION_ID}`]: detail({
      state: "SUBMITTED",
      readyProblems: [],
      setup: {
        batchId: "batch-1",
        batchNameEn: "BJS Evening",
        batchNameBn: "বিজেএস সন্ধ্যা",
        courseFee: 9000,
        installments: 3,
        installmentPlan: PLAN,
        customPlan: true,
        admittedOn: "2026-11-09",
        firstDueOn: "2026-12-09",
        courseEndsOn: "2027-02-09",
      },
    }),
    [`GET /api/v1/offline-admissions/${ADMISSION_ID}/history`]: [],
    [`GET /api/v1/offline-admissions/${ADMISSION_ID}/fees`]: {
      admissionId: ADMISSION_ID,
      admissionStatus: "SUBMITTED",
      personName: "Rahim",
      owed: 9000 - payments.reduce((sum: number, p) => sum + ((p as { status: string }).status === "APPROVED" ? 4500 : 0), 0),
      overdue: 0,
      dues: [],
      payments,
    },
    "GET /api/v1/offline-batches": [],
    "GET /api/v1/live-exams": page([]),
    "GET /api/v1/live-exam-modules": page([]),
    ...extra,
  });
  renderWithProviders(<AdmissionDetailView id={ADMISSION_ID} />, { user, locale: "en", route: `/staff/admissions/${ADMISSION_ID}` });
  return api;
}

describe("payments before onboarding (E3)", () => {
  it("shows the planned instalments, covered in order by the approved payments, with Record payment", async () => {
    renderDetail(fixtures.systemAdmin, [APPROVED]);

    const card = (await screen.findByRole("heading", { name: "Payments", level: 2 })).closest("section") as HTMLElement;
    expect(await within(card).findByText(/The planned instalments/)).toBeInTheDocument();
    expect(within(card).getByRole("button", { name: "Record payment" })).toBeInTheDocument();
    // 4,500 covers the first instalment (4,000) and 500 of the second.
    expect(within(card).getByText(/৳4,500.00 still owed|owed.*4,500/)).toBeInTheDocument();
    expect(within(card).getByText(/RCPT-2026-000042/)).toBeInTheDocument();
    expect(within(card).getByRole("button", { name: "Open receipt" })).toBeInTheDocument();
  });

  it("is not shown before the batch and the instalments are set", async () => {
    mockApi({
      [`GET /api/v1/offline-admissions/${ADMISSION_ID}`]: detail({ state: "SUBMITTED", readyProblems: [], setup: { ...detail().setup, batchId: null } }),
      [`GET /api/v1/offline-admissions/${ADMISSION_ID}/history`]: [],
      "GET /api/v1/offline-batches": [],
    });
    renderWithProviders(<AdmissionDetailView id={ADMISSION_ID} />, {
      user: fixtures.systemAdmin,
      locale: "en",
      route: `/staff/admissions/${ADMISSION_ID}`,
    });

    expect(await screen.findByRole("heading", { name: "Setup" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Payments", level: 2 })).not.toBeInTheDocument();
  });

  it("after the Master Admin records a payment, the dialog offers its receipt at once (E4)", async () => {
    const user = userEvent.setup();
    renderDetail(fixtures.masterAdmin, [], { [`POST /api/v1/offline-admissions/${ADMISSION_ID}/payments`]: APPROVED });

    await user.click(await screen.findByRole("button", { name: "Record payment" }));
    const dialog = await screen.findByRole("dialog");
    await user.clear(within(dialog).getByLabelText(/Amount/));
    await user.type(within(dialog).getByLabelText(/Amount/), "4500");
    await user.click(within(dialog).getByRole("button", { name: "Record payment" }));

    expect(await within(dialog).findByText("Approved. Receipt RCPT-2026-000042 is ready.")).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Open receipt" })).toBeInTheDocument();
  });

  it("a staff payment says the receipt follows the approval", async () => {
    const user = userEvent.setup();
    renderDetail(fixtures.systemAdmin, [], {
      [`POST /api/v1/offline-admissions/${ADMISSION_ID}/payments`]: { ...APPROVED, status: "PENDING", receiptNo: null },
    });

    await user.click(await screen.findByRole("button", { name: "Record payment" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Record payment" }));

    expect(await within(dialog).findByText(/Waiting for the Master Admin's approval/)).toBeInTheDocument();
    expect(within(dialog).queryByRole("button", { name: "Open receipt" })).not.toBeInTheDocument();
  });
});
