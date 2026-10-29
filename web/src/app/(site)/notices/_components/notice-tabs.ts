import type { Notice, NoticeGroup, UnreadCounts } from "@/lib/api/exam/notices";

/** The tabs, in the order they show (N1, Phase 4 A8). */
export const NOTICE_TABS: readonly NoticeGroup[] = ["RESULTS", "EXAMS", "ANNOUNCEMENTS"];

/** The `tab` query value, or null when it is absent or not one of the three tabs. */
export function parseNoticeTab(value: string | null | undefined): NoticeGroup | null {
  return NOTICE_TABS.find((tab) => tab === value) ?? null;
}

/**
 * The tab a visit opens on when the URL names none: Results if it has unread notices, else Exams if it has unread, else
 * Announcements if it has unread, else Exams.
 */
export function defaultNoticeTab(byGroup: UnreadCounts["byGroup"] | undefined): NoticeGroup {
  const unread = (tab: NoticeGroup) => (byGroup?.[tab] ?? 0) > 0;
  if (unread("RESULTS")) return "RESULTS";
  if (unread("EXAMS")) return "EXAMS";
  if (unread("ANNOUNCEMENTS")) return "ANNOUNCEMENTS";
  return "EXAMS";
}

/** The coloured edge of a card: results in the success colour, exams in the accent, announcements muted. */
export function tabEdgeClass(tab: NoticeGroup): string {
  if (tab === "RESULTS") return "border-l-ok";
  if (tab === "EXAMS") return "border-l-accent";
  return "border-l-muted";
}

/** The payment history for a notice's exam, or for every payment when the notice names no exam (Phase 4, S4.6). */
export function paymentHref(notice: Pick<Notice, "exam">): string {
  return notice.exam ? `/account/payments?examId=${notice.exam.id}` : "/account/payments";
}
