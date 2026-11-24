import { screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { AddClassDialog } from "@/components/staff/offline/add-class-dialog";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { classRow, milon, rahim } from "./fixtures";

vi.mock("@/i18n/messages", async (importOriginal) => {
  const mod = await importOriginal<typeof import("@/i18n/messages")>();
  const { withBatchPay } = await import("./catalog");
  return withBatchPay(mod);
});

describe("Add class: the instructor list (F13)", () => {
  it("lists the batch's instructors first, then the other active instructors, after No instructor", async () => {
    // 24 Nov: the batch's instructors are the ones who teach its classes (there are no rates any more).
    mockApi({
      "GET /api/v1/offline-batches/b1/classes": [classRow({ id: "c1", instructorId: "i2", instructorName: "Rahim Sir" })],
      "GET /api/v1/offline-instructors": [milon, rahim],
    });
    renderWithProviders(<AddClassDialog batchId="b1" classMinutes={60} open onOpenChange={vi.fn()} onAdded={vi.fn()} />, {
      user: fixtures.masterAdmin,
      locale: "en",
    });

    const select = await screen.findByRole("combobox", { name: "Instructor" });
    await waitFor(() => expect(within(select).getAllByRole("option")).toHaveLength(3));

    expect(within(select).getAllByRole("option").map((option) => option.textContent)).toEqual(["No instructor", "Rahim Sir", "Milon Hossain"]);
    const groups = Array.from(select.querySelectorAll("optgroup")).map((group) => group.label);
    expect(groups).toEqual(["Instructors of this batch", "Other instructors"]);
  });
});
