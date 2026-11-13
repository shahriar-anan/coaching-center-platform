"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFile, apiRequest } from "@/lib/api/client";
import type { AdmissionState } from "@/lib/api/exam/offline-admission";

/*
 * The student's fee schedule for My admission (plan 5 and 6.4). The API answers with the approved payments only: a payment
 * waiting for approval never reaches the student. Money comes as plain numbers (BDT).
 */

export type StudentDueState = "PAID" | "UNPAID" | "PART_PAID";

export interface StudentDue {
  id: string;
  seq: number;
  /** yyyy-MM-dd */
  dueOn: string;
  amount: number;
  paid: number;
  owed: number;
  /** True when the due has passed unpaid. */
  overdue: boolean;
  state: StudentDueState;
}

export interface StudentPayment {
  id: string;
  amount: number;
  method?: string | null;
  reference?: string | null;
  /** yyyy-MM-dd */
  paidOn: string;
  receiptNo: string;
  /** Not sent by the API, which lists approved payments only. A row with any other status is never shown. */
  status?: string;
}

export interface StudentSchedule {
  admissionId: string;
  admissionStatus: AdmissionState;
  owed: number;
  overdue: number;
  dues: StudentDue[];
  payments: StudentPayment[];
}

/** One exam or package the student may open through this admission. Dates are ISO instants. */
export interface AdmissionItem {
  scope: "EXAM" | "MODULE";
  /** Set for an exam; null for a package. */
  examId: string | null;
  /** Set for a package; null for an exam. */
  moduleId: string | null;
  titleEn: string | null;
  titleBn: string | null;
  /** The active exams in a package; null for an exam. */
  packageExamCount: number | null;
  /** The end of the access grant, or null when it has none. */
  accessEndsAt: string | null;
}

/** The routine PDF of the student's batch. The API answers 404 when there is none. */
export const ADMISSION_ROUTINE_PATH = "/api/v1/me/admission/batch-routine.pdf";

export const myAdmissionKeys = {
  fees: ["account", "admission", "fees"] as const,
  items: ["account", "admission", "items"] as const,
};

/** The exams and packages of the student's admission. Pass `enabled: false` until the admission is open (not pending). */
export function useMyAdmissionItems({ enabled = true }: { enabled?: boolean } = {}) {
  return useQuery({
    queryKey: myAdmissionKeys.items,
    queryFn: () => apiRequest<AdmissionItem[]>("/api/v1/me/admission/items"),
    enabled,
  });
}

/** The schedule of the student's admission. Pass `enabled: false` until the admission is open (not pending). */
export function useMyAdmissionFees({ enabled = true }: { enabled?: boolean } = {}) {
  return useQuery({
    queryKey: myAdmissionKeys.fees,
    queryFn: () => apiRequest<StudentSchedule>("/api/v1/me/admission/fees"),
    enabled,
  });
}

export function admissionReceiptPath(paymentId: string): string {
  return `/api/v1/me/admission/payments/${paymentId}/receipt.pdf`;
}

/** Downloads one approved payment's receipt, with the file name the server gives it. */
export async function downloadAdmissionReceipt(paymentId: string, fallbackName: string): Promise<void> {
  const { blob, fileName } = await apiFile(admissionReceiptPath(paymentId));
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName ?? `${fallbackName}.pdf`;
  link.click();
  URL.revokeObjectURL(url);
}
