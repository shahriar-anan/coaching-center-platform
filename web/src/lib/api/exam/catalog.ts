"use client";

import { useInfiniteQuery, useQueries, useQuery } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
import { ApiError } from "@/lib/api/errors";
import type {
  CategoryExam,
  CategoryNode,
  ExamType,
  MyStatus,
  PageResponse,
  PublicExam,
  PublicModule,
  StudentExam,
} from "@/lib/api/exam/types";

export type {
  CategoryNode,
  ExamType,
  MyStatus,
  PageResponse,
  PaperLanguage,
  PublicExam,
  PublicModule,
  StudentExam,
} from "@/lib/api/exam/types";

/** A module the student may sit (roster or module purchase), from the student read. */
export interface StudentModule {
  id: string;
  categoryId: string;
  titleEn: string;
  titleBn: string;
  descriptionEn: string | null;
  descriptionBn: string | null;
  status: string;
  position: number;
  price: number | null;
}

/** One row of `GET /api/v1/me/exam-routine`. */
export interface RoutineRow {
  id: string;
  moduleId: string;
  examType: ExamType;
  titleEn: string;
  titleBn: string;
  moduleTitleEn: string;
  moduleTitleBn: string;
  categories: { id: string; titleEn: string; titleBn: string }[];
  /** Null until the start is announced; the row stays on the routine with its status. */
  liveAt: string | null;
  endAt: string | null;
  durationMinutes: number;
  myStatus: MyStatus | null;
  retakeAvailable: boolean | null;
}

const CATALOG_STALE = 60_000;

export const catalogKeys = {
  all: ["catalog"] as const,
  tree: (examType: ExamType | null) => ["catalog", "tree", examType ?? "ALL"] as const,
  publicModule: (id: string) => ["catalog", "public-module", id] as const,
  publicExams: (moduleId: string, examType: ExamType | null) =>
    ["catalog", "public-exams", moduleId, examType ?? "ALL"] as const,
  publicExam: (id: string) => ["catalog", "public-exam", id] as const,
  myModules: () => ["catalog", "my-modules"] as const,
  myAccess: () => ["catalog", "my-access"] as const,
  myExams: (moduleId: string, examType: ExamType | null) =>
    ["catalog", "my-exams", moduleId, examType ?? "ALL"] as const,
  myExam: (id: string) => ["catalog", "my-exam", id] as const,
  routine: () => ["catalog", "routine"] as const,
  categoryExams: (categoryId: string, examType: ExamType, branch: boolean) =>
    ["catalog", "category-exams", categoryId, examType, branch] as const,
};

/** The category exam list's page size (NR-01, S2.1). */
export const CATEGORY_EXAM_PAGE = 20;

/** The URL of one page of a category exam list. `branch` adds the exams of every subcategory below it. */
export function categoryExamsUrl(categoryId: string, examType: ExamType, branch: boolean, page: number): string {
  const params = new URLSearchParams({
    examType,
    branch: String(branch),
    page: String(page),
    size: String(CATEGORY_EXAM_PAGE),
  });
  return `/api/v1/public/live-exam-categories/${categoryId}/exams?${params.toString()}`;
}

function typeQuery(examType: ExamType | null, extra: Record<string, string> = {}): string {
  const params = new URLSearchParams(extra);
  if (examType) params.set("examType", examType);
  const text = params.toString();
  return text ? `?${text}` : "";
}

/**
 * The WS1 type filter is merged, so this only matters against an older API
 * that answers `400` for an unknown `examType`: ask again without it. The
 * exam lists are also filtered by type on the client, so either way is right.
 */
async function withTypeFallback<T>(
  examType: ExamType | null,
  load: (type: ExamType | null) => Promise<T>,
): Promise<T> {
  try {
    return await load(examType);
  } catch (error) {
    if (examType && error instanceof ApiError && error.code === "VALIDATION_FAILED") {
      return load(null);
    }
    throw error;
  }
}

/** 404 and 403 on a student read mean "no access", not an error. */
async function orNull<T>(promise: Promise<T>): Promise<T | null> {
  try {
    return await promise;
  } catch (error) {
    if (
      error instanceof ApiError &&
      (error.code === "RESOURCE_NOT_FOUND" || error.code === "ACCESS_DENIED")
    ) {
      return null;
    }
    throw error;
  }
}

export function useCategoryTree(examType: ExamType | null) {
  return useQuery({
    queryKey: catalogKeys.tree(examType),
    staleTime: CATALOG_STALE,
    queryFn: ({ signal }) =>
      withTypeFallback(examType, (type) =>
        apiRequest<CategoryNode[]>(`/api/v1/public/live-exam-categories${typeQuery(type)}`, {
          signal,
        }),
      ),
  });
}

/**
 * `signedIn` is part of the key so the read runs again once the session is restored. A page that
 * loads before that gets the guest view (no access block) and would keep it until it went stale.
 */
export function usePublicModule(id: string, signedIn = false) {
  return useQuery({
    queryKey: [...catalogKeys.publicModule(id), signedIn],
    staleTime: CATALOG_STALE,
    queryFn: ({ signal }) =>
      apiRequest<PublicModule>(`/api/v1/public/live-exam-modules/${id}`, { signal }),
  });
}

export function usePublicModuleExams(moduleId: string | undefined, examType: ExamType | null, signedIn = false) {
  return useQuery({
    queryKey: [...catalogKeys.publicExams(moduleId ?? "", examType), signedIn],
    staleTime: CATALOG_STALE,
    enabled: Boolean(moduleId),
    queryFn: ({ signal }) =>
      withTypeFallback(examType, (type) =>
        apiRequest<PageResponse<PublicExam>>(
          `/api/v1/public/live-exam-modules/${moduleId}/exams${typeQuery(type, { size: "100" })}`,
          { signal },
        ),
      ).then((page) =>
        examType ? page.content.filter((exam) => exam.examType === examType) : page.content,
      ),
  });
}

/** An exam in the "upcoming" strip, with its module's titles (G12). */
/** `moduleOnly` (S-027, A1): true when the exam has no price of its own and is sold only in its module. */
export type UpcomingExam = PublicExam & {
  moduleTitleEn?: string;
  moduleTitleBn?: string | null;
  moduleOnly?: boolean;
};

/**
 * Published modules matching `q` (G12). Only runs once there is text. An older
 * API that ignores `q` returns everything, so the screen filters by title too.
 */
/**
 * The exams of one category, a page at a time (NR-01, S2.1). `branch` on lists every subcategory below it; off lists only the
 * category's own exams. Changing either setting starts a new list.
 */
export function useCategoryExams(categoryId: string, examType: ExamType, branch: boolean) {
  return useInfiniteQuery({
    queryKey: catalogKeys.categoryExams(categoryId, examType, branch),
    initialPageParam: 0,
    staleTime: CATALOG_STALE,
    queryFn: ({ pageParam, signal }) =>
      apiRequest<PageResponse<CategoryExam>>(categoryExamsUrl(categoryId, examType, branch, pageParam), { signal }),
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  });
}

export function useModuleSearch(q: string) {
  const term = q.trim();
  return useQuery({
    queryKey: ["catalog", "module-search", term] as const,
    enabled: term.length > 0,
    staleTime: CATALOG_STALE,
    queryFn: ({ signal }) =>
      apiRequest<PageResponse<PublicModule>>(
        `/api/v1/public/live-exam-modules?q=${encodeURIComponent(term)}&size=20`,
        { signal },
      ).then((page) => page.content),
  });
}

/** The soonest upcoming exams of one type (G12). Empty until the API has the endpoint. */
export function useUpcomingExams(examType: ExamType) {
  return useQuery({
    queryKey: ["catalog", "upcoming", examType] as const,
    staleTime: 60_000,
    queryFn: async ({ signal }) => {
      try {
        const page = await apiRequest<PageResponse<UpcomingExam>>(
          `/api/v1/public/live-exams?examType=${examType}&upcoming=true&size=5`,
          { signal },
        );
        return page.content;
      } catch (error) {
        if (error instanceof ApiError && error.code === "RESOURCE_NOT_FOUND") return [];
        throw error;
      }
    },
  });
}

/**
 * Whether checkout is on (`GET /api/v1/public/exam-site/status`, cached a minute). An older
 * API without the endpoint answers 404: then checkout counts as on and no notice shows.
 */
export function useCheckoutEnabled() {
  return useQuery({
    queryKey: ["catalog", "exam-site-status"] as const,
    staleTime: 60_000,
    queryFn: async ({ signal }) => {
      try {
        const status = await apiRequest<{ checkoutEnabled: boolean }>("/api/v1/public/exam-site/status", { signal });
        return status.checkoutEnabled;
      } catch (error) {
        if (error instanceof ApiError && error.code === "RESOURCE_NOT_FOUND") return true;
        throw error;
      }
    },
  });
}

export function usePublicExam(id: string, signedIn = false) {
  return useQuery({
    queryKey: [...catalogKeys.publicExam(id), signedIn],
    staleTime: CATALOG_STALE,
    queryFn: ({ signal }) => apiRequest<PublicExam>(`/api/v1/public/live-exams/${id}`, { signal }),
  });
}

/** The student's granted modules (roster or module purchase). */
export function useMyModules(enabled: boolean) {
  return useQuery({
    queryKey: catalogKeys.myModules(),
    staleTime: CATALOG_STALE,
    enabled,
    queryFn: ({ signal }) =>
      apiRequest<PageResponse<StudentModule>>("/api/v1/live-exam-modules?size=100", { signal }).then(
        (page) => page.content,
      ),
  });
}

/**
 * One grant of the student's own (`GET /api/v1/me/live-exam-access`, API-ACCESS). `endsAt` is null for lifetime access and
 * `expired` is true once it has run out (NR-01, my exams). Both are additive fields, so older rows read as lifetime and live.
 */
export interface OwnModuleGrant {
  moduleId: string;
  via: "ROSTER" | "MODULE" | "MANUAL";
  orderId?: string;
  grantedAt?: string;
  endsAt?: string | null;
  expired?: boolean;
}

export interface OwnExamGrant {
  examId: string;
  moduleId: string;
  via: "EXAM" | "MANUAL";
  orderId?: string;
  grantedAt?: string;
  endsAt?: string | null;
  expired?: boolean;
}

/** Every module and exam the signed-in student owns. No paging. */
export interface MyLiveExamAccess {
  modules: OwnModuleGrant[];
  exams: OwnExamGrant[];
}

/** The student's own grants, for "my modules". Ownership on a single page comes from its `access` object. */
export function useMyLiveExamAccess(enabled: boolean) {
  return useQuery({
    queryKey: catalogKeys.myAccess(),
    staleTime: CATALOG_STALE,
    enabled,
    queryFn: ({ signal }) => apiRequest<MyLiveExamAccess>("/api/v1/me/live-exam-access", { signal }),
  });
}

/**
 * The public exam rows for the student's own exams, one read per id, signed in so each row carries its access. The public
 * read works for an expired grant too, where the signed-in read does not (NR-01, my exams).
 */
export function usePublicExamsById(ids: string[]) {
  return useQueries({
    queries: ids.map((id) => ({
      queryKey: [...catalogKeys.publicExam(id), true],
      staleTime: CATALOG_STALE,
      queryFn: ({ signal }: { signal?: AbortSignal }) =>
        apiRequest<PublicExam>(`/api/v1/public/live-exams/${id}`, { signal }),
    })),
  });
}

/** The public package rows for the student's own packages, one read per id (NR-01, my exams). */
export function usePublicModulesById(ids: string[]) {
  return useQueries({
    queries: ids.map((id) => ({
      queryKey: [...catalogKeys.publicModule(id), true],
      staleTime: CATALOG_STALE,
      queryFn: ({ signal }: { signal?: AbortSignal }) =>
        apiRequest<PublicModule>(`/api/v1/public/live-exam-modules/${id}`, { signal }),
    })),
  });
}

function loadMyExams(moduleId: string, examType: ExamType | null, signal?: AbortSignal) {
  return orNull(
    withTypeFallback(examType, (type) =>
      apiRequest<PageResponse<StudentExam>>(
        `/api/v1/live-exam-modules/${moduleId}/exams${typeQuery(type, { size: "100" })}`,
        { signal },
      ),
    ),
  ).then((page) => {
    const rows = page?.content ?? [];
    return examType ? rows.filter((exam) => exam.examType === examType) : rows;
  });
}

/** Exams in the module the student may sit, with myStatus. Empty when none. */
export function useMyModuleExams(moduleId: string, examType: ExamType | null, enabled: boolean) {
  return useQuery({
    queryKey: catalogKeys.myExams(moduleId, examType),
    staleTime: 0,
    enabled,
    refetchOnWindowFocus: true,
    queryFn: ({ signal }) => loadMyExams(moduleId, examType, signal),
  });
}

export function useMyModulesExams(moduleIds: string[], enabled: boolean) {
  return useQueries({
    queries: moduleIds.map((id) => ({
      queryKey: catalogKeys.myExams(id, null),
      staleTime: 0,
      enabled,
      queryFn: ({ signal }: { signal?: AbortSignal }) => loadMyExams(id, null, signal),
    })),
  });
}

/** Null when the student may not sit this exam. */
export function useMyExam(id: string, enabled: boolean) {
  return useQuery<StudentExam | null>({
    queryKey: catalogKeys.myExam(id),
    staleTime: 0,
    enabled,
    refetchOnWindowFocus: true,
    // Before the window opens the status is UPCOMING (NOT_ANNOUNCED with no date yet); poll so
    // Start, or a newly announced date, shows without a reload.
    refetchInterval: (query) =>
      query.state.data?.myStatus === "UPCOMING" || query.state.data?.myStatus === "NOT_ANNOUNCED" ? 15_000 : false,
    queryFn: ({ signal }) => orNull(apiRequest<StudentExam>(`/api/v1/live-exams/${id}`, { signal })),
  });
}

export function useRoutine(enabled: boolean) {
  return useQuery({
    queryKey: catalogKeys.routine(),
    staleTime: 0,
    enabled,
    refetchOnWindowFocus: true,
    queryFn: ({ signal }) =>
      apiRequest<PageResponse<RoutineRow>>("/api/v1/me/exam-routine?size=100", { signal }).then(
        (page) => page.content,
      ),
  });
}
