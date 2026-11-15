"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
import { offlineResultKeys } from "./offline-results";

/*
 * The batch pages for offline instructor pay (plan section 6 and 8.1): the monthly routines and their import preview, the instructors,
 * the default rate of each instructor in a batch, and the classes with their settling. Every change refreshes all of these queries, so the
 * tabs agree. Query keys start with "batchPay". The student copy and the original PDFs are read with apiBlob by the screens.
 */

export const batchPayKeys = {
  all: ["batchPay"] as const,
};

// ---- views -------------------------------------------------------------------------------------------------------------

export type RoutineStatus = "PREVIEW" | "CONFIRMED" | "SUPERSEDED";

/** One routine in a list. `month` is yyyy-MM. `version` counts the uploads of that month. */
export type RoutineSummary = {
  id: string;
  /** The day the routine counts from (yyyy-MM-dd): the day it was uploaded. */
  effectiveFrom: string;
  version: number;
  status: RoutineStatus;
  legacyOriginal: boolean;
  originalName: string | null;
  studentCopy: boolean;
  uploadedAt: string;
  confirmedAt: string | null;
  rowCount: number;
  flaggedCount: number;
  canConfirm: boolean;
};

/** One reason a row needs a look: a code the screens key on, and the reason in English for the record. */
export type RoutineFlag = { code: string; reason: string };

export type RoutineKind = "LECTURE" | "EXAM" | "OTHER";

export type RoutineRow = {
  index: number;
  date: string | null;
  day: string | null;
  time: string | null;
  startsAt: string | null;
  endsAt: string | null;
  subject: string | null;
  topic: string | null;
  kind: RoutineKind;
  instructorText: string | null;
  instructorId: string | null;
  instructorName: string | null;
  flags: RoutineFlag[];
};

/** A class as it is in the batch now, in a difference. */
export type RoutineClassRef = {
  id: string;
  date: string;
  startsAt: string | null;
  subject: string | null;
  topic: string | null;
  instructorText: string | null;
  status: string;
  /** An exam that has a result stays as it is: a routine never cancels or moves it (results rule A1). */
  keptForResult?: boolean;
};

export type RoutineChange = { before: RoutineClassRef; after: RoutineRow };

/** The difference between a preview and the confirmed routine of its month. */
export type RoutineDifference = {
  added: RoutineRow[];
  changed: RoutineChange[];
  cancelled: RoutineClassRef[];
  settled: RoutineClassRef[];
  unchanged: number;
};

export type UnmatchedName = { name: string; rows: number };

/** One routine in full: its rows, what blocks the confirm, and the difference when the month already has a confirmed routine. */
export type RoutineView = {
  id: string;
  batchId: string;
  /** The day the routine counts from (yyyy-MM-dd). The classes from that day onward are the ones a confirm replaces. */
  effectiveFrom: string;
  version: number;
  status: RoutineStatus;
  legacyOriginal: boolean;
  originalName: string | null;
  studentCopy: boolean;
  uploadedAt: string;
  confirmedAt: string | null;
  rowCount: number;
  flaggedCount: number;
  unmatchedNames: UnmatchedName[];
  /** What the admin is warned of before confirming. Nothing blocks the confirm (15 Nov): rows with no date or time are left out. */
  warnings: string[];
  canConfirm: boolean;
  rows: RoutineRow[];
  difference: RoutineDifference | null;
};

export type InstructorRow = {
  id: string;
  name: string;
  phone: string | null;
  note: string | null;
  aliases: string[];
  archived: boolean;
  version: number;
};

/** The default rate of one instructor in one batch. */
export type RateRow = { instructorId: string; name: string; rate: number | string };

export type ClassStatus = "SCHEDULED" | "HELD" | "PAID_PENDING" | "PAID" | "NOT_TAKEN_PENDING" | "NOT_TAKEN" | "CANCELLED";

export type PayMethod = "CASH" | "BKASH" | "NAGAD" | "ROCKET" | "BANK";

export type ClassPayment = {
  id: string;
  instructorId: string;
  amount: number | string;
  method: PayMethod;
  reference: string | null;
  paidOn: string;
  note: string | null;
  status: "PENDING" | "APPROVED" | "REJECTED" | "VOIDED";
};

export type ClassDecision = { id: string; reason: string; status: string; requestedAt: string };

/** One class of a batch. `status` is the computed one: HELD is a scheduled class whose end time has passed. */
export type BatchClass = {
  id: string;
  batchId: string;
  routineId: string | null;
  heldOn: string;
  startsAt: string | null;
  endsAt: string | null;
  subject: string;
  topic: string | null;
  kind: RoutineKind;
  instructorId: string | null;
  instructorName: string | null;
  instructorText: string | null;
  amount: number | string | null;
  rateOverride: number | string | null;
  status: ClassStatus;
  version: number;
  payment: ClassPayment | null;
  notTakenRequest: ClassDecision | null;
  /** An exam with a staged or published result: its date cannot change (results rule A1). The API always sends it. */
  hasResult?: boolean;
};

export type ClassFilters = { month?: string; instructorId?: string; status?: string };

// ---- reads -------------------------------------------------------------------------------------------------------------

function queryString(params: Record<string, string | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value) {
      search.set(key, value);
    }
  }
  const text = search.toString();
  return text ? `?${text}` : "";
}

/** The routines of a batch: every month and every upload, the newest month first. */
export function useBatchRoutines(batchId: string) {
  return useQuery({
    queryKey: [...batchPayKeys.all, "routines", batchId],
    queryFn: ({ signal }) => apiRequest<RoutineSummary[]>(`/api/v1/offline-batches/${batchId}/routines`, { signal }),
    enabled: Boolean(batchId),
    staleTime: 0,
  });
}

/** One routine with its rows. Refetched after each change to it. */
export function useRoutine(routineId: string) {
  return useQuery({
    queryKey: [...batchPayKeys.all, "routine", routineId],
    queryFn: ({ signal }) => apiRequest<RoutineView>(`/api/v1/offline-routines/${routineId}`, { signal }),
    enabled: Boolean(routineId),
    staleTime: 0,
  });
}

/** The instructors that are not archived, for the pickers. */
export function useInstructors(enabled = true) {
  return useQuery({
    queryKey: [...batchPayKeys.all, "instructors"],
    queryFn: ({ signal }) => apiRequest<InstructorRow[]>("/api/v1/offline-instructors", { signal }),
    enabled,
    staleTime: 0,
  });
}

export function useBatchRates(batchId: string) {
  return useQuery({
    queryKey: [...batchPayKeys.all, "rates", batchId],
    queryFn: ({ signal }) => apiRequest<RateRow[]>(`/api/v1/offline-batches/${batchId}/rates`, { signal }),
    enabled: Boolean(batchId),
    staleTime: 0,
  });
}

/** The classes of a batch, by date. Filters go to the API, so the list and the counts agree. */
export function useBatchClasses(batchId: string, filters: ClassFilters = {}) {
  return useQuery({
    queryKey: [...batchPayKeys.all, "classes", batchId, filters],
    queryFn: ({ signal }) =>
      apiRequest<BatchClass[]>(
        `/api/v1/offline-batches/${batchId}/classes${queryString({
          month: filters.month,
          status: filters.status,
          instructorId: filters.instructorId,
        })}`,
        { signal },
      ),
    enabled: Boolean(batchId),
    staleTime: 0,
  });
}

// ---- writes ------------------------------------------------------------------------------------------------------------

/** Refreshes every batch pay query, so the routine, the rates, the classes and the instructors agree after a change. */
function useBatchPayCall<TVars, TResult>(call: (vars: TVars) => { path: string; method: "POST" | "PATCH" | "PUT" | "DELETE"; body?: unknown }) {
  const queryClient = useQueryClient();
  return useMutation<TResult, Error, TVars>({
    mutationFn: async (vars) => {
      const request = call(vars);
      return apiRequest<TResult>(request.path, { method: request.method, body: request.body });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: batchPayKeys.all }),
  });
}

/** Uploads a routine for one month. The result is a PREVIEW to check; nothing is saved to the classes yet. */
export function useUploadRoutine(batchId: string) {
  const queryClient = useQueryClient();
  // There is no month: the routine counts from the day it is uploaded.
  return useMutation<RoutineView, Error, { file: File }>({
    mutationFn: ({ file }) => {
      const body = new FormData();
      body.append("file", file);
      return apiRequest<RoutineView>(`/api/v1/offline-batches/${batchId}/routines`, { method: "POST", body });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: batchPayKeys.all }),
  });
}

/** Changes one row of a preview. Send only the fields that change. */
export function useEditRoutineRow(routineId: string) {
  return useBatchPayCall<{ index: number; body: Partial<Record<"date" | "day" | "time" | "subject" | "topic" | "kind" | "instructorId", string | null>> }, RoutineView>(
    ({ index, body }) => ({ path: `/api/v1/offline-routines/${routineId}/rows/${index}`, method: "PATCH", body }),
  );
}

/** Maps an unknown name to an existing instructor (`instructorId`) or to a new one (`newName`). */
export function useMatchInstructor(routineId: string) {
  return useBatchPayCall<{ name: string; instructorId?: string; newName?: string }, RoutineView>((body) => ({
    path: `/api/v1/offline-routines/${routineId}/instructor-matches`,
    method: "POST",
    body,
  }));
}

/** Applies the preview to the classes of the batch. */
export function useConfirmRoutine(routineId: string) {
  return useBatchPayCall<void, RoutineView>(() => ({ path: `/api/v1/offline-routines/${routineId}/confirm`, method: "POST" }));
}

/** Throws away a preview. */
export function useDiscardRoutine(routineId: string) {
  return useBatchPayCall<void, void>(() => ({ path: `/api/v1/offline-routines/${routineId}`, method: "DELETE" }));
}

/** Saves an instructor's phone number. The version the caller read goes with it, so a stale edit is refused. */
export function useSaveInstructorPhone() {
  return useBatchPayCall<{ id: string; version: number; phone: string | null }, InstructorRow>(({ id, version, phone }) => ({
    path: `/api/v1/offline-instructors/${id}`,
    method: "PATCH",
    body: { phone, version },
  }));
}

export function useSetRate(batchId: string) {
  return useBatchPayCall<{ instructorId: string; rate: string }, RateRow>(({ instructorId, rate }) => ({
    path: `/api/v1/offline-batches/${batchId}/rates/${instructorId}`,
    method: "PUT",
    body: { rate },
  }));
}

export function useRemoveRate(batchId: string) {
  return useBatchPayCall<string, void>((instructorId) => ({
    path: `/api/v1/offline-batches/${batchId}/rates/${instructorId}`,
    method: "DELETE",
  }));
}

export type ClassEditBody = {
  version: number;
  heldOn?: string;
  startsAt?: string;
  endsAt?: string;
  subject?: string;
  topic?: string;
  instructorId?: string | null;
  rateOverride?: string | null;
};

/** Edits a class that is not settled yet. A stale version is refused (409). */
export function useEditClass() {
  return useBatchPayCall<{ id: string; body: ClassEditBody }, BatchClass>(({ id, body }) => ({
    path: `/api/v1/offline-classes/${id}`,
    method: "PATCH",
    body,
  }));
}

/** A class added by hand. The end is optional: the API adds the batch's class length to the start. */
export type ClassCreateBody = {
  heldOn: string;
  startsAt: string;
  endsAt?: string;
  subject: string;
  topic?: string;
  kind: RoutineKind;
  instructorId?: string;
};

/** Adds a class to a batch that is not archived. The new class shows in the classes list after the refresh. */
export function useAddClass(batchId: string) {
  const queryClient = useQueryClient();
  return useMutation<BatchClass, Error, ClassCreateBody>({
    mutationFn: (body) => apiRequest<BatchClass>(`/api/v1/offline-batches/${batchId}/classes`, { method: "POST", body }),
    // A new exam can have a result, so the Results tab is refreshed with the class lists.
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: batchPayKeys.all });
      void queryClient.invalidateQueries({ queryKey: offlineResultKeys.all });
    },
  });
}

export type ClassPaymentBody = { amount: string; method: PayMethod; reference?: string; paidOn: string; note?: string };

export function useRecordClassPayment() {
  return useBatchPayCall<{ id: string; body: ClassPaymentBody }, BatchClass>(({ id, body }) => ({
    path: `/api/v1/offline-classes/${id}/payment`,
    method: "POST",
    body,
  }));
}

export function useMarkNotTaken() {
  return useBatchPayCall<{ id: string; reason: string }, BatchClass>(({ id, reason }) => ({
    path: `/api/v1/offline-classes/${id}/not-taken`,
    method: "POST",
    body: { reason },
  }));
}

// ---- files and checks --------------------------------------------------------------------------------------------------

/** The routine PDF limit the API also enforces (20 MB). */
export const ROUTINE_MAX_BYTES = 20 * 1024 * 1024;

export type RoutineFileProblem = "notPdf" | "tooLarge";

/**
 * Refuses a file that cannot be a routine before it is uploaded: not a PDF (by its name or type, and by its first bytes when the
 * browser can read them) or over 20 MB. The API checks again.
 */
export async function routineFileProblem(file: File): Promise<RoutineFileProblem | null> {
  const named = file.type === "application/pdf" || /\.pdf$/i.test(file.name);
  if (!named) {
    return "notPdf";
  }
  if (file.size > ROUTINE_MAX_BYTES) {
    return "tooLarge";
  }
  try {
    const head = new Uint8Array(await file.slice(0, 1024).arrayBuffer());
    const magic = [0x25, 0x50, 0x44, 0x46, 0x2d];
    const found = [...head].some((_, start) => magic.every((byte, i) => head[start + i] === byte));
    return found ? null : "notPdf";
  } catch {
    // The browser cannot read the bytes here; the name and size checks stand and the API checks the rest.
    return null;
  }
}

/** A month input value (yyyy-MM), or null when it is empty or malformed. */
export function validMonth(value: string): string | null {
  return /^\d{4}-(0[1-9]|1[0-2])$/.test(value.trim()) ? value.trim() : null;
}
