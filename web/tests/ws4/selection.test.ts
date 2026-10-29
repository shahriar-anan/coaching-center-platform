import { describe, expect, it } from "vitest";
import {
  answerPayload,
  mergeSelection,
  nextRetryDelay,
  toggleFlag,
  toggleOption,
} from "@/components/student/sitting/selection";
import { crossedWarning, inFinalMinute } from "@/components/student/sitting/warnings";

describe("selection", () => {
  it("tapping the selected option again clears it, another option replaces it", () => {
    const first = toggleOption({}, "q1", "a");
    expect(first).toEqual({ q1: ["a"] });
    expect(toggleOption(first, "q1", "a")).toEqual({});
    expect(toggleOption(first, "q1", "b")).toEqual({ q1: ["b"] });
  });

  it("sends the whole selection as a list of option ids", () => {
    expect(answerPayload({ q1: ["a"], q2: ["c"] }, new Set(), true)).toEqual([
      { questionId: "q1", optionIds: ["a"] },
      { questionId: "q2", optionIds: ["c"] },
    ]);
    expect(answerPayload({}, new Set(), true)).toEqual([]);
  });

  it("flags toggle without touching the answers", () => {
    expect(toggleFlag([], "q1")).toEqual(["q1"]);
    expect(toggleFlag(["q1", "q2"], "q1")).toEqual(["q2"]);
  });

  it("keeps the server copy when there is no backup", () => {
    expect(mergeSelection({ q1: ["a"] }, 1000, null)).toEqual({ q1: ["a"] });
  });

  it("uses the backup when this device is newer than the server copy", () => {
    const backup = { selection: { q1: ["b"], q2: ["c"] }, flagged: [], savedAt: 2000 };
    expect(mergeSelection({ q1: ["a"] }, 1000, backup)).toEqual({ q1: ["a"], q2: ["c"] });
  });

  it("server wins where both have an answer when the server copy is at least as new", () => {
    const backup = { selection: { q1: ["b"] }, flagged: [], savedAt: 1000 };
    expect(mergeSelection({ q1: ["a"] }, 2000, backup)).toEqual({ q1: ["a"] });
  });

  it("when the server has no save time, the backup fills in", () => {
    const backup = { selection: { q3: ["d"] }, flagged: [], savedAt: 500 };
    expect(mergeSelection({}, null, backup)).toEqual({ q3: ["d"] });
  });

  it("retries at 1 s, 2 s, 4 s and then stops", () => {
    expect([0, 1, 2, 3].map(nextRetryDelay)).toEqual([1000, 2000, 4000, null]);
  });
});

describe("time warnings", () => {
  const minute = 60_000;

  it("fires at 10, 5 and 1 minute, once each", () => {
    expect(crossedWarning(null, 11 * minute)).toBeNull();
    expect(crossedWarning(11 * minute, 10 * minute)).toBe(10);
    expect(crossedWarning(10 * minute, 9 * minute)).toBeNull();
    expect(crossedWarning(9 * minute, 5 * minute)).toBe(5);
    expect(crossedWarning(5 * minute, 1 * minute)).toBe(1);
    expect(crossedWarning(1 * minute, 30_000)).toBeNull();
  });

  it("a long jump past several thresholds reports the largest one crossed", () => {
    expect(crossedWarning(20 * minute, 4 * minute)).toBe(10);
  });

  it("the bar turns to the warning tone only in the final minute", () => {
    expect(inFinalMinute(61_000)).toBe(false);
    expect(inFinalMinute(60_000)).toBe(true);
    expect(inFinalMinute(0)).toBe(false);
  });
});
