"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
import type { ExamType, OrderKind, OrderStatus, PageResponse } from "@/lib/api/exam/types";

/*
 * Staff operations: payments (read only), the audit log, the staff leaderboard,
 * and the staff account. Owned by WS6b. Query keys start with "staffOps".
 */

/** One row of `GET /api/v1/live-exam-orders`. The API sends the status words as stored. */
export type StaffOrderRow = {
  orderId: string;
  tranId: string;
  status: OrderStatus;
  amount: number;
  currency: string;
  scope: OrderKind;
  moduleId: string;
  moduleTitleEn: string;
  moduleTitleBn: string;
  examId: string | null;
  examTitleEn: string | null;
  examTitleBn: string | null;
  /** From the SSLCommerz validation, for example `BKASH-BKash`. Null before payment. */
  paymentChannel: string | null;
  valId: string | null;
  accessGranted: boolean;
  studentId: string;
  studentName: string | null;
  studentPhone: string;
  studentEmail: string;
  studentCode: string | null;
  createdAt: string;
  updatedAt: string;
};

export type StaffPaymentFilters = {
  page: number;
  size: number;
  q: string;
  tranId: string;
  status: string;
};

/** Builds a query string, leaving out empty values, so the URL stays short. */
export function queryString(params: Record<string, string | number | null | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === null || value === undefined || value === "") {
      continue;
    }
    search.set(key, String(value));
  }
  const text = search.toString();
  return text ? `?${text}` : "";
}

export function useStaffPayments(filters: StaffPaymentFilters) {
  return useQuery({
    queryKey: ["staffOps", "payments", filters],
    queryFn: ({ signal }) =>
      apiRequest<PageResponse<StaffOrderRow>>(
        `/api/v1/live-exam-orders${queryString({
          page: filters.page,
          size: filters.size,
          q: filters.q,
          tranId: filters.tranId,
          status: filters.status,
        })}`,
        { signal },
      ),
    staleTime: 0,
  });
}

/**
 * One payment, found by its transaction id. The API has no single-order read for
 * staff, so this reads the list filtered by `tranId`.
 */
export function useStaffPaymentDetail(tranId: string) {
  return useQuery({
    queryKey: ["staffOps", "payment", tranId],
    queryFn: async ({ signal }) => {
      const page = await apiRequest<PageResponse<StaffOrderRow>>(
        `/api/v1/live-exam-orders${queryString({ tranId, size: 1 })}`,
        { signal },
      );
      return page.content.find((row) => row.tranId === tranId) ?? null;
    },
    enabled: tranId.length > 0,
    staleTime: 0,
  });
}

/** One row of `GET /api/v1/audit-log`. */
export type AuditRow = {
  id: string;
  actorUserId: string | null;
  action: string;
  entityType: string;
  entityId: string | null;
  ip: string | null;
  createdAt: string;
};

export function useAuditLog(page: number) {
  return useQuery({
    queryKey: ["staffOps", "audit", page],
    queryFn: ({ signal }) =>
      apiRequest<PageResponse<AuditRow>>(`/api/v1/audit-log${queryString({ page, size: 20 })}`, { signal }),
    staleTime: 0,
  });
}

/** One row of `GET /api/v1/live-exams/{id}/leaderboard`. Ranks are shared on a tie. */
export type LeaderboardRow = {
  name: string;
  rank: number;
  score: number;
  maximum: number;
  passed: boolean;
};

export type LeaderboardResponse = { rows: LeaderboardRow[] };

/** The staff read of an exam, for the leaderboard header and its empty state. */
export type StaffExamView = {
  id: string;
  moduleId: string;
  examType: ExamType;
  titleEn: string;
  titleBn: string;
  /** ISO UTC, or null when the date is not announced. */
  liveAt: string | null;
  endAt: string | null;
  status: string;
};

export function useStaffExam(examId: string) {
  return useQuery({
    queryKey: ["staffOps", "exam", examId],
    queryFn: ({ signal }) => apiRequest<StaffExamView>(`/api/v1/live-exams/${examId}`, { signal }),
    staleTime: 60_000,
  });
}

export function useLeaderboard(examId: string) {
  return useQuery({
    queryKey: ["staffOps", "leaderboard", examId],
    queryFn: ({ signal }) =>
      apiRequest<LeaderboardResponse>(`/api/v1/live-exams/${examId}/leaderboard`, { signal }),
    staleTime: 0,
  });
}

/** Account calls. A changed phone returns `MeView`; the session is refreshed by the caller. */
export function useChangePassword() {
  return useMutation({
    mutationFn: (body: { currentPassword: string; newPassword: string }) =>
      apiRequest<void>("/api/v1/auth/change-password", { method: "POST", body }),
  });
}

export function useChangePhone() {
  return useMutation({
    mutationFn: (body: { currentPassword: string; phone: string }) =>
      apiRequest<unknown>("/api/v1/auth/change-phone", { method: "POST", body }),
  });
}

export function useVerifyEmail() {
  return useMutation({
    mutationFn: (body: { code: string }) =>
      apiRequest<void>("/api/v1/auth/verify-email", { method: "POST", body }),
  });
}

export function useResendEmailVerification() {
  return useMutation({
    mutationFn: (body: { email: string }) =>
      apiRequest<{ message: string }>("/api/v1/auth/verify-email/resend", { method: "POST", body }),
  });
}
