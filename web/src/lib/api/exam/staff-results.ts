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
  averageScore?: number | null;
  highestScore?: number | null;
  lowestScore?: number | null;
  passCount: number;
  passRate?: number | null;
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
  /** True when a correction to a released result waits for release. */
  releasePending?: boolean | null;
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

/** What blocks a release and what waits for one. From `GET /api/v1/live-exams/{id}/release-status`. */
export type ReleaseStatus = {
  awaiting: number;
  open: number;
  drafts: number;
  ready: number;
  failedToSubmit: number;
  canRelease: boolean;
};

/** The release panel's numbers. Master Admin and System Admin only; `enabled` is false for others. */
export function useReleaseStatus(examId: string, enabled: boolean) {
  return useQuery({
    queryKey: ["staffResults", "release", examId],
    queryFn: ({ signal }) => apiRequest<ReleaseStatus>(`/api/v1/live-exams/${examId}/release-status`, { signal }),
    enabled,
    staleTime: 0,
  });
}

/**
 * Releases a written exam's results (Master Admin only). `ready` releases only the papers the markers flagged ready,
 * which skips the completeness gate. A refused release throws an ApiError with `details: { awaiting, open }`.
 */
export function useReleaseResults(examId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ ready }: { ready: boolean }) =>
      apiRequest<{ published: number }>(`/api/v1/live-exams/${examId}/publish-results${ready ? "?ready=true" : ""}`, {
        method: "POST",
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["staffResults"] });
      queryClient.invalidateQueries({ queryKey: ["staffPeople"] });
      queryClient.invalidateQueries({ queryKey: ["staffMarking"] });
    },
  });
}

