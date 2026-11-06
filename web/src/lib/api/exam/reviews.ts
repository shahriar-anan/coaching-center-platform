"use client";

import { useInfiniteQuery, useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
import { catalogKeys } from "@/lib/api/exam/catalog";
import type { PageResponse } from "@/lib/api/exam/types";

// Reviews of exams and packages (reviews plan, section 6). Paths and field names follow the API contract.

/** The two things a student can rate. */
export type ReviewKind = "exam" | "package";

export type ReviewStatus = "PENDING" | "APPROVED" | "HIDDEN";

/** The student's own review. `comment` is null when the student only gave stars. */
export interface MyReview {
  id: string;
  rating: number;
  comment: string | null;
  status: ReviewStatus;
  /** Set by staff when they hide the review; the student sees it. */
  hideReason: string | null;
  createdAt: string;
  updatedAt: string;
}

/** `GET /api/v1/me/reviews/{kind}/{id}`. `review` is present when `reviewed` is true. */
export interface ReviewState {
  canReview: boolean;
  reviewed: boolean;
  review?: MyReview;
}

/** One row of `GET /api/v1/me/reviews`, newest first. */
export interface MyReviewRow {
  kind: ReviewKind;
  targetId: string;
  targetTitleEn: string;
  targetTitleBn: string;
  review: MyReview;
}

/** A published comment. `name` is the first name and the last initial, never an id or a contact detail. */
export interface PublicReview {
  id: string;
  name: string;
  rating: number;
  comment: string;
  createdAt: string;
  /** The author's profile picture, a public path, or null when there is none. Absent on an older API. */
  photoUrl?: string | null;
}

/** A staff-picked review for the landing page, with the exam or package it is about. */
export interface FeaturedReview extends PublicReview {
  kind: ReviewKind;
  targetId: string;
  targetTitleEn: string;
  targetTitleBn: string;
}

export type StarCounts = Record<"1" | "2" | "3" | "4" | "5", number>;

/** `GET /api/v1/public/reviews/{kind}/{id}`. The summary repeats on every page; `page` holds the comments. */
export interface PublicReviews {
  /** Null when there are fewer than three ratings. */
  ratingAverage: number | null;
  ratingCount: number;
  stars: StarCounts;
  page: PageResponse<PublicReview>;
}

export interface ReviewInput {
  rating: number;
  /** Optional. Sent as null, or left out, when the student has no comment. */
  comment?: string | null;
}

/** The comment limit in characters, checked by the form and by the API. */
export const REVIEW_MAX_LENGTH = 1000;

/** The comments page size of the public section ("Show more"). */
export const REVIEW_PAGE = 10;

export const reviewKeys = {
  mine: (kind: ReviewKind, id: string) => ["reviews", "mine", kind, id] as const,
  list: () => ["reviews", "list"] as const,
  public: (kind: ReviewKind, id: string) => ["reviews", "public", kind, id] as const,
  featured: () => ["reviews", "featured"] as const,
};

function ownPath(kind: ReviewKind, id: string): string {
  return `/api/v1/me/reviews/${kind}/${id}`;
}

/**
 * Whether the student may review the target, and their review if they wrote one. Only asked for a signed-in student, since
 * the API answers for the caller alone.
 */
export function useMyReview(kind: ReviewKind, id: string, enabled = true) {
  return useQuery({
    queryKey: reviewKeys.mine(kind, id),
    queryFn: ({ signal }) => apiRequest<ReviewState>(ownPath(kind, id), { signal }),
    staleTime: 0,
    enabled: enabled && Boolean(id),
  });
}

/**
 * Creates or replaces the student's review. A stars-only save is published at once; a comment waits for review. The list of
 * reviews, the card and the public views of the target are read again after each save.
 */
export function useSaveReview(kind: ReviewKind, id: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (input: ReviewInput) => apiRequest<MyReview>(ownPath(kind, id), { method: "PUT", body: input }),
    retry: false,
    onSuccess: (review) => {
      client.setQueryData<ReviewState>(reviewKeys.mine(kind, id), { canReview: true, reviewed: true, review });
    },
    onSettled: () => invalidateReviews(client, kind, id),
  });
}

/** Removes the student's review. The card then shows the rating form again. */
export function useDeleteReview(kind: ReviewKind, id: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: () => apiRequest<void>(ownPath(kind, id), { method: "DELETE" }),
    retry: false,
    onSuccess: () => {
      client.setQueryData<ReviewState | undefined>(reviewKeys.mine(kind, id), (old) =>
        old ? { canReview: old.canReview, reviewed: false } : old,
      );
    },
    onSettled: () => invalidateReviews(client, kind, id),
  });
}

/** Every review the student has written. Used to hide "Rate" on rows already rated. */
export function useMyReviews(enabled = true) {
  return useQuery({
    queryKey: reviewKeys.list(),
    queryFn: ({ signal }) => apiRequest<MyReviewRow[]>("/api/v1/me/reviews", { signal }),
    staleTime: 0,
    enabled,
  });
}

/** The keys `kind:id` of every target the student has reviewed, for quick lookups in a list. */
export function reviewedTargets(rows: MyReviewRow[] | undefined): Set<string> {
  return new Set((rows ?? []).map((row) => `${row.kind}:${row.targetId}`));
}

/** The approved comments of a target, a page at a time (`Show more`). Each page carries the same average and breakdown. */
export function usePublicReviews(kind: ReviewKind, id: string) {
  return useInfiniteQuery({
    queryKey: reviewKeys.public(kind, id),
    initialPageParam: 0,
    staleTime: 60_000,
    enabled: Boolean(id),
    queryFn: ({ pageParam, signal }) =>
      apiRequest<PublicReviews>(`/api/v1/public/reviews/${kind}/${id}?page=${pageParam}&size=${REVIEW_PAGE}`, { signal }),
    getNextPageParam: (last) => (last.page.page + 1 < last.page.totalPages ? last.page.page + 1 : undefined),
  });
}

/** The staff-picked reviews for the landing page, up to nine. A failed read leaves the section out. */
export function useFeaturedReviews() {
  return useQuery({
    queryKey: reviewKeys.featured(),
    staleTime: 60_000,
    queryFn: ({ signal }) => apiRequest<FeaturedReview[]>("/api/v1/public/reviews/featured", { signal }),
  });
}

/** Re-reads everything a save or delete can change: the card, the student's list, the target's public views and its average. */
function invalidateReviews(client: QueryClient, kind: ReviewKind, id: string) {
  void client.invalidateQueries({ queryKey: reviewKeys.mine(kind, id) });
  void client.invalidateQueries({ queryKey: reviewKeys.list() });
  void client.invalidateQueries({ queryKey: reviewKeys.public(kind, id) });
  void client.invalidateQueries({
    queryKey: kind === "exam" ? catalogKeys.publicExam(id) : catalogKeys.publicModule(id),
  });
}
