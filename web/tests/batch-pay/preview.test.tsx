import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { RoutinePreview } from "@/components/staff/offline/routine-preview";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { batch, milon, previewRoutine, rahim, ROUTINE_ID } from "./fixtures";

vi.mock("@/i18n/messages", async (importOriginal) => {
  const mod = await importOriginal<typeof import("@/i18n/messages")>();
  const { withBatchPay } = await import("./catalog");
  return withBatchPay(mod);
});

/** The preview answers from a value the test can change, so a match or an edit shows on the next read. */
function previewApi(initial: ReturnType<typeof previewRoutine>) {
  let routine = initial;
  const matched = previewRoutine({
    unmatchedNames: [],
    blockers: [],
    canConfirm: true,
    flaggedCount: 0,
    rows: [
      { ...initial.rows[0], instructorId: "i1", instructorName: "Milon Hossain", flags: [] },
      initial.rows[1],
    ],
  });
  const api = mockApi({
    [`GET /api/v1/offline-routines/${ROUTINE_ID}`]: () => routine,
    [`POST /api/v1/offline-routines/${ROUTINE_ID}/instructor-matches`]: () => {
      routine = matched;
      return routine;
    },
    "GET /api/v1/offline-batches/b1": batch,
    "GET /api/v1/offline-instructors": [milon, rahim],
  });
  return api;
}

type Call = { method: string; path: string; body: unknown };

function postedMatch(calls: Call[]) {
  return calls.find((call) => call.method === "POST" && call.path.endsWith("/instructor-matches"));
}

describe("import preview (plan 8.1)", () => {
  it("takes the instructor from the PDF: an unknown name is a new instructor and does not block Confirm", async () => {
    const user = userEvent.setup();
    const api = previewApi(previewRoutine());
    renderWithProviders(<RoutinePreview batchId="b1" routineId={ROUTINE_ID} />, { user: fixtures.masterAdmin, locale: "en" });

    const confirm = await screen.findByRole("button", { name: "Confirm 2 classes" });
    expect(confirm).toBeEnabled();
    expect(screen.getByText("New: Milon Sir")).toBeInTheDocument();
    expect(screen.getByText(/Confirming adds Milon Sir to the instructors/)).toBeInTheDocument();
    expect(screen.getByText("New instructor: added when you confirm.")).toBeInTheDocument();

    // When the routine's name is someone already in the list, Change picks them: every row with that name takes them.
    await user.click(screen.getAllByRole("button", { name: "Change" })[0]);
    expect(await screen.findByText("Instructor as per routine")).toBeInTheDocument();
    await user.click(screen.getByRole("radio", { name: "Someone else" }));
    await user.type(screen.getByLabelText("Instructor name"), "Milon Hossain");
    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(postedMatch(api.calls)?.body).toEqual({ name: "Milon Sir", instructorId: "i1" }));
  });

  it("enables Confirm when nothing blocks it", async () => {
    previewApi(
      previewRoutine({
        unmatchedNames: [],
        blockers: [],
        canConfirm: true,
        flaggedCount: 0,
        rows: [
          { ...previewRoutine().rows[0], instructorId: "i1", instructorName: "Milon Hossain", flags: [] },
          previewRoutine().rows[1],
        ],
      }),
    );
    renderWithProviders(<RoutinePreview batchId="b1" routineId={ROUTINE_ID} />, { user: fixtures.masterAdmin, locale: "en" });

    expect(await screen.findByRole("button", { name: "Confirm 2 classes" })).toBeEnabled();
  });
});
