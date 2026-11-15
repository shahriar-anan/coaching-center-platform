import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { Me } from "@/lib/api/exam/types";
import { describe, expect, it } from "vitest";
import { ExamResultSheet } from "@/components/staff/offline/result-sheet-view";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import {
  BATCH_ID,
  CLASS_BOTH,
  SHEET_PUBLISHED,
  SHEET_STAGED,
  batch,
  sheet,
  sheetRow,
  sheetView,
} from "./fixtures";

const PUBLISHED = sheet({
  id: SHEET_PUBLISHED,
  status: "PUBLISHED",
  fileName: "Exam-01.xlsx",
  rows: [sheetRow({ roll: "192001", studentName: "Rahim Uddin", total: 45, mcq: 45, rank: 1, passed: true })],
});

const STAGED = sheet({
  id: SHEET_STAGED,
  status: "STAGED",
  fileName: "Exam-01-fixed.xlsx",
  uploadedBy: "Office Staff",
  uploadedAt: "2026-11-13T05:00:00Z",
  publishedBy: null,
  publishedAt: null,
  // The students the publish tells, from the server (not the batch's count).
  notifyCount: 42,
  rows: [
    sheetRow({ roll: "192001", studentName: "Rahim Uddin", total: 46, mcq: 46, rank: 1, passed: true }),
    sheetRow({ roll: "192002", studentName: "Sumi Akter", total: 30, mcq: 30, rank: 2, passed: false, mismatch: true, fileTotal: 31 }),
  ],
});

/** The sheet view with the exam's sheets, and the calls it makes. */
function renderSheet(
  role: Me,
  sheets: { published?: typeof PUBLISHED | null; staged?: typeof STAGED | null },
) {
  const api = mockApi({
    [`GET /api/v1/offline-batches/${BATCH_ID}`]: batch,
    [`GET /api/v1/offline-classes/${CLASS_BOTH}/results`]: sheetView({
      classId: CLASS_BOTH,
      batchId: BATCH_ID,
      published: sheets.published ?? null,
      staged: sheets.staged ?? null,
    }),
    [`POST /api/v1/offline-classes/${CLASS_BOTH}/results/publish`]: sheetView({ classId: CLASS_BOTH, batchId: BATCH_ID, published: STAGED }),
    [`DELETE /api/v1/offline-classes/${CLASS_BOTH}/results/staged`]: null,
  });
  renderWithProviders(<ExamResultSheet batchId={BATCH_ID} classId={CLASS_BOTH} />, { user: role, locale: "en" });
  return { api, user: userEvent.setup() };
}

describe("the sheet view shows one exam's sheets (plan 7.7)", () => {
  it("offers a Published and a Staged tab when both exist, and opens on the staged sheet", async () => {
    renderSheet(fixtures.masterAdmin, { published: PUBLISHED, staged: STAGED });

    const published = await screen.findByRole("tab", { name: "Published" });
    const staged = screen.getByRole("tab", { name: "Staged" });
    expect(published).toBeInTheDocument();
    expect(staged).toHaveAttribute("aria-selected", "true");
    expect(screen.getByText("Exam-01-fixed.xlsx")).toBeInTheDocument();
  });

  it("switches to the published sheet and shows its rows", async () => {
    const { user } = renderSheet(fixtures.masterAdmin, { published: PUBLISHED, staged: STAGED });

    await user.click(await screen.findByRole("tab", { name: "Published" }));

    expect(screen.getByText("Exam-01.xlsx")).toBeInTheDocument();
    const table = screen.getByRole("table", { name: "Result sheet" });
    expect(within(table).getByText("Rahim Uddin")).toBeInTheDocument();
    expect(within(table).queryByText("Sumi Akter")).toBeNull();
  });

  it("shows one sheet and no tabs when only the staged sheet exists", async () => {
    renderSheet(fixtures.masterAdmin, { staged: STAGED });

    expect(await screen.findByText("Exam-01-fixed.xlsx")).toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: "Published" })).toBeNull();
  });

  it("names who uploaded and who published the sheet", async () => {
    renderSheet(fixtures.masterAdmin, { published: PUBLISHED });

    expect(await screen.findByText(/^Uploaded .* by Office Staff$/)).toBeInTheDocument();
    expect(screen.getByText(/^Published .* by Master Admin$/)).toBeInTheDocument();
  });
});

describe("the actions on the sheet view", () => {
  it("Publish confirms with the number of students, then calls publish", async () => {
    const { api, user } = renderSheet(fixtures.masterAdmin, { published: PUBLISHED, staged: STAGED });

    await user.click(await screen.findByRole("button", { name: "Publish" }));
    const dialog = (await screen.findAllByRole("dialog")).at(-1) as HTMLElement;
    expect(within(dialog).getByText("42 students will be told in the app.")).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Publish" }));

    await waitFor(() =>
      expect(api.calls.some((call) => call.method === "POST" && call.path.endsWith(`/${CLASS_BOTH}/results/publish`))).toBe(true),
    );
  });

  it("Discard staged confirms that the published one stays, then calls DELETE on the staged sheet", async () => {
    const { api, user } = renderSheet(fixtures.masterAdmin, { published: PUBLISHED, staged: STAGED });

    await user.click(await screen.findByRole("button", { name: "Discard staged" }));
    const dialog = (await screen.findAllByRole("dialog")).at(-1) as HTMLElement;
    expect(within(dialog).getByText("The staged version is removed. The published one stays.")).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Discard" }));

    await waitFor(() => {
      const call = api.calls.find((item) => item.method === "DELETE");
      expect(call?.path).toBe(`/api/v1/offline-classes/${CLASS_BOTH}/results/staged`);
    });
  });

  it("hides Publish from the System Admin, who can still discard and upload", async () => {
    renderSheet(fixtures.systemAdmin, { published: PUBLISHED, staged: STAGED });

    await screen.findByRole("button", { name: "Discard staged" });
    expect(screen.queryByRole("button", { name: "Publish" })).toBeNull();
    expect(screen.getByRole("button", { name: "Upload new version" })).toBeEnabled();
  });

  it("offers Download file for the sheet on show", async () => {
    renderSheet(fixtures.masterAdmin, { published: PUBLISHED });
    expect(await screen.findByRole("button", { name: "Download file" })).toBeEnabled();
  });
});
