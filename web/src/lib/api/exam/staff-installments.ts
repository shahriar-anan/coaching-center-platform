"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { apiFile, apiRequest } from "@/lib/api/client";
import type { PageResponse } from "@/lib/api/exam/types";
import { staffNotificationKeys } from "@/lib/api/exam/staff-notifications";

/*
 * The Master Admin's Installments pages (plan direct-enrolment-installments, sections 6 and 8). Every route is Master Admin only.
 * Money is taka as a JSON number; the screens convert it to whole paisa before any arithmetic. Dates are Dhaka dates as YYYY-MM-DD.
 * Optional fields are left out by the API when they are null, so they are typed as optional.
 */

export type InstallmentStatus = "ACTIVE" | "DUE_SOON" | "OVERDUE" | "COMPLETED" | "ACCESS_REVOKED";
export type InstallmentRowState = "PAID" | "PART_PAID" | "DUE" | "OVERDUE" | "UPCOMING";
export type InstallmentMethod = "CASH" | "BKASH" | "NAGAD" | "ROCKET" | "BANK";

/** One plan, as the list and the detail show it. `paid` and `due` are what the schedule says now. */
export interface InstallmentPlan {
  planId: string;
  grantId: string;
  studentId: string;
  studentName?: string | null;
  scope: "MODULE" | "EXAM";
  examId?: string | null;
  moduleId?: string | null;
  itemTitleEn?: string | null;
  itemTitleBn?: string | null;
  agreedTotal: number;
  paid: number;
  due: number;
  accessRevoked: boolean;
  nextDueOn?: string | null;
  nextDueAmount?: number | null;
  status: InstallmentStatus;
  createdAt: string;
}

/** The totals for the summary band. Completed plans add nothing to `totalDue`. */
export interface InstallmentSummary {
  totalDue: number;
  overdueCount: number;
  dueSoonCount: number;
}

/** One installment with what has been paid on it. `uncovered` is what is still owed on it. */
export interface InstallmentRow {
  seq: number;
  amount: number;
  dueOn: string;
  paid: number;
  uncovered: number;
  state: InstallmentRowState;
}

/**
 * One payment. `reference` is the staff copy: the screens show it masked, never in full (plan section 9.1). `note` and
 * `voidReason` are staff text.
 */
export interface InstallmentPayment {
  id: string;
  planId: string;
  amount: number;
  method: InstallmentMethod;
  reference: string;
  paidOn: string;
  note?: string | null;
  receiptNo: string;
  paidToDate: number;
  dueAfter: number;
  nextDueOn?: string | null;
  nextDueAmount?: number | null;
  installmentNo: number;
  installmentCount: number;
  createdAt: string;
  voidedAt?: string | null;
  voidReason?: string | null;
}

/** `GET /api/v1/installment-plans/{id}`: the plan, its schedule, and every payment, voided ones included. */
export interface InstallmentPlanDetail {
  plan: InstallmentPlan;
  schedule: InstallmentRow[];
  payments: InstallmentPayment[];
}

/** The list's filters. `status` is an API status, or absent for all. */
export type InstallmentFilters = {
  page: number;
  status?: string;
  q?: string;
  studentId?: string;
  examId?: string;
  moduleId?: string;
};

/** The body of `POST .../payments`. `amount` is taka, at most two decimals. */
export type RecordPaymentBody = {
  amount: number;
  method: InstallmentMethod;
  reference: string;
  paidOn: string;
  note: string | null;
};

/** One row of `PUT .../schedule`: the whole schedule, the paid rows as they are, then the unpaid rows. */
export type ScheduleLineBody = { amount: number; dueOn: string };

export function installmentPlansPath(filters: InstallmentFilters): string {
  const search = new URLSearchParams({ page: String(filters.page) });
  if (filters.status) {
    search.set("status", filters.status);
  }
  for (const key of ["q", "studentId", "examId", "moduleId"] as const) {
    const value = filters[key]?.trim();
    if (value) {
      search.set(key, value);
    }
  }
  return `/api/v1/installment-plans?${search}`;
}

export const installmentKeys = {
  all: ["staffInstallments"] as const,
  list: (filters: InstallmentFilters) => ["staffInstallments", "list", filters] as const,
  summary: ["staffInstallments", "summary"] as const,
  plan: (planId: string) => ["staffInstallments", "plan", planId] as const,
};

/**
 * After any change to a plan: the Installments list, summary and detail, the enrolled lists (`staffEnrol`), the access rows
 * (`staffAdmin.access`) and the notification bell and list (plan section 8).
 */
export function invalidateInstallmentQueries(client: QueryClient) {
  return Promise.all([
    client.invalidateQueries({ queryKey: installmentKeys.all }),
    client.invalidateQueries({ queryKey: ["staffEnrol"] }),
    client.invalidateQueries({ queryKey: ["staffAdmin", "access"] }),
    client.invalidateQueries({ queryKey: staffNotificationKeys.all }),
  ]);
}

export function useInstallmentPlans(filters: InstallmentFilters) {
  return useQuery({
    queryKey: installmentKeys.list(filters),
    queryFn: ({ signal }) => apiRequest<PageResponse<InstallmentPlan>>(installmentPlansPath(filters), { signal }),
    placeholderData: keepPreviousData,
    staleTime: 0,
  });
}

export function useInstallmentSummary() {
  return useQuery({
    queryKey: installmentKeys.summary,
    queryFn: ({ signal }) => apiRequest<InstallmentSummary>("/api/v1/installment-plans/summary", { signal }),
    staleTime: 0,
  });
}

export function useInstallmentPlan(planId: string) {
  return useQuery({
    queryKey: installmentKeys.plan(planId),
    queryFn: ({ signal }) => apiRequest<InstallmentPlanDetail>(`/api/v1/installment-plans/${planId}`, { signal }),
    staleTime: 0,
  });
}

/** Records a payment. Access is not changed. The receipt number comes back in the payment. */
export function useRecordInstallmentPayment(planId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: RecordPaymentBody) =>
      apiRequest<InstallmentPayment>(`/api/v1/installment-plans/${planId}/payments`, { method: "POST", body }),
    retry: false,
    onSettled: () => invalidateInstallmentQueries(client),
  });
}

/** Replaces the unpaid rows. The whole schedule is sent; the paid rows must come back unchanged. */
export function useChangeInstallmentSchedule(planId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (schedule: ScheduleLineBody[]) =>
      apiRequest<InstallmentPlanDetail>(`/api/v1/installment-plans/${planId}/schedule`, { method: "PUT", body: { schedule } }),
    retry: false,
    onSettled: () => invalidateInstallmentQueries(client),
  });
}

/** Voids a payment. Only the latest live payment can be voided, and a reason is required. */
export function useVoidInstallmentPayment() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ paymentId, reason }: { paymentId: string; reason: string }) =>
      apiRequest<InstallmentPayment>(`/api/v1/installment-payments/${paymentId}/void`, {
        method: "POST",
        body: { reason: reason.trim() },
      }),
    retry: false,
    onSettled: () => invalidateInstallmentQueries(client),
  });
}

/** Gives back access that was revoked on this plan, with its original access period. */
export function useRestoreInstallmentAccess(planId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiRequest<unknown>(`/api/v1/installment-plans/${planId}/restore-access`, { method: "POST", body: {} }),
    retry: false,
    onSettled: () => invalidateInstallmentQueries(client),
  });
}

/** Revokes access through the existing grant route, with the reason the audit log keeps. */
export function useRevokeInstallmentAccess() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ grantId, reason }: { grantId: string; reason: string }) =>
      apiRequest<unknown>(`/api/v1/live-exam-grants/${grantId}?reason=${encodeURIComponent(reason.trim())}`, {
        method: "DELETE",
      }),
    retry: false,
    onSettled: () => invalidateInstallmentQueries(client),
  });
}

export function installmentReceiptPath(paymentId: string): string {
  return `/api/v1/installment-payments/${paymentId}/receipt.pdf`;
}

/** Saves a receipt PDF as a file. Throws when the download fails; the caller shows the message. */
export async function downloadInstallmentReceipt(paymentId: string, fallbackName: string): Promise<void> {
  const { blob, fileName: serverName } = await apiFile(installmentReceiptPath(paymentId));
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = serverName ?? `${fallbackName}.pdf`;
  link.click();
  URL.revokeObjectURL(url);
}
