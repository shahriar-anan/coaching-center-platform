"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
import type { PageResponse } from "@/lib/api/exam/types";

/** The student's notices: reminders, MCQ scores and written results. Keys live in this file. */

/** The kinds from plan section 6.2. A kind not in this list still renders, with the generic label. */
export type NoticeKind =
  | "EXAM_REMINDER"
  | "EXAM_STARTING_SOON"
  | "EXAM_OPEN"
  | "PURCHASE_GRANTED"
  | "EXAM_PUBLISHED"
  | "EXAM_TIME_CHANGED"
  | "ACCESS_GRANTED"
  | "RETAKE_AVAILABLE"
  | "ANNOUNCEMENT"
  | "MCQ_SCORE"
  | "WRITTEN_RESULT"
  | (string & {});

export interface NoticeExam {
  id: string;
  moduleId: string;
  examType: "MCQ" | "WRITTEN";
  titleEn: string;
  titleBn: string;
  liveAt: string | null;
  endAt: string | null;
}

/** The announcement an ANNOUNCEMENT notice carries (plan 6.4). Either language may be blank. */
export interface NoticeAnnouncement {
  id: string;
  titleEn: string | null;
  titleBn: string | null;
  bodyEn: string | null;
  bodyBn: string | null;
  /** ALL, MODULE (one package), EXAM (one exam) or SELECTED (chosen students). */
  audience: "ALL" | "MODULE" | "EXAM" | "SELECTED" | (string & {});
  /** Set only for a MODULE audience. */
  moduleId: string | null;
  /** Set only for an EXAM audience. */
  examId: string | null;
}

/**
 * The money a direct-enrolment notice is about (plan section 7). The API leaves out any field it has no value for, and the whole
 * block is left out for a notice whose plan is gone, so every field is optional. Titles come from the plan, not the exam.
 */
export interface NoticeInstallment {
  titleEn?: string | null;
  titleBn?: string | null;
  /** The payment's amount on a payment notice, or what is still owed on the installment for a reminder. */
  amount?: number | null;
  /** A Dhaka calendar date, YYYY-MM-DD: the next date for a payment, the installment's date for a reminder. */
  dueOn?: string | null;
  paid?: number | null;
  due?: number | null;
  /** Set on a payment notice only. */
  receiptNo?: string | null;
}

export interface Notice {
  id: string;
  kind: NoticeKind;
  createdAt: string;
  /** Null while unread. */
  readAt: string | null;
  /** Null for an announcement, which names no exam. */
  exam: NoticeExam | null;
  /** Set on an ANNOUNCEMENT notice, and null on every other kind. */
  announcement: NoticeAnnouncement | null;
  /** Set on a direct-enrolment money notice. Left out on every other kind. */
  installment?: NoticeInstallment | null;
  /** Set on a written-result notice sent after an earlier one for the same paper (a correction was released). */
  updated?: boolean | null;
  /** MCQ score or written mark, with the maximum. */
  score: number | null;
  maximum: number | null;
  passed: boolean | null;
  /** The tab the API files the notice under (N1). */
  group?: NoticeGroup;
}

/** The three tabs of the notices page (N1). */
export type NoticeGroup = "RESULTS" | "EXAMS" | "ANNOUNCEMENTS";

/** Unread notices in all (`count`) and in each tab (`byGroup`). */
export interface UnreadCounts {
  count: number;
  byGroup: Partial<Record<NoticeGroup, number>>;
}

export const noticeKeys = {
  all: ["notices"] as const,
  list: (page: number, group: NoticeGroup | null) => ["notices", "list", page, group ?? "ALL"] as const,
  unread: () => ["notices", "unread"] as const,
};

/** One page of a tab, or of every notice when no tab is given. */
export function useNotices(page: number, group: NoticeGroup | null = null) {
  const query = group ? `&group=${group}` : "";
  return useQuery({
    queryKey: noticeKeys.list(page, group),
    queryFn: () => apiRequest<PageResponse<Notice>>(`/api/v1/me/notices?page=${page}&size=20${query}`),
    refetchOnWindowFocus: true,
  });
}

/** Unread counts for the bell and the tabs. Refreshes on focus and every two minutes. */
export function useUnreadCount() {
  return useQuery({
    queryKey: noticeKeys.unread(),
    queryFn: () => apiRequest<UnreadCounts>("/api/v1/me/notices/unread-count"),
    refetchOnWindowFocus: true,
    refetchInterval: 2 * 60 * 1000,
    staleTime: 30_000,
  });
}

function invalidateNotices(queryClient: ReturnType<typeof useQueryClient>) {
  return queryClient.invalidateQueries({ queryKey: noticeKeys.all });
}

export function useMarkNoticeRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      apiRequest<Notice>(`/api/v1/me/notices/${id}/read`, { method: "POST", body: {} }),
    onSuccess: () => invalidateNotices(queryClient),
  });
}

/** Marks the unread notices of one tab read, or every one when no tab is given (N1). */
export function useMarkAllNoticesRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (group: NoticeGroup | null) =>
      apiRequest<{ updated: number }>(`/api/v1/me/notices/read-all${group ? `?group=${group}` : ""}`, {
        method: "POST",
        body: {},
      }),
    onSuccess: () => invalidateNotices(queryClient),
  });
}
