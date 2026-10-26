import type { PillTone } from "@/components/ui/pill";
import type { AccountStatus } from "@/lib/api/exam/types";
import type { SubmissionRow } from "@/lib/api/exam/staff-people";

export function accountStatusTone(status: AccountStatus): PillTone {
  switch (status) {
    case "ACTIVE":
      return "ok";
    case "PENDING_ACTIVATION":
      return "warn";
    case "INACTIVE":
      return "muted";
  }
}

export function accountStatusKey(status: AccountStatus): string {
  return `staffPeople.status.${status}`;
}

export type SubmissionFilter = "all" | "answer" | "failed" | "draft" | "published";

export const SUBMISSION_FILTERS: SubmissionFilter[] = ["all", "answer", "failed", "draft", "published"];

/** A draft is a marked script that is not published yet. */
export function isDraft(row: Pick<SubmissionRow, "evaluatedPdf" | "published">): boolean {
  return row.evaluatedPdf === true && !row.published;
}

/**
 * Filters the loaded rows of one module. The server sends a module's written rows
 * together, so these filters run on the client.
 */
export function matchesSubmissionFilter(row: SubmissionRow, filter: SubmissionFilter): boolean {
  switch (filter) {
    case "all":
      return true;
    case "answer":
      return row.answerPdf;
    case "failed":
      return row.status === "FAILED_TO_SUBMIT";
    case "draft":
      return isDraft(row);
    case "published":
      return row.published;
  }
}

/** Case-insensitive match on the student's name, against the names already loaded. */
export function matchesName(row: SubmissionRow, query: string): boolean {
  const needle = query.trim().toLowerCase();
  return needle === "" || row.studentName.toLowerCase().includes(needle);
}

/**
 * The next paper to mark after `currentId`, in list order: the next row that has an
 * answer and no draft yet. Wraps to the start, so "সেভ করে পরেরটি" never runs out.
 */
export function nextToMark(rows: SubmissionRow[], currentId: string): SubmissionRow | null {
  const markable = rows.filter((row) => row.answerPdf && !isDraft(row) && !row.published);
  const start = rows.findIndex((row) => row.id === currentId);
  for (let step = 1; step <= rows.length; step++) {
    const candidate = rows[(start + step) % rows.length];
    if (candidate && candidate.id !== currentId && markable.includes(candidate)) {
      return candidate;
    }
  }
  return null;
}

/** The step of the marking queue: the row before or after the current one, in list order. */
export function neighbour(rows: SubmissionRow[], currentId: string, direction: -1 | 1): SubmissionRow | null {
  const index = rows.findIndex((row) => row.id === currentId);
  if (index < 0) {
    return null;
  }
  return rows[index + direction] ?? null;
}
