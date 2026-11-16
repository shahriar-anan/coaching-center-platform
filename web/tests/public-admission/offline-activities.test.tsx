import { screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { OfflineActivitiesPage } from "@/components/public-admission/offline-activities-page";
import type { PublicBatch } from "@/lib/api/exam/public-admission";
import { admission, schedule } from "../my-admission/fixture";
import { ErrorBody, fixtures, mockApi, renderWithProviders } from "../helpers";

const OPEN_B1: PublicBatch = {
  id: "b-1",
  nameEn: "Batch 3 (Evening)",
  nameBn: "ব্যাচ ৩ (সন্ধ্যা)",
  startsOn: null,
  endsOn: "2027-05-31",
  courseFee: 3000,
  descriptionEn: null,
  descriptionBn: null,
  photoUrl: null,
  admissionsOpen: true,
  routineAvailable: false,
};

const OPEN_B2: PublicBatch = { ...OPEN_B1, id: "b-2", nameEn: "Batch 4", nameBn: "ব্যাচ ৪", courseFee: 2500 };
const OPEN_B3: PublicBatch = { ...OPEN_B1, id: "b-3", nameEn: "Batch 5", nameBn: "ব্যাচ ৫" };

/** The batches an applicant can still apply for, once the student has the admissions the test gives. */
function applyLinks(): string[] {
  return screen.queryAllByRole("link", { name: "Apply" }).map((link) => link.getAttribute("href") ?? "");
}

describe("the Offline activities page without an offline admission (P15)", () => {
  it("shows only the published batches as cards, with Apply, and no My admission", async () => {
    mockApi({
      "GET /api/v1/me/admission": new ErrorBody(404, { code: "RESOURCE_NOT_FOUND", message: "Not found." }),
      "GET /api/v1/public/offline-batches": [OPEN_B1, OPEN_B2],
    });
    renderWithProviders(<OfflineActivitiesPage />, { user: fixtures.student, locale: "en", route: "/offline-activities" });

    expect(await screen.findByRole("heading", { name: "Offline activities", level: 1 })).toBeInTheDocument();
    expect(await screen.findByRole("link", { name: /Batch 4/ })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Batches you can apply for" })).toBeInTheDocument();
    expect(applyLinks()).toEqual(["/admission/apply/b-1", "/admission/apply/b-2"]);
    expect(screen.queryByRole("heading", { name: "My admission" })).toBeNull();
  });
});

describe("the Offline activities page with one offline admission (P15)", () => {
  it("shows My admission first, then the published batches the student is not already in", async () => {
    const onboarded = admission("ONBOARDED", { id: "adm-a", batchId: "b-1", batchNameEn: "Batch 3 (Evening)" });
    mockApi({
      "GET /api/v1/me/admission": onboarded,
      "GET /api/v1/me/admissions": [onboarded],
      "GET /api/v1/me/admission/fees": schedule(),
      "GET /api/v1/me/admission/items": [],
      "GET /api/v1/public/offline-batches": [OPEN_B1, OPEN_B2],
    });
    renderWithProviders(<OfflineActivitiesPage />, { user: fixtures.student, locale: "en", route: "/offline-activities" });

    expect(await screen.findByRole("heading", { name: "Batches you can apply for" })).toBeInTheDocument();
    const text = document.body.textContent ?? "";
    expect(text.indexOf("Batch 3 (Evening)")).toBeGreaterThanOrEqual(0);
    // The admission sections come before the offers.
    expect(text.indexOf("Batch 3 (Evening)")).toBeLessThan(text.indexOf("Batches you can apply for"));
    // The batch the student is in is not offered again; the other one is.
    await waitFor(() => expect(applyLinks()).toEqual(["/admission/apply/b-2"]));
  });
});

describe("the Offline activities page with several offline admissions (P11, P15)", () => {
  it("shows one section per open batch, a card for the one under review, and offers only the batches not in", async () => {
    const open = admission("ONBOARDED", { id: "adm-a", batchId: "b-1", batchNameEn: "Batch 3 (Evening)" });
    const review = admission("SUBMITTED", { id: "adm-c", batchId: "b-3", batchNameEn: "Batch 5" });
    mockApi({
      "GET /api/v1/me/admission": open,
      "GET /api/v1/me/admissions": [open, review],
      "GET /api/v1/me/admissions/adm-a/fees": schedule({ admissionId: "adm-a" }),
      "GET /api/v1/me/admissions/adm-a/items": [],
      "GET /api/v1/public/offline-batches": [OPEN_B1, OPEN_B2, OPEN_B3],
    });
    renderWithProviders(<OfflineActivitiesPage />, { user: fixtures.student, locale: "en", route: "/offline-activities" });

    expect(await screen.findByRole("region", { name: "Batch 3 (Evening)" })).toBeInTheDocument();
    const underReview = screen.getByRole("region", { name: "Batch 5" });
    expect(within(underReview).getByText(/Under review/)).toBeInTheDocument();
    await waitFor(() => expect(applyLinks()).toEqual(["/admission/apply/b-2"]));
  });
});

describe("the Offline activities page for a visitor", () => {
  it("shows the published batches as cards, and no admission", async () => {
    mockApi({ "GET /api/v1/public/offline-batches": [OPEN_B1, OPEN_B2] });
    renderWithProviders(<OfflineActivitiesPage />, { locale: "en", route: "/offline-activities" });

    expect(await screen.findByRole("heading", { name: "Offline activities", level: 1 })).toBeInTheDocument();
    expect(await screen.findByRole("link", { name: /Batch 3 \(Evening\)/ })).toBeInTheDocument();
    expect(applyLinks()).toEqual(["/admission/apply/b-1", "/admission/apply/b-2"]);
    expect(screen.queryByRole("heading", { name: "My admission" })).toBeNull();
  });
});
