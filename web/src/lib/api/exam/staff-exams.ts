import { useEffect } from "react";
import { keepPreviousData, useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
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

/** Rows per request for the Exams page. The page then asks for the next page until the server has no more. */
export const EXAM_LIST_PAGE_SIZE = 200;

/**
 * The query string for one page of `GET /live-exams` (zero-based `page`). Empty filters are left out; `popular` and
 * `pending` are sent only when they are on.
 */
export function examListQuery(params: ExamListParams, page = 0): string {
  const query = new URLSearchParams({ page: String(page), size: String(EXAM_LIST_PAGE_SIZE) });
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

/** The page after `last`, or `null` when `last` is the final page (an empty result has no pages). */
export function nextExamListPage(last: Pick<PageResponse<unknown>, "page" | "totalPages">): number | null {
  return last.page + 1 < last.totalPages ? last.page + 1 : null;
}

/**
 * The exams the staff member can see, filtered on the server. Rows carry `packages[]` and `pendingChanges`. The
 * grouped views need every row of a package or category, so the hook asks for the next page as long as there is one.
 * Read the rows from `data.pages`.
 */
export function useExamList(params: ExamListParams) {
  const query = useInfiniteQuery({
    queryKey: staffExamKeys.list(params),
    queryFn: ({ pageParam }) => apiRequest<PageResponse<StaffExam>>(`/api/v1/live-exams?${examListQuery(params, pageParam)}`),
    initialPageParam: 0,
    getNextPageParam: (last) => nextExamListPage(last),
    placeholderData: keepPreviousData,
    staleTime: 30_000,
  });
  const { hasNextPage, isFetchingNextPage, isError, fetchNextPage } = query;
  useEffect(() => {
    if (hasNextPage && !isFetchingNextPage && !isError) {
      void fetchNextPage();
    }
  }, [hasNextPage, isFetchingNextPage, isError, fetchNextPage]);
  return query;
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
