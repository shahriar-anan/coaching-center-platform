"use client";

import { useQuery } from "@tanstack/react-query";
import { apiBlob, apiRequest } from "@/lib/api/client";
import type { AdmissionForm, AdmissionView } from "@/lib/api/exam/offline-admission";

/*
 * The public side of offline admission (plan section 5): the batch cards and batch page, and an application started without signing
 * in. Everything here is open to visitors; the API answers 404 while the offline switch is off.
 */

/** One batch as the public cards and batch page show it. Money comes as a JSON number or string. */
export type PublicBatch = {
  id: string;
  nameEn: string;
  nameBn: string;
  /** yyyy-MM-dd, or null when the batch has no start date. */
  startsOn: string | null;
  /** yyyy-MM-dd. */
  endsOn: string;
  /** The total course fee. Students never see how many instalments (16 Nov). */
  courseFee: number | string;
  descriptionEn: string | null;
  descriptionBn: string | null;
  /** The API path of the batch photo (served without sign-in), or null when there is none. */
  photoUrl: string | null;
  /** Off shows "Admissions closed" and refuses new applications. */
  admissionsOpen: boolean;
  /** True when the batch has a routine PDF to show. The API names it routineAvailable. */
  routineAvailable: boolean;
};

export const publicAdmissionKeys = {
  batches: ["publicAdmission", "batches"] as const,
  batch: (id: string) => ["publicAdmission", "batch", id] as const,
};

/** The open batches first, then the closed ones, in display order. An archived batch is never listed. */
export function usePublicBatches(enabled = true) {
  return useQuery({
    queryKey: publicAdmissionKeys.batches,
    queryFn: ({ signal }) => apiRequest<PublicBatch[]>("/api/v1/public/offline-batches", { signal }),
    enabled,
  });
}

export function usePublicBatch(id: string) {
  return useQuery({
    queryKey: publicAdmissionKeys.batch(id),
    queryFn: ({ signal }) => apiRequest<PublicBatch>(`/api/v1/public/offline-batches/${id}`, { signal }),
    enabled: id !== "",
  });
}

/** The student copy of the batch routine (no instructor names). */
export function publicBatchRoutinePath(id: string): string {
  return `/api/v1/public/offline-batches/${id}/routine.pdf`;
}

export function loadPublicBatchRoutinePdf(id: string): Promise<Blob> {
  return apiBlob(publicBatchRoutinePath(id));
}

/**
 * An application started or reopened without an account: the token the browser keeps for the form, and the admission it belongs to.
 * The token is the only key the public routes need after this.
 */
export type PublicApplication = {
  token: string;
  admission: AdmissionView;
};

/** Starts the application for a batch with the form's email (plan P10: no email code). An open one for the same email comes back. */
export function startPublicApplication(body: { batchId: string; email: string }): Promise<PublicApplication> {
  return apiRequest<PublicApplication>("/api/v1/public/offline-applications", { method: "POST", body });
}

/** Saves the whole form as a draft. The API checks nothing until the submit. */
export function savePublicForm(token: string, form: AdmissionForm): Promise<AdmissionView> {
  return apiRequest<AdmissionView>(`/api/v1/public/offline-applications/${token}/form`, { method: "PUT", body: form });
}

/** Uploads the photo of the application. The server re-encodes it. */
export function uploadPublicPhoto(token: string, file: File): Promise<AdmissionView> {
  const body = new FormData();
  body.append("file", file);
  return apiRequest<AdmissionView>(`/api/v1/public/offline-applications/${token}/photo`, { method: "PUT", body });
}

/** Sends the application to the office. Every field and the photo are checked on the server (VALIDATION_FAILED with field errors). */
export function submitPublicApplication(token: string): Promise<AdmissionView> {
  return apiRequest<AdmissionView>(`/api/v1/public/offline-applications/${token}/submit`, { method: "POST" });
}

/** The form again after "changes requested" (plan Q4). The link lasts 7 days; an expired or used link is refused. */
export function loadReopenedApplication(linkToken: string): Promise<PublicApplication> {
  return apiRequest<PublicApplication>(`/api/v1/public/offline-applications/reopen/${linkToken}`);
}
