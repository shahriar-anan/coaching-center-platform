"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiBlob, apiRequest } from "@/lib/api/client";
import { ApiError } from "@/lib/api/errors";
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

/** Where an application came from: the public form (Website), a signed-in student (Account), or the desk (Desk). */
export type AdmissionSource = "PUBLIC" | "ACCOUNT" | "DESK";

/** A batch the student already has access to (plan P14), with both names. */
export type AccessibleBatch = { nameEn: string; nameBn: string };

/**
 * The refusal of a desk start for a phone that already has an open offline admission (plan P13). The API answers 409 OPEN_ADMISSION and
 * names the batch in `details`. Returns null for any other error; `batch` is null when the answer names no batch.
 */
export function openAdmissionRefusal(error: unknown): { batch: AccessibleBatch | null } | null {
  if (!(error instanceof ApiError) || error.code !== "OPEN_ADMISSION") {
    return null;
  }
  const details = error.details as Partial<{ batchNameEn: unknown; batchNameBn: unknown }> | null | undefined;
  if (!details || typeof details.batchNameEn !== "string") {
    return { batch: null };
  }
  const nameBn = typeof details.batchNameBn === "string" ? details.batchNameBn : details.batchNameEn;
  return { batch: { nameEn: details.batchNameEn, nameBn } };
}

/** The account an applicant without one matches at review (plan Q3), by email then phone. */
/** The account an applicant with no account is matched to (by email, then phone). Joining it is the onboarding, not a new account. */
export type MatchedAccount = { id: string; fullName: string; email: string | null };

export type AdmissionQueueRow = {
  id: string;
  studentId: string | null;
  /** Website, Account or Desk. */
  source: AdmissionSource;
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
  /** Waiting for the form only: the student has used the activation code. */
  activated: boolean;
  /** Waiting for the form only: when the activation code stops working, or null when there is none. */
  codeExpiresAt: string | null;
  /** The student's account was deleted after they applied (16 Nov). Staff close or delete the admission. */
  accountDeleted?: boolean;
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
  /** The total the student pays for the batch (course-fee-plan.md). */
  courseFee: number | string | null;
  /** The number of monthly instalments, chosen by the admin. */
  installments: number | null;
  /** Each instalment the onboarding writes: number, date and amount. Empty until the fee, the count and the first due date are set. */
  installmentPlan?: InstallmentPreview[];
  /** True when the admin set the instalments one by one (E1); false for the even split of the course fee. */
  customPlan?: boolean;
  admittedOn: string | null;
  firstDueOn: string | null;
  courseEndsOn: string | null;
};

/** One instalment of a course fee. Money comes as a number or a decimal string. */
export type InstallmentPreview = { seq: number; dueOn: string; amount: number | string };

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
  /** Null for an application from someone who has no account yet (until onboarding). */
  studentId: string | null;
  /** Website, Account or Desk. */
  source: AdmissionSource;
  /** The account the applicant matches by email (plan Q3, 13 Nov D3), as the API sends it: its name and email, or null. */
  matchedName: string | null;
  matchedEmail: string | null;
  /** True when the matched account already has an open admission for this batch: onboarding it again is refused. */
  matchedHasBatch: boolean;
  /** The batches the student already has access to (plan P14). Empty when there are none. */
  alreadyAccessTo: AccessibleBatch[];
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
  /** The student's roll number, once onboarded (rule R4). */
  rollNo?: string | null;
  /** When the join or activation code went out, or null when none has. */
  joinCodeSentAt: string | null;
  /** True once the matched or linked account is set (the form's email is that account's). */
  matchedLinked: boolean;
  /** Set when the form's phone belongs to another account: staff are warned and onboarding is refused (13 Nov D3). */
  phoneOwnerName: string | null;
  /**
   * Another person whose open admission in this batch has the same mobile or email (16 Nov). Only then is onboarding refused. Absent
   * when there is none, or once onboarded.
   */
  sameBatchConflictName?: string | null;
  /** True while a join code waits for the student to join. Onboarding is not offered again then. */
  joinPending: boolean;
  /** The student's account was deleted after they applied (16 Nov). Staff close or delete the admission. */
  accountDeleted?: boolean;
  /** Set when the admission was deleted from its batch but its payments or results keep it (V105). */
  deletedAt?: string | null;
  deletedReason?: string | null;
  /** The roll it gave up when it was deleted. */
  releasedRoll?: string | null;
  /** True when a new code can be sent (a join code, or a new activation code for an account not yet activated). */
  canResendCode: boolean;
  /** The code that was sent (join or activation), while it can still be used, so staff can read it out (V101). */
  sentCode?: string | null;
  /**
   * Whether Onboard sends a join code to a student who has an account. False since 24 Nov (client): Onboard admits them at once, and
   * a code sent before is cancelled. Absent from an older API, which always sent one.
   */
  joinCodeOnOnboard?: boolean;
};

/**
 * True while Onboard must wait for the student to join with the code that was sent. With join codes off (24 Nov) it never waits: the
 * Master Admin onboards, which admits the student and cancels the code.
 */
export function waitsForJoin(admission: Pick<AdmissionDetail, "joinPending" | "joinCodeOnOnboard">): boolean {
  return admission.joinPending && admission.joinCodeOnOnboard !== false;
}

export type ItemAdded = { admission: AdmissionDetail; overlaps: ItemOverlap[]; alreadyHeld: boolean };

/** The answer to a desk start: the student exists, and this is the one-time code to read out (10 minutes). */
export type DeskStart = {
  admissionId: string;
  studentId: string;
  phone: string;
  code: string;
  expiresAt: string;
};

/**
 * The activation code of a desk admission. While live: the code. Expired: no code, `expired` is true. Activated: no code, the student
 * has used it. `code` and `expiresAt` are null when there is none.
 */
export type ActivationCodeView = {
  code: string | null;
  expiresAt: string | null;
  activated: boolean;
  expired?: boolean;
};

/** The tab and dashboard counts (plan 5). */
export type OfflineCounts = {
  awaitingForm: number;
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
  /** The total a student pays for the batch. */
  courseFee: number | string;
  /** The number of monthly instalments a new admission starts with. */
  defaultInstallments: number;
  status: "ACTIVE" | "ARCHIVED";
  descriptionEn: string | null;
  descriptionBn: string | null;
  /** True when a photo is stored; the photo is read with the staff token. */
  photoUploaded: boolean;
  /** The roll the batch's first student gets (rule R5). Null when none is set. */
  rollStart?: string | null;
  /** The batch's WhatsApp group or channel link, or null (15 Nov). Staff only; students get it once onboarded. */
  whatsappUrl?: string | null;
  /** When the photo was last uploaded; the staff photo address carries it, so a new photo shows at once. */
  photoUploadedAt?: string | null;
  /** True once the Master Admin published the batch (V103); a draft is hidden from students. */
  published?: boolean;
  publishedAt?: string | null;
  /** Off shows "Admissions closed" on the public cards and refuses new applications. */
  admissionsOpen: boolean;
  /** The order of the batches on the cards and in lists; lower first. */
  displayOrder: number;
  studentCount: number;
  routineUploaded: boolean;
  routineFileName: string | null;
  routineUploadedAt: string | null;
  /** Who uploaded the routine, or null when none is uploaded. */
  routineUploadedBy: string | null;
  /** How many students the last routine upload told. Null when it has not been sent. */
  studentsNotified: number | null;
  /** The default length of a class, in minutes, for a routine time that gives only a start. */
  defaultClassMinutes: number;
};

export type BatchStudent = {
  admissionId: string;
  studentId: string;
  fullName: string;
  mobile: string;
  status: AdmissionState;
  /** The student's roll number, once onboarded. Missing or null means none yet. */
  roll?: string | null;
};

// ---- reads -------------------------------------------------------------------------------------------------------------

export type QueueFilters = {
  status?: string;
  batchId?: string;
  source?: string;
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
          source: filters.source,
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

/**
 * The activation code of a desk admission that has not been activated yet. Read each time the code dialog opens, so the code shown is the
 * live one. Pass `enabled: false` when the dialog already holds a code.
 */
export function useActivationCode(id: string, enabled = true) {
  return useQuery({
    queryKey: [...offlineAdminKeys.all, "activationCode", id],
    queryFn: ({ signal }) => apiRequest<ActivationCodeView>(`/api/v1/offline-admissions/${id}/activation-code`, { signal }),
    enabled: enabled && Boolean(id),
    staleTime: 0,
    gcTime: 0,
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
          if (row.studentId) {
            byStudent.set(row.studentId, { admissionId: row.id, status: row.status });
          }
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

/**
 * Starts a desk admission from the phone number alone: the student is made, and the code comes back to read out. The student fills in
 * the rest of the form after activating. The API refuses a phone already in use (DUPLICATE_PHONE).
 */
export function useDeskStart() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (phone: string) => apiRequest<DeskStart>("/api/v1/offline-admissions/desk", { method: "POST", body: { phone } }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: offlineAdminKeys.all });
      void queryClient.invalidateQueries({ queryKey: ["staffPeople"] });
    },
  });
}

/** A new 10-minute code for a desk admission. The old code stops working at once. */
export function useRenewActivationCode(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiRequest<{ code: string; expiresAt: string }>(`/api/v1/offline-admissions/${id}/activation-code`, { method: "POST" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: offlineAdminKeys.all });
    },
  });
}

export type SetupBody = {
  batchId?: string | null;
  courseFee?: string | number | null;
  installments?: number | null;
  /** The admin's own instalments (E1). Sent instead of the fee and count; the course fee is their sum. */
  installmentPlan?: { dueOn: string; amount: string }[];
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
  return useOfflineCall<{ rollNo: string }>((body) => ({ path: `/api/v1/offline-admissions/${id}/onboard`, method: "POST", body }));
}

/** The suggested roll of the admission's batch (rule A7): the next free one, or null when the batch has none to start from. */
export function useNextRoll(id: string, enabled = true) {
  return useQuery({
    queryKey: [...offlineAdminKeys.all, "next-roll", id],
    queryFn: ({ signal }) => apiRequest<{ suggestion: string | null }>(`/api/v1/offline-admissions/${id}/next-roll`, { signal }),
    enabled,
    staleTime: 0,
  });
}

/** A new code for an onboarded admission (plan P8): the join code or the activation code, whichever it was. The old one stops working. */
export function useResendCode(id: string) {
  return useOfflineCall<void>(() => ({ path: `/api/v1/offline-admissions/${id}/resend-code`, method: "POST" }));
}

/** Changes the roll of an onboarded admission (plan O3). Only the Master Admin; the API refuses it for any other admission state. */
export function useChangeRoll(id: string) {
  return useOfflineCall<{ rollNo: string }>((body) => ({ path: `/api/v1/offline-admissions/${id}/roll`, method: "PATCH", body }));
}

export function useSaveTerms(id: string) {
  return useOfflineCall<{ courseFee?: string | number; installments?: number; courseEndsOn?: string }>((body) => ({
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

/**
 * Deletes the admission from its batch so its roll is free (16 Nov, issue 2; the Master Admin). An admission with no payments and no
 * results is removed; one with them stays as a stub that no list shows. The page goes back to the list afterwards.
 */
export function useDeleteFromBatch(id: string) {
  return useOfflineCall<string, void>((reason) => ({
    path: `/api/v1/offline-admissions/${id}/delete-from-batch`,
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
  rollStart?: string | null;
  /** The batch's WhatsApp group or channel link, or null (15 Nov). Staff only; students get it once onboarded. */
  whatsappUrl?: string | null;
  nameEn?: string;
  nameBn?: string;
  startsOn?: string | null;
  endsOn?: string;
  courseFee?: string | number;
  defaultInstallments?: number;
  descriptionEn?: string | null;
  descriptionBn?: string | null;
  admissionsOpen?: boolean;
  displayOrder?: number;
};

/** The batch's id is given at each call, because a batch being created has none until it is saved. */
function useBatchSubcall<TVars, TResult>(build: (vars: TVars) => { id: string } & Call) {
  const queryClient = useQueryClient();
  return useMutation<TResult, Error, TVars>({
    mutationFn: (vars) => {
      const call = build(vars);
      return apiRequest<TResult>(call.path, { method: call.method, body: call.body });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: offlineAdminKeys.all }),
  });
}

/** Uploads the batch photo. The server re-encodes it, the same as the profile photo. */
export function useUploadBatchPhoto() {
  return useBatchSubcall<{ id: string; file: File }, BatchView>(({ id, file }) => {
    const body = new FormData();
    body.append("file", file);
    return { id, path: `/api/v1/offline-batches/${id}/photo`, method: "PUT", body };
  });
}

export function useRemoveBatchPhoto() {
  return useBatchSubcall<{ id: string }, BatchView>(({ id }) => ({ id, path: `/api/v1/offline-batches/${id}/photo`, method: "DELETE" }));
}

export function useCreateBatch() {
  return useOfflineCall<BatchBody, BatchView>((body) => ({ path: "/api/v1/offline-batches", method: "POST", body }));
}

export function useSaveBatch(id: string) {
  return useOfflineCall<BatchBody, BatchView>((body) => ({ path: `/api/v1/offline-batches/${id}`, method: "PATCH", body }));
}

/** Publishes a draft batch (Master Admin, V103): students see it and admissions can choose it. */
export function usePublishBatch(id: string) {
  return useOfflineCall<void, BatchView>(() => ({ path: `/api/v1/offline-batches/${id}/publish`, method: "POST" }));
}

/** Moves a published batch back to draft (Master Admin, V103), so its form can be edited. */
export function useUnpublishBatch(id: string) {
  return useOfflineCall<void, BatchView>(() => ({ path: `/api/v1/offline-batches/${id}/unpublish`, method: "POST" }));
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

/** The batch photo for the staff screens, with the upload time so a new photo is fetched at once (the old one is not reused). */
export function batchPhotoPath(batch: Pick<BatchView, "id" | "photoUploadedAt">): string {
  const version = batch.photoUploadedAt ? `?v=${encodeURIComponent(batch.photoUploadedAt)}` : "";
  return `/api/v1/offline-batches/${batch.id}/photo${version}`;
}
