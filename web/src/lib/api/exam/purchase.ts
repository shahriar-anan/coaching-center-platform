"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";

export type PaymentState = "PENDING" | "PAID" | "FAILED" | "CANCELLED";

export interface OrderView {
  orderId: string;
  tranId: string;
  status: PaymentState;
  amount: number;
  redirectUrl: string | null;
  scope: "MODULE" | "EXAM" | "BUNDLE";
  moduleId: string | null;
  examId: string | null;
}

export interface PaymentStatus {
  status: PaymentState;
  examName: string;
}

export type PurchaseTarget = { examId: string } | { moduleId: string };

export const purchaseKeys = {
  all: ["purchase"] as const,
  status: (tranId: string) => ["purchase", "status", tranId] as const,
  order: (orderId: string) => ["purchase", "order", orderId] as const,
};

export function useStartOrder() {
  return useMutation({
    retry: false,
    mutationFn: (target: PurchaseTarget) =>
      apiRequest<OrderView>("/api/v1/live-exam-orders", { method: "POST", body: target }),
  });
}

/** One payment for several exams (plan 8): `{ examIds }`. An owned exam refuses the whole request. */
export function useBundleOrder() {
  return useMutation({
    retry: false,
    mutationFn: (examIds: string[]) =>
      apiRequest<OrderView>("/api/v1/live-exam-orders", { method: "POST", body: { examIds } }),
  });
}

export function useCancelOrder() {
  return useMutation({
    retry: false,
    mutationFn: (orderId: string) =>
      apiRequest<OrderView>(`/api/v1/live-exam-orders/${orderId}/cancel`, {
        method: "POST",
        body: {},
      }),
  });
}

const POLL_MS = 3000;
const POLL_WINDOW_MS = 60_000;

/**
 * Milliseconds until the next status call, or false to stop. Polls every 3 s
 * while the payment is pending, and only for one minute from `startedAt`.
 */
export function paymentPollDelay(
  status: string | undefined,
  startedAt: number,
  now: number,
): number | false {
  return status === "PENDING" && now - startedAt < POLL_WINDOW_MS ? POLL_MS : false;
}

/** Polls every 3 s while pending, for one minute from `startedAt`. */
export function usePaymentStatus(tranId: string, enabled: boolean, startedAt: number) {
  return useQuery<PaymentStatus>({
    queryKey: purchaseKeys.status(tranId),
    enabled,
    staleTime: 0,
    retry: false,
    refetchInterval: (query) => paymentPollDelay(query.state.data?.status, startedAt, Date.now()),
    queryFn: ({ signal }) =>
      apiRequest<PaymentStatus>(
        `/api/v1/live-exam-orders/status?tranId=${encodeURIComponent(tranId)}`,
        { signal },
      ),
  });
}

export function useOrder(orderId: string | null, enabled: boolean) {
  return useQuery({
    queryKey: purchaseKeys.order(orderId ?? ""),
    enabled: enabled && Boolean(orderId),
    staleTime: 0,
    retry: false,
    queryFn: ({ signal }) => apiRequest<OrderView>(`/api/v1/live-exam-orders/${orderId}`, { signal }),
  });
}

/** After a paid order, every catalog read (access, myStatus, routine) is stale. */
export function useInvalidateCatalog() {
  const client = useQueryClient();
  return () => client.invalidateQueries({ queryKey: ["catalog"] });
}
