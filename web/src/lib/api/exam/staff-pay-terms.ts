"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";

// Instructor and pay per written exam (staff pay plan, phase 8, decision P18). Paths and field names follow the API contract.
// Money comes as JSON numbers. Keys start with "staffPay", so the pay screens' invalidation refreshes these reads too.

export type PayTermsChangeKind = "ASSIGNED" | "UNASSIGNED" | "RATE_SET" | "RATE_CHANGED";

/** One line of an exam's change history, newest first. */
export interface PayTermsChange {
  kind: PayTermsChangeKind;
  instructorName: string | null;
  previousInstructorName: string | null;
  sheetRate: number | null;
  previousSheetRate: number | null;
  /** Null when the actor is not known (for example a change the watcher recorded). */
  changedByName: string | null;
  createdAt: string;
}

/** The exam's marker, its own rate (null when none is set), the rate in force, and the history. */
export interface PayTerms {
  examId: string;
  instructor: { id: string; name: string } | null;
  ownSheetRate: number | null;
  effectiveSheetRate: number;
  changes: PayTermsChange[];
}

/** The create call's optional fields for a written exam. Master Admin only; the API refuses them from anyone else. */
export interface NewWrittenExamPay {
  sheetRate: number;
  instructorId?: string;
}

/** One assigned written exam in the instructor's "My exams" table. */
export interface MyPayExamRow {
  examId: string;
  titleEn: string;
  titleBn: string;
  sheetRate: number;
  markedCount: number;
  publishedCount: number;
}

export const MY_PAY_EXAMS_PATH = "/api/v1/me/pay/exams";

export function payTermsPath(examId: string): string {
  return `/api/v1/live-exams/${examId}/pay-terms`;
}

export const staffPayTermsKeys = {
  exam: (examId: string) => ["staffPay", "payTerms", examId] as const,
  myExams: () => ["staffPay", "me", "exams"] as const,
};

/**
 * What the API sends. Its names differ from the page's (examRate, effectiveRate, log; marked, published), and it leaves
 * null fields out of the JSON, so every optional field may be missing. The two functions below turn it into the page's shape.
 */
interface ApiPayTermsLogEntry {
  kind: PayTermsChangeKind;
  instructorName?: string | null;
  previousInstructorName?: string | null;
  sheetRate?: number | null;
  previousSheetRate?: number | null;
  changedByName?: string | null;
  createdAt: string;
}

export interface ApiPayTerms {
  instructor?: { id: string; name?: string | null } | null;
  examRate?: number | null;
  effectiveRate: number;
  log?: ApiPayTermsLogEntry[] | null;
}

export interface ApiMyPayExam {
  examId: string;
  titleEn?: string | null;
  titleBn?: string | null;
  examRate?: number | null;
  effectiveRate: number;
  marked?: number | null;
  published?: number | null;
}

/** The API's pay terms as the page uses them: missing fields become null, and the history is always a list. */
export function toPayTerms(examId: string, raw: ApiPayTerms): PayTerms {
  return {
    examId,
    instructor: raw.instructor ? { id: raw.instructor.id, name: raw.instructor.name ?? "" } : null,
    ownSheetRate: raw.examRate ?? null,
    effectiveSheetRate: raw.effectiveRate,
    changes: (raw.log ?? []).map((entry) => ({
      kind: entry.kind,
      instructorName: entry.instructorName ?? null,
      previousInstructorName: entry.previousInstructorName ?? null,
      sheetRate: entry.sheetRate ?? null,
      previousSheetRate: entry.previousSheetRate ?? null,
      changedByName: entry.changedByName ?? null,
      createdAt: entry.createdAt,
    })),
  };
}

/** The API's list of the instructor's exams as the page's rows. */
export function toMyPayExams(raw: ApiMyPayExam[] | null | undefined): { rows: MyPayExamRow[] } {
  return {
    rows: (raw ?? []).map((row) => ({
      examId: row.examId,
      titleEn: row.titleEn ?? "",
      titleBn: row.titleBn ?? "",
      sheetRate: row.effectiveRate,
      markedCount: row.marked ?? 0,
      publishedCount: row.published ?? 0,
    })),
  };
}

/** The exam's marker, rate and change history. Read by the Master Admin only (plan decision O1). */
export function usePayTerms(examId: string, enabled = true) {
  return useQuery({
    queryKey: staffPayTermsKeys.exam(examId),
    queryFn: async ({ signal }) => toPayTerms(examId, await apiRequest<ApiPayTerms>(payTermsPath(examId), { signal })),
    enabled: enabled && Boolean(examId),
  });
}

/** Sets or clears the exam's own rate (Master Admin). Null clears it. The history is refreshed after it settles. */
export function useSavePayTerms(examId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (sheetRate: number | null) =>
      toPayTerms(examId, await apiRequest<ApiPayTerms>(payTermsPath(examId), { method: "PUT", body: { sheetRate } })),
    retry: false,
    onSettled: () => queryClient.invalidateQueries({ queryKey: staffPayTermsKeys.exam(examId) }),
  });
}

/** The signed-in instructor's assigned written exams, each with its rate per sheet and the counts. */
export function useMyPayExams() {
  return useQuery({
    queryKey: staffPayTermsKeys.myExams(),
    queryFn: async ({ signal }) => toMyPayExams(await apiRequest<ApiMyPayExam[]>(MY_PAY_EXAMS_PATH, { signal })),
  });
}
