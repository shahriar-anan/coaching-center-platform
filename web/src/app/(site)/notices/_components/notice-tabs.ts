import type { Notice, NoticeGroup } from "@/lib/api/exam/notices";

/** A tab: All (every notice, newest first) or one group. */
export type NoticeTab = NoticeGroup | "ALL";

/** The tabs, in the order they show. All comes first (N1, Phase 4 A8). */
export const NOTICE_TABS: readonly NoticeTab[] = ["ALL", "RESULTS", "EXAMS", "ANNOUNCEMENTS"];

/** The `tab` query value, or null when it is absent or not one of the tabs. */
export function parseNoticeTab(value: string | null | undefined): NoticeTab | null {
  return NOTICE_TABS.find((tab) => tab === value) ?? null;
}

/** The group the API is asked for: null on All, which asks for every notice. */
export function groupFilter(tab: NoticeTab): NoticeGroup | null {
  return tab === "ALL" ? null : tab;
}

/** The group a row belongs to: its own on the All tab, the tab's otherwise. */
export function rowGroup(notice: Pick<Notice, "group">, tab: NoticeTab): NoticeGroup {
  return tab === "ALL" ? (notice.group ?? "EXAMS") : tab;
}

/** The tab a visit opens on when the URL names none: always All, whatever is unread. */
export function defaultNoticeTab(): NoticeTab {
  return "ALL";
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
