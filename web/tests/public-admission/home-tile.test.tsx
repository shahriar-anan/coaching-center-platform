import { screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { OfflineActivitiesTile } from "@/components/student/home/home-page";
import { admissionView, completeForm } from "../offline-admission/admission-fixture";
import { mockApi, renderWithProviders, fixtures } from "../helpers";

/** The offline switch the tile reads at render time. */
const flags = vi.hoisted(() => ({ offline: true }));

vi.mock("@/lib/config", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/config")>();
  return {
    ...actual,
    get offlineAdmissionEnabled() {
      return flags.offline;
    },
  };
});

const BATCHES = [
  {
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
    routineUploaded: false,
  },
  {
    id: "b-2",
    nameEn: "Batch 4",
    nameBn: "ব্যাচ ৪",
    startsOn: null,
    endsOn: "2027-06-30",
    courseFee: 2500,
    descriptionEn: null,
    descriptionBn: null,
    photoUrl: null,
    admissionsOpen: false,
    routineUploaded: false,
  },
];

/** The batch name of an admission, as the API gives it once the application names a batch. */
const NAMED = { batchNameEn: "Batch 3 (Evening)", batchNameBn: "ব্যাচ ৩ (সন্ধ্যা)" };

function renderTile(admissions: unknown[]) {
  mockApi({
    "GET /api/v1/me/admissions": admissions,
    "GET /api/v1/public/offline-batches": BATCHES,
  });
  return renderWithProviders(<OfflineActivitiesTile />, { user: fixtures.student, locale: "en", route: "/" });
}

describe("the Offline activities doors on the dashboard (16 Nov)", () => {
  beforeEach(() => {
    flags.offline = true;
  });

  it("with no admission, says no batch yet and how many batches are open, each door opening its tab", async () => {
    renderTile([]);

    expect(await screen.findByRole("heading", { name: "Offline activities" })).toBeInTheDocument();
    const mine = screen.getByRole("link", { name: /My offline batches/ });
    expect(mine).toHaveAttribute("href", "/offline-activities?tab=mine");
    expect(within(mine).getByText("No batch yet")).toBeInTheDocument();
    const offers = screen.getByRole("link", { name: /Batches on offer/ });
    expect(offers).toHaveAttribute("href", "/offline-activities?tab=offers");
    expect(await within(offers).findByText("1 open for admission")).toBeInTheDocument();
    // Minimal: no batch names or fees on the dashboard.
    expect(screen.queryByText(/Batch 3 \(Evening\)/)).toBeNull();
  });

  it("counts the onboarded batches", async () => {
    renderTile([
      admissionView("ONBOARDED", { ...NAMED }, completeForm),
      admissionView("ONBOARDED", { id: "a-2", batchId: "b-2", batchNameEn: "Batch 4", batchNameBn: "ব্যাচ ৪" }, completeForm),
    ]);

    expect(await screen.findByText("2 onboarded batches")).toBeInTheDocument();
  });

  it("says an application is in progress while it is under review", async () => {
    renderTile([admissionView("SUBMITTED", { ...NAMED, submittedAt: "2026-11-10T04:00:00Z" }, completeForm)]);

    expect(await screen.findByText("Application in progress")).toBeInTheDocument();
  });

  it("is not shown at all while the offline switch is off", () => {
    flags.offline = false;
    renderTile([]);
    expect(screen.queryByRole("heading", { name: "Offline activities" })).toBeNull();
  });
});
