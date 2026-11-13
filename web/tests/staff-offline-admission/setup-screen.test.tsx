import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import SetupPage from "@/app/(staff)/staff/admissions/[id]/setup/page";
import { AdmissionDetailView } from "@/components/staff/offline/admission-detail";
import type { AdmissionDetail, AdmissionItem, BatchView } from "@/lib/api/exam/offline-admin";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { categoryTree } from "../staff-admin/picker-data";
import { ADMISSION_ID, detail, page } from "./fixtures";

const EMPTY_SETUP = {
  batchId: null,
  batchNameEn: null,
  batchNameBn: null,
  monthlyFee: null,
  admittedOn: null,
  firstDueOn: null,
  courseEndsOn: null,
};

/** One exam the admission already holds, as the detail returns it. */
const EXAM_ITEM: AdmissionItem = {
  id: "item-exam-1",
  scope: "EXAM",
  examId: "e1",
  moduleId: null,
  titleEn: "Mock exam 1",
  titleBn: "মক এক্সাম ১",
  grantId: null,
  addedAt: "2026-11-10T04:00:00Z",
};

/** The routes the detail page and the setup screen read. */
function routes(admission: AdmissionDetail, batches: BatchView[] = []) {
  return mockApi({
    [`GET /api/v1/offline-admissions/${ADMISSION_ID}`]: admission,
    [`GET /api/v1/offline-admissions/${ADMISSION_ID}/history`]: [],
    "GET /api/v1/offline-batches": batches,
    [`PATCH /api/v1/offline-admissions/${ADMISSION_ID}/setup`]: admission,
    "GET /api/v1/live-exams": page([]),
    "GET /api/v1/live-exam-modules": page([]),
    "GET /api/v1/live-exam-categories": categoryTree,
  });
}

/** A batch as the batch list returns it: 1,500 a month, ending 31 December 2026, routine uploaded, 14 students. */
const TEST_BATCH: BatchView = {
  id: "batch-test",
  nameEn: "Test batch",
  nameBn: "টেস্ট ব্যাচ",
  startsOn: null,
  endsOn: "2026-12-31",
  defaultMonthlyFee: 1500,
  status: "ACTIVE",
  studentCount: 14,
  routineUploaded: true,
  routineFileName: "routine.pdf",
  routineUploadedAt: "2026-11-02T04:00:00Z",
  routineUploadedBy: "Master Admin",
  studentsNotified: 3,
};

/** A promise React reads at once, as Next does for route params, so `use()` does not suspend in a test. */
function resolved<T>(value: T): Promise<T> {
  const promise = Promise.resolve(value) as Promise<T> & { status?: string; value?: T };
  promise.status = "fulfilled";
  promise.value = value;
  return promise;
}

function renderSetupRoute() {
  const params = resolved({ id: ADMISSION_ID });
  renderWithProviders(<SetupPage params={params} />, {
    user: fixtures.systemAdmin,
    locale: "en",
    route: `/staff/admissions/${ADMISSION_ID}/setup`,
  });
}

describe("the setup on its own screen", () => {
  it("leaves the monthly fee empty, not 'undefined', when no batch is chosen", async () => {
    routes(detail({ state: "SUBMITTED", setup: EMPTY_SETUP }));
    renderSetupRoute();

    const fee = await screen.findByLabelText(/Monthly fee/);
    expect(fee).toHaveValue("");
    expect(screen.getByRole("combobox", { name: /Batch/ })).toHaveValue("");
  });

  it("leaves the monthly fee empty when the server sends no fee at all", async () => {
    const setupWithoutFee: Partial<typeof EMPTY_SETUP> = { ...EMPTY_SETUP };
    delete setupWithoutFee.monthlyFee;
    routes(detail({ state: "SUBMITTED", setup: setupWithoutFee as AdmissionDetail["setup"] }));
    renderSetupRoute();

    const fee = await screen.findByLabelText(/Monthly fee/);
    expect(fee).toHaveValue("");
  });

  it("renders the two numbered cards, the payments line and the summary at the setup route", async () => {
    routes(detail({ state: "SUBMITTED", readyProblems: [] }));
    renderSetupRoute();

    expect(await screen.findByRole("heading", { level: 1, name: "Setup" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Back to admission" })).toHaveAttribute("href", `/staff/admissions/${ADMISSION_ID}`);
    expect(screen.getByRole("heading", { level: 2, name: "Batch and fees" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 2, name: "Exams and packages" })).toBeInTheDocument();
    expect(screen.getByLabelText(/Monthly fee/)).toHaveValue("3000");
    expect(screen.getByText(/^Payments: /)).toBeInTheDocument();
    expect(screen.getByText("Ready checklist")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Mark ready" })).toBeEnabled();
  });

  it("shows the totals as the monthly fee times the months, and the first to last due date", async () => {
    routes(detail({ state: "SUBMITTED" }));
    renderSetupRoute();

    // 3,000 a month from 9 December to 9 February: three dues, 9,000 in all.
    expect(await screen.findByText(/× 3 months = .*9,000/)).toBeInTheDocument();
    expect(screen.getByText(/9 December 2026 → 9 February 2027/)).toBeInTheDocument();
  });

  it("keeps Mark ready off, with the reason, until the checklist passes", async () => {
    routes(detail({ state: "SUBMITTED", readyProblems: [{ field: "items", message: "Add at least one exam or package." }] }));
    renderSetupRoute();

    const ready = await screen.findByRole("button", { name: "Mark ready" });
    expect(ready).toBeDisabled();
    expect(screen.getByText("1 still missing")).toBeInTheDocument();
    expect(ready).toHaveAttribute("aria-describedby", "setup-ready-reason");
  });

  it("locks every card when the admission is ready: no inputs, no add, no remove", async () => {
    routes(detail({ state: "READY", items: [EXAM_ITEM] }));
    renderSetupRoute();

    expect(await screen.findByRole("button", { name: "Back to review" })).toBeEnabled();
    expect(screen.getByLabelText(/Monthly fee/)).toBeDisabled();
    expect(screen.getByLabelText(/Admitted on/)).toBeDisabled();
    expect(screen.getByRole("combobox", { name: /Batch/ })).toBeDisabled();
    expect(screen.queryByRole("button", { name: "+ Add" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Remove" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Mark ready" })).not.toBeInTheDocument();
  });
});

describe("the batch picker shows the name, and the details below it", () => {
  it("lists the batch by its name alone, and says what to choose while no batch is chosen", async () => {
    routes(detail({ state: "SUBMITTED", setup: EMPTY_SETUP }), [TEST_BATCH]);
    renderSetupRoute();

    const option = await screen.findByRole("option", { name: "Test batch" });
    expect(option.textContent).toBe("Test batch");
    expect(screen.getByText("Choose a batch to fill in the fee and the end date.")).toBeInTheDocument();
  });

  it("fills the fee and the end date when a batch is chosen, and shows its details in a list", async () => {
    routes(detail({ state: "SUBMITTED", setup: EMPTY_SETUP }), [TEST_BATCH]);
    renderSetupRoute();
    const user = userEvent.setup();

    // The options arrive with the batch list, so wait for the batch before choosing it.
    await screen.findByRole("option", { name: "Test batch" });
    await user.selectOptions(screen.getByRole("combobox", { name: /Batch/ }), TEST_BATCH.id);

    expect(screen.getByLabelText(/Monthly fee/)).toHaveValue("1500");
    expect(screen.getByLabelText(/Course end date/)).toHaveValue("2026-12-31");
    const details = screen.getByText("Ends on").closest("dl");
    if (!details) {
      throw new Error("The batch details are not a list");
    }
    expect(within(details).getByText("Monthly fee")).toBeInTheDocument();
    expect(within(details).getByText("31 December 2026")).toBeInTheDocument();
    expect(within(details).getByText("Uploaded 2 November 2026")).toBeInTheDocument();
    expect(within(details).getByText("Students in this batch").nextElementSibling).toHaveTextContent("14");
    expect(screen.queryByText("Choose a batch to fill in the fee and the end date.")).not.toBeInTheDocument();
  });
});

describe("the admission detail shows the setup as a summary", () => {
  it("shows the summary and an Edit setup link for a SUBMITTED admission", async () => {
    routes(detail({ state: "SUBMITTED" }));
    renderWithProviders(<AdmissionDetailView id={ADMISSION_ID} />, {
      user: fixtures.systemAdmin,
      locale: "en",
      route: `/staff/admissions/${ADMISSION_ID}`,
    });

    const summary = await screen.findByRole("region", { name: "Setup" });
    expect(within(summary).getByText("BJS Evening")).toBeInTheDocument();
    expect(within(summary).getByText("Ready status")).toBeInTheDocument();
    expect(within(summary).getByRole("link", { name: "Edit setup" })).toHaveAttribute(
      "href",
      `/staff/admissions/${ADMISSION_ID}/setup`,
    );
    expect(screen.queryByLabelText(/Monthly fee/)).not.toBeInTheDocument();
  });

  it("shows no Edit setup button for an ONBOARDED admission", async () => {
    routes(detail({ state: "ONBOARDED", onboardedAt: "2026-11-12T04:00:00Z" }));
    renderWithProviders(<AdmissionDetailView id={ADMISSION_ID} />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: `/staff/admissions/${ADMISSION_ID}`,
    });

    await screen.findByRole("heading", { level: 1, name: "Rahim Uddin" });
    expect(screen.queryByRole("link", { name: "Edit setup" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Edit setup" })).not.toBeInTheDocument();
  });
});
