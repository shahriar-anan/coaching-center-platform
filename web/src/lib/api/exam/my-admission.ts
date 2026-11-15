"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiBlob, apiFile, apiRequest } from "@/lib/api/client";
import { admissionKeys, type AdmissionState, type AdmissionView } from "@/lib/api/exam/offline-admission";

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

/**
 * One class of the student's batch (plan 8.5). The API sends these fields and no others: never an instructor, an amount or a
 * pay status. `kind` is LECTURE or EXAM. `topic` is null when the routine has none.
 */
export interface StudentClass {
  /** yyyy-MM-dd, the Dhaka date of the class. */
  date: string;
  /** The English weekday name, for example "Monday". */
  day: string;
  /** HH:mm:ss, Dhaka time. */
  startsAt: string;
  /** HH:mm:ss, Dhaka time. */
  endsAt: string;
  subject: string;
  topic: string | null;
  kind: "LECTURE" | "EXAM";
}

export const myAdmissionKeys = {
  fees: ["account", "admission", "fees"] as const,
  items: ["account", "admission", "items"] as const,
  routine: ["account", "admission", "routine"] as const,
  list: admissionKeys.list,
};

/**
 * The paths of one admission's data. Without an id, the routes answer the student's newest admission, which is what a student with
 * one admission uses. With an id (several admissions, plan P11), the per-admission routes answer that admission.
 */
function admissionBase(admissionId?: string): string {
  return admissionId ? `/api/v1/me/admissions/${admissionId}` : "/api/v1/me/admission";
}

/** The student copy of the batch routine for one month (yyyy-MM). The API answers 404 when there is none. */
export function admissionRoutinePdfPath(month: string, admissionId?: string): string {
  return `${admissionBase(admissionId)}/batch-routine.pdf?month=${encodeURIComponent(month)}`;
}

/** The classes of the student's batch in one month (yyyy-MM). Pass `enabled: false` when the batch has no routine. */
export function useMyAdmissionRoutine(month: string, { enabled = true, admissionId }: { enabled?: boolean; admissionId?: string } = {}) {
  return useQuery({
    queryKey: [...myAdmissionKeys.routine, admissionId ?? "newest", month],
    queryFn: () => apiRequest<StudentClass[]>(`${admissionBase(admissionId)}/routine?month=${encodeURIComponent(month)}`),
    enabled,
  });
}

/** Loads the student copy of the routine PDF for one month, with the caller's auth. */
export function loadAdmissionRoutinePdf(month: string, admissionId?: string): Promise<Blob> {
  return apiBlob(admissionRoutinePdfPath(month, admissionId));
}

/** The exams and packages of the student's admission. Pass `enabled: false` until the admission is open (not pending). */
export function useMyAdmissionItems({ enabled = true, admissionId }: { enabled?: boolean; admissionId?: string } = {}) {
  return useQuery({
    queryKey: [...myAdmissionKeys.items, admissionId ?? "newest"],
    queryFn: () => apiRequest<AdmissionItem[]>(`${admissionBase(admissionId)}/items`),
    enabled,
  });
}

/** The schedule of the student's admission. Pass `enabled: false` until the admission is open (not pending). */
export function useMyAdmissionFees({ enabled = true, admissionId }: { enabled?: boolean; admissionId?: string } = {}) {
  return useQuery({
    queryKey: [...myAdmissionKeys.fees, admissionId ?? "newest"],
    queryFn: () => apiRequest<StudentSchedule>(`${admissionBase(admissionId)}/fees`),
    enabled,
  });
}

/** Every admission of the student, one per batch, newest first (plan P11). Pass `enabled: false` for anyone who is not a student. */
export function useMyAdmissions({ enabled = true }: { enabled?: boolean } = {}) {
  return useQuery({
    queryKey: myAdmissionKeys.list,
    queryFn: ({ signal }) => apiRequest<AdmissionView[]>("/api/v1/me/admissions", { signal }),
    enabled,
  });
}

/**
 * Starts the student's application for a batch, prefilled from the account (plan B). The answer is the new admission, which is the
 * newest one, so the form's own routes work on it. The API refuses a second open application for the same batch.
 */
export function useApplyForBatch() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (batchId: string) =>
      apiRequest<AdmissionView>("/api/v1/me/admission/apply", { method: "POST", body: { batchId } }),
    onSuccess: (view) => {
      queryClient.setQueryData(admissionKeys.mine, view);
      void queryClient.invalidateQueries({ queryKey: myAdmissionKeys.list });
    },
  });
}

/** Joins the onboarded admission with the join code from the email (plan B, C). The answer is the admission that was joined. */
export function useJoinAdmission() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (code: string) => apiRequest<AdmissionView>("/api/v1/me/admission/join", { method: "POST", body: { code } }),
    onSuccess: (view) => {
      queryClient.setQueryData(admissionKeys.mine, view);
      void queryClient.invalidateQueries({ queryKey: myAdmissionKeys.list });
    },
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
