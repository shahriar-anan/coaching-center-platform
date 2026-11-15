import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AdmissionBatchesPage } from "@/components/public-admission/batches-page";
import { BatchPage } from "@/components/public-admission/batch-page";
import type { PublicBatch } from "@/lib/api/exam/public-admission";
import { mockApi, renderWithProviders } from "../helpers";

const OPEN: PublicBatch = {
  id: "b-1",
  nameEn: "Batch 3 (Evening)",
  nameBn: "ব্যাচ ৩ (সন্ধ্যা)",
  startsOn: "2026-12-01",
  endsOn: "2027-05-31",
  monthlyFee: 3000,
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
  monthlyFee: 2500,
  descriptionEn: null,
  admissionsOpen: false,
  routineAvailable: false,
};

describe("the Admission page shows the batches as cards (plan 3.2, 8)", () => {
  it("shows each batch with its dates and monthly fee, and Apply only where admissions are open", async () => {
    mockApi({ "GET /api/v1/public/offline-batches": [OPEN, CLOSED] });
    renderWithProviders(<AdmissionBatchesPage />, { locale: "en", route: "/admission" });

    const open = (await screen.findByRole("link", { name: /Batch 3 \(Evening\)/ })).closest("article");
    expect(open).not.toBeNull();
    const card = within(open as HTMLElement);
    expect(card.getByText("৳3,000 a month")).toBeInTheDocument();
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
    expect(screen.getByText("৳3,000 a month")).toBeInTheDocument();
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
