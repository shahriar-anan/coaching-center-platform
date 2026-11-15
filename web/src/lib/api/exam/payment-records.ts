"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";

/*
 * The payment records page (payment-records-edit-plan.md): every recorded payment the caller may see, and corrections to them. A System
 * Admin's correction waits for the Master Admin; the Master Admin's applies at once. A finalized correction changes the payment row
 * itself, so every money screen (Finance, fees, instructor pay, instalments) shows it: each change refreshes every query.
 */

export const paymentRecordKeys = {
  all: ["paymentRecords"] as const,
};

export type PaymentKind = "FEE" | "CLASS" | "DIRECT";
export type PaymentMethod = "CASH" | "BKASH" | "NAGAD" | "ROCKET" | "BANK";
export type PaymentRecordStatus = "PENDING" | "APPROVED" | "REJECTED" | "VOIDED";
export type PaymentEditStatus = "PENDING" | "FINALIZED" | "REJECTED";

/** The values a correction changes. The API omits a null note. */
export type PaymentValues = {
  amount: number | string;
  paidOn: string;
  method: PaymentMethod;
  reference: string | null;
  note?: string | null;
};

export type PendingEdit = {
  id: string;
  after: PaymentValues;
  reason: string;
  requestedByName?: string | null;
  requestedAt: string;
};

export type PaymentRow = {
  kind: PaymentKind;
  id: string;
  paidOn: string;
  amount: number | string;
  method: PaymentMethod;
  reference?: string | null;
  note?: string | null;
  status: PaymentRecordStatus;
  receiptNo?: string | null;
  personName?: string | null;
  batchId?: string | null;
  batchNameEn?: string | null;
  batchNameBn?: string | null;
  instructorId?: string | null;
  instructorName?: string | null;
  recordedByName?: string | null;
  recordedAt?: string | null;
  correctedAt?: string | null;
  pendingEdit?: PendingEdit | null;
};

export type PaymentEdit = {
  id: string;
  kind: PaymentKind;
  paymentId: string;
  status: PaymentEditStatus;
  before: PaymentValues;
  after: PaymentValues;
  reason: string;
  personName?: string | null;
  requestedByName?: string | null;
  requestedAt: string;
  decidedByName?: string | null;
  decidedAt?: string | null;
  decisionReason?: string | null;
};

export type PaymentRecordFilters = {
  from: string;
  to: string;
  kind: PaymentKind | "";
  batchId: string;
  instructorId: string;
  q: string;
};

/** The fields a correction sends: only the ones it changes need to differ, and the reason is required. */
export type PaymentEditBody = {
  amount: string;
  paidOn: string;
  method: PaymentMethod;
  reference: string;
  note: string;
  reason: string;
};

function queryString(params: Record<string, string | null | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value) search.set(key, value);
  }
  const text = search.toString();
  return text ? `?${text}` : "";
}

export function usePaymentRecords(filters: PaymentRecordFilters) {
  return useQuery({
    queryKey: [...paymentRecordKeys.all, "list", filters],
    queryFn: ({ signal }) =>
      apiRequest<PaymentRow[]>(
        `/api/v1/payment-records${queryString({
          from: filters.from,
          to: filters.to,
          kind: filters.kind,
          batchId: filters.batchId,
          instructorId: filters.instructorId,
          q: filters.q.trim(),
        })}`,
        { signal },
      ),
    staleTime: 0,
  });
}

/** The corrections, newest first: the waiting ones, or the decided ones (finalized and rejected) for the history. */
export function usePaymentEdits(status: "PENDING" | "DECIDED") {
  return useQuery({
    queryKey: [...paymentRecordKeys.all, "edits", status],
    queryFn: ({ signal }) => apiRequest<PaymentEdit[]>(`/api/v1/payment-records/edits${queryString({ status })}`, { signal }),
    staleTime: 0,
  });
}

/** A correction changes money everywhere, so every query is refreshed, not only this page's. */
function useMoneyCall<TVars>(build: (vars: TVars) => { path: string; body?: unknown }) {
  const queryClient = useQueryClient();
  return useMutation<PaymentEdit, Error, TVars>({
    mutationFn: (vars) => {
      const call = build(vars);
      return apiRequest<PaymentEdit>(call.path, { method: "POST", body: call.body ?? {} });
    },
    onSuccess: () => queryClient.invalidateQueries(),
  });
}

export function useProposePaymentEdit() {
  return useMoneyCall<{ kind: PaymentKind; id: string; body: PaymentEditBody }>(({ kind, id, body }) => ({
    path: `/api/v1/payment-records/${kind.toLowerCase()}/${id}/edits`,
    body,
  }));
}

export function useFinalizePaymentEdit() {
  return useMoneyCall<string>((id) => ({ path: `/api/v1/payment-edits/${id}/finalize` }));
}

export function useRejectPaymentEdit() {
  return useMoneyCall<{ id: string; reason: string }>(({ id, reason }) => ({
    path: `/api/v1/payment-edits/${id}/reject`,
    body: { reason },
  }));
}
