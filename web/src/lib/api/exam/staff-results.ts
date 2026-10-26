"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
import type { PageResponse } from "@/lib/api/exam/types";
import { queryString } from "@/lib/api/exam/staff-ops";

/*
 * Staff results (WS9 OPS): the exam results report, a student's exam history, the bulk
 * publish of written results and the draft count shown on its button. Query keys start
 * with "staffResults".
 */

/** The summary counts live sittings only. Pass rate is a percentage of the counted results. */
export type ResultsSummary = {
  participants: number;
  submitted: number;
  failedToSubmit: number;
  averageScore: number | null;
  highestScore: number | null;
  lowestScore: number | null;
  passCount: number;
  passRate: number | null;
  practiceParticipants: number;
};

/** One sitting in the results report. Never carries a phone number. */
export type ResultsRow = {
  participantId: string;
  studentId: string;
  studentName: string;
  studentCode: string | null;
  status: "IN_PROGRESS" | "SUBMITTED" | "FAILED_TO_SUBMIT";
  kind: "LIVE" | "RETAKE";
  score: number | null;
  maximum: number;
  passed: boolean | null;
  startedAt: string | null;
  submittedAt: string | null;
  publishedAt: string | null;
};

export type ResultsReport = {
  summary: ResultsSummary;
  rows: PageResponse<ResultsRow>;
};

export type ResultsFilters = {
  page: number;
  size: number;
  kind: string;
  status: string;
  q: string;
  sort: string;
};

/** One sitting of one student, across exams. `published` is the written-result flag. */
export type ExamHistoryRow = {
  participantId: string;
  examId: string;
  examTitleEn: string;
  examTitleBn: string;
  moduleId: string;
  moduleTitleEn: string;
  moduleTitleBn: string;
  kind: "LIVE" | "RETAKE";
  status: "IN_PROGRESS" | "SUBMITTED" | "FAILED_TO_SUBMIT";
  score: number | null;
  maximum: number;
  passed: boolean | null;
  submittedAt: string | null;
  published: boolean;
};

export function useExamResults(examId: string, filters: ResultsFilters) {
  return useQuery({
    queryKey: ["staffResults", "report", examId, filters],
    queryFn: ({ signal }) =>
      apiRequest<ResultsReport>(`/api/v1/live-exams/${examId}/results${queryString({ ...filters })}`, { signal }),
    staleTime: 0,
  });
}

export function useStudentExamHistory(studentId: string, page: number) {
  return useQuery({
    queryKey: ["staffResults", "history", studentId, page],
    queryFn: ({ signal }) =>
      apiRequest<PageResponse<ExamHistoryRow>>(
        `/api/v1/students/${studentId}/live-exam-results${queryString({ page, size: 20 })}`,
        { signal },
      ),
    staleTime: 0,
  });
}

/**
 * How many written results are drafts (marked, not yet published) for one exam. The
 * submissions feed counts them with `status=DRAFT`; the page size is 1 because only the total is used.
 * Only the caller that shows the publish control (Master Admin) asks, so `enabled` is false for others.
 */
export function useExamDraftCount(examId: string, enabled: boolean) {
  return useQuery({
    queryKey: ["staffResults", "drafts", examId],
    queryFn: async ({ signal }) => {
      const page = await apiRequest<PageResponse<unknown>>(
        `/api/v1/live-exam-submissions${queryString({ examId, status: "DRAFT", size: 1 })}`,
        { signal },
      );
      return page.totalElements;
    },
    enabled,
    staleTime: 0,
  });
}

/** Publishes every draft of one written exam at once (Master Admin only). The API returns how many it published. */
export function usePublishAllResults(examId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiRequest<{ published: number }>(`/api/v1/live-exams/${examId}/publish-results`, { method: "POST" }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["staffResults"] });
      queryClient.invalidateQueries({ queryKey: ["staffPeople"] });
      queryClient.invalidateQueries({ queryKey: ["staffMarking"] });
    },
  });
}
