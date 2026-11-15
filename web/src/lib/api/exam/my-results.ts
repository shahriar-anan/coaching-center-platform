"use client";

import { useQuery } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";

/*
 * The student's published offline exam results (docs/offline-admission/results-api-contract.md, the student routes). Only the
 * admission's own student can read them; another student's admission id is a 404. A null mark means the exam has none for this
 * student (an absent student has no total, rank or pass mark).
 */

export type OfflineExamType = "MCQ" | "MCQ_WRITTEN" | "WRITTEN";

/** One published exam of the student's batch, with the student's own row. Dates are yyyy-MM-dd; instants are ISO. */
export interface MyExamResult {
  classId: string;
  heldOn: string;
  subject: string;
  topic: string | null;
  examType: OfflineExamType;
  fullMarks: number;
  /** Null when the exam has no pass mark: no pass or fail is shown. */
  passMark: number | null;
  publishedAt: string;
  /** True when this student has no row in the sheet. */
  absent: boolean;
  /** True when right, wrong and written are all zero (rule R2). */
  maybeAbsent: boolean;
  total: number | null;
  /** Null for a written-only exam. */
  mcq: number | null;
  /** Null for an MCQ-only exam. */
  written: number | null;
  rank: number | null;
  /** The number of ranked students (absent ones excluded). */
  rankOf: number | null;
  passed: boolean | null;
}

/** The admission's results: the batch, the student's roll and the published exams. */
export interface MyAdmissionResults {
  batchNameEn: string | null;
  batchNameBn: string | null;
  roll: string | null;
  exams: MyExamResult[];
}

/** One row of an exam's leaderboard. Roll numbers are visible to every student of the batch (rule R6). */
export interface MyLeaderboardRow {
  roll: string;
  name: string;
  rank: number | null;
  total: number | null;
  mcq: number | null;
  written: number | null;
  passed: boolean | null;
  absent: boolean;
  maybeAbsent: boolean;
  mine: boolean;
}

/** One exam's leaderboard: sorted by rank then roll, absent students last. */
export interface MyExamLeaderboard {
  classId: string;
  subject: string;
  topic: string | null;
  heldOn: string;
  examType: OfflineExamType;
  fullMarks: number;
  passMark: number | null;
  publishedAt: string;
  myRoll: string | null;
  rows: MyLeaderboardRow[];
}

export const myResultKeys = {
  results: ["account", "admission", "results"] as const,
  leaderboard: ["account", "admission", "leaderboard"] as const,
};

function resultsPath(admissionId: string): string {
  return `/api/v1/me/admissions/${encodeURIComponent(admissionId)}/results`;
}

/** The page of one exam's leaderboard, under the admission (`/offline-activities/results/[admissionId]/[classId]`). */
export function offlineResultPath(admissionId: string, classId: string): string {
  return `/offline-activities/results/${encodeURIComponent(admissionId)}/${encodeURIComponent(classId)}`;
}

/** The published results of one admission. Pass `enabled: false` until the admission is open (onboarded or closed). */
export function useMyAdmissionResults(admissionId: string, enabled = true) {
  return useQuery({
    queryKey: [...myResultKeys.results, admissionId],
    queryFn: ({ signal }) => apiRequest<MyAdmissionResults>(resultsPath(admissionId), { signal }),
    enabled,
  });
}

/** One exam's leaderboard, read through the student's own admission. */
export function useMyExamLeaderboard(admissionId: string, classId: string) {
  return useQuery({
    queryKey: [...myResultKeys.leaderboard, admissionId, classId],
    queryFn: ({ signal }) =>
      apiRequest<MyExamLeaderboard>(`${resultsPath(admissionId)}/${encodeURIComponent(classId)}`, { signal }),
    enabled: admissionId !== "" && classId !== "",
  });
}
