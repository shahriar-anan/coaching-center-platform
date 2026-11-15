"use client";

import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
import { useBatches, type BatchView } from "@/lib/api/exam/offline-admin";
import { queryString } from "@/lib/api/exam/staff-ops";
import type { Role } from "@/lib/api/exam/types";

/*
 * The offline instructor pay screens (plan sections 6 and 8.2, 8.3): the classes that wait for payment, the approvals, the offline
 * instructors. Money fields are JSON numbers or strings. Query keys start with "instructorPay"; every write refreshes them all.
 */

export const instructorPayKeys = {
  all: ["instructorPay"] as const,
};

// ---- views -------------------------------------------------------------------------------------------------------------

export type ClassStatus = "SCHEDULED" | "HELD" | "PAID_PENDING" | "PAID" | "NOT_TAKEN_PENDING" | "NOT_TAKEN" | "CANCELLED";

export type PaymentStatus = "PENDING" | "APPROVED" | "REJECTED" | "VOIDED";

export type PaymentRecord = {
  id: string;
  instructorId: string | null;
  amount: number | string;
  method: "CASH" | "BKASH" | "NAGAD" | "ROCKET" | "BANK";
  reference: string | null;
  paidOn: string;
  note: string | null;
  status: PaymentStatus;
};

export type NotTakenRequest = {
  id: string;
  reason: string;
  status: string;
  requestedAt: string;
};

/** One class as the staff see it (the API's ClassView). Times are Dhaka wall-clock, "HH:mm" or "HH:mm:ss". */
export type ClassRow = {
  id: string;
  batchId: string;
  routineId: string | null;
  heldOn: string;
  startsAt: string | null;
  endsAt: string | null;
  subject: string;
  topic: string | null;
  kind: string;
  instructorId: string | null;
  instructorName: string | null;
  instructorText: string | null;
  amount: number | string | null;
  rateOverride: number | string | null;
  status: ClassStatus;
  version: number;
  payment: PaymentRecord | null;
  notTakenRequest: NotTakenRequest | null;
};

export type UnsettledPage = {
  items: ClassRow[];
  page: number;
  size: number;
  total: number;
};

export type UnsettledCounts = {
  held: number;
  waitingApproval: number;
  heldAmount: number | string;
};

export type BulkResult = { id: string; ok: boolean; message: string | null };

export type OfflineInstructor = {
  id: string;
  name: string;
  phone: string | null;
  note: string | null;
  aliases: string[];
  archived: boolean;
  version: number;
};

export type UnsettledFilters = {
  batchId?: string;
  instructorId?: string;
  from?: string;
  to?: string;
};

// ---- reading -----------------------------------------------------------------------------------------------------------

/** Every page of the unsettled classes for the filters: the page reads them 200 at a time, so the groups are complete. */
async function unsettledAll(filters: UnsettledFilters, signal?: AbortSignal): Promise<ClassRow[]> {
  const rows: ClassRow[] = [];
  for (let page = 0; page < 50; page += 1) {
    const result = await apiRequest<UnsettledPage>(
      `/api/v1/offline-classes/unsettled${queryString({ ...filters, page, size: 200 })}`,
      { signal },
    );
    rows.push(...result.items);
    if (result.items.length === 0 || (page + 1) * result.size >= result.total) {
      break;
    }
  }
  return rows;
}

/** The unsettled classes (HELD, waiting for payment approval, waiting for a not-taken approval), oldest first. */
export function useUnsettledClasses(filters: UnsettledFilters) {
  return useQuery({
    queryKey: [...instructorPayKeys.all, "unsettled", filters],
    queryFn: ({ signal }) => unsettledAll(filters, signal),
    staleTime: 0,
  });
}

/** The counts the cards show: held classes, classes waiting for approval, and the amount still owed on held classes. */
export function useUnsettledCounts(enabled = true, refetchInterval?: number) {
  return useQuery({
    queryKey: [...instructorPayKeys.all, "counts"],
    queryFn: ({ signal }) => apiRequest<UnsettledCounts>("/api/v1/offline-classes/unsettled/counts", { signal }),
    enabled,
    staleTime: 0,
    refetchInterval,
  });
}

/**
 * Every class of every batch, read batch by batch. The batch's own list has all statuses, so the paid, not-taken, owed and
 * per-instructor figures all come from it. Cancelled classes are left out by the API.
 */
export function useAllBatchClasses() {
  const batches = useBatches("");
  const list: BatchView[] = batches.data ?? [];
  const queries = useQueries({
    queries: list.map((batch) => ({
      queryKey: [...instructorPayKeys.all, "batchClasses", batch.id],
      queryFn: ({ signal }: { signal?: AbortSignal }) =>
        apiRequest<ClassRow[]>(`/api/v1/offline-batches/${batch.id}/classes`, { signal }),
      staleTime: 0,
    })),
  });
  const rows = queries.flatMap((query) => query.data ?? []);
  const isPending = batches.isPending || queries.some((query) => query.isPending);
  const isError = batches.isError || queries.some((query) => query.isError);
  const refetch = () => {
    void batches.refetch();
    queries.forEach((query) => void query.refetch());
  };
  return { batches: list, rows, isPending, isError, refetch };
}

/** The offline instructors by name. Archived ones only when asked. */
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

/**
 * The count the sidebar shows on the Instructor payments item: held classes, plus the entries waiting for approval for the Master
 * Admin. Only the two admin roles read it; the caller passes null for any other role, and when the offline switch is off.
 */
export function useInstructorPayAttention(role: Role | null | undefined): number {
  const admin = role === "MASTER_ADMIN" || role === "SYSTEM_ADMIN";
  const master = role === "MASTER_ADMIN";
  const counts = useUnsettledCounts(admin, 30_000);
  if (!admin) {
    return 0;
  }
  return (counts.data?.held ?? 0) + (master ? (counts.data?.waitingApproval ?? 0) : 0);
}

// ---- writes ------------------------------------------------------------------------------------------------------------

export type RecordPaymentBody = {
  amount: string;
  method: PaymentRecord["method"];
  reference?: string;
  paidOn: string;
  note?: string;
};

/** Refreshes every instructor pay query, so the lists, cards and sidebar agree after a write. */
function useInstructorPayWrite<TVars, TResult>(run: (vars: TVars) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation<TResult, Error, TVars>({
    mutationFn: run,
    onSettled: () => queryClient.invalidateQueries({ queryKey: instructorPayKeys.all }),
  });
}

/** Records a payment for a held class. Staff's waits for approval; the Master Admin's is approved at once. */
export function useRecordClassPayment() {
  return useInstructorPayWrite<{ classId: string; body: RecordPaymentBody }, ClassRow>(({ classId, body }) =>
    apiRequest<ClassRow>(`/api/v1/offline-classes/${classId}/payment`, { method: "POST", body }),
  );
}

/** Marks a held class not taken with a reason. Staff's waits for approval. */
export function useMarkNotTaken() {
  return useInstructorPayWrite<{ classId: string; reason: string }, ClassRow>(({ classId, reason }) =>
    apiRequest<ClassRow>(`/api/v1/offline-classes/${classId}/not-taken`, { method: "POST", body: { reason } }),
  );
}

/** Approves waiting payments. Each id is handled on its own; the result says which failed and why. */
export function useApprovePayments() {
  return useInstructorPayWrite<string[], BulkResult[]>((ids) =>
    apiRequest<BulkResult[]>("/api/v1/offline-class-payments/approve-many", { method: "POST", body: { ids } }),
  );
}

/** Approves waiting not-taken requests. */
export function useApproveNotTakenMany() {
  return useInstructorPayWrite<string[], BulkResult[]>((ids) =>
    apiRequest<BulkResult[]>("/api/v1/offline-class-decisions/approve-many", { method: "POST", body: { ids } }),
  );
}

export function useRejectPayment() {
  return useInstructorPayWrite<{ id: string; reason: string }, unknown>(({ id, reason }) =>
    apiRequest(`/api/v1/offline-class-payments/${id}/reject`, { method: "POST", body: { reason } }),
  );
}

export function useRejectNotTaken() {
  return useInstructorPayWrite<{ id: string; reason: string }, unknown>(({ id, reason }) =>
    apiRequest(`/api/v1/offline-class-decisions/${id}/reject`, { method: "POST", body: { reason } }),
  );
}

export type InstructorBody = { name?: string; phone?: string | null; note?: string | null; aliases?: string[]; version?: number };

/** Adds an instructor, or changes one when an id is given. A version the caller read goes with the change, so a stale edit is refused. */
export function useSaveInstructor() {
  return useInstructorPayWrite<{ id?: string; body: InstructorBody }, OfflineInstructor>(({ id, body }) =>
    id
      ? apiRequest<OfflineInstructor>(`/api/v1/offline-instructors/${id}`, { method: "PATCH", body })
      : apiRequest<OfflineInstructor>("/api/v1/offline-instructors", { method: "POST", body }),
  );
}

/** Archives an instructor. The API refuses while live classes still name them, and says how many. */
export function useArchiveInstructor() {
  return useInstructorPayWrite<string, OfflineInstructor>((id) =>
    apiRequest<OfflineInstructor>(`/api/v1/offline-instructors/${id}/archive`, { method: "POST" }),
  );
}
