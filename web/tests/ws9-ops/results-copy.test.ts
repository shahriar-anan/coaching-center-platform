import { describe, expect, it } from "vitest";
import {
  publishedTone,
  resultStatusTone,
  resultTone,
  resultsSummaryItems,
} from "@/components/staff/results/results-copy";
import type { ResultsSummary } from "@/lib/api/exam/staff-results";
import type { useFormat } from "@/lib/format/use-format";

/** A translator that returns the key and its parameters, so a test can see which copy is chosen. */
const t = (key: string, params?: Record<string, string | number>) =>
  params ? `${key}|${Object.values(params).join(",")}` : key;

/** Formats numbers plainly; the real formatter is covered by the page tests. */
const format = {
  formatNumber: (value: number, options?: { decimals?: number }) =>
    options?.decimals ? value.toFixed(options.decimals) : String(value),
  formatMoney: (value: number | null | undefined) => (value == null ? null : `৳${value}`),
} as unknown as ReturnType<typeof useFormat>;

const summary: ResultsSummary = {
  participants: 4,
  submitted: 3,
  failedToSubmit: 1,
  averageScore: 7.5,
  highestScore: 9,
  lowestScore: 6,
  passCount: 2,
  passRate: 66.7,
  practiceParticipants: 0,
};

describe("results summary strip", () => {
  it("shows the eight counts in report order", () => {
    const items = resultsSummaryItems(summary, t, format);
    expect(items.map((item) => item.label)).toEqual([
      "staffResults.summary.participants",
      "staffResults.summary.submitted",
      "staffResults.summary.failed",
      "staffResults.summary.average",
      "staffResults.summary.highest",
      "staffResults.summary.lowest",
      "staffResults.summary.passed",
      "staffResults.summary.passRate",
    ]);
    expect(items.map((item) => item.value)).toEqual([
      "4",
      "3",
      "1",
      "7.5",
      "9",
      "6",
      "2",
      "staffResults.percent|66.7",
    ]);
  });

  it("shows a dash, not zero, when there is no score or pass rate yet", () => {
    const items = resultsSummaryItems(
      { ...summary, averageScore: null, highestScore: null, lowestScore: null, passRate: null },
      t,
      format,
    );
    expect(items.slice(3, 6).map((item) => item.value)).toEqual(["staffResults.result.none", "staffResults.result.none", "staffResults.result.none"]);
    expect(items[7].value).toBe("staffResults.result.none");
  });

  it("marks failed-to-submit in danger only when there are some", () => {
    expect(resultsSummaryItems(summary, t, format)[2].tone).toBe("danger");
    expect(resultsSummaryItems({ ...summary, failedToSubmit: 0 }, t, format)[2].tone).toBe("muted");
  });
});

describe("result pills", () => {
  it("maps a sitting status to a tone", () => {
    expect(resultStatusTone("SUBMITTED")).toBe("ok");
    expect(resultStatusTone("FAILED_TO_SUBMIT")).toBe("danger");
    expect(resultStatusTone("IN_PROGRESS")).toBe("warn");
  });

  it("keeps a result with no score neutral, not failed", () => {
    expect(resultTone(null)).toBe("muted");
    expect(resultTone(true)).toBe("ok");
    expect(resultTone(false)).toBe("danger");
  });

  it("shows a written result as published or waiting", () => {
    expect(publishedTone(true)).toBe("ok");
    expect(publishedTone(false)).toBe("warn");
  });
});

describe("results summary strip when the API leaves score fields out", () => {
  it("shows dashes for the missing scores instead of failing (a written exam before release)", () => {
    const omitted = { participants: 2, submitted: 2, failedToSubmit: 0, passCount: 0, practiceParticipants: 1 } as unknown as ResultsSummary;
    const items = resultsSummaryItems(omitted, t, format);
    expect(items.find((item) => item.label === "staffResults.summary.average")?.value).toBe("staffResults.result.none");
    expect(items.find((item) => item.label === "staffResults.summary.highest")?.value).toBe("staffResults.result.none");
    expect(items.find((item) => item.label === "staffResults.summary.lowest")?.value).toBe("staffResults.result.none");
    expect(items.find((item) => item.label === "staffResults.summary.passRate")?.value).toBe("staffResults.result.none");
    expect(items.find((item) => item.label === "staffResults.summary.participants")?.value).toBe("2");
  });
});
