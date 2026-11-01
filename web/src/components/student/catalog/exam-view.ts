import type { ExamView } from "@/lib/api/exam/catalog";
import type { PriceView } from "@/lib/api/exam/types";

/** The tabs in display order. */
export const EXAM_VIEWS: readonly ExamView[] = ["ALL", "FREE", "LIVE", "ARCHIVED"];

/**
 * Reads `?view=`. The API reads it without regard to case, so this does too. Absent or unknown is All, so a stale or
 * typed-in link shows every exam.
 */
export function parseExamView(value: string | null | undefined): ExamView {
  const upper = value?.trim().toUpperCase();
  return EXAM_VIEWS.find((view) => view === upper) ?? "ALL";
}

/** Writes the tab into a copy of a query. All is no parameter, so the default URL stays as it was. */
export function withExamView(params: URLSearchParams, view: ExamView): URLSearchParams {
  const next = new URLSearchParams(params);
  if (view === "ALL") {
    next.delete("view");
  } else {
    next.set("view", view);
  }
  return next;
}

/** What the tab rules read from an exam. `mode` is LIVE or PRACTICE; a missing mode is not LIVE. */
export interface ViewableExam {
  mode?: string | null;
  liveAt: string | null;
  endAt: string | null;
  pricing?: PriceView | null;
}

/**
 * Whether an exam shows under a tab, with the same rules as the API's `view` (REQ-007). `nowMs` is the time in
 * milliseconds, corrected to the server clock.
 *
 * - ALL: every exam.
 * - FREE: an exam with no price, in any mode, practice included.
 * - LIVE: a live-mode exam whose window has not ended (UNANNOUNCED, UPCOMING or LIVE).
 * - ARCHIVED: a live-mode exam whose window has ended. Practice never reaches LIVE or ARCHIVED.
 *
 * A window has ended once its end has passed, and only when it has a start too (no start means not announced).
 */
export function examInView(exam: ViewableExam, view: ExamView, nowMs: number): boolean {
  if (view === "ALL") return true;
  if (view === "FREE") return exam.pricing?.free === true;
  if (exam.mode !== "LIVE") return false;
  const ended = exam.liveAt !== null && exam.endAt !== null && Date.parse(exam.endAt) <= nowMs;
  return view === "ARCHIVED" ? ended : !ended;
}
