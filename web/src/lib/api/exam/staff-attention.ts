"use client";

/**
 * What needs the signed-in staff member's attention, as one number per sidebar item. Each number is asked for only by the roles
 * that act on it, so a role never calls a route it may not read. The queries sit under `staffHome`, so a marking change, a
 * publish or an order decision refreshes them, and they refresh on their own every half minute.
 */

import { useQuery } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
import { staffHomeKeys, type ParticipationSummary } from "@/lib/api/exam/staff-home";
import type { StaffNavKey } from "@/components/shell/staff-nav";
import type { PageResponse, Role } from "@/lib/api/exam/types";

export type AttentionCounts = Partial<Record<StaffNavKey, number>>;

const REFRESH_MS = 30_000;

/** Retake requests waiting for the Master Admin or the System Admin to decide. The list holds pending requests only. */
export const pendingRetakesKey = ["staffHome", "pendingRetakeRequests"] as const;

export function useStaffAttention(role: Role | null | undefined): AttentionCounts {
  const marker = role === "MASTER_ADMIN" || role === "INSTRUCTOR";
  const decider = role === "MASTER_ADMIN" || role === "SYSTEM_ADMIN";
  const master = role === "MASTER_ADMIN";

  // Papers to mark, and results ready to publish. The API limits the counts to the papers this role can see.
  const summary = useQuery({
    queryKey: staffHomeKeys.summary,
    queryFn: () => apiRequest<ParticipationSummary>("/api/v1/live-exam-participation/summary"),
    enabled: marker,
    refetchInterval: REFRESH_MS,
    refetchOnWindowFocus: true,
  });
  const retakes = useQuery({
    queryKey: pendingRetakesKey,
    queryFn: async () => (await apiRequest<unknown[]>("/api/v1/retake-requests?status=PENDING")).length,
    enabled: decider,
    refetchInterval: REFRESH_MS,
    refetchOnWindowFocus: true,
  });
  // Orders still open at the gateway, which the Master Admin confirms or rejects.
  const orders = useQuery({
    queryKey: staffHomeKeys.openOrders,
    queryFn: async () => (await apiRequest<PageResponse<unknown>>("/api/v1/live-exam-orders?status=INITIATED&size=1")).totalElements,
    enabled: master,
    refetchInterval: REFRESH_MS,
    refetchOnWindowFocus: true,
  });

  return {
    submissions: marker ? (summary.data?.awaitingMarking ?? 0) : 0,
    participation: master ? (summary.data?.draftsAwaitingPublish ?? 0) : 0,
    retakeRequests: decider ? (retakes.data ?? 0) : 0,
    orders: master ? (orders.data ?? 0) : 0,
    payments: master ? (orders.data ?? 0) : 0,
  };
}
