import { screen, within } from "@testing-library/react";
import type { Me } from "@/lib/api/exam/types";
import { describe, expect, it, vi } from "vitest";
import { BatchResults } from "@/components/staff/offline/batch-results";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { BATCH_ID, CLASS_NONE, batch, examRow } from "./fixtures";

function renderTab(role: Me, rows: ReturnType<typeof examRow>[]) {
  mockApi({
    [`GET /api/v1/offline-batches/${BATCH_ID}`]: batch,
    [`GET /api/v1/offline-batches/${BATCH_ID}/results`]: rows,
  });
  renderWithProviders(<BatchResults batchId={BATCH_ID} onGo={vi.fn()} />, { user: role, locale: "en" });
}

async function rowOf(title: string) {
  const table = await screen.findByRole("table");
  const row = within(table).getByText(title).closest("tr");
  if (!row) {
    throw new Error(`No row for ${title}`);
  }
  return within(row);
}

describe("an exam marked as not taken takes no result (F8)", () => {
  it.each(["NOT_TAKEN", "NOT_TAKEN_PENDING"])("turns Upload off for status %s, with the reason under it", async (status) => {
    renderTab(fixtures.masterAdmin, [examRow({ classId: CLASS_NONE, subject: "Economy", topic: "Budget", status })]);
    const row = await rowOf("Economy · Budget");

    expect(row.getByRole("button", { name: "Upload result" })).toBeDisabled();
    expect(row.getByText("This exam is marked as not taken, so it takes no result.")).toBeInTheDocument();
  });

  it("keeps Upload on for a held exam that is paid", async () => {
    renderTab(fixtures.masterAdmin, [examRow({ classId: CLASS_NONE, subject: "Economy", topic: "Budget", status: "PAID" })]);
    const row = await rowOf("Economy · Budget");

    expect(row.getByRole("button", { name: "Upload result" })).toBeEnabled();
    expect(row.queryByText("This exam is marked as not taken, so it takes no result.")).toBeNull();
  });
});
