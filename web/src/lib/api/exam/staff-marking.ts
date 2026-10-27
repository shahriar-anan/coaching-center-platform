"use client";

import { useQueries, useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiBlob, apiRequest, apiUpload } from "@/lib/api/client";
import { isMissing } from "@/lib/api/missing";
import type { PageResponse } from "@/lib/api/exam/types";

/*
 * Staff marking: the cross-module submissions feed, one paper's read and save,
 * the question paper, the bulk publish and the marked-today count. Owned by WS9 MRK.
 * Query keys start with "staffMarking" (and invalidate the people and home keys too).
 */

/** Builds a query string, leaving out empty values. */
function queryString(params: Record<string, string | number | boolean | null | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === null || value === undefined || value === "") {
      continue;
    }
    search.set(key, String(value));
  }
  const text = search.toString();
  return text ? `?${text}` : "";
}

export type CategoryRef = { id: string; titleEn: string; titleBn: string };

/** A module as the staff module list sends it: the category chain and the one teacher. */
export type MarkingModule = {
  id: string;
  titleEn: string;
  titleBn: string;
  categoryPath: CategoryRef[];
  instructor: { id: string; name: string; phone: string } | null;
};

/** The feed's status: `AWAITING` (answer stored, no mark), `DRAFT` (marked, not published), `PUBLISHED`, `FAILED_TO_SUBMIT`. */
export type FeedStatus = "AWAITING" | "DRAFT" | "PUBLISHED" | "FAILED_TO_SUBMIT";

/** One row of `GET /api/v1/live-exam-submissions`. `evaluatorName` is staff-only. */
export type FeedRow = {
  participantId: string;
  examId: string;
  examTitleEn: string;
  examTitleBn: string;
  moduleId: string;
  moduleTitleEn: string;
  moduleTitleBn: string;
  categoryPath: CategoryRef[];
  studentName: string;
  status: FeedStatus;
  kind: "LIVE" | "RETAKE";
  answerPdf: boolean;
  submittedAt: string | null;
  evaluatedAt: string | null;
  publishedAt: string | null;
  evaluatorName: string | null;
  /** True when a correction to a released result waits for release. */
  releasePending?: boolean | null;
  /** True when the marker has flagged the paper ready for release. */
  readyForRelease?: boolean | null;
};

export type FeedSort = "submittedAt,desc" | "submittedAt,asc" | "studentName";

export type FeedParams = {
  status: string;
  moduleId: string;
  examId: string;
  q: string;
  sort: FeedSort;
  page: number;
  size: number;
  /** Only the papers the markers flagged ready for release. */
  ready?: boolean;
};

/** One paper, as `GET /api/v1/live-exam-participants/{id}` sends it (the marking read). */
export type MarkingPaper = {
  id: string;
  examId: string;
  studentId: string;
  studentName: string;
  status: "SUBMITTED" | "FAILED_TO_SUBMIT" | "IN_PROGRESS";
  score: number | null;
  maximum: number;
  passed: boolean | null;
  kind: "LIVE" | "RETAKE";
  answerPdf: boolean;
  evaluatedPdf: boolean | null;
  evaluatorName: string | null;
  published: boolean;
  /** The saved teacher's comment. Null when none was saved. */
  comment: string | null;
  /** The stored marked PDF's name. Null when none is stored. */
  evaluatedFileName: string | null;
  moduleId: string;
  moduleTitleEn: string;
  moduleTitleBn: string;
  examTitleEn: string;
  examTitleBn: string;
  submittedAt: string | null;
  evaluatedAt: string | null;
  publishedAt: string | null;
  /** True when a correction to a released result waits for release. */
  releasePending?: boolean | null;
  /** True when the marker has flagged the paper ready for release. */
  readyForRelease?: boolean | null;
};

/** The most rows one feed page carries. A category view reads one page per module. */
export const FEED_PAGE_MAX = 100;

export const markingKeys = {
  modules: ["staffMarking", "modules"] as const,
  feed: (params: FeedParams) => ["staffMarking", "feed", params] as const,
  paper: (id: string) => ["staffMarking", "paper", id] as const,
  today: (evaluator: string) => ["staffMarking", "today", evaluator] as const,
};

/** Every read a marking change can move: the paper, the feed, the people lists and the home counts. */
export function useInvalidateMarking() {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ["staffMarking"] }),
      queryClient.invalidateQueries({ queryKey: ["staffPeople"] }),
      queryClient.invalidateQueries({ queryKey: ["staffHome"] }),
    ]);
}

/** The staff module list with categories and teachers. Instructors get their assigned modules only. */
export function useMarkingModules() {
  return useQuery({
    queryKey: markingKeys.modules,
    queryFn: ({ signal }) =>
      apiRequest<PageResponse<MarkingModule>>(`/api/v1/live-exam-modules${queryString({ size: FEED_PAGE_MAX })}`, {
        signal,
      }),
    staleTime: 60_000,
  });
}

function feedUrl(params: FeedParams): string {
  return `/api/v1/live-exam-submissions${queryString({
    status: params.status,
    moduleId: params.moduleId,
    examId: params.examId,
    q: params.q,
    sort: params.sort,
    page: params.page,
    size: params.size,
    ready: params.ready ? "true" : undefined,
  })}`;
}

/** One page of the cross-module feed. Open sittings are not in it; they appear once their deadline has passed. */
export function useFeed(params: FeedParams, enabled = true) {
  return useQuery({
    queryKey: markingKeys.feed(params),
    queryFn: ({ signal }) => apiRequest<PageResponse<FeedRow>>(feedUrl(params), { signal }),
    enabled,
    staleTime: 0,
  });
}

/**
 * The feed for each of several modules, one request each. The category view uses this:
 * the feed takes one module at a time. Each module reads its first page only.
 */
export function useFeedByModules(moduleIds: string[], base: Omit<FeedParams, "moduleId" | "page" | "size">) {
  return useQueries({
    queries: moduleIds.map((moduleId) => {
      const params: FeedParams = { ...base, moduleId, page: 0, size: FEED_PAGE_MAX };
      return {
        queryKey: markingKeys.feed(params),
        queryFn: ({ signal }: { signal: AbortSignal }) =>
          apiRequest<PageResponse<FeedRow>>(feedUrl(params), { signal }),
        staleTime: 0,
      };
    }),
  });
}

/** One paper. Reads the comment and the stored marked PDF name, so a reopened mark keeps its comment. */
export function useMarkingPaper(participantId: string) {
  return useQuery({
    queryKey: markingKeys.paper(participantId),
    queryFn: ({ signal }) => apiRequest<MarkingPaper>(`/api/v1/live-exam-participants/${participantId}`, { signal }),
    staleTime: 0,
  });
}

export function answerPaperBlob(participantId: string): Promise<Blob> {
  return apiBlob(`/api/v1/live-exam-participants/${participantId}/answer-pdf`);
}

/** The marked PDF already uploaded for a paper. Master Admin, System Admin and the assigned instructor; the API refuses others. */
export function evaluatedPaperBlob(participantId: string): Promise<Blob> {
  return apiBlob(`/api/v1/live-exam-participants/${participantId}/evaluated-pdf`);
}

/** The question paper. The server answers 404 to an instructor who is not assigned to the module. */
export function questionPaperBlob(examId: string): Promise<Blob> {
  return apiBlob(`/api/v1/live-exams/${examId}/question-pdf`);
}

/**
 * Saves a draft mark. The marked PDF is optional when the paper already has one; a save without
 * a file keeps the stored PDF. The first save must send one (the form checks this before the call).
 */
export function useSaveMarking(participantId: string) {
  const invalidate = useInvalidateMarking();
  return useMutation({
    mutationFn: ({ marks, comment, file }: { marks: number; comment: string; file: File | null }) => {
      const form = new FormData();
      form.append("marks", String(marks));
      if (comment.trim()) {
        form.append("comment", comment.trim());
      }
      if (file) {
        form.append("file", file);
      }
      return apiUpload<MarkingPaper>(`/api/v1/live-exam-participants/${participantId}/evaluation`, form);
    },
    onSuccess: invalidate,
  });
}

/**
 * Flags the paper ready for release, or clears the flag. The API refuses a flag on a paper with no mark. Any save clears
 * the flag on its own.
 */
export function useSetReady(participantId: string) {
  const queryClient = useQueryClient();
  const invalidate = useInvalidateMarking();
  return useMutation({
    mutationFn: (ready: boolean) =>
      apiRequest<MarkingPaper>(`/api/v1/live-exam-participants/${participantId}/ready`, {
        method: ready ? "POST" : "DELETE",
      }),
    onSuccess: () => {
      void invalidate();
      queryClient.invalidateQueries({ queryKey: ["staffResults"] });
    },
  });
}

/** Publishes one paper's draft. */
export function publishPaper(participantId: string): Promise<MarkingPaper> {
  return apiRequest<MarkingPaper>(`/api/v1/live-exam-participants/${participantId}/publish`, { method: "POST" });
}

export function usePublishPaper(participantId: string) {
  const invalidate = useInvalidateMarking();
  return useMutation({
    mutationFn: () => publishPaper(participantId),
    onSuccess: invalidate,
  });
}

/**
 * How many papers this evaluator marked today (Dhaka calendar), from the feed's drafts and
 * published rows. The count is `null` unless both lists fit one page, so a partial count is never shown.
 */
export function useMarkedToday(evaluatorName: string | undefined, isToday: (value: string) => boolean) {
  const drafts = useFeed({ status: "DRAFT", moduleId: "", examId: "", q: "", sort: "submittedAt,desc", page: 0, size: FEED_PAGE_MAX }, Boolean(evaluatorName));
  const published = useFeed({ status: "PUBLISHED", moduleId: "", examId: "", q: "", sort: "submittedAt,desc", page: 0, size: FEED_PAGE_MAX }, Boolean(evaluatorName));

  const complete =
    drafts.data !== undefined &&
    published.data !== undefined &&
    drafts.data.totalElements <= FEED_PAGE_MAX &&
    published.data.totalElements <= FEED_PAGE_MAX;

  let count: number | null = null;
  if (complete && evaluatorName) {
    count = [...drafts.data!.content, ...published.data!.content].filter(
      (row) => row.evaluatorName === evaluatorName && !isMissing(row.evaluatedAt) && isToday(row.evaluatedAt),
    ).length;
  }
  return { count, isPending: drafts.isPending || published.isPending };
}
