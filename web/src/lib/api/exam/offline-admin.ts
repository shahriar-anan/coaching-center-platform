"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiBlob, apiRequest } from "@/lib/api/client";
import type { AdmissionForm, AdmissionState } from "@/lib/api/exam/offline-admission";
import type { PageResponse, Role } from "@/lib/api/exam/types";

/*
 * The staff side of offline admissions (plan section 5): the queue, one admission, its setup, exams and packages, review,
 * onboarding, fees, batches and the fee approvals. Money fields are JSON numbers or strings, so screens read them with formatMoney.
 * Query keys start with "offlineAdmin"; every change refreshes all of them.
 */

export const offlineAdminKeys = {
  all: ["offlineAdmin"] as const,
};

// ---- views -------------------------------------------------------------------------------------------------------------

export type AdmissionQueueRow = {
  id: string;
  studentId: string;
  fullName: string;
  mobile: string;
  batchId: string | null;
  batchNameEn: string | null;
  batchNameBn: string | null;
  status: AdmissionState;
  submitCount: number;
  submittedAt: string | null;
  createdAt: string;
  hasPhoto: boolean;
  /** The money flags the server computed: no payment yet, overdue, and a payment waiting approval. */
  flags: AdmissionFlag[];
  /** What the admission still owes in all. Zero when it is paid up or has no dues. */
  owedTotal: number | string | null;
  /** The next due that is still owed, or null when nothing is owed. */
  nextDueOn: string | null;
};

export type AdmissionFlag = "NO_PAYMENT" | "OVERDUE" | "PENDING_APPROVAL";

/** The money filter of the queue. The server applies it, so paging and the total agree with it. */
export type QueueMoneyFilter = "unpaid" | "overdue" | "pending-approval";

/** One requirement the API says is missing before "ready": the field it names and the message. */
export type FieldProblem = { field: string; message: string };

export type AdmissionSetup = {
  batchId: string | null;
  batchNameEn: string | null;
  batchNameBn: string | null;
  monthlyFee: number | string | null;
  admittedOn: string | null;
  firstDueOn: string | null;
  courseEndsOn: string | null;
};

export type AdmissionItem = {
  id: string;
  scope: "EXAM" | "MODULE";
  examId: string | null;
  moduleId: string | null;
  titleEn: string;
  titleBn: string;
  grantId: string | null;
  addedAt: string;
};

export type AdmissionSubmission = {
  id: string;
  submittedAt: string;
  form: AdmissionForm;
  returnedReason: string | null;
  returnedBy: string | null;
  returnedAt: string | null;
};

export type SignedCopy = { fileName: string; uploadedAt: string; uploadedBy: string | null };

/** What an item overlaps with: a grant the student already holds, by an exam or through a package. */
export type ItemOverlap = {
  kind: string;
  id: string;
  titleEn: string;
  titleBn: string;
  via: string;
  planId: string | null;
  paid: number | string | null;
  due: number | string | null;
  packageTitleEn: string | null;
  packageTitleBn: string | null;
};

/** One exam or package on the admission, with the access the student already holds that overlaps it. Read only; never blocks. */
export type ItemOverlaps = {
  itemId: string;
  titleEn: string;
  titleBn: string;
  warnings: ItemOverlap[];
};

export type AdmissionDetail = {
  id: string;
  studentId: string;
  state: AdmissionState;
  version: number;
  submitCount: number;
  submittedAt: string | null;
  createdAt: string;
  hasPhoto: boolean;
  photoUrl: string | null;
  form: AdmissionForm;
  changesReason: string | null;
  changesRequestedAt: string | null;
  setup: AdmissionSetup;
  items: AdmissionItem[];
  submissions: AdmissionSubmission[];
  readyProblems: FieldProblem[];
  readyBy: string | null;
  readyAt: string | null;
  onboardedBy: string | null;
  onboardedAt: string | null;
  closedAt: string | null;
  closedReason: string | null;
  signedCopy: SignedCopy | null;
  overlaps: ItemOverlaps[];
};

export type ItemAdded = { admission: AdmissionDetail; overlaps: ItemOverlap[]; alreadyHeld: boolean };

/** The tab and dashboard counts (plan 5). */
export type OfflineCounts = {
  submitted: number;
  changesRequested: number;
  ready: number;
  onboarded: number;
  closed: number;
  paymentsPending: number;
  overdue: number;
};

/** One admission's oldest overdue unpaid due, with what it still owes in all. */
export type OverdueRow = {
  admissionId: string;
  studentId: string;
  name: string;
  batchNameEn: string | null;
  dueOn: string;
  seq: number;
  amount: number | string;
  daysLate: number;
  owedTotal: number | string;
};

/** One line of an admission's history, newest first. `action` is the audit action name the API sends. */
export type HistoryRow = {
  at: string;
  action: string;
  actorName: string | null;
  detail: string | null;
};

export type DueView = {
  id: string;
  seq: number;
  dueOn: string;
  amount: number | string;
  paid: number | string;
  owed: number | string;
  overdue: boolean;
  state: "PAID" | "UNPAID" | "PART_PAID";
};

export type PaymentStatus = "PENDING" | "APPROVED" | "REJECTED" | "VOIDED";

export type FeePayment = {
  id: string;
  admissionId: string;
  studentId: string;
  /** The student's name, as the approval list shows it. */
  name: string;
  /** The English name of the admission's batch, or null when it has none. */
  batchNameEn: string | null;
  amount: number | string;
  method: "CASH" | "BKASH" | "NAGAD" | "ROCKET" | "BANK";
  reference: string | null;
  paidOn: string;
  note: string | null;
  status: PaymentStatus;
  receiptNo: string | null;
  recordedBy: string | null;
  recordedByName: string | null;
  recordedAt: string;
  decidedAt: string | null;
  decisionReason: string | null;
  voidedAt: string | null;
  voidReason: string | null;
};

export type FeeSchedule = {
  admissionId: string;
  admissionStatus: AdmissionState;
  personName: string;
  owed: number | string;
  overdue: number | string;
  dues: DueView[];
  payments: FeePayment[];
};

export type BatchView = {
  id: string;
  nameEn: string;
  nameBn: string;
  startsOn: string | null;
  endsOn: string;
  defaultMonthlyFee: number | string;
  status: "ACTIVE" | "ARCHIVED";
  studentCount: number;
  routineUploaded: boolean;
  routineFileName: string | null;
  routineUploadedAt: string | null;
  /** Who uploaded the routine, or null when none is uploaded. */
  routineUploadedBy: string | null;
  /** How many students the last routine upload told. Null when it has not been sent. */
  studentsNotified: number | null;
};

export type BatchStudent = {
  admissionId: string;
  studentId: string;
  fullName: string;
  mobile: string;
  status: AdmissionState;
};

// ---- reads -------------------------------------------------------------------------------------------------------------

export type QueueFilters = {
  status?: string;
  batchId?: string;
  q?: string;
  flag?: QueueMoneyFilter;
  page?: number;
  size?: number;
};

function queryString(params: Record<string, string | number | null | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === null || value === undefined || value === "") {
      continue;
    }
    search.set(key, String(value));
  }
  const text = search.toString();
  return text ? `?${text}` : "";
}

/** The queue: one page of admissions, newest first. */
export function useAdmissionQueue(filters: QueueFilters) {
  return useQuery({
    queryKey: [...offlineAdminKeys.all, "queue", filters],
    queryFn: ({ signal }) =>
      apiRequest<PageResponse<AdmissionQueueRow>>(
        `/api/v1/offline-admissions${queryString({
          status: filters.status,
          batchId: filters.batchId,
          q: filters.q,
          flag: filters.flag,
          page: filters.page ?? 0,
          size: filters.size ?? 20,
        })}`,
        { signal },
      ),
    placeholderData: (previous) => previous,
    staleTime: 0,
  });
}

/** The tab counts, the nav badges and the dashboard cards: one request for all of them. */
export function useOfflineCounts(enabled = true, refetchInterval?: number) {
  return useQuery({
    queryKey: [...offlineAdminKeys.all, "counts"],
    queryFn: ({ signal }) => apiRequest<OfflineCounts>("/api/v1/offline-admissions/counts", { signal }),
    enabled,
    staleTime: 0,
    refetchInterval,
  });
}

/** The admissions with an overdue unpaid due, one row each, most days late first. Paged. */
export function useOverdueAdmissions(page: number, size = 20) {
  return useQuery({
    queryKey: [...offlineAdminKeys.all, "overdue", page, size],
    queryFn: ({ signal }) =>
      apiRequest<PageResponse<OverdueRow>>(`/api/v1/offline-admissions/overdue${queryString({ page, size })}`, { signal }),
    placeholderData: (previous) => previous,
    staleTime: 0,
  });
}

/** The history of one admission: its audit events, payments and submissions, newest first. */
export function useAdmissionHistory(id: string) {
  return useQuery({
    queryKey: [...offlineAdminKeys.all, "history", id],
    queryFn: ({ signal }) => apiRequest<HistoryRow[]>(`/api/v1/offline-admissions/${id}/history`, { signal }),
    enabled: Boolean(id),
    staleTime: 0,
  });
}

export function useAdmission(id: string) {
  return useQuery({
    queryKey: [...offlineAdminKeys.all, "admission", id],
    queryFn: ({ signal }) => apiRequest<AdmissionDetail>(`/api/v1/offline-admissions/${id}`, { signal }),
    staleTime: 0,
  });
}

/** An admission's dues and payments. Only meaningful once it is onboarded. */
export function useAdmissionFees(id: string, enabled = true) {
  return useQuery({
    queryKey: [...offlineAdminKeys.all, "fees", id],
    queryFn: ({ signal }) => apiRequest<FeeSchedule>(`/api/v1/offline-admissions/${id}/fees`, { signal }),
    enabled: enabled && Boolean(id),
    staleTime: 0,
  });
}

/** The fee payments: the Master Admin sees every one, staff only the ones they recorded. */
export function useFeePayments(status: PaymentStatus | "" = "", enabled = true) {
  return useQuery({
    queryKey: [...offlineAdminKeys.all, "payments", status],
    queryFn: ({ signal }) =>
      apiRequest<FeePayment[]>(`/api/v1/offline-fee-payments${queryString({ status })}`, { signal }),
    enabled,
    staleTime: 0,
  });
}

export function useBatches(status = "") {
  return useQuery({
    queryKey: [...offlineAdminKeys.all, "batches", status],
    queryFn: ({ signal }) => apiRequest<BatchView[]>(`/api/v1/offline-batches${queryString({ status })}`, { signal }),
    staleTime: 0,
  });
}

export function useBatch(id: string) {
  return useQuery({
    queryKey: [...offlineAdminKeys.all, "batch", id],
    queryFn: ({ signal }) => apiRequest<BatchView>(`/api/v1/offline-batches/${id}`, { signal }),
    staleTime: 0,
  });
}

export function useBatchStudents(id: string) {
  return useQuery({
    queryKey: [...offlineAdminKeys.all, "batchStudents", id],
    queryFn: ({ signal }) => apiRequest<BatchStudent[]>(`/api/v1/offline-batches/${id}/students`, { signal }),
    staleTime: 0,
  });
}

/** The admission id of each student who has an open admission, for the students list badge. Only the first page of each state is read. */
export function useOfflineStudentStates(enabled: boolean) {
  const states: AdmissionState[] = ["AWAITING_FORM", "SUBMITTED", "CHANGES_REQUESTED", "READY", "ONBOARDED"];
  return useQuery({
    queryKey: [...offlineAdminKeys.all, "studentStates"],
    queryFn: async () => {
      const pages = await Promise.all(
        states.map((status) =>
          apiRequest<PageResponse<AdmissionQueueRow>>(`/api/v1/offline-admissions${queryString({ status, size: 100 })}`),
        ),
      );
      const byStudent = new Map<string, { admissionId: string; status: AdmissionState }>();
      for (const page of pages) {
        for (const row of page.content) {
          byStudent.set(row.studentId, { admissionId: row.id, status: row.status });
        }
      }
      return byStudent;
    },
    enabled,
    staleTime: 30_000,
  });
}

/**
 * The counts the sidebar shows for this role: forms waiting for review (and, for the Master Admin, ready ones) and payments waiting
 * for the Master Admin's approval. Read from the counts endpoint, and refreshed every half minute like the other attention counts.
 */
export function useOfflineAttention(role: Role | null | undefined): { admissions: number; offlineFees: number } {
  const reviewer = role === "MASTER_ADMIN" || role === "SYSTEM_ADMIN";
  const master = role === "MASTER_ADMIN";
  const counts = useOfflineCounts(reviewer, 30_000);
  return {
    admissions: reviewer ? (counts.data?.submitted ?? 0) + (master ? (counts.data?.ready ?? 0) : 0) : 0,
    offlineFees: master ? (counts.data?.paymentsPending ?? 0) : 0,
  };
}

// ---- writes ------------------------------------------------------------------------------------------------------------

type Call = { path: string; method: "POST" | "PATCH" | "PUT" | "DELETE"; body?: unknown };

/** An exam or package that can be added to an admission: published ones only, the API refuses the rest. */
export type ItemOption = { id: string; titleEn: string; titleBn: string };

/** The published exams and packages to pick from. Each list reads the first 100 rows; the picker filters them by name. */
export function useItemOptions(enabled = true) {
  return useQuery({
    queryKey: [...offlineAdminKeys.all, "itemOptions"],
    queryFn: async () => {
      const [exams, modules] = await Promise.all([
        apiRequest<PageResponse<{ id: string; titleEn: string; titleBn: string; status: string }>>("/api/v1/live-exams?size=100"),
        apiRequest<PageResponse<{ id: string; titleEn: string; titleBn: string; status: string }>>("/api/v1/live-exam-modules?size=100"),
      ]);
      const published = (rows: { id: string; titleEn: string; titleBn: string; status: string }[]): ItemOption[] =>
        rows.filter((row) => row.status === "PUBLISHED").map(({ id, titleEn, titleBn }) => ({ id, titleEn, titleBn }));
      return { exams: published(exams.content), packages: published(modules.content) };
    },
    enabled,
    staleTime: 60_000,
  });
}

/** One write: sends the call, then refreshes every offline query so the admission, queue and counts agree. */
function useOfflineCall<TVars, TResult = AdmissionDetail>(build: (vars: TVars) => Call) {
  const queryClient = useQueryClient();
  return useMutation<TResult, Error, TVars>({
    mutationFn: (vars) => {
      const call = build(vars);
      return apiRequest<TResult>(call.path, { method: call.method, body: call.body });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: offlineAdminKeys.all }),
  });
}

export type SetupBody = {
  batchId?: string | null;
  monthlyFee?: string | number | null;
  admittedOn?: string | null;
  firstDueOn?: string | null;
  courseEndsOn?: string | null;
};

export function useSaveSetup(id: string) {
  return useOfflineCall<SetupBody>((body) => ({ path: `/api/v1/offline-admissions/${id}/setup`, method: "PATCH", body }));
}

export function useAddItem(id: string) {
  return useOfflineCall<{ scope: "EXAM" | "MODULE"; examId?: string; moduleId?: string }, ItemAdded>((body) => ({
    path: `/api/v1/offline-admissions/${id}/items`,
    method: "POST",
    body,
  }));
}

export function useRemoveItem(id: string) {
  return useOfflineCall<string>((itemId) => ({
    path: `/api/v1/offline-admissions/${id}/items/${itemId}`,
    method: "DELETE",
  }));
}

export function useRequestChanges(id: string) {
  return useOfflineCall<string>((reason) => ({
    path: `/api/v1/offline-admissions/${id}/request-changes`,
    method: "POST",
    body: { reason },
  }));
}

export function useMarkReady(id: string) {
  return useOfflineCall<void>(() => ({ path: `/api/v1/offline-admissions/${id}/ready`, method: "POST" }));
}

export function useBackToReview(id: string) {
  return useOfflineCall<void>(() => ({ path: `/api/v1/offline-admissions/${id}/back-to-review`, method: "POST" }));
}

export function useOnboard(id: string) {
  return useOfflineCall<void>(() => ({ path: `/api/v1/offline-admissions/${id}/onboard`, method: "POST" }));
}

export function useSaveTerms(id: string) {
  return useOfflineCall<{ monthlyFee?: string | number; courseEndsOn?: string }>((body) => ({
    path: `/api/v1/offline-admissions/${id}/terms`,
    method: "PATCH",
    body,
  }));
}

export function useCloseAdmission(id: string) {
  return useOfflineCall<string | null>((reason) => ({
    path: `/api/v1/offline-admissions/${id}/close`,
    method: "POST",
    body: { reason },
  }));
}

/** The signed paper: a PDF or a phone photo. The API checks the type and the size. */
export function useUploadSigned(id: string) {
  return useOfflineCall<File>((file) => {
    const body = new FormData();
    body.append("file", file);
    return { path: `/api/v1/offline-admissions/${id}/signed-form`, method: "PUT", body };
  });
}

export type PaymentBody = {
  amount: string;
  method: FeePayment["method"];
  reference?: string;
  paidOn: string;
  note?: string;
};

export function useRecordPayment(id: string) {
  return useOfflineCall<PaymentBody, FeePayment>((body) => ({
    path: `/api/v1/offline-admissions/${id}/payments`,
    method: "POST",
    body,
  }));
}

export function useApprovePayment() {
  return useOfflineCall<string, FeePayment>((paymentId) => ({
    path: `/api/v1/offline-fee-payments/${paymentId}/approve`,
    method: "POST",
  }));
}

export function useRejectPayment() {
  return useOfflineCall<{ id: string; reason: string }, FeePayment>(({ id, reason }) => ({
    path: `/api/v1/offline-fee-payments/${id}/reject`,
    method: "POST",
    body: { reason },
  }));
}

export type BatchBody = {
  nameEn?: string;
  nameBn?: string;
  startsOn?: string | null;
  endsOn?: string;
  defaultMonthlyFee?: string | number;
};

export function useCreateBatch() {
  return useOfflineCall<BatchBody, BatchView>((body) => ({ path: "/api/v1/offline-batches", method: "POST", body }));
}

export function useSaveBatch(id: string) {
  return useOfflineCall<BatchBody, BatchView>((body) => ({ path: `/api/v1/offline-batches/${id}`, method: "PATCH", body }));
}

export function useArchiveBatch(id: string) {
  return useOfflineCall<void, BatchView>(() => ({ path: `/api/v1/offline-batches/${id}/archive`, method: "POST" }));
}

export function useUploadRoutine(id: string) {
  return useOfflineCall<File, BatchView>((file) => {
    const body = new FormData();
    body.append("file", file);
    return { path: `/api/v1/offline-batches/${id}/routine-pdf`, method: "PUT", body };
  });
}

/** Opens a file that needs the bearer token (a PDF or a photo) in a new tab. The tab is opened first, so the browser allows it. */
export async function openAuthorizedFile(path: string): Promise<void> {
  const tab = window.open("", "_blank");
  const blob = await apiBlob(path);
  const url = URL.createObjectURL(blob);
  if (tab) {
    tab.location.href = url;
  } else {
    window.open(url, "_blank");
  }
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
