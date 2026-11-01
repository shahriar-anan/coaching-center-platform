import { useInfiniteQuery } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
import type { ExamType, PageResponse } from "@/lib/api/exam/types";
import type { StaffExam, StaffModule } from "@/lib/api/exam/staff-catalog";

/** Rows per page of the target picker. The picker asks for one page at a time and never loads the whole catalog. */
export const TARGET_PAGE_SIZE = 10;

export type TargetKind = "package" | "exam";

/** One package or one exam as the target picker shows it. Both lists are normalised to this shape. */
export type TargetRow = {
  id: string;
  titleEn: string;
  titleBn: string;
  categoryId: string | null;
  status: string;
  /** Exams only: MCQ or WRITTEN. Null for a package. */
  examType: ExamType | null;
  /** Packages only: the MCQ and written exams the package holds. Null when the server does not count them. */
  examCount: number | null;
};

export type TargetSearchParams = {
  kind: TargetKind;
  /** Title search, either language. Blank or undefined sends nothing. */
  q?: string;
  categoryId?: string;
  /** MCQ or WRITTEN, exams only. The staff package list does not filter by type, so packages never send it. */
  type?: string;
  /** One server-side status such as "PUBLISHED". Undefined means any status. */
  status?: string;
};

type PackageListRow = Pick<StaffModule, "id" | "titleEn" | "titleBn" | "status" | "categoryId" | "mcqCount" | "writtenCount">;
type ExamListRow = Pick<StaffExam, "id" | "titleEn" | "titleBn" | "status" | "categoryId" | "examType">;

/**
 * The query string for one page of the package list or the exam list (zero-based `page`). Empty filters are left out.
 * The names are the ones the controllers read: `q`, `categoryId`, `status`, and `type` for exams (`examType` on packages
 * is not sent, because the staff list ignores it).
 */
export function targetSearchQuery(params: TargetSearchParams, page = 0): string {
  const query = new URLSearchParams({ page: String(page), size: String(TARGET_PAGE_SIZE) });
  const q = params.q?.trim();
  if (q) {
    query.set("q", q);
  }
  if (params.categoryId) {
    query.set("categoryId", params.categoryId);
  }
  if (params.status) {
    query.set("status", params.status);
  }
  if (params.kind === "exam" && params.type) {
    query.set("type", params.type);
  }
  return query.toString();
}

/** The path and query for one page of the picker's list. */
export function targetSearchPath(params: TargetSearchParams, page = 0): string {
  const base = params.kind === "package" ? "/api/v1/live-exam-modules" : "/api/v1/live-exams";
  return `${base}?${targetSearchQuery(params, page)}`;
}

function packageRow(row: PackageListRow): TargetRow {
  const examCount = row.mcqCount != null && row.writtenCount != null ? row.mcqCount + row.writtenCount : null;
  return {
    id: row.id,
    titleEn: row.titleEn,
    titleBn: row.titleBn,
    categoryId: row.categoryId,
    status: row.status,
    examType: null,
    examCount,
  };
}

function examRow(row: ExamListRow): TargetRow {
  return {
    id: row.id,
    titleEn: row.titleEn,
    titleBn: row.titleBn,
    categoryId: row.categoryId ?? null,
    status: row.status,
    examType: row.examType,
    examCount: null,
  };
}

/**
 * One page at a time of the packages or exams that match the filters, 10 rows per page. The picker shows "Load more"
 * for the next page; nothing asks for the following page on its own. Pass `enabled: false` while the picker is
 * collapsed to its chosen line, so no list is loaded.
 */
export function useTargetSearch(params: TargetSearchParams, options: { enabled?: boolean } = {}) {
  return useInfiniteQuery({
    queryKey: ["staff-picker", "targets", params.kind, targetSearchQuery(params)],
    queryFn: async ({ pageParam, signal }) => {
      if (params.kind === "package") {
        const page = await apiRequest<PageResponse<PackageListRow>>(targetSearchPath(params, pageParam), { signal });
        return { ...page, content: page.content.map(packageRow) };
      }
      const page = await apiRequest<PageResponse<ExamListRow>>(targetSearchPath(params, pageParam), { signal });
      return { ...page, content: page.content.map(examRow) };
    },
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
    enabled: options.enabled ?? true,
    staleTime: 30_000,
  });
}
