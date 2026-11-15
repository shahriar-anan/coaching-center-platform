"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFile, apiRequest } from "@/lib/api/client";
import { dhakaParts } from "@/lib/format";
import { serverNow } from "@/lib/time/clock";

/*
 * The staff side of offline exam results (plan 7.4 to 7.7, results API contract). An admin uploads an exam's result sheet (.xlsx), checks
 * the preview, and saves it as staged; the Master Admin publishes it. Query keys start with "offlineResults"; every write refreshes them all.
 */

export const offlineResultKeys = {
  all: ["offlineResults"] as const,
};

// ---- views -------------------------------------------------------------------------------------------------------------

/** MCQ, MCQ + Written, or Written: the columns the sheet must have (rule R7). */
export type ResultExamType = "MCQ" | "MCQ_WRITTEN" | "WRITTEN";

/** The state of one exam's results in a batch list. */
export type ResultState = "NONE" | "STAGED" | "PUBLISHED" | "PUBLISHED_WITH_STAGED";

/** One exam of a batch, in the Results tab. Newest first. `rowCount` is the published sheet's rows, or 0. */
export type BatchExamRow = {
  classId: string;
  heldOn: string;
  subject: string;
  topic: string;
  instructorName: string | null;
  kind: string;
  cancelled: boolean;
  /** The class status: SCHEDULED, HELD, CANCELLED, NOT_TAKEN_PENDING, NOT_TAKEN, PAID_PENDING, PAID, and so on. */
  status: string;
  state: ResultState;
  publishedAt: string | null;
  rowCount: number;
};

/** One student's line in a preview or a saved sheet. Marks are null for an absent student. */
export type ResultRow = {
  rowNumber?: number | null;
  roll: string;
  admissionId: string | null;
  studentName: string | null;
  absent: boolean;
  right: number | null;
  wrong: number | null;
  mcq: number | null;
  written: number | null;
  total: number | null;
  rank: number | null;
  passed: boolean | null;
  fileTotal: number | null;
  fileRank: number | null;
  mismatch: boolean;
  maybeAbsent: boolean;
};

/** A row of a saved sheet, with its id. */
export type ResultSheetRow = ResultRow & { id: string };

export type ResultCounts = { rows: number; matched: number; absent: number; mismatched: number };

/** The values a problem or a warning names, such as the roll or the column. Numbers are shown in the locale's digits. */
export type ResultMessageParams = Record<string, string | number | null>;

/** A row of the file that blocks the save. `rowNumber` and `roll` are null when the file has no such cell. `code` picks the text. */
export type ResultProblem = {
  rowNumber: number | null;
  roll: string | null;
  code: string;
  params: ResultMessageParams;
  /** English, for the record. The screens show the text of `code`, and this only when the code is unknown. */
  message: string;
};

/** A note on the file that does not block the save. */
export type ResultWarning = { code: string; params: ResultMessageParams; message: string };

/** The answer to a check: nothing is saved. The problems block the save; the warnings do not. */
export type ResultPreview = {
  examType: ResultExamType;
  fullMarks: number;
  negative: number;
  passMark: number | null;
  counts: ResultCounts;
  rows: ResultRow[];
  problems: ResultProblem[];
  warnings: ResultWarning[];
};

/** A matched student whose state changed after the staged sheet was saved (a closed admission, or a new roll). */
export type ResultChange = {
  admissionId: string;
  name: string;
  oldRoll: string;
  newRoll: string | null;
  closed: boolean;
};

export type ResultSheetStatus = "STAGED" | "PUBLISHED" | "REPLACED";

export type ResultSheet = {
  id: string;
  status: ResultSheetStatus;
  examType: ResultExamType;
  fullMarks: number;
  negative: number;
  passMark: number | null;
  fileName: string;
  uploadedBy: string | null;
  uploadedAt: string;
  publishedBy: string | null;
  publishedAt: string | null;
  rows: ResultSheetRow[];
  /** Staged sheets only: how many students the publish tells (matched or absent rows whose admission is onboarded). */
  notifyCount?: number;
  /** Staged sheets only: matched students whose state changed after the save. The publish still goes ahead. */
  changedSinceSave?: ResultChange[];
};

/** One exam with both of its sheets: the published one (if any) and the staged one (if any). */
export type ResultSheetView = {
  classId: string;
  batchId: string;
  subject: string;
  topic: string;
  heldOn: string;
  cancelled: boolean;
  published: ResultSheet | null;
  staged: ResultSheet | null;
};

/** The file and the choices of one check or save. `passMark` is the typed text; empty means none. */
export type ResultUploadInput = {
  file: File;
  examType: ResultExamType;
  passMark: string;
};

// ---- reads -------------------------------------------------------------------------------------------------------------

/** The batch's exams with their result state, newest first. */
export function useBatchResults(batchId: string) {
  return useQuery({
    queryKey: [...offlineResultKeys.all, "batch", batchId],
    queryFn: ({ signal }) => apiRequest<BatchExamRow[]>(`/api/v1/offline-batches/${batchId}/results`, { signal }),
    enabled: Boolean(batchId),
    staleTime: 0,
  });
}

/** One exam's sheets: the published and the staged, each with its rows. */
export function useSheetView(classId: string) {
  return useQuery({
    queryKey: [...offlineResultKeys.all, "sheet", classId],
    queryFn: ({ signal }) => apiRequest<ResultSheetView>(`/api/v1/offline-classes/${classId}/results`, { signal }),
    enabled: Boolean(classId),
    staleTime: 0,
  });
}

// ---- writes ------------------------------------------------------------------------------------------------------------

/** The multipart body of a check or a save: the file, the exam type and the pass mark when one is typed. */
export function resultFormData(input: ResultUploadInput): FormData {
  const body = new FormData();
  body.append("file", input.file);
  body.append("examType", input.examType);
  if (input.passMark.trim() !== "") {
    body.append("passMark", input.passMark.trim());
  }
  return body;
}

/** Checks a file. Saves nothing; the answer lists the rows, the problems and the warnings. */
export function useCheckResult(classId: string) {
  return useMutation<ResultPreview, Error, ResultUploadInput>({
    mutationFn: (input) =>
      apiRequest<ResultPreview>(`/api/v1/offline-classes/${classId}/results/preview`, { method: "POST", body: resultFormData(input) }),
  });
}

/** Saves the file as the staged sheet. It replaces an earlier staged one; the published sheet stays as it is. */
export function useSaveStagedResult(classId: string) {
  const queryClient = useQueryClient();
  return useMutation<ResultSheetView, Error, ResultUploadInput>({
    mutationFn: (input) =>
      apiRequest<ResultSheetView>(`/api/v1/offline-classes/${classId}/results`, { method: "POST", body: resultFormData(input) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: offlineResultKeys.all }),
  });
}

/** Publishes the staged sheet. Students are told in the app. */
export function usePublishResult(classId: string) {
  const queryClient = useQueryClient();
  return useMutation<ResultSheetView, Error, void>({
    mutationFn: () => apiRequest<ResultSheetView>(`/api/v1/offline-classes/${classId}/results/publish`, { method: "POST" }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: offlineResultKeys.all }),
  });
}

/** Removes the staged sheet. The published one is never touched. */
export function useDiscardStagedResult(classId: string) {
  const queryClient = useQueryClient();
  return useMutation<void, Error, void>({
    mutationFn: () => apiRequest<void>(`/api/v1/offline-classes/${classId}/results/staged`, { method: "DELETE" }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: offlineResultKeys.all }),
  });
}

/** Saves the uploaded file to the device, with the name the server gives it. It needs the bearer token, so it is fetched first. */
export async function downloadResultFile(classId: string, sheetId: string, fallbackName: string): Promise<void> {
  const { blob, fileName } = await apiFile(`/api/v1/offline-classes/${classId}/results/${sheetId}/file`);
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName ?? fallbackName;
  link.click();
  URL.revokeObjectURL(url);
}

// ---- rules shared by the screens ---------------------------------------------------------------------------------------

/** Today in Dhaka as yyyy-MM-dd, on the server's clock (rule A4). */
export function dhakaToday(now: number = serverNow()): string {
  const parts = dhakaParts(now);
  const month = String(parts.month + 1).padStart(2, "0");
  const day = String(parts.day).padStart(2, "0");
  return `${parts.year}-${month}-${day}`;
}

/**
 * Why an exam takes no upload: it is cancelled, marked as not taken (or waiting for that approval), or not held yet (rule A4). Null when a
 * result can be uploaded. `status` is the class status; the sheet view does not carry it, so only the Results tab passes it.
 */
export function uploadBlockReason(
  exam: { heldOn: string; cancelled: boolean; status?: string },
  today: string,
): "cancelled" | "notTaken" | "future" | null {
  if (exam.cancelled) {
    return "cancelled";
  }
  if (exam.status === "NOT_TAKEN" || exam.status === "NOT_TAKEN_PENDING") {
    return "notTaken";
  }
  if (exam.heldOn > today) {
    return "future";
  }
  return null;
}

/** The largest file the check takes (the API limit is 5 MB). */
export const RESULT_MAX_BYTES = 5 * 1024 * 1024;

/** Whether a file is an .xlsx workbook by its name. The API checks the content too. */
export function isXlsxName(name: string): boolean {
  return /\.xlsx$/i.test(name);
}
