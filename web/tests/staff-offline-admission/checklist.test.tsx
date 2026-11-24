import { fireEvent, screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AdmissionDetailView } from "@/components/staff/offline/admission-detail";
import { AdmissionSetupView } from "@/components/staff/offline/admission-setup";
import { ReadyChecklist } from "@/components/staff/offline/ready-checklist";
import { canMarkReady, checklistRows } from "@/components/staff/offline/offline-model";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { ADMISSION_ID, detail, page } from "./fixtures";

/** The routes the detail page and the setup screen read: the admission, the batches for the setup, and the catalogue for the exams and packages. */
function admissionRoutes(admission: ReturnType<typeof detail>) {
  return mockApi({
    [`GET /api/v1/offline-admissions/${ADMISSION_ID}`]: admission,
    "GET /api/v1/offline-batches": [],
    "GET /api/v1/live-exams": page([]),
    "GET /api/v1/live-exam-modules": page([]),
  });
}

function renderSetup() {
  renderWithProviders(<AdmissionSetupView id={ADMISSION_ID} />, {
    user: fixtures.systemAdmin,
    locale: "en",
    route: `/staff/admissions/${ADMISSION_ID}/setup`,
  });
}

describe("the ready checklist (plan 6.3, principle 2)", () => {
  it("ticks every line when nothing is missing, and says the form can be marked ready", () => {
    renderWithProviders(<ReadyChecklist problems={[]} />, { user: fixtures.systemAdmin, locale: "en" });

    expect(screen.getByText("Batch chosen")).toBeInTheDocument();
    expect(screen.getAllByText("Ticked")).toHaveLength(7);
    expect(screen.queryByText("Missing")).not.toBeInTheDocument();
    expect(screen.getByText("Everything is ticked. You can mark this ready for approval.")).toBeInTheDocument();
  });

  it("shows each missing requirement with what the server says is wrong, and says why the button is off", () => {
    renderWithProviders(
      <ReadyChecklist
        problems={[
          { field: "batchId", message: "Choose a batch." },
        ]}
      />,
      { user: fixtures.systemAdmin, locale: "en" },
    );

    const batchRow = screen.getByText("Batch chosen").closest("li");
    expect(batchRow).toHaveTextContent("Missing");
    expect(batchRow).toHaveTextContent("Choose a batch.");
    // Exams and packages are optional: the checklist has no row for them.
    expect(screen.queryByText("At least one exam or package")).not.toBeInTheDocument();
    expect(screen.getByText("Form is complete").closest("li")).toHaveTextContent("Ticked");
    expect(screen.getByText("Ready for approval stays off until every line is ticked.")).toBeInTheDocument();
  });

  it("keeps the Ready button off while anything is missing, and on once all is ticked", () => {
    expect(canMarkReady([{ field: "batchId", message: "Choose a batch." }], "SUBMITTED")).toBe(false);
    expect(canMarkReady([], "SUBMITTED")).toBe(true);
    expect(canMarkReady([], "CHANGES_REQUESTED")).toBe(false);
    expect(checklistRows([{ field: "nid", message: "NID has 9 digits." }])[0]).toEqual({
      key: "form",
      missing: [{ field: "nid", message: "NID has 9 digits." }],
    });
  });

  it("disables Ready for approval on the setup screen while the checklist has a gap", async () => {
    admissionRoutes(detail({ readyProblems: [{ field: "batchId", message: "Choose a batch." }] }));
    renderSetup();

    const ready = await screen.findByRole("button", { name: "Mark ready" });
    expect(ready).toBeDisabled();
    expect(screen.getByText("1 still missing")).toBeInTheDocument();
    expect(screen.getByText("Choose a batch.")).toBeInTheDocument();
  });

  it("turns Ready for approval on once every line is ticked", async () => {
    admissionRoutes(detail({ readyProblems: [] }));
    renderWithProviders(<AdmissionDetailView id={ADMISSION_ID} />, {
      user: fixtures.systemAdmin,
      locale: "en",
      route: `/staff/admissions/${ADMISSION_ID}`,
    });

    await waitFor(() => expect(screen.getByRole("button", { name: "Ready for approval" })).toBeEnabled());
  });
});

describe("the setup shows the overlaps the detail reports (plan 4.4)", () => {
  it("lists what the student already holds that overlaps an item, from the admission itself", async () => {
    admissionRoutes(
      detail({
        items: [
          { id: "item-1", scope: "EXAM", examId: "exam-9", moduleId: null, titleEn: "Physics Mock 2", titleBn: "পদার্থ মক ২", grantId: null, addedAt: "2026-11-10T04:00:00Z" },
        ],
        overlaps: [
          {
            itemId: "item-1",
            titleEn: "Physics Mock 2",
            titleBn: "পদার্থ মক ২",
            warnings: [
              {
                kind: "EXAM",
                id: "exam-7",
                titleEn: "Physics Mock 1",
                titleBn: "পদার্থ মক ১",
                via: "PACKAGE",
                planId: null,
                paid: null,
                due: null,
                packageTitleEn: "Physics Package",
                packageTitleBn: "পদার্থ প্যাকেজ",
              },
            ],
          },
        ],
      }),
    );
    renderSetup();

    const heading = await screen.findByText("Already holds this from an online order");
    const alert = heading.parentElement as HTMLElement;
    expect(alert).toHaveTextContent("Physics Mock 1");
    expect(alert).toHaveTextContent("Physics Package");
  });

  it("shows no overlap notice when the detail reports none", async () => {
    admissionRoutes(detail({ overlaps: [] }));
    renderSetup();

    await screen.findByRole("button", { name: "Mark ready" });
    expect(screen.queryByText("Already holds this from an online order")).not.toBeInTheDocument();
  });
});

describe("setup of an application that arrives with its batch (17-18 Nov)", () => {
  const BATCH = {
    id: "batch-1",
    nameEn: "Batch 3 (Evening)",
    nameBn: "ব্যাচ ৩",
    startsOn: "2026-12-01",
    endsOn: "2027-06-30",
    courseFee: 12000,
    defaultInstallments: 3,
    status: "ACTIVE",
    descriptionEn: null,
    descriptionBn: null,
    photoUploaded: false,
    admissionsOpen: true,
    published: true,
    routineUploaded: false,
    studentCount: 0,
  };

  it("fills the fee and end date from the batch, leaves the admitted date to the admin, and spreads the instalments once it is entered", async () => {
    const empty = detail();
    const admission = detail({
      setup: { ...empty.setup, batchId: "batch-1", courseFee: null, installments: null, admittedOn: null, firstDueOn: null, courseEndsOn: null,
        installmentPlan: [] } as unknown as typeof empty.setup,
    });
    let saved: Record<string, unknown> = {};
    const api = mockApi({
      [`GET /api/v1/offline-admissions/${ADMISSION_ID}`]: () => ({ ...admission, setup: { ...admission.setup, ...saved } }),
      "GET /api/v1/offline-batches": [BATCH],
      "GET /api/v1/live-exams": page([]),
      "GET /api/v1/live-exam-modules": page([]),
      // The API answers with the setup as saved, as the real one does.
      [`PATCH /api/v1/offline-admissions/${ADMISSION_ID}/setup`]: ({ body }: { body: unknown }) => {
        saved = { ...saved, ...(body as Record<string, unknown>) };
        return { ...admission, setup: { ...admission.setup, ...saved } };
      },
    });
    renderSetup();

    const fee = await screen.findByLabelText(/^Course fee for this student/);
    await waitFor(() => expect(fee).toHaveValue("12000"));
    expect(screen.getByLabelText(/^Course end date/)).toHaveValue("2027-06-30");
    const admitted = screen.getByLabelText(/^Admitted on/);
    expect(admitted).toHaveValue("");
    expect(screen.getByText(/Enter the day the student was admitted/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Instalment 1 amount/)).toBeNull();

    fireEvent.change(admitted, { target: { value: "2026-11-18" } });
    await waitFor(() => expect((screen.getByLabelText(/^Number of instalments/) as HTMLInputElement).value).toBe("3"));
    expect(screen.getByLabelText(/^First due date/)).toHaveValue("2026-12-18");
    expect(screen.getByLabelText(/^Instalment 1 amount/)).toHaveValue("4000");
    expect(screen.queryByText(/Enter the day the student was admitted/)).toBeNull();
    // Nothing saved a guessed admitted date.
    await waitFor(() => expect(api.calls.some((call) => call.method === "PATCH")).toBe(true));
    expect(api.calls.filter((call) => call.method === "PATCH").every((call) => {
      const body = call.body as { admittedOn?: string | null };
      return !body.admittedOn || body.admittedOn === "2026-11-18";
    })).toBe(true);
  });
});
