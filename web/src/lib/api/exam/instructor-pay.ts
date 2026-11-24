"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
import { queryString } from "@/lib/api/exam/staff-ops";

/*
 * The offline instructors and their pay (24 Nov, instructor-pay-simple-plan.md). Pay no longer comes from the classes: the Master Admin
 * records each payment by hand. Money fields are JSON numbers or strings. Query keys start with "instructorPay".
 */

export const instructorPayKeys = {
  all: ["instructorPay"] as const,
};

// ---- views -------------------------------------------------------------------------------------------------------------

export type PayMethod = "CASH" | "BKASH" | "NAGAD" | "ROCKET" | "BANK";

/** What a payment was for. A class payment from before 24 Nov takes its class's kind. */
export type PayKind = "LECTURE" | "EXAM" | "OTHER";

export type PaymentStatus = "PENDING" | "APPROVED" | "REJECTED" | "VOIDED";

/**
 * One payment to an instructor. `recordKind` is OTHER for a payment recorded by hand, CLASS for a class payment made before 24 Nov (its
 * type, classes, batch and description come from the class, and only its money fields can be corrected).
 */
export type InstructorPayment = {
  recordKind: "OTHER" | "CLASS";
  id: string;
  instructorId: string;
  instructorName: string;
  kind: PayKind;
  classCount: number | null;
  batchId: string | null;
  batchNameEn: string | null;
  batchNameBn: string | null;
  description: string | null;
  classId: string | null;
  classOn: string | null;
  amount: number | string;
  method: PayMethod;
  reference: string | null;
  paidOn: string;
  note: string | null;
  status: PaymentStatus;
  recordedByName: string | null;
  recordedAt: string;
  voidReason: string | null;
  correctedAt: string | null;
};

export type PaymentList = { items: InstructorPayment[]; total: number | string; truncated: boolean };

export type PaySummary = {
  totalPaid: number | string;
  paidThisMonth: number | string;
  lastPaidOn: string | null;
  lastAmount: number | string | null;
  payments: number;
  batches: { id: string; nameEn: string; nameBn: string }[];
};

export type PayCard = { amount: number | string; payments: number };

export type PayCards = { thisMonth: PayCard; lastMonth: PayCard; thisYear: PayCard };

/** Each instructor's paid-this-month and last payment date, keyed by instructor id. */
export type PayFigures = Record<
  string,
  { paidThisMonth: number | string; lastPaidOn: string | null; batches: { id: string; nameEn: string; nameBn: string }[] }
>;

export type OfflineInstructor = {
  id: string;
  name: string;
  phone: string | null;
  note: string | null;
  aliases: string[];
  archived: boolean;
  version: number;
};

export type PaymentFilters = {
  instructorId?: string;
  from?: string;
  to?: string;
  batchId?: string;
  kind?: string;
};

// ---- reading -----------------------------------------------------------------------------------------------------------

/** The offline instructors by name. Archived ones only when asked. Both admin roles read the list, to pick an instructor for a class. */
export function useOfflineInstructors(includeArchived = false) {
  return useQuery({
    queryKey: [...instructorPayKeys.all, "instructors", includeArchived],
    queryFn: ({ signal }) =>
      apiRequest<OfflineInstructor[]>(`/api/v1/offline-instructors${queryString({ includeArchived: includeArchived ? "true" : "" })}`, {
        signal,
      }),
    staleTime: 0,
  });
}

/** One instructor's payments, every status, newest first (the Payment history tab). */
export function useInstructorPayments(instructorId: string, filters: { from?: string; to?: string; kind?: string } = {}) {
  return useQuery({
    queryKey: [...instructorPayKeys.all, "history", instructorId, filters],
    queryFn: ({ signal }) =>
      apiRequest<PaymentList>(`/api/v1/offline-instructors/${instructorId}/payments${queryString(filters)}`, { signal }),
    staleTime: 0,
  });
}

export function usePaySummary(instructorId: string) {
  return useQuery({
    queryKey: [...instructorPayKeys.all, "summary", instructorId],
    queryFn: ({ signal }) => apiRequest<PaySummary>(`/api/v1/offline-instructors/${instructorId}/pay-summary`, { signal }),
    staleTime: 0,
  });
}

/** Payments across instructors (the Instructor payments page): approved and voided, newest first. */
export function useAllInstructorPayments(filters: PaymentFilters) {
  return useQuery({
    queryKey: [...instructorPayKeys.all, "all", filters],
    queryFn: ({ signal }) => apiRequest<PaymentList>(`/api/v1/offline-instructor-payments${queryString(filters)}`, { signal }),
    staleTime: 0,
  });
}

export function usePayCards() {
  return useQuery({
    queryKey: [...instructorPayKeys.all, "cards"],
    queryFn: ({ signal }) => apiRequest<PayCards>("/api/v1/offline-instructor-payments/cards", { signal }),
    staleTime: 0,
  });
}

export function usePayFigures() {
  return useQuery({
    queryKey: [...instructorPayKeys.all, "figures"],
    queryFn: ({ signal }) => apiRequest<PayFigures>("/api/v1/offline-instructor-payments/figures", { signal }),
    staleTime: 0,
  });
}

// ---- writes ------------------------------------------------------------------------------------------------------------

/** What the Record payment form sends. Empty optional fields are sent as null. */
export type PaymentBody = {
  paidOn: string;
  kind: PayKind;
  classCount: number | null;
  amount: string;
  method: PayMethod;
  reference: string | null;
  batchId: string | null;
  description: string | null;
  note: string | null;
};

/** Writes that move money: every query is refreshed, so Finance and Payment records follow. */
function useMoneyWrite<TVars, TResult>(call: (vars: TVars) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation<TResult, Error, TVars>({ mutationFn: call, onSuccess: () => queryClient.invalidateQueries() });
}

export function useRecordInstructorPayment(instructorId: string) {
  return useMoneyWrite<PaymentBody, InstructorPayment>((body) =>
    apiRequest<InstructorPayment>(`/api/v1/offline-instructors/${instructorId}/payments`, { method: "POST", body }),
  );
}

/** Corrects a payment, with the reason for the correction. It applies at once. */
export function useEditInstructorPayment() {
  return useMoneyWrite<{ payment: InstructorPayment; body: Partial<PaymentBody> & { editReason: string } }, InstructorPayment>(
    ({ payment, body }) =>
      apiRequest<InstructorPayment>(`/api/v1/offline-instructor-payments/${payment.recordKind}/${payment.id}`, { method: "PATCH", body }),
  );
}

/** Voids a payment made by mistake, with a reason. */
export function useVoidInstructorPayment() {
  return useMoneyWrite<{ payment: InstructorPayment; reason: string }, InstructorPayment>(({ payment, reason }) =>
    apiRequest<InstructorPayment>(`/api/v1/offline-instructor-payments/${payment.recordKind}/${payment.id}/void`, {
      method: "POST",
      body: { reason },
    }),
  );
}

export type InstructorBody = { name?: string; phone?: string | null; note?: string | null; aliases?: string[]; version?: number };

/** Refreshes every instructor query after a write. */
function useInstructorWrite<TVars, TResult>(run: (vars: TVars) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation<TResult, Error, TVars>({
    mutationFn: run,
    onSettled: () => queryClient.invalidateQueries({ queryKey: instructorPayKeys.all }),
  });
}

/** Adds an instructor, or changes one when an id is given (the Master Admin). A stale version is refused. */
export function useSaveInstructor() {
  return useInstructorWrite<{ id?: string; body: InstructorBody }, OfflineInstructor>(({ id, body }) =>
    id
      ? apiRequest<OfflineInstructor>(`/api/v1/offline-instructors/${id}`, { method: "PATCH", body })
      : apiRequest<OfflineInstructor>("/api/v1/offline-instructors", { method: "POST", body }),
  );
}

/** Archives an instructor. The API refuses while live classes still name them, and says how many. */
export function useArchiveInstructor() {
  return useInstructorWrite<string, OfflineInstructor>((id) =>
    apiRequest<OfflineInstructor>(`/api/v1/offline-instructors/${id}/archive`, { method: "POST" }),
  );
}
