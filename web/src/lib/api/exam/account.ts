"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { apiBlob, apiRequest } from "@/lib/api/client";
import { ApiError } from "@/lib/api/errors";
import type { Me, PageResponse } from "@/lib/api/exam/types";

/** Account and auth mutations for the signed-in student. Keys live in this file. */

/** The public order statuses the API sends (`LiveExamOrderService.publicStatus`). */
export type PaymentStatus = "PENDING" | "PAID" | "FAILED" | "CANCELLED";

/** One row of the student's own payment history: `GET /api/v1/me/live-exam-orders`. */
export interface MyOrder {
  orderId: string;
  tranId: string | null;
  status: PaymentStatus;
  amount: number;
  currency: string;
  /** "MODULE", "EXAM", or "BUNDLE" (a package bought as a whole, with its exams). */
  scope: "MODULE" | "EXAM" | "BUNDLE";
  moduleId: string | null;
  moduleTitleEn: string | null;
  moduleTitleBn: string | null;
  examId: string | null;
  examTitleEn: string | null;
  examTitleBn: string | null;
  /** Payment channel as the gateway names it (bKash, VISA...), when known. */
  paymentChannel: string | null;
  /** ISO UTC. */
  createdAt: string;
  /** The price the student saw, and the discount, when there was one (R19). */
  listPrice: number | null;
  discountPercent: number | null;
  /** ISO UTC, set once the order is paid (P2). Null before. */
  paidAt: string | null;
  /** RCPT-<year>-<6 digits>, set once the order is paid (P4). Null before. */
  receiptNo: string | null;
}

/** The list filters (P1). Every field is optional; an empty one is left out of the query. */
export interface OrderFilter {
  status?: PaymentStatus | "";
  scope?: "MODULE" | "EXAM" | "BUNDLE" | "";
  from?: string;
  to?: string;
  examId?: string;
  moduleId?: string;
  orderId?: string;
}

/** The totals for the summary strip (P1). */
export interface OrderSummary {
  totalPaid: number;
  currency: string;
  paidCount: number;
  pendingCount: number;
}

export const accountKeys = {
  orders: (page: number, filter: OrderFilter = {}) => ["account", "orders", page, filter] as const,
  summary: () => ["account", "orders", "summary"] as const,
};

/** The query string for a filter. Empty values are left out, so an unfiltered list has no filter in its URL. */
export function orderFilterQuery(filter: OrderFilter): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filter)) {
    if (value) params.set(key, value);
  }
  const query = params.toString();
  return query ? `&${query}` : "";
}

export function useMyOrders(page: number, filter: OrderFilter = {}) {
  return useQuery({
    queryKey: accountKeys.orders(page, filter),
    queryFn: () =>
      apiRequest<PageResponse<MyOrder>>(`/api/v1/me/live-exam-orders?page=${page}&size=20${orderFilterQuery(filter)}`),
    staleTime: 0,
    refetchOnWindowFocus: true,
  });
}

/** One order by id, through the list (P3): zero or one row, with its titles. */
export function useMyOrder(orderId: string | null) {
  return useQuery({
    queryKey: accountKeys.orders(0, { orderId: orderId ?? "" }),
    queryFn: () =>
      apiRequest<PageResponse<MyOrder>>(`/api/v1/me/live-exam-orders?page=0&size=1&orderId=${orderId}`),
    enabled: Boolean(orderId),
    staleTime: 0,
  });
}

/** Paid total, paid and pending counts (P1), for the summary strip. */
export function useMyOrderSummary() {
  return useQuery({
    queryKey: accountKeys.summary(),
    queryFn: () => apiRequest<OrderSummary>("/api/v1/me/live-exam-orders/summary"),
    staleTime: 0,
    refetchOnWindowFocus: true,
  });
}

/**
 * The student's open (pending) order for one package or exam, if there is one (P1: `moduleId` or `examId` with `status=PENDING`).
 * A pending bundle counts for each of its exams. Only asked for a signed-in student who does not own the item yet.
 */
export function useOpenOrder(filter: { moduleId?: string; examId?: string }, enabled: boolean) {
  const scoped: OrderFilter = { ...filter, status: "PENDING" };
  return useQuery({
    queryKey: accountKeys.orders(0, scoped),
    queryFn: () => apiRequest<PageResponse<MyOrder>>(`/api/v1/me/live-exam-orders?page=0&size=1${orderFilterQuery(scoped)}`),
    enabled,
    staleTime: 0,
  });
}

/** The receipt PDF of a paid order (P4). It needs the bearer token, so it is fetched as a blob and saved by the caller. */
export function receiptPdfPath(orderId: string): string {
  return `/api/v1/me/live-exam-orders/${orderId}/receipt.pdf`;
}

/** Saves the receipt PDF of a paid order as a file (P4). Throws when the download fails; the caller shows the message. */
export async function downloadReceiptPdf(orderId: string, fileName: string): Promise<void> {
  const blob = await apiBlob(receiptPdfPath(orderId));
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `${fileName}.pdf`;
  link.click();
  URL.revokeObjectURL(url);
}

export function useChangePassword() {
  return useMutation({
    mutationFn: (body: { currentPassword: string; newPassword: string }) =>
      apiRequest<void>("/api/v1/auth/change-password", { method: "POST", body }),
  });
}

/** Changes the own phone with the current password. Returns the updated user. */
export function useChangePhone() {
  return useMutation({
    mutationFn: (body: { currentPassword: string; phone: string }) =>
      apiRequest<Me>("/api/v1/auth/change-phone", { method: "POST", body }),
  });
}

/** Changes the student's own display name. Staff names stay as created. Returns the updated user. */
export function useChangeName() {
  return useMutation({
    mutationFn: (body: { fullName: string }) =>
      apiRequest<Me>("/api/v1/me", { method: "PATCH", body }),
  });
}

/** Sets the phone once, for a Google sign-up that has none. */
export function useSetPhone() {
  return useMutation({
    mutationFn: (body: { phone: string }) =>
      apiRequest<Me>("/api/v1/me/phone", { method: "POST", body }),
  });
}

/** Confirms the email with the code from the mail. Needs a signed-in session. */
export function useVerifyEmail() {
  return useMutation({
    mutationFn: (body: { code: string }) =>
      apiRequest<void>("/api/v1/auth/verify-email", { method: "POST", body }),
  });
}

export function useResendEmailVerification() {
  return useMutation({
    mutationFn: (body: { email: string }) =>
      apiRequest<{ message: string }>("/api/v1/auth/verify-email/resend", {
        method: "POST",
        body,
        skipAuthRetry: true,
      }),
  });
}

/**
 * True when an API that predates an endpoint answered: a RESOURCE_NOT_FOUND body, a 405,
 * or a 404 with no API error body. The client keeps the HTTP status on ApiError.
 * Screens show "not yet" instead of an error.
 */
export function isNotDeployed(error: unknown): boolean {
  if (!(error instanceof ApiError)) return false;
  if (error.code === "RESOURCE_NOT_FOUND") return true;
  // A 405, or a 404 with no API error body: the client keeps the HTTP status, so no text matching.
  return error.status === 405 || (error.status === 404 && error.code === "INTERNAL_ERROR");
}
