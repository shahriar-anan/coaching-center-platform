import { screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { RoutinePreview } from "@/components/staff/offline/routine-preview";
import type { RoutineClassRef, RoutineDifference } from "@/lib/api/exam/offline-batch-pay";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { batch, milon, previewRoutine, rahim, ROUTINE_ID } from "./fixtures";

vi.mock("@/i18n/messages", async (importOriginal) => {
  const mod = await importOriginal<typeof import("@/i18n/messages")>();
  const { withBatchPay } = await import("./catalog");
  return withBatchPay(mod);
});

function ref(overrides: Partial<RoutineClassRef> & { id: string; subject: string }): RoutineClassRef {
  return {
    date: "2026-11-02",
    startsAt: "17:10:00",
    topic: null,
    instructorText: null,
    status: "SCHEDULED",
    ...overrides,
  };
}

function difference(overrides: Partial<RoutineDifference>): RoutineDifference {
  return { added: [], changed: [], cancelled: [], settled: [], unchanged: 0, ...overrides };
}

function setup(diff: RoutineDifference) {
  mockApi({
    [`GET /api/v1/offline-routines/${ROUTINE_ID}`]: previewRoutine({ status: "CONFIRMED", difference: diff }),
    "GET /api/v1/offline-batches/b1": batch,
    "GET /api/v1/offline-instructors": [milon, rahim],
  });
  renderWithProviders(<RoutinePreview batchId="b1" routineId={ROUTINE_ID} />, { user: fixtures.masterAdmin, locale: "en" });
}

describe("routine preview: an exam with a result is kept (plan 7.9)", () => {
  it("marks a kept exam, and counts only the classes that are really removed", async () => {
    setup(
      difference({
        cancelled: [
          ref({ id: "c1", subject: "Constitution", keptForResult: true }),
          ref({ id: "c2", subject: "Biology", date: "2026-11-03", keptForResult: false }),
        ],
        unchanged: 4,
      }),
    );

    expect(await screen.findByText("Cancelled (1)")).toBeInTheDocument();
    expect(screen.getByText("Kept, has a result (1)")).toBeInTheDocument();
    const kept = screen.getByText("Has a result · kept").closest("li");
    expect(kept).not.toBeNull();
    expect(within(kept as HTMLElement).getByText(/Constitution/)).toBeInTheDocument();
  });

  it("does not list a kept exam as removed or moved when it is the only change", async () => {
    setup(
      difference({
        cancelled: [ref({ id: "c1", subject: "Constitution", keptForResult: true })],
        changed: [
          {
            before: ref({ id: "c3", subject: "Chemistry", keptForResult: true }),
            after: {
              index: 0,
              date: "2026-11-05",
              day: "Thursday",
              time: "5.10 PM",
              startsAt: "17:10",
              endsAt: "18:30",
              subject: "Chemistry",
              topic: null,
              kind: "EXAM",
              instructorText: null,
              instructorId: null,
              instructorName: null,
              flags: [],
            },
          },
        ],
      }),
    );

    expect(await screen.findByText("Kept, has a result (2)")).toBeInTheDocument();
    expect(screen.queryByText(/^Cancelled \(/)).not.toBeInTheDocument();
    expect(screen.queryByText(/^Changed \(/)).not.toBeInTheDocument();
    expect(screen.queryByText("Nothing changes in the classes.")).not.toBeInTheDocument();
  });
});
