import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AdmissionBatchesPage } from "@/components/public-admission/batches-page";
import { BatchPage } from "@/components/public-admission/batch-page";
import type { PublicBatch } from "@/lib/api/exam/public-admission";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { admissionView } from "../offline-admission/admission-fixture";

const OPEN: PublicBatch = {
  id: "b-1",
  nameEn: "Batch 3 (Evening)",
  nameBn: "ব্যাচ ৩ (সন্ধ্যা)",
  startsOn: "2026-12-01",
  endsOn: "2027-05-31",
  courseFee: 3000,
  installments: 6,
  descriptionEn: "The first paragraph.\n\nThe second paragraph.",
  descriptionBn: null,
  photoUrl: null,
  admissionsOpen: true,
  routineAvailable: true,
};

const CLOSED: PublicBatch = {
  ...OPEN,
  id: "b-2",
  nameEn: "Batch 4",
  nameBn: "ব্যাচ ৪",
  startsOn: null,
  courseFee: 2500,
  installments: 6,
  descriptionEn: null,
  admissionsOpen: false,
  routineAvailable: false,
};

describe("the Admission page shows the batches as cards (plan 3.2, 8)", () => {
  it("shows each batch with its dates and course fee, and Apply only where admissions are open", async () => {
    mockApi({ "GET /api/v1/public/offline-batches": [OPEN, CLOSED] });
    renderWithProviders(<AdmissionBatchesPage />, { locale: "en", route: "/admission" });

    const open = (await screen.findByRole("link", { name: /Batch 3 \(Evening\)/ })).closest("article");
    expect(open).not.toBeNull();
    const card = within(open as HTMLElement);
    expect(card.getByText("Course fee: ৳3,000, in 6 monthly instalments")).toBeInTheDocument();
    expect(card.getByText(/31 May 2027/)).toBeInTheDocument();
    expect(card.getByRole("link", { name: "Apply" })).toHaveAttribute("href", "/admission/apply/b-1");

    const closed = within(screen.getByRole("link", { name: /^Batch 4/ }).closest("article") as HTMLElement);
    expect(closed.getByText("Admissions closed")).toBeInTheDocument();
    expect(closed.queryByRole("link", { name: "Apply" })).toBeNull();
    expect(screen.getAllByRole("link", { name: "Apply" })).toHaveLength(1);
  });

  it("says so when no batch is open", async () => {
    mockApi({ "GET /api/v1/public/offline-batches": [] });
    renderWithProviders(<AdmissionBatchesPage />, { locale: "en", route: "/admission" });
    expect(await screen.findByText("No offline batches are open right now.")).toBeInTheDocument();
  });
});

describe("the batch page (plan 2A)", () => {
  it("shows the description, the dates and fee, the routine copy and Apply for an open batch", async () => {
    mockApi({ "GET /api/v1/public/offline-batches/b-1": OPEN });
    renderWithProviders(<BatchPage id="b-1" />, { locale: "en", route: "/admission/batches/b-1" });

    expect(await screen.findByText("The first paragraph.")).toBeInTheDocument();
    expect(screen.getByText("The second paragraph.")).toBeInTheDocument();
    expect(screen.getByText("Course fee")).toBeInTheDocument();
    expect(screen.getByText("৳3,000, in 6 monthly instalments")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Open the routine PDF" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Apply" })).toHaveAttribute("href", "/admission/apply/b-1");
  });

  it("hides the routine when none is uploaded, and shows the closed note instead of Apply", async () => {
    mockApi({ "GET /api/v1/public/offline-batches/b-2": CLOSED });
    renderWithProviders(<BatchPage id="b-2" />, { locale: "en", route: "/admission/batches/b-2" });

    expect(await screen.findByText("Admissions for this batch are closed.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Open the routine PDF" })).toBeNull();
    expect(screen.queryByRole("link", { name: "Apply" })).toBeNull();
  });
});

describe("the batch page shows the student's own application (admission-access-plan.md)", () => {
  function renderOwn(admissions: unknown) {
    mockApi({ "GET /api/v1/public/offline-batches/b-1": OPEN, "GET /api/v1/me/admissions": admissions });
    renderWithProviders(<BatchPage id="b-1" />, { user: fixtures.student, locale: "en", route: "/admission/batches/b-1" });
  }

  it("says the application is under review, with the print link, in place of Apply", async () => {
    renderOwn([admissionView("SUBMITTED", { batchId: "b-1", source: "ACCOUNT" })]);

    expect(await screen.findByRole("heading", { name: "Your application" })).toBeInTheDocument();
    expect(screen.getByText("Under review. The office will tell you when it is decided.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View / print my form" })).toHaveAttribute("href", "/admission/print");
    expect(screen.queryByRole("link", { name: "Apply" })).toBeNull();
  });

  it("leads to the join form once approved with a join code out", async () => {
    renderOwn([admissionView("READY", { batchId: "b-1", source: "ACCOUNT", joinCodePending: true })]);

    expect(await screen.findByRole("link", { name: "Enter join code" })).toHaveAttribute("href", "/admission/join");
  });

  it("leads an admitted student to Offline activities", async () => {
    renderOwn([admissionView("ONBOARDED", { batchId: "b-1" })]);

    expect(await screen.findByText("You are in this batch.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Open Offline activities" })).toHaveAttribute("href", "/offline-activities");
  });

  it("keeps Apply for an application to another batch, or a closed one", async () => {
    renderOwn([admissionView("SUBMITTED", { id: "adm-2", batchId: "b-9" }), admissionView("CLOSED", { batchId: "b-1" })]);

    expect(await screen.findByRole("link", { name: "Apply" })).toHaveAttribute("href", "/admission/apply/b-1");
    expect(screen.queryByRole("heading", { name: "Your application" })).toBeNull();
  });
});
