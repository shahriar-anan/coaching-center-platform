import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { MyAdmissionPage } from "@/components/student/my-admission/my-admission-page";
import { admission, schedule } from "../my-admission/fixture";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

/** Two onboarded batches and one application under review, for one student (plan P11). */
const OPEN_A = admission("ONBOARDED", { id: "adm-a", batchNameEn: "Batch 3", batchNameBn: "ব্যাচ ৩" });
const OPEN_B = admission("ONBOARDED", { id: "adm-b", batchNameEn: "Batch 4", batchNameBn: "ব্যাচ ৪" });
const REVIEW = admission("SUBMITTED", { id: "adm-c", batchNameEn: "Batch 5", batchNameBn: "ব্যাচ ৫" });

describe("My admission lists each open batch in its own section (plan P11)", () => {
  it("shows one section per onboarded batch, with its own fees, and a card for the batch under review", async () => {
    const api = mockApi({
      "GET /api/v1/me/admission": OPEN_B,
      "GET /api/v1/me/admissions": [OPEN_B, OPEN_A, REVIEW],
      "GET /api/v1/me/admissions/adm-a/fees": schedule({ admissionId: "adm-a" }),
      "GET /api/v1/me/admissions/adm-b/fees": schedule({ admissionId: "adm-b" }),
      "GET /api/v1/me/admissions/adm-a/items": [],
      "GET /api/v1/me/admissions/adm-b/items": [],
    });
    renderWithProviders(<MyAdmissionPage />, { user: fixtures.student, locale: "en", route: "/my-admission" });

    expect(await screen.findByRole("heading", { name: "Batch 3" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Batch 4" })).toBeInTheDocument();
    const review = screen.getByRole("region", { name: "Batch 5" });
    expect(within(review).getByText(/Under review/)).toBeInTheDocument();

    expect(api.calls.some((call) => call.path === "/api/v1/me/admissions/adm-a/fees")).toBe(true);
    expect(api.calls.some((call) => call.path === "/api/v1/me/admissions/adm-b/fees")).toBe(true);
    // The single-admission routes are not used once there are several.
    expect(api.calls.some((call) => call.path === "/api/v1/me/admission/fees")).toBe(false);
  });
});
