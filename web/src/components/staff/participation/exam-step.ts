import { isMissing } from "@/lib/api/missing";
import type { ParticipationRow } from "@/lib/api/exam/staff-people";

/** What the page asks the viewer to look at. "" is every exam. */
export const FOCUSES = ["", "release", "mark", "changes", "drafts"] as const;
export type Focus = (typeof FOCUSES)[number];

export function isFocus(value: string): value is Focus {
  return (FOCUSES as readonly string[]).includes(value);
}

/** The counts of one exam's papers. A count the API leaves out is zero. */
export type PaperCounts = {
  /** Handed in. */
  submitted: number;
  notSubmitted: number;
  toMark: number;
  /** Marked and not yet released to the student, including changes to a released result. */
  toRelease: number;
  /** Of `toRelease`, the corrections to results the student already has. */
  changes: number;
  released: number;
  /** Marked, released or not. */
  marked: number;
};

export function paperCounts(row: ParticipationRow): PaperCounts {
  const toMark = row.awaitingMarking ?? 0;
  const toRelease = row.draftsAwaitingPublish ?? 0;
  const released = row.published ?? 0;
  return {
    submitted: row.submitted ?? 0,
    notSubmitted: row.failedToSubmit ?? 0,
    toMark,
    toRelease,
    changes: row.correctionsWaiting ?? 0,
    released,
    marked: toRelease + released,
  };
}

/**
 * The one thing that is next for an exam, in the order a marker meets it: not started, still
 * open, papers to mark, papers to release, all released, nothing handed in. MCQ exams score
 * themselves, so staff have nothing to do.
 */
export type StepKind = "auto" | "upcoming" | "open" | "mark" | "release" | "done" | "empty";

export function stepOf(row: ParticipationRow, now: number): StepKind {
  if (row.examType === "MCQ") {
    return "auto";
  }
  const counts = paperCounts(row);
  if (!isMissing(row.liveAt) && now < Date.parse(row.liveAt)) {
    return "upcoming";
  }
  const windowOpen = !isMissing(row.endAt) && now < Date.parse(row.endAt);
  if (windowOpen || (row.sitting ?? 0) > 0) {
    return "open";
  }
  if (counts.toMark > 0) {
    return "mark";
  }
  if (counts.toRelease > 0) {
    return "release";
  }
  return counts.submitted > 0 || counts.released > 0 ? "done" : "empty";
}

/** Whether the exam has started, is running, or is over, for the chip beside its dates. */
export type Phase = "upcoming" | "live" | "closed" | "unset";

export function phaseOf(row: ParticipationRow, now: number): Phase {
  if (isMissing(row.liveAt) || isMissing(row.endAt)) {
    return "unset";
  }
  if (now < Date.parse(row.liveAt)) {
    return "upcoming";
  }
  return now < Date.parse(row.endAt) ? "live" : "closed";
}

/** Whether a row belongs under a tab. Only written exams ever do. The drafts tab lists other things, so no row fits it. */
export function matchesFocus(row: ParticipationRow, focus: Focus): boolean {
  if (focus === "") {
    return true;
  }
  if (focus === "drafts") {
    return false;
  }
  if (row.examType !== "WRITTEN") {
    return false;
  }
  const counts = paperCounts(row);
  if (focus === "release") return counts.toRelease > 0;
  if (focus === "mark") return counts.toMark > 0;
  return counts.changes > 0;
}

/** The totals the tabs show: papers, and for release the number of exams they sit in. */
export function focusTotals(rows: ParticipationRow[]) {
  const written = rows.filter((row) => row.examType === "WRITTEN");
  const sum = (pick: (counts: PaperCounts) => number) => written.reduce((total, row) => total + pick(paperCounts(row)), 0);
  return {
    release: sum((counts) => counts.toRelease),
    releaseExams: written.filter((row) => paperCounts(row).toRelease > 0).length,
    mark: sum((counts) => counts.toMark),
    changes: sum((counts) => counts.changes),
  };
}
