import { describe, expect, it } from "vitest";
import { examInView, parseExamView, withExamView } from "@/components/student/catalog/exam-view";
import type { PriceView } from "@/lib/api/exam/types";

const NOW = Date.parse("2026-10-31T10:00:00Z");
const HOUR = 60 * 60_000;

const free: PriceView = { price: null, discountPercent: null, discountEndsAt: null, effectivePrice: null, free: true, moduleOnly: false };
const paid: PriceView = { price: 500, discountPercent: null, discountEndsAt: null, effectivePrice: 500, free: false, moduleOnly: false };
const moduleOnly: PriceView = { price: null, discountPercent: null, discountEndsAt: null, effectivePrice: null, free: false, moduleOnly: true };

/** A live-mode exam with a window of the given offsets from NOW, in hours. */
function windowed(startHours: number, endHours: number, pricing: PriceView = paid) {
  return {
    mode: "LIVE",
    liveAt: new Date(NOW + startHours * HOUR).toISOString(),
    endAt: new Date(NOW + endHours * HOUR).toISOString(),
    pricing,
  };
}

describe("the exam tab from the URL (REQ-007)", () => {
  it("reads the four tabs in any case, and anything else as All", () => {
    expect(parseExamView("FREE")).toBe("FREE");
    expect(parseExamView("live")).toBe("LIVE");
    expect(parseExamView(" Archived ")).toBe("ARCHIVED");
    expect(parseExamView("ALL")).toBe("ALL");
    expect(parseExamView(null)).toBe("ALL");
    expect(parseExamView("")).toBe("ALL");
    expect(parseExamView("PRACTICE")).toBe("ALL");
    expect(parseExamView("nonsense")).toBe("ALL");
  });

  it("writes the tab next to the other query values, and All removes it", () => {
    const params = new URLSearchParams("type=MCQ&view=LIVE");
    expect(withExamView(params, "ARCHIVED").toString()).toBe("type=MCQ&view=ARCHIVED");
    expect(withExamView(params, "ALL").toString()).toBe("type=MCQ");
    expect(withExamView(new URLSearchParams("type=WRITTEN"), "FREE").toString()).toBe("type=WRITTEN&view=FREE");
  });
});

describe("the rules of each tab", () => {
  it("All keeps every exam, practice included", () => {
    expect(examInView(windowed(1, 2), "ALL", NOW)).toBe(true);
    expect(examInView({ mode: "PRACTICE", liveAt: null, endAt: null, pricing: paid }, "ALL", NOW)).toBe(true);
  });

  it("Free is an exam with no price, in any mode, and never a module-only exam", () => {
    expect(examInView({ mode: "PRACTICE", liveAt: null, endAt: null, pricing: free }, "FREE", NOW)).toBe(true);
    expect(examInView({ ...windowed(1, 2), pricing: free }, "FREE", NOW)).toBe(true);
    expect(examInView(windowed(1, 2, paid), "FREE", NOW)).toBe(false);
    expect(examInView(windowed(1, 2, moduleOnly), "FREE", NOW)).toBe(false);
    expect(examInView({ ...windowed(1, 2), pricing: null }, "FREE", NOW)).toBe(false);
  });

  it("Live is a live-mode exam whose window has not ended (unannounced, upcoming or running)", () => {
    const unannounced = { mode: "LIVE", liveAt: null, endAt: null, pricing: paid };
    expect(examInView(unannounced, "LIVE", NOW)).toBe(true);
    expect(examInView(windowed(1, 3), "LIVE", NOW)).toBe(true);
    expect(examInView(windowed(-1, 1), "LIVE", NOW)).toBe(true);
    expect(examInView(windowed(-3, -1), "LIVE", NOW)).toBe(false);
  });

  it("Archived is a live-mode exam whose window has ended", () => {
    expect(examInView(windowed(-3, -1), "ARCHIVED", NOW)).toBe(true);
    expect(examInView(windowed(-1, 1), "ARCHIVED", NOW)).toBe(false);
    expect(examInView({ mode: "LIVE", liveAt: null, endAt: null, pricing: paid }, "ARCHIVED", NOW)).toBe(false);
  });

  it("practice never reaches Live or Archived, however its dates read", () => {
    const practice = { mode: "PRACTICE", liveAt: null, endAt: null, pricing: paid };
    expect(examInView(practice, "LIVE", NOW)).toBe(false);
    expect(examInView(practice, "ARCHIVED", NOW)).toBe(false);
    expect(examInView({ ...practice, liveAt: "2026-10-01T00:00:00Z", endAt: "2026-10-02T00:00:00Z" }, "ARCHIVED", NOW)).toBe(
      false,
    );
  });

  it("an exam with no mode is not live", () => {
    expect(examInView({ liveAt: null, endAt: null, pricing: paid }, "LIVE", NOW)).toBe(false);
  });
});
