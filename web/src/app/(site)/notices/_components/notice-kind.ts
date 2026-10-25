/** The notice kinds the API sends (plan section 6.2). Labels live under `notices.kind.<KIND>`. */
export const NOTICE_KINDS = [
  "EXAM_REMINDER",
  "EXAM_STARTING_SOON",
  "EXAM_OPEN",
  "PURCHASE_GRANTED",
  "EXAM_PUBLISHED",
  "RETAKE_AVAILABLE",
  "ANNOUNCEMENT",
  "MCQ_SCORE",
  "WRITTEN_RESULT",
] as const;

export type KnownNoticeKind = (typeof NOTICE_KINDS)[number];

export function isKnownNoticeKind(kind: string): kind is KnownNoticeKind {
  return (NOTICE_KINDS as readonly string[]).includes(kind);
}

/** The label key for a kind. A kind this page does not know gets the generic label. */
export function noticeKindLabelKey(kind: string): string {
  return isKnownNoticeKind(kind) ? `notices.kind.${kind}` : "notices.kind.other";
}

/** The part of a notice the link needs. */
export type NoticeTarget = { kind: string; exam: { id: string } | null };

/** The two kinds that report a finished sitting. Their row opens the result, not the offer. */
const RESULT_KINDS: readonly string[] = ["MCQ_SCORE", "WRITTEN_RESULT"];

/**
 * Where a notice row opens. A score or a published result opens the exam's result page.
 * Other exam kinds open the exam offer. A purchase opens the purchased exam, or the
 * student's modules when the notice names no exam. An announcement has no link: it is
 * read in place.
 */
export function noticeHref(notice: NoticeTarget): string | null {
  if (notice.kind === "ANNOUNCEMENT") return null;
  if (notice.exam) {
    return RESULT_KINDS.includes(notice.kind) ? `/exam/${notice.exam.id}/result` : `/exam/${notice.exam.id}`;
  }
  if (notice.kind === "PURCHASE_GRANTED") return "/my-modules";
  return null;
}
