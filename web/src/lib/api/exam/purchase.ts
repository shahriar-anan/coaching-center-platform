"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
import type { MyOrder } from "@/lib/api/exam/account";

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

/**
 * What an order is for: one exam, one package, or the exams of a bundle (plan 8). `receiptEmail` is the address typed for the
 * receipt (Phase 4B). It is sent only when filled in, so a skipped field sends nothing.
 */
export type PurchaseTarget = ({ examId: string } | { moduleId: string } | { examIds: string[] }) & { receiptEmail?: string };

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
    mutationFn: ({ examIds, receiptEmail }: { examIds: string[]; receiptEmail?: string }) =>
      apiRequest<OrderView>("/api/v1/live-exam-orders", {
        method: "POST",
        body: receiptEmail ? { examIds, receiptEmail } : { examIds },
      }),
  });
}

export function useCancelOrder() {
  const client = useQueryClient();
  return useMutation({
    retry: false,
    mutationFn: (orderId: string) =>
      apiRequest<OrderView>(`/api/v1/live-exam-orders/${orderId}/cancel`, {
        method: "POST",
        body: {},
      }),
    // The payment history and the open-order lines on the detail pages are asked again.
    onSettled: () => client.invalidateQueries({ queryKey: ["account", "orders"] }),
  });
}

/** The order fields a payment needs to be paid again (the list row, P1). */
export type ResumableOrder = Pick<MyOrder, "orderId" | "scope" | "moduleId" | "examId"> & { receiptEmail?: string | null };

/**
 * The target to post again to pay an open order (S4.6). An exam or package order posts its own id; a bundle posts its exam ids,
 * which the order read gives. The API answers with the same open order and a fresh gateway redirect, so no new order is made.
 * The receipt address already on the order goes back with it, so paying again keeps it (Phase 4B).
 */
export async function pendingTarget(order: ResumableOrder): Promise<PurchaseTarget> {
  const receiptEmail = order.receiptEmail ?? undefined;
  if (order.scope === "EXAM" && order.examId) return { examId: order.examId, receiptEmail };
  if (order.scope === "MODULE" && order.moduleId) return { moduleId: order.moduleId, receiptEmail };
  const detail = await apiRequest<{ examIds: string[]; receiptEmail: string | null }>(`/api/v1/live-exam-orders/${order.orderId}`);
  return { examIds: detail.examIds, receiptEmail: detail.receiptEmail ?? undefined };
}

/** Pays an open order again: posts its target and goes to the gateway page the answer names. Throws when there is no page. */
export function useResumeOrder() {
  const start = useStartOrder();
  return async (order: ResumableOrder) => {
    const next = await start.mutateAsync(await pendingTarget(order));
    if (!next.redirectUrl) throw new Error("no gateway page");
    window.location.assign(next.redirectUrl);
  };
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

/** After a paid order, every catalog read (access, myStatus, routine) is stale. */
export function useInvalidateCatalog() {
  const client = useQueryClient();
  return () => client.invalidateQueries({ queryKey: ["catalog"] });
}
