import { INSTALLMENT_HREF, isInstallmentKind } from "./installment-notice";

/** The notice kinds the API sends (plan section 6.2, and the money kinds of section 7). Labels live under `notices.kind.<KIND>`. */
export const NOTICE_KINDS = [
  "EXAM_REMINDER",
  "EXAM_STARTING_SOON",
  "EXAM_OPEN",
  "PURCHASE_GRANTED",
  "EXAM_PUBLISHED",
  "EXAM_TIME_CHANGED",
  "ACCESS_GRANTED",
  "RETAKE_AVAILABLE",
  "ANNOUNCEMENT",
  "MCQ_SCORE",
  "WRITTEN_RESULT",
  "INSTALLMENT_PAYMENT_RECEIVED",
  "INSTALLMENT_DUE_SOON",
  "INSTALLMENT_OVERDUE",
  "INSTALLMENT_PAYMENT_VOIDED",
  "INSTALLMENT_SCHEDULE_CHANGED",
  "ADMISSION_APPROVED",
  "ADMISSION_CHANGES_REQUESTED",
  "ADMISSION_UPDATED",
  "ADMISSION_CLOSED",
  "ADMISSION_JOIN_CODE",
  "ADMISSION_BATCH_ADDED",
  "BATCH_ROUTINE_UPDATED",
  "OFFLINE_FEE_RECEIVED",
  "OFFLINE_FEE_VOIDED",
  "OFFLINE_FEE_DUE_SOON",
  "OFFLINE_FEE_DUE_TODAY",
  "OFFLINE_FEE_OVERDUE",
  "OFFLINE_RESULT_PUBLISHED",
  "OFFLINE_RESULT_UPDATED",
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
export type NoticeTarget = { kind: string; exam: { id: string } | null; code?: string | null };

/** The offline admission and fee kinds: their row opens the student's offline activities page (plan section 7). */
const OFFLINE_KINDS: readonly string[] = [
  "ADMISSION_APPROVED",
  "ADMISSION_CHANGES_REQUESTED",
  "ADMISSION_UPDATED",
  "ADMISSION_CLOSED",
  "ADMISSION_BATCH_ADDED",
  "BATCH_ROUTINE_UPDATED",
  "OFFLINE_FEE_RECEIVED",
  "OFFLINE_FEE_VOIDED",
  "OFFLINE_FEE_DUE_SOON",
  "OFFLINE_FEE_DUE_TODAY",
  "OFFLINE_FEE_OVERDUE",
];

/** The two kinds that report a finished sitting. Their row opens the result, not the offer. */
const RESULT_KINDS: readonly string[] = ["MCQ_SCORE", "WRITTEN_RESULT"];

/** The offline exam result kinds: their row opens the results section of the admission page (plan 7.12). */
const OFFLINE_RESULT_KINDS: readonly string[] = ["OFFLINE_RESULT_PUBLISHED", "OFFLINE_RESULT_UPDATED"];

/**
 * Where a notice row opens. A money notice opens the student's payments, before the exam check, so it
 * never opens an exam (plan section 7). A score or a published result opens the exam's result page.
 * Other exam kinds open the exam offer. A purchase opens the purchased exam, or the
 * student's modules when the notice names no exam. An announcement has no link: it is
 * read in place.
 */
export function noticeHref(notice: NoticeTarget): string | null {
  if (notice.kind === "ANNOUNCEMENT") return null;
  if (isInstallmentKind(notice.kind)) return INSTALLMENT_HREF;
  if (OFFLINE_RESULT_KINDS.includes(notice.kind)) return "/offline-activities#results";
  if (notice.exam) {
    return RESULT_KINDS.includes(notice.kind) ? `/exam/${notice.exam.id}/result` : `/exam/${notice.exam.id}`;
  }
  if (notice.kind === "PURCHASE_GRANTED") return "/my-modules";
  // The join form reads the code from the address fragment, so the student only presses Join (V101).
  if (notice.kind === "ADMISSION_JOIN_CODE") return notice.code ? `/admission/join#code=${encodeURIComponent(notice.code)}` : "/admission/join";
  if (OFFLINE_KINDS.includes(notice.kind)) return "/offline-activities";
  return null;
}

/** The fee kinds of an offline admission: their link opens the payments of the admission. */
const OFFLINE_FEE_KINDS: readonly string[] = [
  "OFFLINE_FEE_RECEIVED",
  "OFFLINE_FEE_VOIDED",
  "OFFLINE_FEE_DUE_SOON",
  "OFFLINE_FEE_DUE_TODAY",
  "OFFLINE_FEE_OVERDUE",
];

/**
 * The words on a notice's link, so they say where it goes: a result opens a result, an admission notice opens the offline
 * activities, a fee notice opens the payments. "Open exam" is only for a notice about an exam.
 */
export function noticeActionKey(notice: NoticeTarget): string {
  if (RESULT_KINDS.includes(notice.kind) || OFFLINE_RESULT_KINDS.includes(notice.kind)) return "notices.viewResult";
  if (OFFLINE_FEE_KINDS.includes(notice.kind)) return "notices.viewPayment";
  if (notice.kind === "ADMISSION_JOIN_CODE") return "notices.enterJoinCode";
  if (OFFLINE_KINDS.includes(notice.kind)) return "notices.openOffline";
  if (!notice.exam && notice.kind === "PURCHASE_GRANTED") return "notices.myExams";
  return "notices.openExamAction";
}
