"use client";

import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest, apiUpload } from "@/lib/api/client";
import type { PageResponse } from "@/lib/api/exam/types";

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
  questions: SittingQuestion[] | null;
  myStatus: string;
  retakeAvailable: boolean | null;
  /** False when an answer, once saved (or confirmed, for a multi-answer question), cannot change (API: `answersChangeable`). Absent means it can. */
  answersChangeable?: boolean | null;
  /** The student's own sitting, or null when there is none (API: `sitting`). */
  sitting: SittingBlock | null;
  /** True once the student's written script is stored on the server (API: `answerStored`). */
  answerStored?: boolean | null;
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
  questions: SittingQuestion[] | null;
  comment: string | null;
  evaluatedPdf: boolean | null;
  /** Where the attempt stands on the merit list, for the position line (API: `position`). Null when there is none. */
  position?: SittingPosition | null;
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
  maximum: number;
  passed: boolean;
  /** True only on the requesting student's own row. */
  mine: boolean;
};

export const sittingKeys = {
  exam: (examId: string) => ["sitting", "exam", examId] as const,
  result: (examId: string) => ["sitting", "result", examId] as const,
  leaderboard: (examId: string) => ["sitting", "leaderboard", examId] as const,
  routine: () => ["sitting", "routine"] as const,
};

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
    onSettled: () => client.invalidateQueries({ queryKey: sittingKeys.exam(examId) }),
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
    onSettled: () => client.invalidateQueries({ queryKey: sittingKeys.exam(examId) }),
  });
}

/** Authorized PDF bytes for the viewer. Only during the open sitting. */
export function questionPdfPath(examId: string) {
  return `/api/v1/live-exams/${examId}/question-pdf`;
}

export function evaluatedPdfPath(examId: string) {
  return `/api/v1/live-exams/${examId}/evaluated-pdf`;
}

/** One row of the student's module exam list (the fields the results hub needs). */
export type HubExam = Pick<
  StudentExam,
  "id" | "moduleId" | "titleEn" | "titleBn" | "examType" | "liveAt" | "myStatus" | "passPercent"
>;

type PageOf<T> = PageResponse<T>;

/** The student's modules, one page. The student owns few modules, so one page is enough. */
export function useMyModules() {
  return useQuery({
    queryKey: ["sitting", "hub", "modules"],
    queryFn: () =>
      apiRequest<PageOf<{ id: string; titleEn: string; titleBn: string | null }>>(
        "/api/v1/live-exam-modules?page=0&size=50",
      ),
    staleTime: 60_000,
  });
}

/** Every module's exams, in parallel. Results are cached per module. */
export function useModuleExams(moduleIds: string[]) {
  return useQueries({
    queries: moduleIds.map((id) => ({
      queryKey: ["sitting", "hub", "exams", id],
      queryFn: () => apiRequest<PageOf<HubExam>>(`/api/v1/live-exam-modules/${id}/exams?page=0&size=50`),
      staleTime: 60_000,
    })),
  });
}
