import { describe, expect, it } from "vitest";
import { examPhasePoll } from "@/lib/api/exam/catalog";

describe("examPhasePoll", () => {
  it("re-reads every 30 seconds while the exam has not ended", () => {
    expect(examPhasePoll("UPCOMING")).toBe(30_000);
    expect(examPhasePoll("LIVE")).toBe(30_000);
    expect(examPhasePoll("UNANNOUNCED")).toBe(30_000);
  });

  it("stops re-reading once the exam has ended or is practice", () => {
    expect(examPhasePoll("ENDED")).toBe(false);
    expect(examPhasePoll("PRACTICE")).toBe(false);
  });

  it("re-reads until the first response arrives, when the phase is not known yet", () => {
    expect(examPhasePoll(undefined)).toBe(30_000);
  });
});
