import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { PublishResultDialog } from "@/components/staff/offline/result-confirms";
import type { ResultChange, ResultSheet } from "@/lib/api/exam/offline-results";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { BATCH_ID, CLASS_STAGED, sheet, sheetView } from "./fixtures";

const VIEW = `GET /api/v1/offline-classes/${CLASS_STAGED}/results`;
const PUBLISH = `POST /api/v1/offline-classes/${CLASS_STAGED}/results/publish`;

/** The publish confirm over a staged sheet, read from the sheet view. The batch's own student count is 42 in the fixtures. */
function renderConfirm(staged: ResultSheet) {
  const onOpenChange = vi.fn();
  const api = mockApi({
    [VIEW]: sheetView({ classId: CLASS_STAGED, batchId: BATCH_ID, staged }),
    [PUBLISH]: sheetView({ classId: CLASS_STAGED, batchId: BATCH_ID, published: staged }),
  });
  renderWithProviders(<PublishResultDialog classId={CLASS_STAGED} open onOpenChange={onOpenChange} />, {
    user: fixtures.masterAdmin,
    locale: "en",
  });
  return { api, onOpenChange, user: userEvent.setup() };
}

const CHANGED: ResultChange[] = [
  { admissionId: "adm-2", name: "Sumi Akter", oldRoll: "192002", newRoll: "192102", closed: false },
  { admissionId: "adm-3", name: "Kamal Hossain", oldRoll: "192003", newRoll: null, closed: true },
];

describe("the publish confirm counts the students the publish tells (F3)", () => {
  it("uses the staged sheet's notifyCount, not the batch's student count", async () => {
    renderConfirm(sheet({ status: "STAGED", publishedAt: null, publishedBy: null, notifyCount: 3 }));

    expect(await screen.findByText("3 students will be told in the app.")).toBeInTheDocument();
    expect(screen.queryByText("42 students will be told in the app.")).toBeNull();
  });

  it("uses the singular for one student", async () => {
    renderConfirm(sheet({ status: "STAGED", publishedAt: null, publishedBy: null, notifyCount: 1 }));

    expect(await screen.findByText("1 student will be told in the app.")).toBeInTheDocument();
  });
});

describe("the publish confirm lists what changed since the save (F12)", () => {
  it("lists each changed student above the confirm: a new roll, or closed", async () => {
    renderConfirm(sheet({ status: "STAGED", publishedAt: null, publishedBy: null, notifyCount: 2, changedSinceSave: CHANGED }));

    // The list comes with the sheet view, so the test waits for it.
    await screen.findByText("Changed since the save");
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByText("Sumi Akter: roll 192002 now 192102")).toBeInTheDocument();
    expect(within(dialog).getByText("Kamal Hossain: closed")).toBeInTheDocument();
  });

  it("still publishes when something changed", async () => {
    const { api, user } = renderConfirm(
      sheet({ status: "STAGED", publishedAt: null, publishedBy: null, notifyCount: 2, changedSinceSave: CHANGED }),
    );

    await screen.findByText("Changed since the save");
    const dialog = screen.getByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Publish" }));

    await waitFor(() => expect(api.calls.some((call) => call.method === "POST" && call.path.endsWith("/results/publish"))).toBe(true));
  });

  it("shows no list when nothing changed", async () => {
    renderConfirm(sheet({ status: "STAGED", publishedAt: null, publishedBy: null, notifyCount: 2, changedSinceSave: [] }));

    await screen.findByText("2 students will be told in the app.");
    expect(screen.queryByText("Changed since the save")).toBeNull();
  });
});
