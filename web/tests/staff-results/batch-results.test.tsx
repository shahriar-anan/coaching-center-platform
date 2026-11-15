import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { Me } from "@/lib/api/exam/types";
import { describe, expect, it, vi } from "vitest";
import { BatchResults } from "@/components/staff/offline/batch-results";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import {
  BATCH_ID,
  CLASS_BOTH,
  CLASS_CANCELLED,
  CLASS_FUTURE,
  CLASS_NONE,
  CLASS_PUBLISHED,
  CLASS_STAGED,
  FUTURE_DATE,
  batch,
  examRow,
} from "./fixtures";

/** The exams of the batch, one per state. Each row is in the table (the phone cards repeat the same buttons, so the tests read the table). */
const EXAMS = [
  examRow({ classId: CLASS_FUTURE, heldOn: FUTURE_DATE, subject: "Economy", topic: "Budget", state: "NONE" }),
  examRow({ classId: CLASS_BOTH, heldOn: "2026-11-12", subject: "Constitution", topic: "Part 2", state: "PUBLISHED_WITH_STAGED", publishedAt: "2026-11-13T04:00:00Z", rowCount: 40 }),
  examRow({ classId: CLASS_STAGED, heldOn: "2026-11-11", subject: "Bangla", topic: "Grammar", state: "STAGED" }),
  examRow({ classId: CLASS_PUBLISHED, heldOn: "2026-11-10", subject: "History", topic: "Liberation", state: "PUBLISHED", publishedAt: "2026-11-11T04:00:00Z", rowCount: 38 }),
  examRow({ classId: CLASS_CANCELLED, heldOn: "2026-11-09", subject: "Science", topic: "Physics", cancelled: true, state: "NONE" }),
  examRow({ classId: CLASS_NONE, heldOn: "2026-11-08", subject: "Math", topic: "Algebra", state: "NONE", instructorName: null }),
];

function renderTab(role: Me, onGo = vi.fn()) {
  mockApi({
    [`GET /api/v1/offline-batches/${BATCH_ID}`]: batch,
    [`GET /api/v1/offline-batches/${BATCH_ID}/results`]: EXAMS,
  });
  renderWithProviders(<BatchResults batchId={BATCH_ID} onGo={onGo} />, { user: role, locale: "en" });
  return { onGo };
}

/** The desktop table: one row per exam, read by the exam's title. */
async function table() {
  return screen.findByRole("table");
}

async function rowOf(title: string) {
  const grid = await table();
  const row = within(grid).getByText(title).closest("tr");
  if (!row) {
    throw new Error(`No row for ${title}`);
  }
  return within(row);
}

describe("the batch Results tab lists the exams with their state (plan 7.5)", () => {
  it("shows each exam's state, the published date and the instructor, newest first", async () => {
    renderTab(fixtures.masterAdmin);
    const grid = await table();

    const rows = within(grid).getAllByRole("row").slice(1);
    expect(rows[0]).toHaveTextContent("Economy · Budget");
    expect(rows[1]).toHaveTextContent("Constitution · Part 2");

    expect((await rowOf("Bangla · Grammar")).getByText("Staged")).toBeInTheDocument();
    expect((await rowOf("History · Liberation")).getByText("Published")).toBeInTheDocument();
    expect((await rowOf("Constitution · Part 2")).getByText("Published · new version staged")).toBeInTheDocument();
    expect((await rowOf("Math · Algebra")).getByText("No instructor")).toBeInTheDocument();
    expect((await rowOf("Math · Algebra")).getByText("No result")).toBeInTheDocument();
  });

  it("shows the publish date under a published state", async () => {
    renderTab(fixtures.masterAdmin);
    const row = await rowOf("History · Liberation");
    expect(row.getAllByText(/^Published\s+\S/).length).toBeGreaterThan(0);
  });

  it("marks a cancelled exam", async () => {
    renderTab(fixtures.masterAdmin);
    expect((await rowOf("Science · Physics")).getByText("Cancelled")).toBeInTheDocument();
  });
});

describe("row actions by role and state", () => {
  it("the Master Admin sees Publish on every row with a staged sheet, and not on the others", async () => {
    renderTab(fixtures.masterAdmin);

    expect((await rowOf("Bangla · Grammar")).getByRole("button", { name: "Publish" })).toBeEnabled();
    expect((await rowOf("Constitution · Part 2")).getByRole("button", { name: "Publish" })).toBeEnabled();
    expect((await rowOf("History · Liberation")).queryByRole("button", { name: "Publish" })).toBeNull();
    expect((await rowOf("Math · Algebra")).queryByRole("button", { name: "Publish" })).toBeNull();
  });

  it("the System Admin uploads and reviews, but sees no Publish anywhere", async () => {
    renderTab(fixtures.systemAdmin);
    const grid = await table();

    expect(within(grid).queryByRole("button", { name: "Publish" })).toBeNull();
    expect((await rowOf("Bangla · Grammar")).getByRole("button", { name: "Upload result" })).toBeEnabled();
    expect((await rowOf("History · Liberation")).getByRole("button", { name: "Upload new version" })).toBeEnabled();
    expect((await rowOf("Bangla · Grammar")).getByRole("link", { name: "Review" })).toHaveAttribute(
      "href",
      `/staff/offline-batches/${BATCH_ID}/results/${CLASS_STAGED}`,
    );
  });

  it("offers Review only once a sheet exists", async () => {
    renderTab(fixtures.masterAdmin);
    expect((await rowOf("Math · Algebra")).queryByRole("link", { name: "Review" })).toBeNull();
  });

  it("switches the upload button to Upload new version when a published sheet exists", async () => {
    renderTab(fixtures.masterAdmin);
    expect((await rowOf("History · Liberation")).getByRole("button", { name: "Upload new version" })).toBeInTheDocument();
    expect((await rowOf("Bangla · Grammar")).getByRole("button", { name: "Upload result" })).toBeInTheDocument();
  });

  it("turns Upload off for a future exam, with the reason under it", async () => {
    renderTab(fixtures.masterAdmin);
    const row = await rowOf("Economy · Budget");

    expect(row.getByRole("button", { name: "Upload result" })).toBeDisabled();
    expect(row.getByText("This exam is not held yet. Results can be uploaded from the exam date.")).toBeInTheDocument();
  });

  it("turns Upload off for a cancelled exam, with the reason under it", async () => {
    renderTab(fixtures.masterAdmin);
    const row = await rowOf("Science · Physics");

    expect(row.getByRole("button", { name: "Upload result" })).toBeDisabled();
    expect(row.getByText("This exam was cancelled, so it takes no result.")).toBeInTheDocument();
  });
});

describe("the empty state", () => {
  it("offers a button to the Classes tab when the batch has no exams", async () => {
    mockApi({
      [`GET /api/v1/offline-batches/${BATCH_ID}`]: batch,
      [`GET /api/v1/offline-batches/${BATCH_ID}/results`]: [],
    });
    const onGo = vi.fn();
    renderWithProviders(<BatchResults batchId={BATCH_ID} onGo={onGo} />, { user: fixtures.masterAdmin, locale: "en" });
    const user = userEvent.setup();

    expect(await screen.findByText("This batch has no exams yet.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Go to Classes" }));
    expect(onGo).toHaveBeenCalledWith("classes");
  });
});
