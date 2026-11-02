"use client";

import { useInfiniteQuery, useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { apiRequest, apiUpload } from "@/lib/api/client";
import { catalogKeys } from "@/lib/api/exam/catalog";
import type { ExamType, PageResponse } from "@/lib/api/exam/types";

// Local types until WS0 adds these to lib/api/exam/types.ts. Shapes follow LiveExamSittingService (API).

export type SittingOption = {
  id: string;
  text: string;
  /** Null until the deadline has passed. */
  correct: boolean | null;
  /** The student's own saved choice. Present during an open sitting (task A). */
  selected: boolean | null;
};

export type SittingQuestion = {
  id: string;
  stem: string;
  marks: number;
  /** True when more than one option is correct (API: `multipleAnswer`). Absent or false is single-answer. */
  multipleAnswer?: boolean | null;
  /** Null until the deadline has passed. */
  explanation: string | null;
  options: SittingOption[];
};

/** The requesting student's sitting, from the exam read. `submittedAt` is set once they submit early. */
export type SittingBlock = {
  kind: "LIVE" | "RETAKE";
  startedAt: string;
  deadlineAt: string;
  submittedAt: string | null;
};

export type StudentExam = {
  id: string;
  moduleId: string;
  examType: "MCQ" | "WRITTEN";
  /** BN, EN, BOTH, or null when the exam has no paper language. Null and BOTH show no language and follow the interface. */
  paperLanguage: "BN" | "EN" | "BOTH" | null;
  titleEn: string;
  titleBn: string | null;
  descriptionEn: string | null;
  descriptionBn: string | null;
  rulesEn: string | null;
  rulesBn: string | null;
  liveAt: string | null;
  endAt: string | null;
  durationMinutes: number;
  passPercent: number | null;
  maximum: number;
  status: string;
  finallySaved: boolean;
  price: number | null;
  questionPdf: boolean | null;
  /** Omitted (absent) for a submitted MCQ until its review opens. Once open it carries the key and explanations. */
  questions?: SittingQuestion[] | null;
  myStatus: string;
  retakeAvailable: boolean | null;
  /** False when an answer, once saved (or confirmed, for a multi-answer question), cannot change (API: `answersChangeable`). Absent means it can. */
  answersChangeable?: boolean | null;
  /** The exam's negative mark per wrong answer, when it has one (API: `negativeMarks`). Absent or 0 means none. */
  negativeMarks?: number | null;
  /** The student's own sitting, or null when there is none (API: `sitting`). */
  sitting: SittingBlock | null;
  /** True once the student's written script is stored on the server (API: `answerStored`). */
  answerStored?: boolean | null;
  /** For a submitted MCQ whose review is still closed: when its answers and explanations open (API: `reviewOpensAt`). */
  reviewOpensAt?: string | null;
};

export type TakeResult = {
  id: string;
  examId: string;
  kind: "LIVE" | "RETAKE";
  serverTime: string;
  deadlineAt: string;
  startedAt: string;
};

export type PostedResult = {
  participantId: string;
  score: number | null;
  maximum: number;
  passed: boolean | null;
  status: string;
};

export type ReviewedResult = {
  /** "LIVE" for the live sitting, "RETAKE" for the later attempt (off the merit list). */
  kind: "LIVE" | "RETAKE";
  score: number | null;
  maximum: number;
  passed: boolean | null;
  status: string;
  /** Absent for a submitted MCQ whose review is closed (see `reviewOpensAt`). */
  questions?: SittingQuestion[] | null;
  /** For a submitted MCQ whose review is closed: when the answers and explanations open (API: `reviewOpensAt`). */
  reviewOpensAt?: string | null;
  comment: string | null;
  evaluatedPdf: boolean | null;
  /** True when the attempt has a submitted answer script the student can open (API: `answerPdf`). Never for MCQ. */
  answerPdf?: boolean;
  /** Where the attempt stands on the merit list, for the position line (API: `position`). Null when there is none. */
  position?: SittingPosition | null;
  /** The exam's own fields, so the result page can stand alone when the exam read is refused (an archived exam). */
  examTitleEn?: string | null;
  examTitleBn?: string | null;
  examType?: ExamType | null;
  passPercent?: number | null;
};

/**
 * CURRENT: a live attempt during the window. BOARD: a live attempt after it, or a practice first attempt.
 * RELATIVE: an archived attempt or a later attempt, ranked as if on the board. `belowPass` means no rank.
 */
export type SittingPosition = {
  type: "CURRENT" | "BOARD" | "RELATIVE";
  rank: number;
  of: number;
  belowPass: boolean;
};

export type LeaderboardRow = {
  name: string;
  rank: number;
  score: number;
  /** The attempt's own full marks (API: `maximum`). */
  maximum: number;
  /** The score as a percent of the attempt's maximum, or `null` when that maximum is 0 (API: `percent`). */
  percent?: number | null;
  passed: boolean;
  /** True only on the requesting student's own row. */
  mine: boolean;
};

/**
 * One exam the student has sat, from its latest attempt (`GET /api/v1/me/exam-results`). `score` and `passed` are set only
 * when `resultVisible` is true. The API drops a null field, so every nullable field may be missing (use `isMissing`).
 */
export type MyExamResult = {
  examId: string;
  titleEn: string;
  titleBn?: string | null;
  examType: ExamType;
  /** The package the exam is in, first joined; absent for a standalone exam. */
  moduleId?: string | null;
  moduleTitleEn?: string | null;
  moduleTitleBn?: string | null;
  liveAt?: string | null;
  passPercent?: number | null;
  /** The legacy status: IN_PROGRESS, SUBMITTED, AWAITING_RESULT, RESULT_READY or FAILED_TO_SUBMIT. */
  status: string;
  /** The full status. */
  detail: string;
  /** LIVE, or RETAKE for every other attempt (a practice exam's first attempt too). */
  kind: "LIVE" | "RETAKE";
  attempts: number;
  submittedAt?: string | null;
  resultVisible: boolean;
  score?: number | null;
  maximum: number;
  passed?: boolean | null;
};

export const sittingKeys = {
  exam: (examId: string) => ["sitting", "exam", examId] as const,
  result: (examId: string) => ["sitting", "result", examId] as const,
  leaderboard: (examId: string) => ["sitting", "leaderboard", examId] as const,
  routine: () => ["sitting", "routine"] as const,
  myResults: (examType: ExamType | null) => ["sitting", "my-results", examType ?? "ALL"] as const,
};

const MY_RESULTS_PAGE = 20;

/**
 * The reads that show how a sitting ended: the exam, its result, the Results list and My exams. Called after a submit, a written
 * upload and the time running out, so the new row shows under Results at once, without a reload.
 */
export function invalidateSittingReads(client: QueryClient, examId: string): Promise<unknown> {
  return Promise.all([
    client.invalidateQueries({ queryKey: sittingKeys.exam(examId) }),
    client.invalidateQueries({ queryKey: sittingKeys.result(examId) }),
    client.invalidateQueries({ queryKey: ["sitting", "my-results"] }),
    client.invalidateQueries({ queryKey: catalogKeys.myExam(examId) }),
    client.invalidateQueries({ queryKey: ["catalog", "my-exams"] }),
  ]);
}

/** The exam as this student sees it. Refetched on focus; never cached, because the window is live. */
export function useStudentExam(examId: string) {
  return useQuery({
    queryKey: sittingKeys.exam(examId),
    queryFn: () => apiRequest<StudentExam>(`/api/v1/live-exams/${examId}`),
    staleTime: 0,
    refetchOnWindowFocus: true,
    enabled: Boolean(examId),
  });
}

export function useTakeExam(examId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: () => apiRequest<TakeResult>(`/api/v1/live-exams/${examId}/take`, { method: "POST", body: {} }),
    onSettled: () => client.invalidateQueries({ queryKey: sittingKeys.exam(examId) }),
  });
}

export function useRetakeExam(examId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: () => apiRequest<TakeResult>(`/api/v1/live-exams/${examId}/retake`, { method: "POST", body: {} }),
    onSettled: () => client.invalidateQueries({ queryKey: sittingKeys.exam(examId) }),
  });
}

/** Replaces the whole selection. The caller decides when to send; this only sends. */
export function useSaveAnswers(examId: string) {
  return useMutation({
    mutationFn: (answers: { questionId: string; optionIds: string[] }[]) =>
      apiRequest<void>(`/api/v1/live-exams/${examId}/answers`, { method: "PUT", body: { answers } }),
    retry: false,
  });
}

/**
 * Confirms one multi-answer question on an exam whose answers cannot change: the selection becomes
 * final. Only sent after the student presses Confirm.
 */
export function useConfirmAnswer(examId: string) {
  return useMutation({
    mutationFn: ({ questionId, optionIds }: { questionId: string; optionIds: string[] }) =>
      apiRequest<void>(`/api/v1/live-exams/${examId}/answers/${questionId}`, {
        method: "PUT",
        body: { optionIds, confirm: true },
      }),
    retry: false,
  });
}

export function useSubmitExam(examId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: () => apiRequest<PostedResult>(`/api/v1/live-exams/${examId}/submit`, { method: "POST", body: {} }),
    onSettled: () => invalidateSittingReads(client, examId),
  });
}

export function useSittingResult(examId: string, enabled = true) {
  return useQuery({
    queryKey: sittingKeys.result(examId),
    queryFn: () => apiRequest<ReviewedResult>(`/api/v1/live-exams/${examId}/result`),
    staleTime: 0,
    enabled: enabled && Boolean(examId),
  });
}

export function useLeaderboard(examId: string) {
  return useQuery({
    queryKey: sittingKeys.leaderboard(examId),
    queryFn: () => apiRequest<{ rows: LeaderboardRow[] }>(`/api/v1/live-exams/${examId}/leaderboard`),
    staleTime: 60_000,
    enabled: Boolean(examId),
  });
}

/** Uploads a written answer script. Progress comes from XHR. */
export function useUploadAnswer(examId: string, onProgress?: (percent: number) => void) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => {
      const form = new FormData();
      form.append("file", file);
      return apiUpload<void>(`/api/v1/live-exams/${examId}/answer-pdf`, form, { onProgress });
    },
    onSettled: () => invalidateSittingReads(client, examId),
  });
}

/** Authorized PDF bytes for the viewer. Only during the open sitting. */
export function questionPdfPath(examId: string) {
  return `/api/v1/live-exams/${examId}/question-pdf`;
}

export function evaluatedPdfPath(examId: string) {
  return `/api/v1/live-exams/${examId}/evaluated-pdf`;
}

/** The student's own submitted answer script. `attempt` picks one attempt; without it, the latest. */
export function answerPdfPath(examId: string, attempt?: number) {
  const base = `/api/v1/live-exams/${examId}/answer-pdf`;
  return attempt === undefined ? base : `${base}?attempt=${attempt}`;
}

/**
 * Every exam the student has sat, newest first, a page at a time (`GET /api/v1/me/exam-results`). `examType` narrows it on
 * the server, so a type change starts a new list. Standalone exams are included.
 */
export function useMyExamResults(examType: ExamType | null) {
  return useInfiniteQuery({
    queryKey: sittingKeys.myResults(examType),
    initialPageParam: 0,
    staleTime: 0,
    queryFn: ({ pageParam, signal }) => {
      const params = new URLSearchParams({ page: String(pageParam), size: String(MY_RESULTS_PAGE) });
      if (examType) params.set("examType", examType);
      return apiRequest<PageResponse<MyExamResult>>(`/api/v1/me/exam-results?${params.toString()}`, { signal });
    },
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  });
}
