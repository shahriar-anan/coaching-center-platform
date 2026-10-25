"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";

export type OrderStatus = "PENDING" | "PAID" | "FAILED" | "CANCELLED";

export interface OrderView {
  orderId: string;
  tranId: string;
  status: OrderStatus;
  amount: number;
  redirectUrl: string | null;
  scope: "MODULE" | "EXAM";
  moduleId: string | null;
  examId: string | null;
}

export interface PaymentStatus {
  status: OrderStatus;
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

/** Polls every 3 s while pending, for one minute from `startedAt`. */
export function usePaymentStatus(tranId: string, enabled: boolean, startedAt: number) {
  return useQuery<PaymentStatus>({
    queryKey: purchaseKeys.status(tranId),
    enabled,
    staleTime: 0,
    retry: false,
    refetchInterval: (query) =>
      query.state.data?.status === "PENDING" && Date.now() - startedAt < POLL_WINDOW_MS
        ? POLL_MS
        : false,
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
