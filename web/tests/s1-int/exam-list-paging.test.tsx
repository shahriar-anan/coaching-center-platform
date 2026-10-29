import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { groupExams } from "@/components/staff/exams/exam-groups";
import * as client from "@/lib/api/client";
import { EXAM_LIST_PAGE_SIZE, examListQuery, nextExamListPage, useExamList } from "@/lib/api/exam/staff-exams";
import type { PageResponse } from "@/lib/api/exam/types";
import type { StaffExam } from "@/lib/api/exam/staff-catalog";

vi.mock("@/lib/api/client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api/client")>()),
  apiRequest: vi.fn(),
}));

const apiRequest = vi.mocked(client.apiRequest);

/** A list row with only the fields the grouping reads. */
function exam(id: string, packageIds: string[]): StaffExam {
  return {
    id,
    titleEn: `Exam ${id}`,
    titleBn: `পরীক্ষা ${id}`,
    packages: packageIds.map((packageId) => ({ id: packageId, titleEn: `Package ${packageId}`, titleBn: `প্যাকেজ ${packageId}` })),
    categoryId: null,
  } as unknown as StaffExam;
}

function page(content: StaffExam[], number: number, totalPages: number): PageResponse<StaffExam> {
  return { content, page: number, size: EXAM_LIST_PAGE_SIZE, totalElements: content.length, totalPages };
}

function wrapperWith(queryClient: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

beforeEach(() => {
  apiRequest.mockReset();
});

describe("exam list paging", () => {
  it("asks for one page at a time, zero-based, with the filters on every page", () => {
    expect(EXAM_LIST_PAGE_SIZE).toBe(200);
    expect(examListQuery({ packageId: "p1" }, 0)).toBe("page=0&size=200&packageId=p1");
    expect(examListQuery({ packageId: "p1" }, 2)).toBe("page=2&size=200&packageId=p1");
    expect(examListQuery({}, 0)).toBe("page=0&size=200");
  });

  it("has a next page only while the page before the last one", () => {
    expect(nextExamListPage({ page: 0, totalPages: 2 })).toBe(1);
    expect(nextExamListPage({ page: 1, totalPages: 2 })).toBeNull();
    expect(nextExamListPage({ page: 0, totalPages: 1 })).toBeNull();
    // An empty result has no pages at all.
    expect(nextExamListPage({ page: 0, totalPages: 0 })).toBeNull();
  });

  it("requests the pages in order until the last one, and then stops", async () => {
    apiRequest
      .mockResolvedValueOnce(page([exam("a", ["p1"])], 0, 2))
      .mockResolvedValueOnce(page([exam("b", ["p1"])], 1, 2));
    const { result } = renderHook(() => useExamList({ packageId: "p1" }), {
      wrapper: wrapperWith(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
    });

    await waitFor(() => expect(result.current.data?.pages).toHaveLength(2));
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(apiRequest.mock.calls.map((call) => call[0])).toEqual([
      "/api/v1/live-exams?page=0&size=200&packageId=p1",
      "/api/v1/live-exams?page=1&size=200&packageId=p1",
    ]);
    expect(result.current.hasNextPage).toBe(false);
  });

  it("makes one request for a single page, and none more", async () => {
    apiRequest.mockResolvedValueOnce(page([exam("a", [])], 0, 1));
    const { result } = renderHook(() => useExamList({}), {
      wrapper: wrapperWith(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
    });

    await waitFor(() => expect(result.current.data?.pages).toHaveLength(1));
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(apiRequest).toHaveBeenCalledTimes(1);
  });

  it("groups the rows of two pages together, in the order the server sent them", () => {
    const first = [exam("a", ["p1"]), exam("b", [])];
    const second = [exam("c", ["p1", "p2"]), exam("d", ["p2"])];
    const rows = [page(first, 0, 2), page(second, 1, 2)].flatMap((pageResponse) => pageResponse.content);

    const groups = groupExams(rows, "package", new Map());

    expect(groups.map((group) => group.key)).toEqual(["package:p1", "package:p2", "none"]);
    expect(groups[0].exams.map((row) => row.id)).toEqual(["a", "c"]);
    expect(groups[1].exams.map((row) => row.id)).toEqual(["c", "d"]);
    expect(groups[2].exams.map((row) => row.id)).toEqual(["b"]);
  });
});
