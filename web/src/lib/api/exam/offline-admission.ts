"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
import { ApiError, isNotFoundError } from "@/lib/api/errors";

/*
 * The student's own offline admission (plan sections 5 and 6.2). The API answers 404 for a student with no admission; the
 * query turns that into `null`, so such a student sees no admission screens and is not gated (plan D22). Every write sends
 * the whole form or the photo and gets the new admission back, which replaces the cached one.
 */

export type AdmissionState =
  | "AWAITING_FORM"
  | "SUBMITTED"
  | "CHANGES_REQUESTED"
  | "READY"
  | "ONBOARDED"
  | "CLOSED";

/** The fields of the registration form, as the API names them. Dates are yyyy-MM-dd. Unfilled fields are null. */
export interface AdmissionForm {
  fullName?: string | null;
  fatherName?: string | null;
  motherName?: string | null;
  dateOfBirth?: string | null;
  email?: string | null;
  gender?: "MALE" | "FEMALE" | null;
  university?: string | null;
  semesterBatch?: string | null;
  nid?: string | null;
  mobile?: string | null;
  emergencyMobile?: string | null;
  hallMess?: string | null;
  thana?: string | null;
  district?: string | null;
  presentAddress?: string | null;
  permanentAddress?: string | null;
}

export type AdmissionFormField = keyof AdmissionForm;

export interface AdmissionView {
  id: string;
  state: AdmissionState;
  /** Why the office sent the form back. Set only in CHANGES_REQUESTED. */
  changesReason?: string | null;
  submittedAt?: string | null;
  submitCount: number;
  hasPhoto: boolean;
  form: AdmissionForm;
}

/** The student's own photo, the same picture as the profile photo (plan side effect 10). */
export const ADMISSION_PHOTO_PATH = "/api/v1/me/photo";

export const admissionKeys = {
  mine: ["account", "admission"] as const,
};

/**
 * The student's open admission, or `null` when there is none. Pass `enabled: false` for anyone who is not a student, so
 * staff and visitors never ask.
 */
export function useMyAdmission({ enabled = true }: { enabled?: boolean } = {}) {
  return useQuery({
    queryKey: admissionKeys.mine,
    queryFn: async (): Promise<AdmissionView | null> => {
      try {
        return await apiRequest<AdmissionView>("/api/v1/me/admission");
      } catch (error) {
        if (isNotFoundError(error)) {
          return null;
        }
        throw error;
      }
    },
    enabled,
    refetchOnWindowFocus: true,
  });
}

/** Saves the form as a draft. Partial bodies are allowed; this screen always sends every field. */
export function useSaveAdmissionForm() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (form: AdmissionForm) =>
      apiRequest<AdmissionView>("/api/v1/me/admission/form", { method: "PUT", body: form }),
    onSuccess: (view) => {
      queryClient.setQueryData(admissionKeys.mine, view);
    },
    onError: (error) => {
      // Another tab changed the state (for example, it was submitted): read the state again.
      if (error instanceof ApiError && error.code === "ADMISSION_STATE") {
        void queryClient.invalidateQueries({ queryKey: admissionKeys.mine });
      }
    },
  });
}

/** Uploads the photo from the camera or the gallery. The server re-encodes it. */
export function useUploadAdmissionPhoto() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => {
      const body = new FormData();
      body.append("file", file);
      return apiRequest<AdmissionView>("/api/v1/me/admission/photo", { method: "PUT", body });
    },
    onSuccess: (view) => {
      queryClient.setQueryData(admissionKeys.mine, view);
    },
  });
}

/** Submits the form for the office to review. Every field and the photo are checked on the server (fieldErrors). */
export function useSubmitAdmission() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiRequest<AdmissionView>("/api/v1/me/admission/submit", { method: "POST" }),
    onSuccess: (view) => {
      queryClient.setQueryData(admissionKeys.mine, view);
    },
    onError: (error) => {
      if (error instanceof ApiError && error.code === "ADMISSION_STATE") {
        void queryClient.invalidateQueries({ queryKey: admissionKeys.mine });
      }
    },
  });
}
