import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { BatchFormDialog } from "@/components/staff/offline/batch-form-dialog";
import type { BatchView } from "@/lib/api/exam/offline-admin";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const SAVED: BatchView = {
  id: "b-1",
  nameEn: "Batch 3",
  nameBn: "ব্যাচ ৩",
  startsOn: null,
  endsOn: "2027-05-31",
  defaultMonthlyFee: 3000,
  status: "ACTIVE",
  studentCount: 0,
  routineUploaded: false,
  routineFileName: null,
  routineUploadedAt: null,
  routineUploadedBy: null,
  studentsNotified: null,
  defaultClassMinutes: 60,
  descriptionEn: "Evening classes.",
  descriptionBn: null,
  photoUploaded: true,
  admissionsOpen: true,
  displayOrder: 2,
};

describe("the batch form takes the catalogue fields (plan 3.1, 6)", () => {
  it("creates a batch with its descriptions, the admissions switch turned off, and the display order", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      "POST /api/v1/offline-batches": { ...SAVED, id: "b-9", descriptionEn: "Morning classes.", admissionsOpen: false, displayOrder: 1 },
    });
    renderWithProviders(<BatchFormDialog batch={null} open onOpenChange={() => undefined} />, { user: fixtures.masterAdmin, locale: "en" });

    await user.type(screen.getByRole("textbox", { name: /Name \(English\)/ }), "Batch 5");
    await user.type(screen.getByRole("textbox", { name: /Name \(Bangla\)/ }), "ব্যাচ ৫");
    fireEvent.change(screen.getByLabelText(/Ends on/), { target: { value: "2027-05-31" } });
    await user.type(screen.getByRole("textbox", { name: /Default monthly fee/ }), "2500");
    await user.type(screen.getByRole("textbox", { name: /Description \(English\)/ }), "Morning classes.");
    await user.clear(screen.getByRole("textbox", { name: /Display order/ }));
    await user.type(screen.getByRole("textbox", { name: /Display order/ }), "1");

    const switchButton = screen.getByRole("switch", { name: /Admissions open/ });
    expect(switchButton).toHaveAttribute("aria-checked", "true");
    await user.click(switchButton);
    expect(switchButton).toHaveAttribute("aria-checked", "false");

    await user.click(screen.getByRole("button", { name: "Create batch" }));
    await waitFor(() => expect(api.calls.some((call) => call.method === "POST" && call.path === "/api/v1/offline-batches")).toBe(true));
    const body = api.calls.find((call) => call.path === "/api/v1/offline-batches")?.body as Record<string, unknown>;
    expect(body).toMatchObject({
      nameEn: "Batch 5",
      endsOn: "2027-05-31",
      descriptionEn: "Morning classes.",
      descriptionBn: null,
      admissionsOpen: false,
      displayOrder: 1,
    });
    // A batch is offline and carries no exams or packages (plan P17): nothing else is sent.
    expect(api.calls.some((call) => call.path.endsWith("/items"))).toBe(false);
    expect(Object.keys(body)).not.toContain("items");
  });

  it.each(["en", "bn"] as const)("renders no raw translation key and no exam or package picker (%s)", (locale) => {
    renderWithProviders(<BatchFormDialog batch={null} open onOpenChange={() => undefined} />, { user: fixtures.masterAdmin, locale });

    const dialog = screen.getByRole("dialog");
    expect(dialog.textContent ?? "").not.toMatch(/staffOffline\.|publicAdmission\.|batchPay\./);
    expect(within(dialog).queryByRole("searchbox")).toBeNull();
    expect(within(dialog).queryByRole("combobox")).toBeNull();
    expect(within(dialog).queryByText(/package|exam/i)).toBeNull();
    expect(within(dialog).getByText(locale === "en" ? "No photo yet" : "এখনও ছবি নেই")).toBeInTheDocument();
  });

  it("shows no default items on an existing batch", () => {
    renderWithProviders(<BatchFormDialog batch={SAVED} open onOpenChange={() => undefined} />, { user: fixtures.masterAdmin, locale: "en" });

    const dialog = screen.getByRole("dialog");
    expect(dialog.textContent ?? "").not.toMatch(/staffOffline\.|publicAdmission\./);
    expect(within(dialog).queryByText(/package|exam/i)).toBeNull();
  });

  it("removes the stored photo when Remove photo is pressed and the batch is saved", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      "PATCH /api/v1/offline-batches/b-1": SAVED,
      "DELETE /api/v1/offline-batches/b-1/photo": SAVED,
    });
    renderWithProviders(<BatchFormDialog batch={SAVED} open onOpenChange={() => undefined} />, { user: fixtures.masterAdmin, locale: "en" });

    await user.click(screen.getByRole("button", { name: "Remove photo" }));
    await user.click(screen.getByRole("button", { name: "Save batch" }));

    await waitFor(() => expect(api.calls.some((call) => call.method === "DELETE")).toBe(true));
    expect(api.calls.find((call) => call.method === "DELETE")?.path).toBe("/api/v1/offline-batches/b-1/photo");
  });

  it("previews a chosen photo, and Undo drops it", async () => {
    const user = userEvent.setup();
    renderWithProviders(<BatchFormDialog batch={null} open onOpenChange={() => undefined} />, { user: fixtures.masterAdmin, locale: "en" });

    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    await user.upload(input, new File(["png"], "batch.png", { type: "image/png" }));
    const dialog = screen.getByRole("dialog");
    expect(await within(dialog).findByAltText("Preview of the batch photo")).toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: "Undo" }));
    expect(within(dialog).queryByAltText("Preview of the batch photo")).toBeNull();
  });
});
