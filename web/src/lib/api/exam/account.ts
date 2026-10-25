"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
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
  /** "MODULE" or "EXAM". */
  scope: "MODULE" | "EXAM";
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
}

export const accountKeys = {
  orders: (page: number) => ["account", "orders", page] as const,
};

export function useMyOrders(page: number) {
  return useQuery({
    queryKey: accountKeys.orders(page),
    queryFn: () =>
      apiRequest<PageResponse<MyOrder>>(`/api/v1/me/live-exam-orders?page=${page}&size=20`),
    staleTime: 0,
    refetchOnWindowFocus: true,
  });
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
