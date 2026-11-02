"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
import type { AccountStatus, ExamType, PageResponse } from "@/lib/api/exam/types";

/*
 * Staff admin: access by hand, revoke, pending orders, answer release,
 * announcements, account status and the configuration checks (STAFF-ADMIN-UI).
 * Query keys start with "staffAdmin". Every route here is merged on the API.
 */

/* ---------- access ---------- */

/** How a student got access. `grantId` is null for roster rows, which have no grant to revoke. */
export type AccessVia = "ROSTER" | "MODULE_PURCHASE" | "EXAM_PURCHASE" | "MANUAL";

/** One row of `GET /api/v1/students/{id}/live-exam-access`. */
export type AdminAccessRow = {
  moduleId: string;
  moduleTitleEn: string;
  moduleTitleBn: string;
  via: AccessVia;
  exams: { id: string; titleEn: string; titleBn: string }[];
  orderId: string | null;
  tranId: string | null;
  grantId: string | null;
};

export type GrantScope = "MODULE" | "EXAM";

/** `POST /api/v1/live-exam-grants` body. `reason` is 1 to 500 characters. */
export type GrantRequest = {
  studentId: string;
  scope: GrantScope;
  moduleId?: string;
  examId?: string;
  reason: string;
};

export function useStudentAccessRows(studentId: string, page: number) {
  return useQuery({
    queryKey: staffAdminKeys.access(studentId, page),
    queryFn: ({ signal }) =>
      apiRequest<PageResponse<AdminAccessRow>>(
        `/api/v1/students/${studentId}/live-exam-access?page=${page}&size=50`,
        { signal },
      ),
    placeholderData: keepPreviousData,
    staleTime: 0,
  });
}

export function useGrantAccess(studentId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Omit<GrantRequest, "studentId">) =>
      apiRequest<unknown>("/api/v1/live-exam-grants", {
        method: "POST",
        body: { ...body, studentId },
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["staffAdmin", "access", studentId] }),
  });
}

/** Master Admin only. The reason is written to the grant and the audit log. */
export function useRevokeGrant(studentId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ grantId, reason }: { grantId: string; reason: string }) =>
      apiRequest<unknown>(
        `/api/v1/live-exam-grants/${grantId}?reason=${encodeURIComponent(reason.trim())}`,
        { method: "DELETE" },
      ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["staffAdmin", "access", studentId] }),
  });
}

/* ---------- pending orders ---------- */

/** One row of `GET /api/v1/live-exam-orders`. `amount` is taka. */
export type PendingOrder = {
  orderId: string;
  tranId: string;
  status: string;
  amount: number;
  scope: string;
  moduleTitleEn: string | null;
  moduleTitleBn: string | null;
  examTitleEn: string | null;
  examTitleBn: string | null;
  studentId: string;
  studentName: string;
  studentPhone: string | null;
  studentCode: string | null;
  createdAt: string;
};

export type PendingOrderFilters = { page: number; size: number; tranId: string };

export function usePendingOrders(filters: PendingOrderFilters) {
  return useQuery({
    queryKey: staffAdminKeys.orders(filters),
    queryFn: ({ signal }) =>
      apiRequest<PageResponse<PendingOrder>>(
        `/api/v1/live-exam-orders${queryString({
          status: "INITIATED",
          tranId: filters.tranId.trim(),
          page: filters.page,
          size: filters.size,
        })}`,
        { signal },
      ),
    placeholderData: keepPreviousData,
    staleTime: 0,
  });
}

/** Confirm (Master Admin only): the payment succeeded at SSLCommerz. */
export function useConfirmOrder() {
  return useOrderDecision("confirm");
}

/** Reject (Master Admin only): the order becomes FAILED with the reason. */
export function useRejectOrder() {
  return useOrderDecision("reject");
}

function useOrderDecision(action: "confirm" | "reject") {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ orderId, reason }: { orderId: string; reason: string }) =>
      apiRequest<unknown>(`/api/v1/live-exam-orders/${orderId}/${action}`, {
        method: "POST",
        body: { reason: reason.trim() },
      }),
    // Also on failure: ORDER_NOT_PENDING means the list is stale, and the copy says it reloads.
    onSettled: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: ["staffAdmin", "orders"] }),
        // The sidebar's count of payments waiting for a decision.
        queryClient.invalidateQueries({ queryKey: ["staffHome"] }),
      ]),
  });
}

/* ---------- answer release ---------- */

/** The staff view of one exam: what the answer-release switch needs. */
export type ExamAnswerRelease = {
  id: string;
  examType: ExamType;
  titleEn: string;
  titleBn: string;
  /** ISO UTC, or `null` while the answers are withheld. */
  answersReleasedAt: string | null;
};

export function useExamAnswerRelease(examId: string) {
  return useQuery({
    queryKey: staffAdminKeys.release(examId),
    queryFn: ({ signal }) => apiRequest<ExamAnswerRelease>(`/api/v1/live-exams/${examId}`, { signal }),
    staleTime: 0,
  });
}

export function useSetAnswersReleased(examId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (released: boolean) =>
      apiRequest<{ examId: string; released: boolean; releasedAt: string | null }>(
        `/api/v1/live-exams/${examId}/answers-released`,
        { method: "PUT", body: { released } },
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: staffAdminKeys.release(examId) });
      void queryClient.invalidateQueries({ queryKey: ["staffCatalog", "exam", examId] });
    },
  });
}

/* ---------- announcements ---------- */

/** Who an announcement goes to. `MODULE` takes `moduleId`, `EXAM` takes `examId`, `SELECTED` takes `studentIds`. */
export type AnnouncementAudience = "ALL" | "MODULE" | "EXAM" | "SELECTED";

/** The audiences the preview counts. `SELECTED` has no preview: its count is the length of the list. */
export type PreviewAudience = Exclude<AnnouncementAudience, "SELECTED">;

/** `POST /api/v1/announcements` body. Titles and bodies may be blank, not all four. Only the target the audience needs is sent. */
export type AnnouncementRequest = {
  titleEn: string;
  titleBn: string;
  bodyEn: string;
  bodyBn: string;
  audience: AnnouncementAudience;
  moduleId?: string;
  examId?: string;
  /** 1 to 500 active students. Only for `SELECTED`. */
  studentIds?: string[];
};

/** The send response. The server leaves out null fields, so a missing target means null. */
export type AnnouncementView = {
  id: string;
  audience: AnnouncementAudience;
  moduleId?: string | null;
  examId?: string | null;
  recipients: number;
  createdAt: string;
};

/**
 * One row of `GET /api/v1/announcements` (Master Admin and System Admin). The body is not returned. The target fields
 * are set for their own audience only, and are left out by the server when null.
 */
export type AnnouncementHistoryRow = {
  id: string;
  titleEn: string;
  titleBn: string;
  audience: AnnouncementAudience;
  moduleId?: string | null;
  moduleTitleEn?: string | null;
  moduleTitleBn?: string | null;
  examId?: string | null;
  examTitleEn?: string | null;
  examTitleBn?: string | null;
  createdAt: string;
  recipients: number;
  withdrawn: boolean;
  withdrawnAt?: string | null;
};

/** The target a preview counts: a package for `MODULE`, an exam for `EXAM`, nothing for `ALL`. */
export type AnnouncementPreviewParams = { audience: PreviewAudience; moduleId?: string; examId?: string };

const HISTORY_PAGE_SIZE = 20;

/** Sent announcements, newest first, optionally only one audience. Master Admin and System Admin. */
export function useAnnouncementHistory(page: number, audience?: AnnouncementAudience) {
  return useQuery({
    queryKey: staffAdminKeys.announcements(page, audience),
    queryFn: ({ signal }) =>
      apiRequest<PageResponse<AnnouncementHistoryRow>>(
        `/api/v1/announcements${queryString({ page, size: HISTORY_PAGE_SIZE, audience })}`,
        { signal },
      ),
    placeholderData: keepPreviousData,
    staleTime: 0,
  });
}

/**
 * How many students the audience reaches now, for the confirm dialog. Asked only while `enabled`. A failed count is not
 * retried, so the dialog can say the count is unknown at once; sending is still allowed.
 */
export function useAnnouncementPreview(params: AnnouncementPreviewParams, enabled: boolean) {
  return useQuery({
    queryKey: staffAdminKeys.announcementPreview(params),
    queryFn: ({ signal }) =>
      apiRequest<{ recipients: number }>(
        `/api/v1/announcements/preview${queryString({
          audience: params.audience,
          moduleId: params.moduleId,
          examId: params.examId,
        })}`,
        { signal },
      ),
    enabled,
    retry: false,
    staleTime: 0,
  });
}

/** Sends an announcement. A sent one is never edited, so the only thing to refresh is the history. */
export function useSendAnnouncement() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: AnnouncementRequest) =>
      apiRequest<AnnouncementView>("/api/v1/announcements", { method: "POST", body }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: staffAdminKeys.announcementHistory }),
  });
}

/**
 * Withdraws a sent announcement: it leaves the pages and the Notices list, and the record stays. The server answers
 * with the row; that row replaces the one in the history at once, and the history is then reloaded.
 */
export function useWithdrawAnnouncement() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      apiRequest<AnnouncementHistoryRow>(`/api/v1/announcements/${id}/withdraw`, { method: "PUT" }),
    onSuccess: (row) => {
      queryClient.setQueriesData<PageResponse<AnnouncementHistoryRow>>(
        { queryKey: staffAdminKeys.announcementHistory },
        (current) =>
          current && {
            ...current,
            content: current.content.map((item) => (item.id === row.id ? row : item)),
          },
      );
      void queryClient.invalidateQueries({ queryKey: staffAdminKeys.announcementHistory });
    },
  });
}

/* ---------- accounts ---------- */

export type AccountKind = "students" | "instructors";

/** Suspend or restore a student or an instructor. Master Admin only. */
export function useSetAccountStatus(kind: AccountKind) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: Exclude<AccountStatus, "PENDING_ACTIVATION"> }) =>
      apiRequest<void>(`/api/v1/${kind}/${id}/status`, { method: "PATCH", body: { status } }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["staffPeople"] }),
  });
}

/** Delete a student or an instructor. Master Admin only; refused with 409 when work or payments exist. */
export function useDeleteAccount(kind: AccountKind) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiRequest<void>(`/api/v1/${kind}/${id}`, { method: "DELETE" }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["staffPeople"] }),
  });
}

/* ---------- configuration checks ---------- */

/** One check of `GET /api/v1/system-status`. The API never sends a value, only the result. */
export type SystemCheck = { name: string; ok: boolean; note: string };

export function useSystemStatus() {
  return useQuery({
    queryKey: staffAdminKeys.systemStatus,
    queryFn: ({ signal }) => apiRequest<{ checks: SystemCheck[] }>("/api/v1/system-status", { signal }),
    staleTime: 0,
  });
}

/* ---------- errors and keys ---------- */

/**
 * Every code the admin routes can return: the grant, order, announcement, account
 * and system status services, and the global handler that answers for all routes.
 * Each has its own message; `tests/staff-admin/copy.test.ts` fails if one is missing.
 */
const ERROR_KEYS: Record<string, string> = {
  GRANT_ALREADY_ACTIVE: "errors.grantAlreadyActive",
  ORDER_NOT_PENDING: "errors.orderNotPending",
  ACCOUNT_HAS_PAID_ORDERS: "errors.accountHasPaidOrders",
  ACCOUNT_HAS_MARKED_WORK: "errors.accountHasMarkedWork",
  MASTER_ADMIN_PROTECTED: "errors.masterAdminProtected",
  RESOURCE_NOT_FOUND: "errors.notFound",
  ACCESS_DENIED: "errors.accessDenied",
  UNAUTHENTICATED: "errors.unauthenticated",
  VALIDATION_FAILED: "errors.validation",
  MALFORMED_REQUEST: "errors.malformed",
  METHOD_NOT_ALLOWED: "errors.methodNotAllowed",
  UNSUPPORTED_MEDIA_TYPE: "errors.unsupportedMedia",
  INTERNAL_ERROR: "errors.internal",
};

/**
 * The `staffAdmin` message key for an API error code. An unknown code gets the
 * generic message, never the raw code.
 */
export function staffAdminErrorKey(code: string): string {
  return ERROR_KEYS[code] ?? "errors.generic";
}

export const staffAdminKeys = {
  access: (studentId: string, page: number) => ["staffAdmin", "access", studentId, page] as const,
  announcementHistory: ["staffAdmin", "announcements"] as const,
  announcements: (page: number, audience?: AnnouncementAudience) =>
    ["staffAdmin", "announcements", page, audience ?? "all"] as const,
  announcementPreview: (params: AnnouncementPreviewParams) => ["staffAdmin", "announcementPreview", params] as const,
  orders: (filters: PendingOrderFilters) => ["staffAdmin", "orders", filters] as const,
  release: (examId: string) => ["staffAdmin", "release", examId] as const,
  systemStatus: ["staffAdmin", "systemStatus"] as const,
};

/** Builds a query string, leaving out empty values. */
function queryString(params: Record<string, string | number | boolean | null | undefined>): string {
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
