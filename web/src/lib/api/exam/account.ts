"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
import type { Me, PageResponse } from "@/lib/api/exam/types";

/** Account and auth mutations for the signed-in student. Keys live in this file. */

export type PaymentStatus = "PENDING" | "SUCCESS" | "FAILED" | "CANCELLED";

/** One row of the student's own payment history (WS1 endpoint, see the Report). */
export interface MyOrder {
  orderId: string;
  tranId: string | null;
  kind: "MODULE" | "EXAM";
  productTitleEn: string;
  productTitleBn: string;
  /** Exam or module id, used to link the row to its page. */
  productId: string;
  status: PaymentStatus;
  amount: number;
  /** Payment method as the gateway names it (bKash, VISA...), when known. */
  method: string | null;
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

/** True for the 404 a not-yet-deployed endpoint returns. Screens treat it as "nothing yet". */
export function isNotDeployed(error: unknown): boolean {
  return typeof error === "object" && error !== null && "code" in error
    ? (error as { code: string }).code === "RESOURCE_NOT_FOUND"
    : false;
}
