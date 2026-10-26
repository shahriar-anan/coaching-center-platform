import type { PillTone } from "@/components/ui/pill";
import type { ResultsRow, ResultsSummary } from "@/lib/api/exam/staff-results";
import type { useFormat } from "@/lib/format/use-format";

/*
 * Pure mapping from API words to what staff read on the results report. The status is
 * always a word; the colour only reinforces it.
 */

export const RESULT_KINDS = ["LIVE", "RETAKE"] as const;

export const RESULT_STATUSES = ["IN_PROGRESS", "SUBMITTED", "FAILED_TO_SUBMIT"] as const;

/** The sort values the API accepts, each with its copy key under `staffResults.sort`. */
export const RESULT_SORTS = [
  { value: "submittedAt,desc", key: "submittedNewest" },
  { value: "submittedAt,asc", key: "submittedOldest" },
  { value: "score,desc", key: "scoreHigh" },
  { value: "score,asc", key: "scoreLow" },
  { value: "studentName", key: "name" },
] as const;

export function resultStatusTone(status: ResultsRow["status"]): PillTone {
  switch (status) {
    case "SUBMITTED":
      return "ok";
    case "FAILED_TO_SUBMIT":
      return "danger";
    case "IN_PROGRESS":
      return "warn";
  }
}

/** A result is passed, not passed, or not yet known (no score yet). */
export function resultTone(passed: boolean | null): PillTone {
  if (passed === null) {
    return "muted";
  }
  return passed ? "ok" : "danger";
}

/** A written result is published or not. An MCQ has no publish step, so its cell says so. */
export function publishedTone(published: boolean): PillTone {
  return published ? "ok" : "warn";
}

type Translate = (key: string, params?: Record<string, string | number>) => string;
type Format = ReturnType<typeof useFormat>;

/** The eight counts on the summary strip. Missing values show a dash, never zero. */
export function resultsSummaryItems(summary: ResultsSummary, t: Translate, format: Format) {
  const dash = t("staffResults.result.none");
  const count = (value: number) => format.formatNumber(value);
  const whole = (value: number | null) => (value === null ? dash : format.formatNumber(value));
  const average = (value: number | null) => (value === null ? dash : format.formatNumber(value, { decimals: 1 }));
  return [
    { label: t("staffResults.summary.participants"), value: count(summary.participants) },
    { label: t("staffResults.summary.submitted"), value: count(summary.submitted), tone: "ok" as const },
    {
      label: t("staffResults.summary.failed"),
      value: count(summary.failedToSubmit),
      tone: summary.failedToSubmit > 0 ? ("danger" as const) : ("muted" as const),
    },
    { label: t("staffResults.summary.average"), value: average(summary.averageScore) },
    { label: t("staffResults.summary.highest"), value: whole(summary.highestScore) },
    { label: t("staffResults.summary.lowest"), value: whole(summary.lowestScore) },
    { label: t("staffResults.summary.passed"), value: count(summary.passCount), tone: "ok" as const },
    {
      label: t("staffResults.summary.passRate"),
      value:
        summary.passRate === null
          ? dash
          : t("staffResults.percent", { value: format.formatNumber(summary.passRate, { decimals: 1 }) }),
    },
  ];
}
