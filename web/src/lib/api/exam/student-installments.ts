"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFile, apiRequest } from "@/lib/api/client";

/*
 * The student's own payment plans and receipts (plan direct-enrolment-installments, sections 6 and 8). Each route answers 404 for a
 * plan or receipt that is not the student's. Money is taka as a JSON number. Dates are Dhaka calendar dates, YYYY-MM-DD. The API
 * leaves a field out when it is null, so every field the screens may not get is optional. The payment's reference is not typed
 * here: the student's screens never show it (plan section 9.1).
 */

/** The plan as the student sees it. `status` is the same set the staff list uses. */
export interface StudentPlan {
  planId: string;
  grantId?: string;
  scope?: "MODULE" | "EXAM";
  examId?: string | null;
  moduleId?: string | null;
  itemTitleEn?: string | null;
  itemTitleBn?: string | null;
  agreedTotal?: number;
  paid?: number;
  due?: number;
  accessRevoked?: boolean;
  nextDueOn?: string | null;
  nextDueAmount?: number | null;
  status?: string;
  createdAt?: string;
}

/** One installment, with what has been paid on it. `uncovered` is what is still owed on it. */
export interface StudentScheduleRow {
  seq: number;
  amount?: number;
  dueOn?: string;
  paid?: number;
  uncovered?: number;
  /** PAID, PART_PAID, DUE, OVERDUE or UPCOMING. */
  state?: string;
}

/** One payment the student has made. A voided payment has `voidedAt`, and its receipt is stamped VOID. */
export interface StudentPayment {
  id: string;
  planId?: string;
  amount?: number;
  /** CASH, BKASH, NAGAD, ROCKET or BANK. */
  method?: string;
  paidOn?: string;
  receiptNo?: string;
  paidToDate?: number;
  dueAfter?: number;
  nextDueOn?: string | null;
  nextDueAmount?: number | null;
  installmentNo?: number;
  installmentCount?: number;
  voidedAt?: string | null;
}

/** `GET /api/v1/me/installment-plans/{id}`: the plan, its schedule, and every payment, voided ones included. */
export interface StudentPlanDetail {
  plan: StudentPlan;
  schedule?: StudentScheduleRow[];
  payments?: StudentPayment[];
}

export const studentInstallmentKeys = {
  plan: (planId: string) => ["account", "installmentPlan", planId] as const,
};

/** One of the student's own plans, with its schedule and payments. Only asked for once the payment panel is open. */
export function useStudentInstallmentPlan(planId: string | null) {
  return useQuery({
    queryKey: studentInstallmentKeys.plan(planId ?? ""),
    queryFn: () => apiRequest<StudentPlanDetail>(`/api/v1/me/installment-plans/${planId}`),
    enabled: Boolean(planId),
    refetchOnWindowFocus: true,
  });
}

/** The receipt PDF of one of the student's payments. It needs the bearer token, so it is fetched as a blob. */
export function studentInstallmentReceiptPath(paymentId: string): string {
  return `/api/v1/me/installment-payments/${paymentId}/receipt.pdf`;
}

/**
 * Saves a receipt PDF as a file, named as the server names it, or `fallbackName` (the receipt number) when it does not. The
 * name never carries a reference or a phone number. Throws when the download fails; the caller shows the message.
 */
export async function downloadStudentInstallmentReceipt(paymentId: string, fallbackName: string): Promise<void> {
  const { blob, fileName: serverName } = await apiFile(studentInstallmentReceiptPath(paymentId));
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = serverName ?? `${fallbackName}.pdf`;
  link.click();
  URL.revokeObjectURL(url);
}
