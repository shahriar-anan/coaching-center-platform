import { screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AdmissionDetailView } from "@/components/staff/offline/admission-detail";
import { ReadyChecklist } from "@/components/staff/offline/ready-checklist";
import { canMarkReady, checklistRows } from "@/components/staff/offline/offline-model";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { ADMISSION_ID, detail, page } from "./fixtures";

/** The routes the detail page reads: the admission, the batches for the setup, and the catalogue for the exams and packages. */
function detailRoutes(admission: ReturnType<typeof detail>) {
  return mockApi({
    [`GET /api/v1/offline-admissions/${ADMISSION_ID}`]: admission,
    "GET /api/v1/offline-batches": [],
    "GET /api/v1/live-exams": page([]),
    "GET /api/v1/live-exam-modules": page([]),
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
          { field: "items", message: "Add at least one exam or package." },
        ]}
      />,
      { user: fixtures.systemAdmin, locale: "en" },
    );

    const batchRow = screen.getByText("Batch chosen").closest("li");
    expect(batchRow).toHaveTextContent("Missing");
    expect(batchRow).toHaveTextContent("Choose a batch.");
    expect(screen.getByText("At least one exam or package").closest("li")).toHaveTextContent(
      "Add at least one exam or package.",
    );
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

  it("disables Ready for approval on the detail page while the checklist has a gap", async () => {
    detailRoutes(detail({ readyProblems: [{ field: "batchId", message: "Choose a batch." }] }));
    renderWithProviders(<AdmissionDetailView id={ADMISSION_ID} />, {
      user: fixtures.systemAdmin,
      locale: "en",
      route: `/staff/admissions/${ADMISSION_ID}`,
    });

    const ready = await screen.findByRole("button", { name: "Ready for approval" });
    expect(ready).toBeDisabled();
    expect(screen.getByText("Choose a batch.")).toBeInTheDocument();
  });

  it("turns Ready for approval on once every line is ticked", async () => {
    detailRoutes(detail({ readyProblems: [] }));
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
    detailRoutes(
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
    renderWithProviders(<AdmissionDetailView id={ADMISSION_ID} />, {
      user: fixtures.systemAdmin,
      locale: "en",
      route: `/staff/admissions/${ADMISSION_ID}`,
    });

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("The student already has access to this:");
    expect(alert).toHaveTextContent("Physics Mock 1");
    expect(alert).toHaveTextContent("Physics Package");
  });

  it("shows no overlap notice when the detail reports none", async () => {
    detailRoutes(detail({ overlaps: [] }));
    renderWithProviders(<AdmissionDetailView id={ADMISSION_ID} />, {
      user: fixtures.systemAdmin,
      locale: "en",
      route: `/staff/admissions/${ADMISSION_ID}`,
    });

    await screen.findByRole("button", { name: "Ready for approval" });
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
