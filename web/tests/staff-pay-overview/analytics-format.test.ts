import { describe, expect, it } from "vitest";
import {
  axisLabel,
  chartScale,
  defaultRange,
  isMonth,
  monthLabel,
  percentOf,
  rangeProblem,
  shiftMonth,
} from "@/components/staff/pay/pay-analytics-format";

describe("the month arithmetic on the analytics screen", () => {
  it("shifts a month across year ends in both directions", () => {
    expect(shiftMonth("2026-11", -11)).toBe("2025-12");
    expect(shiftMonth("2026-01", -1)).toBe("2025-12");
    expect(shiftMonth("2026-12", 1)).toBe("2027-01");
    expect(shiftMonth("2026-11", 0)).toBe("2026-11");
  });

  it("accepts only a month written YYYY-MM", () => {
    expect(isMonth("2026-11")).toBe(true);
    for (const text of ["2026-1", "2026-13", "2026-00", "26-11", "", "2026/11"]) {
      expect(isMonth(text)).toBe(false);
    }
  });

  it("defaults to the last twelve months, ending in this month in Dhaka", () => {
    // 05:00 on 5 November in Dhaka is 23:00 UTC on 4 November.
    expect(defaultRange(Date.parse("2026-11-04T23:00:00Z"))).toEqual({ from: "2025-12", to: "2026-11" });
    // 01:00 on 1 November in Dhaka is 19:00 UTC on 31 October: the range already ends in November.
    expect(defaultRange(Date.parse("2026-10-31T19:00:00Z")).to).toBe("2026-11");
  });
});

describe("which ranges the screen asks for", () => {
  it("allows one month and up to thirty-six months", () => {
    expect(rangeProblem("2026-11", "2026-11")).toBeNull();
    expect(rangeProblem("2026-02", "2029-01")).toBeNull();
  });

  it("refuses a blank or malformed month, an end before the start, and more than thirty-six months", () => {
    expect(rangeProblem("", "2026-11")).toBe("invalid");
    expect(rangeProblem("2026-13", "2026-11")).toBe("invalid");
    expect(rangeProblem("2026-11", "2026-10")).toBe("backwards");
    expect(rangeProblem("2026-02", "2029-02")).toBe("long");
  });
});

describe("the chart arithmetic", () => {
  it("rounds the largest value up to 1, 2 or 5 times a power of ten, and shows 0 to 1,000 when everything is zero", () => {
    expect(chartScale([5, 20, 3])).toBe(20);
    expect(chartScale([15])).toBe(20);
    expect(chartScale([3200, 150])).toBe(5000);
    expect(chartScale([6000])).toBe(10000);
    expect(chartScale([1000])).toBe(1000);
    expect(chartScale([0, 0])).toBe(1000);
    expect(chartScale([])).toBe(1000);
  });

  it("keeps a bar's height between zero and the full scale", () => {
    expect(percentOf(50, 200)).toBe(25);
    expect(percentOf(300, 200)).toBe(100);
    expect(percentOf(-5, 200)).toBe(0);
  });

  it("writes axis ticks in full below 10,000 and in thousands from there", () => {
    expect(axisLabel(0, "en")).toBe("0");
    expect(axisLabel(9999, "en")).toBe("9,999");
    expect(axisLabel(20000, "en")).toBe("20K");
    expect(axisLabel(20000, "bn")).toContain("K");
  });
});

describe("the month labels", () => {
  it("are short month names in the viewer's language, with the year only on January", () => {
    expect(monthLabel("2026-11", "en")).toBe("Nov");
    expect(monthLabel("2027-01", "en")).toMatch(/Jan.*27/);
    expect(monthLabel("2026-11", "bn")).not.toBe("");
  });
});
