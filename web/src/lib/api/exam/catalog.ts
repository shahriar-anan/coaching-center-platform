"use client";

import { useQueries, useQuery } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
import { ApiError } from "@/lib/api/errors";
import type {
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
  liveAt: string;
  endAt: string;
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
  myModule: (id: string) => ["catalog", "my-module", id] as const,
  myExams: (moduleId: string, examType: ExamType | null) =>
    ["catalog", "my-exams", moduleId, examType ?? "ALL"] as const,
  myExam: (id: string) => ["catalog", "my-exam", id] as const,
  routine: () => ["catalog", "routine"] as const,
};

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

export function usePublicModule(id: string) {
  return useQuery({
    queryKey: catalogKeys.publicModule(id),
    staleTime: CATALOG_STALE,
    queryFn: ({ signal }) =>
      apiRequest<PublicModule>(`/api/v1/public/live-exam-modules/${id}`, { signal }),
  });
}

export function usePublicModuleExams(moduleId: string | undefined, examType: ExamType | null) {
  return useQuery({
    queryKey: catalogKeys.publicExams(moduleId ?? "", examType),
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

export function usePublicExam(id: string) {
  return useQuery({
    queryKey: catalogKeys.publicExam(id),
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

/** Null when the student has no module-wide access. */
export function useMyModule(id: string, enabled: boolean) {
  return useQuery({
    queryKey: catalogKeys.myModule(id),
    staleTime: CATALOG_STALE,
    enabled,
    queryFn: ({ signal }) =>
      orNull(apiRequest<StudentModule>(`/api/v1/live-exam-modules/${id}`, { signal })),
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
  return useQuery({
    queryKey: catalogKeys.myExam(id),
    staleTime: 0,
    enabled,
    refetchOnWindowFocus: true,
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
