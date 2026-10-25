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

export interface Notice {
  id: string;
  kind: NoticeKind;
  createdAt: string;
  /** Null while unread. */
  readAt: string | null;
  exam: NoticeExam | null;
  /** Set on an ANNOUNCEMENT notice: the title and body in both languages (plan 6.4). */
  announcement: {
    titleEn: string | null;
    titleBn: string | null;
    bodyEn: string | null;
    bodyBn: string | null;
  } | null;
  /** MCQ score or written mark, with the maximum. */
  score: number | null;
  maximum: number | null;
  passed: boolean | null;
}

export const noticeKeys = {
  all: ["notices"] as const,
  list: (page: number) => ["notices", "list", page] as const,
  unread: () => ["notices", "unread"] as const,
};

export function useNotices(page: number) {
  return useQuery({
    queryKey: noticeKeys.list(page),
    queryFn: () => apiRequest<PageResponse<Notice>>(`/api/v1/me/notices?page=${page}&size=20`),
    refetchOnWindowFocus: true,
  });
}

/** Unread count for the bell. Refreshes on focus and every two minutes. */
export function useUnreadCount() {
  return useQuery({
    queryKey: noticeKeys.unread(),
    queryFn: () => apiRequest<{ count: number }>("/api/v1/me/notices/unread-count"),
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

export function useMarkAllNoticesRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiRequest<{ updated: number }>("/api/v1/me/notices/read-all", { method: "POST", body: {} }),
    onSuccess: () => invalidateNotices(queryClient),
  });
}
