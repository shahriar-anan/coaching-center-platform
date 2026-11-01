"use client";

import { useInfiniteQuery, useMutation, useQuery, useQueryClient, type InfiniteData, type QueryClient } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
import { reviewKeys, type ReviewKind, type ReviewStatus } from "@/lib/api/exam/reviews";
import type { PageResponse } from "@/lib/api/exam/types";

// Staff reviews (admin reviews plan, section 5; reviews plan, section 6). Paths and field names follow the API contract.

/** Rows per page of the queue ("Load more"). */
export const STAFF_REVIEW_PAGE_SIZE = 20;

export type ReviewSort = "newest" | "oldest" | "lowest" | "highest";

/** One row of the queue. The server leaves null fields out, so they are read back as null. */
export type StaffReviewRow = {
  id: string;
  /** Null when the exam or package has been removed. */
  kind: ReviewKind | null;
  targetId: string | null;
  targetTitleEn: string | null;
  targetTitleBn: string | null;
  studentId: string;
  studentName: string;
  rating: number;
  comment: string | null;
  status: ReviewStatus;
  hideReason: string | null;
  featured: boolean;
  createdAt: string;
  updatedAt: string;
  moderatedAt: string | null;
};

/** The tab badges. Counted with every filter except the status. */
export type StaffReviewCounts = {
  pending: number;
  approved: number;
  hidden: number;
  featured: number;
};

/** The filters of the Reviews page. `status` is the tab; the counts never use it. */
export type StaffReviewFilters = {
  status?: ReviewStatus;
  kind?: ReviewKind;
  targetId?: string;
  rating?: number;
  featured?: boolean;
  q?: string;
  sort?: ReviewSort;
};

type RawRow = Partial<StaffReviewRow> & { id: string };

type QueuePage = PageResponse<StaffReviewRow>;

/** Fills the nulls the server leaves out. */
function normalizeRow(raw: RawRow): StaffReviewRow {
  return {
    id: raw.id,
    kind: raw.kind ?? null,
    targetId: raw.targetId ?? null,
    targetTitleEn: raw.targetTitleEn ?? null,
    targetTitleBn: raw.targetTitleBn ?? null,
    studentId: raw.studentId ?? "",
    studentName: raw.studentName ?? "",
    rating: raw.rating ?? 0,
    comment: raw.comment ?? null,
    status: raw.status ?? "PENDING",
    hideReason: raw.hideReason ?? null,
    featured: Boolean(raw.featured),
    createdAt: raw.createdAt ?? "",
    updatedAt: raw.updatedAt ?? "",
    moderatedAt: raw.moderatedAt ?? null,
  };
}

/** The filters the counts use: all of them but the status and the sort. */
function countFilters(filters: StaffReviewFilters) {
  return {
    kind: filters.kind,
    targetId: filters.targetId,
    rating: filters.rating,
    featured: filters.featured ? true : undefined,
    q: filters.q?.trim() || undefined,
  };
}

export const staffReviewKeys = {
  lists: () => ["staffReviews", "list"] as const,
  list: (filters: StaffReviewFilters) => ["staffReviews", "list", filters] as const,
  counts: (filters: StaffReviewFilters) => ["staffReviews", "counts", countFilters(filters)] as const,
  countsAll: () => ["staffReviews", "counts"] as const,
  pendingCount: () => ["staffReviews", "pendingCount"] as const,
};

/** The path and query of one page of the queue. Empty filters are left out. */
export function staffReviewsPath(filters: StaffReviewFilters, page: number): string {
  const query = new URLSearchParams();
  if (filters.status) {
    query.set("status", filters.status);
  }
  for (const [name, value] of Object.entries(countFilters(filters))) {
    if (value !== undefined && value !== "") {
      query.set(name, String(value));
    }
  }
  query.set("sort", filters.sort ?? "newest");
  query.set("page", String(page));
  query.set("size", String(STAFF_REVIEW_PAGE_SIZE));
  return `/api/v1/staff/reviews?${query}`;
}

/** The path and query of the tab counts. The status is never sent. */
export function staffReviewCountsPath(filters: StaffReviewFilters): string {
  const query = new URLSearchParams();
  for (const [name, value] of Object.entries(countFilters(filters))) {
    if (value !== undefined && value !== "") {
      query.set(name, String(value));
    }
  }
  const search = query.toString();
  return `/api/v1/staff/reviews/counts${search ? `?${search}` : ""}`;
}

/** One page of the queue at a time, as an infinite list ("Load more"). */
export function useStaffReviews(filters: StaffReviewFilters) {
  return useInfiniteQuery({
    queryKey: staffReviewKeys.list(filters),
    initialPageParam: 0,
    queryFn: async ({ pageParam, signal }): Promise<QueuePage> => {
      const page = await apiRequest<PageResponse<RawRow>>(staffReviewsPath(filters, pageParam), { signal });
      return { ...page, content: page.content.map(normalizeRow) };
    },
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  });
}

/** The tab badges for the filters on the page, whatever tab is open. */
export function useStaffReviewCounts(filters: StaffReviewFilters) {
  return useQuery({
    queryKey: staffReviewKeys.counts(filters),
    queryFn: ({ signal }) => apiRequest<StaffReviewCounts>(staffReviewCountsPath(filters), { signal }),
  });
}

/** The reviews waiting for approval, for the sidebar badge. Only asked for when the person may moderate. */
export function usePendingReviewCount(enabled = true) {
  return useQuery({
    queryKey: staffReviewKeys.pendingCount(),
    queryFn: ({ signal }) => apiRequest<{ count: number }>("/api/v1/staff/reviews/pending-count", { signal }),
    enabled,
    staleTime: 30_000,
  });
}

export type StatusChange = {
  /** The row as it was when the person chose the action. */
  row: StaffReviewRow;
  status: "APPROVED" | "HIDDEN";
  /** Required for HIDDEN: the reason the student sees. */
  reason?: string;
};

/** Approves or hides a review. The rows, the counts and the sidebar badge are updated when it succeeds. */
export function useReviewStatusChange() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async (change: StatusChange) => {
      const body = change.status === "HIDDEN" ? { status: change.status, reason: change.reason } : { status: change.status };
      const updated = await apiRequest<RawRow>(`/api/v1/staff/reviews/${change.row.id}/status`, { method: "PUT", body });
      return normalizeRow(updated);
    },
    retry: false,
    onSuccess: (updated, change) => applyReviewChange(client, change.row, updated),
    onSettled: () => invalidatePublicReviews(client),
  });
}

export type FeatureChange = {
  row: StaffReviewRow;
  featured: boolean;
};

/** Features or unfeatures an approved review that has a comment. The server answers 409 for any other review. */
export function useReviewFeature() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async (change: FeatureChange) => {
      const updated = await apiRequest<RawRow>(`/api/v1/staff/reviews/${change.row.id}/featured`, {
        method: "PUT",
        body: { featured: change.featured },
      });
      return normalizeRow(updated);
    },
    retry: false,
    onSuccess: (updated, change) => applyReviewChange(client, change.row, updated),
    onSettled: () => invalidatePublicReviews(client),
  });
}

/**
 * Puts one changed review into every cached queue and count, and moves the sidebar badge. A queue drops the row when the
 * row no longer fits its status or featured filter. Counts move by the difference between the row before and after.
 */
export function applyReviewChange(client: QueryClient, before: StaffReviewRow, after: StaffReviewRow) {
  for (const query of client.getQueryCache().findAll({ queryKey: staffReviewKeys.lists() })) {
    const filters = query.queryKey[2] as StaffReviewFilters;
    client.setQueryData<InfiniteData<QueuePage, number>>(query.queryKey, (data) => patchQueue(data, after, filters));
  }
  for (const query of client.getQueryCache().findAll({ queryKey: staffReviewKeys.countsAll() })) {
    const filters = query.queryKey[2] as StaffReviewFilters;
    client.setQueryData<StaffReviewCounts>(query.queryKey, (counts) => counts && shiftCounts(counts, filters, before, after));
  }
  const waiting = (after.status === "PENDING" ? 1 : 0) - (before.status === "PENDING" ? 1 : 0);
  if (waiting !== 0) {
    client.setQueryData<{ count: number }>(staffReviewKeys.pendingCount(), (old) =>
      old ? { count: Math.max(0, old.count + waiting) } : old,
    );
  }
}

function keepsRow(filters: StaffReviewFilters, row: StaffReviewRow): boolean {
  if (filters.status && row.status !== filters.status) {
    return false;
  }
  return !filters.featured || row.featured;
}

function patchQueue(data: InfiniteData<QueuePage, number> | undefined, after: StaffReviewRow, filters: StaffReviewFilters) {
  if (!data) {
    return data;
  }
  const keep = keepsRow(filters, after);
  let found = false;
  let removed = 0;
  const pages = data.pages.map((page) => {
    const content: StaffReviewRow[] = [];
    for (const row of page.content) {
      if (row.id !== after.id) {
        content.push(row);
        continue;
      }
      found = true;
      if (keep) {
        content.push(after);
      } else {
        removed += 1;
      }
    }
    return { ...page, content };
  });
  if (!found) {
    return data;
  }
  return {
    ...data,
    pages: pages.map((page) => ({ ...page, totalElements: Math.max(0, page.totalElements - removed) })),
  };
}

/** The counts a review adds to, under one set of filters. A review outside those filters adds to none. */
function countsOf(row: StaffReviewRow): StaffReviewCounts {
  return {
    pending: row.status === "PENDING" ? 1 : 0,
    approved: row.status === "APPROVED" ? 1 : 0,
    hidden: row.status === "HIDDEN" ? 1 : 0,
    featured: row.featured ? 1 : 0,
  };
}

function inCounts(filters: StaffReviewFilters, row: StaffReviewRow): boolean {
  if (filters.kind && row.kind !== filters.kind) {
    return false;
  }
  if (filters.targetId && row.targetId !== filters.targetId) {
    return false;
  }
  if (filters.rating && row.rating !== filters.rating) {
    return false;
  }
  return !filters.featured || row.featured;
}

const NO_COUNTS: StaffReviewCounts = { pending: 0, approved: 0, hidden: 0, featured: 0 };

function shiftCounts(counts: StaffReviewCounts, filters: StaffReviewFilters, before: StaffReviewRow, after: StaffReviewRow) {
  const was = inCounts(filters, before) ? countsOf(before) : NO_COUNTS;
  const now = inCounts(filters, after) ? countsOf(after) : NO_COUNTS;
  return {
    pending: Math.max(0, counts.pending - was.pending + now.pending),
    approved: Math.max(0, counts.approved - was.approved + now.approved),
    hidden: Math.max(0, counts.hidden - was.hidden + now.hidden),
    featured: Math.max(0, counts.featured - was.featured + now.featured),
  };
}

/** Re-reads the public views a moderation change can move: the averages, the comments and the landing section. */
function invalidatePublicReviews(client: QueryClient) {
  void client.invalidateQueries({ queryKey: ["reviews", "public"] });
  void client.invalidateQueries({ queryKey: reviewKeys.featured() });
}
