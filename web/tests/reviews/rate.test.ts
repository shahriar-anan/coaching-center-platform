import { describe, expect, it } from "vitest";
import { canRateSitting } from "@/components/student/reviews/rate-link";
import { reviewedTargets, type MyReviewRow } from "@/lib/api/exam/reviews";

describe("Rate on result and My exams rows", () => {
  it("offers Rate for a submitted sitting that has a result and no review yet", () => {
    const none = new Set<string>();
    expect(canRateSitting("RESULT_READY", "e1", none)).toBe(true);
    expect(canRateSitting("AWAITING_RESULT", "e1", none)).toBe(true);
  });

  it("does not offer Rate for a sitting still in progress, one that failed to submit, or an unknown exam", () => {
    const none = new Set<string>();
    expect(canRateSitting("IN_PROGRESS", "e1", none)).toBe(false);
    expect(canRateSitting("SUBMITTED", "e1", none)).toBe(false);
    expect(canRateSitting("FAILED_TO_SUBMIT", "e1", none)).toBe(false);
    expect(canRateSitting(undefined, "e1", none)).toBe(false);
  });

  it("hides Rate once the exam is reviewed, and only that exam", () => {
    const rows: MyReviewRow[] = [
      {
        kind: "exam",
        targetId: "e1",
        targetTitleEn: "One",
        targetTitleBn: "এক",
        review: { id: "r1", rating: 3, comment: null, status: "APPROVED", hideReason: null, createdAt: "", updatedAt: "" },
      },
      {
        kind: "package",
        targetId: "e2",
        targetTitleEn: "Package",
        targetTitleBn: "প্যাকেজ",
        review: { id: "r2", rating: 5, comment: null, status: "APPROVED", hideReason: null, createdAt: "", updatedAt: "" },
      },
    ];
    const reviewed = reviewedTargets(rows);
    expect(canRateSitting("RESULT_READY", "e1", reviewed)).toBe(false);
    expect(canRateSitting("RESULT_READY", "e2", reviewed)).toBe(true);
    expect(reviewedTargets(undefined).size).toBe(0);
  });
});
