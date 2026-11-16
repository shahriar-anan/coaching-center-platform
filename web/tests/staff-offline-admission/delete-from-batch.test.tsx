import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AdmissionDetailView } from "@/components/staff/offline/admission-detail";
import { fixtures, mockApi, renderWithProviders, routerState } from "../helpers";
import { ADMISSION_ID, detail, page } from "./fixtures";

function routes(overrides: Parameters<typeof detail>[0] = {}) {
  return mockApi({
    [`GET /api/v1/offline-admissions/${ADMISSION_ID}`]: detail({ state: "READY", readyProblems: [], rollNo: "10000001", ...overrides }),
    [`POST /api/v1/offline-admissions/${ADMISSION_ID}/delete-from-batch`]: undefined,
    "GET /api/v1/offline-batches": [],
    "GET /api/v1/live-exams": page([]),
    "GET /api/v1/live-exam-modules": page([]),
  });
}

describe("delete from the batch (16 Nov, issue 2)", () => {
  it("lets the Master Admin delete with a reason, names the roll it frees, and goes back to the list", async () => {
    const user = userEvent.setup();
    const api = routes();
    renderWithProviders(<AdmissionDetailView id={ADMISSION_ID} />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: `/staff/admissions/${ADMISSION_ID}`,
    });

    await user.click(await screen.findByRole("button", { name: "Delete from batch" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/Roll 10000001 becomes free/)).toBeInTheDocument();
    const confirm = within(dialog).getByRole("button", { name: "Delete from batch" });
    expect(confirm).toBeDisabled();

    await user.type(within(dialog).getByRole("textbox"), "Left the course");
    await user.click(confirm);

    await waitFor(() =>
      expect(api.calls.find((call) => call.path === `/api/v1/offline-admissions/${ADMISSION_ID}/delete-from-batch`)?.body).toEqual({
        reason: "Left the course",
      }),
    );
    await waitFor(() => expect(routerState.push).toHaveBeenCalledWith("/staff/admissions"));
  });

  it("is not offered to a System Admin", async () => {
    routes();
    renderWithProviders(<AdmissionDetailView id={ADMISSION_ID} />, {
      user: fixtures.systemAdmin,
      locale: "en",
      route: `/staff/admissions/${ADMISSION_ID}`,
    });

    expect(await screen.findByText("Waiting for the Master Admin to onboard.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Delete from batch" })).not.toBeInTheDocument();
  });

  it("says when the student's account was deleted, and when the admission itself was deleted and kept for its payments", async () => {
    routes({ accountDeleted: true, deletedAt: "2026-11-16T05:00:00Z", deletedReason: "Moved away", releasedRoll: "10000001", rollNo: null });
    renderWithProviders(<AdmissionDetailView id={ADMISSION_ID} />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: `/staff/admissions/${ADMISSION_ID}`,
    });

    expect(await screen.findByText(/account was deleted after they applied/)).toBeInTheDocument();
    expect(screen.getByText(/Roll 10000001 was freed\. Reason: Moved away/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Delete from batch" })).not.toBeInTheDocument();
  });
});
