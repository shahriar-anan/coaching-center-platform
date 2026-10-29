"use client";

import { useQuery } from "@tanstack/react-query";
import { apiBlob, apiRequest } from "@/lib/api/client";
import type { ExamType, PaperLanguage } from "@/lib/api/exam/types";

/** One option of a preview question. The API never says which option is correct. */
export interface PreviewOption {
  id: string;
  text: string;
  position: number;
}

export interface PreviewQuestion {
  id: string;
  stem: string;
  marks: number;
  position: number;
  multipleAnswer: boolean;
  options: PreviewOption[];
}

/** `GET /api/v1/live-exams/{id}/preview`: the paper without the key or the explanations (C10', P-b). */
export interface PreviewPaper {
  id: string;
  examType: ExamType;
  paperLanguage: PaperLanguage;
  titleEn: string;
  titleBn: string;
  durationMinutes: number;
  passPercent: number | null;
  /** The full marks: the sum of the questions for an MCQ paper, the written maximum otherwise. */
  maximum: number;
  /** Set for a written exam: whether its question paper is a PDF. */
  hasPaper: boolean | null;
  /** Set for an MCQ exam. */
  questions: PreviewQuestion[] | null;
}

export function previewPath(examId: string): string {
  return `/api/v1/live-exams/${examId}/preview`;
}

export function previewPdfPath(examId: string): string {
  return `${previewPath(examId)}/question-pdf`;
}

/** The preview of one paper. It answers not found while the preview is closed, so the page shows the refused state. */
export function usePreviewPaper(examId: string) {
  return useQuery<PreviewPaper>({
    queryKey: ["live-exam-preview", examId],
    staleTime: 60_000,
    queryFn: ({ signal }) => apiRequest<PreviewPaper>(previewPath(examId), { signal }),
  });
}

/**
 * Opens the question paper in a new tab. The PDF needs the bearer token, which a plain link cannot send, so the tab opens first
 * (a click keeps it from being blocked) and then shows the file fetched with the token. Throws when the tab or the file fails.
 */
export async function openPreviewPdf(examId: string): Promise<void> {
  const popup = window.open("about:blank", "_blank");
  if (!popup) {
    throw new Error("The new tab was blocked.");
  }
  try {
    const blob = await apiBlob(previewPdfPath(examId));
    popup.location.href = URL.createObjectURL(blob);
    popup.opener = null;
  } catch (error) {
    popup.close();
    throw error;
  }
}
