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
    installments: 6,
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
    installments: 6,
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

describe("the Offline activities tile on the dashboard (plan P9)", () => {
  beforeEach(() => {
    flags.offline = true;
  });

  it("with no admission, offers the open batches and a link to all of them", async () => {
    renderTile([]);

    expect(await screen.findByRole("heading", { name: "Batches on offer" })).toBeInTheDocument();
    expect(await screen.findByRole("link", { name: /Batch 3 \(Evening\)/ }, { timeout: 4000 })).toHaveAttribute("href", "/admission/batches/b-1");
    expect(screen.queryByRole("link", { name: /Batch 4/ })).toBeNull();
    expect(screen.getByRole("link", { name: "See all" })).toHaveAttribute("href", "/admission");
    expect(screen.queryByText(/Your batch/)).toBeNull();
  });

  it("with an application under review, says so by batch name", async () => {
    renderTile([admissionView("SUBMITTED", { ...NAMED, submittedAt: "2026-11-10T04:00:00Z" }, completeForm)]);

    expect(await screen.findByText("Batch 3 (Evening): under review")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Open My admission" })).toBeNull();
  });

  it("with an onboarded admission, shows Your batch and opens My admission", async () => {
    renderTile([admissionView("ONBOARDED", { ...NAMED }, completeForm)]);

    const line = (await screen.findByText("Your batch: Batch 3 (Evening)")).closest("li") as HTMLElement;
    expect(within(line).getByRole("link", { name: "Open My admission" })).toHaveAttribute("href", "/offline-activities");
  });

  it("with changes requested, names the batch and opens the form", async () => {
    renderTile([admissionView("CHANGES_REQUESTED", { ...NAMED, changesReason: "Fix the photo." }, completeForm)]);

    const line = (await screen.findByText("Batch 3 (Evening): changes requested")).closest("li") as HTMLElement;
    expect(within(line).getByRole("link", { name: "Open the form" })).toHaveAttribute("href", "/admission/form");
  });

  it("is not shown at all while the offline switch is off", () => {
    flags.offline = false;
    renderTile([]);
    expect(screen.queryByRole("heading", { name: "Offline activities" })).toBeNull();
  });
});
