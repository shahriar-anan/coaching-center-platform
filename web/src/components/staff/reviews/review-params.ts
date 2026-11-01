import type { ReviewKind, ReviewStatus } from "@/lib/api/exam/reviews";
import type { ReviewSort, StaffReviewFilters } from "@/lib/api/exam/staff-reviews";

/** The tabs of the Reviews page. Waiting is the default. */
export const REVIEW_TABS = ["waiting", "published", "hidden", "all"] as const;

export type ReviewTab = (typeof REVIEW_TABS)[number];

const TAB_STATUS: Record<ReviewTab, ReviewStatus | undefined> = {
  waiting: "PENDING",
  published: "APPROVED",
  hidden: "HIDDEN",
  all: undefined,
};

const SORTS: readonly ReviewSort[] = ["newest", "oldest", "lowest", "highest"];

/**
 * The URL parameters of the page, with their defaults. `useListParams` leaves a value equal to its default out of the URL,
 * so a plain Waiting, any-stars, newest list has no query at all.
 */
export const REVIEW_PARAM_DEFAULTS = {
  tab: "waiting",
  kind: "",
  targetId: "",
  rating: 0,
  featured: false,
  q: "",
  sort: "newest",
};

export type ReviewParams = {
  tab: string;
  kind: string;
  targetId: string;
  rating: number;
  featured: boolean;
  q: string;
  sort: string;
};

/** An unknown tab word falls back to Waiting. */
export function activeTab(value: string): ReviewTab {
  return (REVIEW_TABS as readonly string[]).includes(value) ? (value as ReviewTab) : "waiting";
}

/** The kind the URL names, or undefined for any kind. */
export function reviewKindOf(value: string): ReviewKind | undefined {
  return value === "exam" || value === "package" ? value : undefined;
}

/** The URL's filters as the API filters. A status the tab does not use is left out; Waiting is always PENDING. */
export function filtersFromParams(params: ReviewParams): StaffReviewFilters {
  const sort = SORTS.includes(params.sort as ReviewSort) ? (params.sort as ReviewSort) : "newest";
  const rating = Number.isInteger(params.rating) && params.rating >= 1 && params.rating <= 5 ? params.rating : undefined;
  return {
    status: TAB_STATUS[activeTab(params.tab)],
    kind: reviewKindOf(params.kind),
    targetId: params.targetId || undefined,
    rating,
    featured: params.featured || undefined,
    q: params.q.trim() || undefined,
    sort,
  };
}

/** Whether any filter other than the tab is set. Decides between "nothing yet" and "no match" in the empty state. */
export function hasActiveFilters(params: ReviewParams): boolean {
  return Boolean(params.kind || params.targetId || params.rating || params.featured || params.q.trim());
}
