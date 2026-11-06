import { QueryClient } from "@tanstack/react-query";
import { describe, expect, it } from "vitest";
import {
  applyReviewChange,
  staffReviewCountsPath,
  staffReviewKeys,
  staffReviewsPath,
  type StaffReviewFilters,
  type StaffReviewRow,
} from "@/lib/api/exam/staff-reviews";
import { REVIEW_PARAM_DEFAULTS, filtersFromParams, hasActiveFilters, type ReviewParams } from "@/components/staff/reviews/review-params";

function row(overrides: Partial<StaffReviewRow> = {}): StaffReviewRow {
  return {
    id: "r1",
    kind: "exam",
    targetId: "exam-1",
    targetTitleEn: "Mock",
    targetTitleBn: "মক",
    studentId: "s1",
    studentName: "Nusrat Jahan",
    photoUrl: null,
    rating: 5,
    comment: "Clear",
    status: "PENDING",
    hideReason: null,
    featured: false,
    createdAt: "2026-10-20T06:20:00Z",
    updatedAt: "2026-10-20T06:20:00Z",
    moderatedAt: null,
    ...overrides,
  };
}

function pages(rows: StaffReviewRow[]) {
  return { pages: [{ content: rows, page: 0, size: 20, totalElements: rows.length, totalPages: 1 }], pageParams: [0] };
}

function params(overrides: Partial<ReviewParams> = {}): ReviewParams {
  return { ...REVIEW_PARAM_DEFAULTS, ...overrides };
}

describe("the queue and count paths", () => {
  it("sends the status, the filters and the sort, and leaves empty filters out", () => {
    const path = staffReviewsPath({ status: "PENDING", sort: "lowest", q: "  Nusrat  " }, 2);
    const query = new URLSearchParams(path.split("?")[1]);
    expect(path.startsWith("/api/v1/staff/reviews?")).toBe(true);
    expect(query.get("status")).toBe("PENDING");
    expect(query.get("sort")).toBe("lowest");
    expect(query.get("q")).toBe("Nusrat");
    expect(query.get("page")).toBe("2");
    expect(query.get("size")).toBe("20");
    expect(query.has("kind")).toBe(false);
    expect(query.has("featured")).toBe(false);
  });

  it("sends newest when no sort is set, and featured only when it is true", () => {
    expect(new URLSearchParams(staffReviewsPath({}, 0).split("?")[1]).get("sort")).toBe("newest");
    expect(staffReviewsPath({ featured: false }, 0)).not.toContain("featured");
    expect(staffReviewsPath({ featured: true }, 0)).toContain("featured=true");
  });

  it("the counts path never carries the status or the sort", () => {
    const filters: StaffReviewFilters = { status: "HIDDEN", sort: "oldest", kind: "package", targetId: "m1", rating: 4, featured: true };
    const path = staffReviewCountsPath(filters);
    expect(path.startsWith("/api/v1/staff/reviews/counts?")).toBe(true);
    expect(path).not.toContain("status");
    expect(path).not.toContain("sort");
    expect(path).toContain("kind=package");
    expect(path).toContain("targetId=m1");
    expect(path).toContain("rating=4");
    expect(path).toContain("featured=true");
    expect(staffReviewCountsPath({})).toBe("/api/v1/staff/reviews/counts");
  });
});

describe("the URL to filter mapping", () => {
  it("maps each tab to its status, Waiting by default", () => {
    expect(filtersFromParams(params()).status).toBe("PENDING");
    expect(filtersFromParams(params({ tab: "published" })).status).toBe("APPROVED");
    expect(filtersFromParams(params({ tab: "hidden" })).status).toBe("HIDDEN");
    expect(filtersFromParams(params({ tab: "all" })).status).toBeUndefined();
    expect(filtersFromParams(params({ tab: "nonsense" })).status).toBe("PENDING");
  });

  it("drops values the API would refuse: an unknown kind, stars outside 1 to 5, an unknown sort", () => {
    const parsed = filtersFromParams(params({ kind: "course", rating: 9, sort: "popular", q: "   ", featured: false }));
    expect(parsed.kind).toBeUndefined();
    expect(parsed.rating).toBeUndefined();
    expect(parsed.sort).toBe("newest");
    expect(parsed.q).toBeUndefined();
    expect(parsed.featured).toBeUndefined();
    expect(filtersFromParams(params({ rating: 3, kind: "exam", targetId: "e1", featured: true })))
      .toMatchObject({ rating: 3, kind: "exam", targetId: "e1", featured: true });
  });

  it("knows when a filter other than the tab is set", () => {
    expect(hasActiveFilters(params())).toBe(false);
    expect(hasActiveFilters(params({ tab: "hidden" }))).toBe(false);
    expect(hasActiveFilters(params({ q: "x" }))).toBe(true);
    expect(hasActiveFilters(params({ targetId: "e1" }))).toBe(true);
    expect(hasActiveFilters(params({ featured: true }))).toBe(true);
  });
});

describe("a moderation change in the cache", () => {
  const pendingKey = staffReviewKeys.list({ status: "PENDING" });
  const allKey = staffReviewKeys.list({});
  const countsKey = staffReviewKeys.counts({ status: "PENDING" });
  const pendingCountKey = staffReviewKeys.pendingCount();

  function seed(client: QueryClient, rows: StaffReviewRow[]) {
    client.setQueryData(pendingKey, pages(rows.filter((r) => r.status === "PENDING")));
    client.setQueryData(allKey, pages(rows));
    client.setQueryData(countsKey, { pending: 2, approved: 1, hidden: 0, featured: 0 });
    client.setQueryData(pendingCountKey, { count: 2 });
  }

  it("approving a waiting review leaves the waiting queue, moves the counts and the badge, and keeps it in All", () => {
    const client = new QueryClient();
    const waiting = row({ id: "r1" });
    seed(client, [waiting, row({ id: "r2", status: "PENDING" }), row({ id: "r3", status: "APPROVED" })]);

    applyReviewChange(client, waiting, { ...waiting, status: "APPROVED" });

    const queue = client.getQueryData<ReturnType<typeof pages>>(pendingKey);
    expect(queue?.pages[0].content.map((r) => r.id)).toEqual(["r2"]);
    expect(queue?.pages[0].totalElements).toBe(1);
    const everything = client.getQueryData<ReturnType<typeof pages>>(allKey);
    expect(everything?.pages[0].content.find((r) => r.id === "r1")?.status).toBe("APPROVED");
    expect(client.getQueryData(countsKey)).toEqual({ pending: 1, approved: 2, hidden: 0, featured: 0 });
    expect(client.getQueryData(pendingCountKey)).toEqual({ count: 1 });
  });

  it("hiding a published review moves only the counts, and the badge stays", () => {
    const client = new QueryClient();
    const published = row({ id: "r3", status: "APPROVED", featured: true });
    seed(client, [published]);
    client.setQueryData(countsKey, { pending: 2, approved: 1, hidden: 0, featured: 1 });

    applyReviewChange(client, published, { ...published, status: "HIDDEN", featured: false, hideReason: "Spam" });

    expect(client.getQueryData(countsKey)).toEqual({ pending: 2, approved: 0, hidden: 1, featured: 0 });
    expect(client.getQueryData(pendingCountKey)).toEqual({ count: 2 });
  });

  it("unfeaturing leaves a featured-only queue and the featured count", () => {
    const client = new QueryClient();
    const featured = row({ id: "r4", status: "APPROVED", featured: true });
    const featuredKey = staffReviewKeys.list({ featured: true });
    client.setQueryData(featuredKey, pages([featured]));
    client.setQueryData(staffReviewKeys.counts({ featured: true }), { pending: 0, approved: 1, hidden: 0, featured: 1 });

    applyReviewChange(client, featured, { ...featured, featured: false });

    expect(client.getQueryData<ReturnType<typeof pages>>(featuredKey)?.pages[0].content).toEqual([]);
    expect(client.getQueryData(staffReviewKeys.counts({ featured: true }))).toEqual({ pending: 0, approved: 0, hidden: 0, featured: 0 });
  });

  it("a count for another target does not move", () => {
    const client = new QueryClient();
    const waiting = row({ id: "r1", targetId: "exam-1" });
    const otherKey = staffReviewKeys.counts({ targetId: "exam-9" });
    client.setQueryData(otherKey, { pending: 4, approved: 0, hidden: 0, featured: 0 });

    applyReviewChange(client, waiting, { ...waiting, status: "APPROVED" });

    expect(client.getQueryData(otherKey)).toEqual({ pending: 4, approved: 0, hidden: 0, featured: 0 });
  });

  it("a review not in a queue leaves that queue alone, and its total is unchanged", () => {
    const client = new QueryClient();
    const other = row({ id: "r9", status: "HIDDEN" });
    client.setQueryData(pendingKey, pages([row({ id: "r1" })]));

    applyReviewChange(client, other, { ...other, status: "APPROVED" });

    expect(client.getQueryData<ReturnType<typeof pages>>(pendingKey)?.pages[0].totalElements).toBe(1);
  });
});
