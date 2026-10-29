import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
import type { PageResponse } from "@/lib/api/exam/types";
import type { StaffExam } from "@/lib/api/exam/staff-catalog";

/** One exam or package with a staged change, as the sidebar and the Exams page list it. */
export type PendingChangeItem = {
  kind: "EXAM" | "PACKAGE";
  id: string;
  titleEn: string | null;
  titleBn: string | null;
  updatedAt: string;
  updatedBy: string | null;
};

export type PendingChanges = {
  items: PendingChangeItem[];
};

export const staffExamKeys = {
  pendingChanges: ["staff-exams", "pending-changes"] as const,
  list: (params: ExamListParams) => ["staff-exams", "list", examListQuery(params)] as const,
};

/** The filters of the Exams page. Each one is optional; the URL holds the same names. */
export type ExamListParams = {
  packageId?: string;
  categoryId?: string;
  type?: string;
  mode?: string;
  status?: string;
  phase?: string;
  popular?: boolean;
  pending?: boolean;
  q?: string;
};

/**
 * The query string for `GET /live-exams`. Empty filters are left out; `popular` and `pending` are sent
 * only when they are on. The page asks for up to 200 rows; groups are formed from the rows it gets.
 */
export function examListQuery(params: ExamListParams): string {
  const query = new URLSearchParams({ size: "200" });
  for (const key of ["packageId", "categoryId", "type", "mode", "status", "phase"] as const) {
    const value = params[key];
    if (value) {
      query.set(key, value);
    }
  }
  const q = params.q?.trim();
  if (q) {
    query.set("q", q);
  }
  if (params.popular) {
    query.set("popular", "true");
  }
  if (params.pending) {
    query.set("pending", "true");
  }
  return query.toString();
}

/** The exams the staff member can see, filtered on the server. Rows carry `packages[]` and `pendingChanges`. */
export function useExamList(params: ExamListParams) {
  return useQuery({
    queryKey: staffExamKeys.list(params),
    queryFn: () => apiRequest<PageResponse<StaffExam>>(`/api/v1/live-exams?${examListQuery(params)}`),
    placeholderData: keepPreviousData,
    staleTime: 30_000,
  });
}

/** After a row action, the Exams list and the catalog's exam lists are read again. */
function useRefreshExams() {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ["staff-exams", "list"] }),
      queryClient.invalidateQueries({ queryKey: ["staffCatalog", "moduleExams"] }),
    ]);
}

/** A copy of the exam, made as a new draft (its id comes back in the result). */
export function useCopyListedExam() {
  const refresh = useRefreshExams();
  return useMutation({
    mutationFn: (examId: string) => apiRequest<StaffExam>(`/api/v1/live-exams/${examId}/copy`, { method: "POST", body: {} }),
    onSuccess: refresh,
  });
}

/** Withdraws a published exam from students. Its results are kept. Master Admin only. */
export function useWithdrawListedExam() {
  const refresh = useRefreshExams();
  return useMutation({
    mutationFn: (examId: string) => apiRequest<StaffExam>(`/api/v1/live-exams/${examId}/archive`, { method: "POST", body: {} }),
    onSuccess: refresh,
  });
}

/** Deletes a draft exam. */
export function useDeleteListedExam() {
  const refresh = useRefreshExams();
  return useMutation({
    mutationFn: (examId: string) => apiRequest<void>(`/api/v1/live-exams/${examId}`, { method: "DELETE" }),
    onSuccess: refresh,
  });
}

/**
 * Every exam and package with a staged change, newest first. Only the Master Admin and the System
 * Admin may read it, so the caller turns it on for those roles only. The sidebar shows the count.
 */
export function usePendingChanges(enabled: boolean) {
  return useQuery({
    queryKey: staffExamKeys.pendingChanges,
    queryFn: () => apiRequest<PendingChanges>("/api/v1/live-exam-pending-changes"),
    enabled,
    staleTime: 30_000,
  });
}
