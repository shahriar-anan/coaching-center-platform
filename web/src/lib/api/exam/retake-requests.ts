"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";

// A student's request for another written retake, and the centre's decision on it (retake requests plan). Paths follow the API.

export type RetakeRequestStatus = "PENDING" | "APPROVED" | "DECLINED";

export type RetakeRequest = {
  id: string;
  examId: string;
  studentId: string;
  status: RetakeRequestStatus;
  reason: string | null;
  decisionNote: string | null;
  requestedAt: string;
  decidedAt: string | null;
};

/** The student's latest request, and whether a new one can be made. `blockedReason` says why not, as a code. */
export type MyRetakeRequest = {
  request: RetakeRequest | null;
  canRequest: boolean;
  blockedReason: string | null;
};

/** One row for the staff list: the exam and the student by name. */
export type StaffRetakeRequest = {
  id: string;
  examId: string;
  examTitleEn: string;
  examTitleBn: string;
  studentId: string;
  studentName: string;
  status: RetakeRequestStatus;
  reason: string | null;
  decisionNote: string | null;
  requestedAt: string;
  decidedAt: string | null;
};

export const retakeRequestKeys = {
  mine: (examId: string) => ["retakeRequest", "mine", examId] as const,
  staff: (status: RetakeRequestStatus) => ["retakeRequest", "staff", status] as const,
};

/** The student's own request state on this exam. Only asked when the student has a written exam to look at. */
export function useMyRetakeRequest(examId: string, enabled: boolean) {
  return useQuery({
    queryKey: retakeRequestKeys.mine(examId),
    queryFn: () => apiRequest<MyRetakeRequest>(`/api/v1/live-exams/${examId}/retake-request`),
    enabled: enabled && Boolean(examId),
    staleTime: 0,
    refetchOnWindowFocus: true,
  });
}

/** Sends a retake request. The reason is optional. */
export function useRequestRetake(examId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (reason: string) =>
      apiRequest<RetakeRequest>(`/api/v1/live-exams/${examId}/retake-request`, {
        method: "POST",
        body: { reason: reason.trim() === "" ? null : reason.trim() },
      }),
    onSettled: () => client.invalidateQueries({ queryKey: retakeRequestKeys.mine(examId) }),
  });
}

/** The requests with a status, for the centre's decision list. */
export function useStaffRetakeRequests(status: RetakeRequestStatus) {
  return useQuery({
    queryKey: retakeRequestKeys.staff(status),
    queryFn: () => apiRequest<StaffRetakeRequest[]>(`/api/v1/retake-requests?status=${status}`),
    staleTime: 0,
  });
}

function invalidateStaff(client: ReturnType<typeof useQueryClient>) {
  return Promise.all(
    (["PENDING", "APPROVED", "DECLINED"] as const).map((status) =>
      client.invalidateQueries({ queryKey: retakeRequestKeys.staff(status) }),
    ),
  );
}

export function useApproveRetakeRequest() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      apiRequest<RetakeRequest>(`/api/v1/retake-requests/${id}/approve`, { method: "POST", body: {} }),
    onSettled: () => invalidateStaff(client),
  });
}

export function useDeclineRetakeRequest() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ id, note }: { id: string; note: string }) =>
      apiRequest<RetakeRequest>(`/api/v1/retake-requests/${id}/decline`, {
        method: "POST",
        body: { note: note.trim() === "" ? null : note.trim() },
      }),
    onSettled: () => invalidateStaff(client),
  });
}
