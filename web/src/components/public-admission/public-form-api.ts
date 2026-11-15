"use client";

import { useCallback, useMemo, useRef } from "react";
import type { AdmissionForm, AdmissionView } from "@/lib/api/exam/offline-admission";
import {
  savePublicForm,
  startPublicApplication,
  submitPublicApplication,
  uploadPublicPhoto,
} from "@/lib/api/exam/public-admission";
import type { AdmissionFormApi } from "@/components/student/admission/admission-form";
import { ApiError } from "@/lib/api/errors";
import { clearApplicationToken, writeApplicationToken } from "./application-token";

/** A complete-looking address. The application starts only from one, so a half-typed email never becomes the application's key. */
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** The email already has an open application for this batch (409 APPLICATION_EXISTS). The page shows that instead of the form. */
class ApplicationExists extends Error {}

/**
 * The form's API for a visitor without an account (plan 2A, P3, P10). The first save or photo starts the application from the form's
 * email; the token it returns is kept in a ref (and in sessionStorage for a new application), and every later call uses it. With no
 * email yet there is nothing to start, so a save does nothing; a submit without an email reaches the server, which names the field.
 *
 * `batchId` starts a new application. For a reopened application (`batchId` null) the token is given and nothing is started.
 */
export function usePublicFormApi({
  batchId,
  initialToken,
  onView,
  onExists,
}: {
  batchId: string | null;
  initialToken: string | null;
  onView: (view: AdmissionView) => void;
  /** Called with the email when it already applied for this batch. */
  onExists?: (email: string) => void;
}): AdmissionFormApi {
  const tokenRef = useRef<string | null>(initialToken);
  // `onView` is a state setter from the page, so it does not change between renders.
  const setView = onView;

  const ensureToken = useCallback(
    async (email: string | null | undefined): Promise<string> => {
      if (tokenRef.current) {
        return tokenRef.current;
      }
      let started;
      try {
        started = await startPublicApplication({ batchId: batchId ?? "", email: (email ?? "").trim() });
      } catch (error) {
        if (error instanceof ApiError && error.code === "APPLICATION_EXISTS") {
          onExists?.((email ?? "").trim());
          throw new ApplicationExists();
        }
        throw error;
      }
      tokenRef.current = started.token;
      if (batchId) {
        writeApplicationToken(batchId, started.token);
      }
      setView(started.admission);
      return started.token;
    },
    [batchId, setView, onExists],
  );

  return useMemo<AdmissionFormApi>(
    () => ({
      save: async (form: AdmissionForm) => {
        if (!tokenRef.current && !EMAIL_PATTERN.test(form.email?.trim() ?? "")) {
          return null;
        }
        let token;
        try {
          token = await ensureToken(form.email);
        } catch (error) {
          // The page has switched to the "already applied" note; this is not a failed save.
          if (error instanceof ApplicationExists) return null;
          throw error;
        }
        setView(await savePublicForm(token, form));
        return null;
      },
      uploadPhoto: async (file: File, form: AdmissionForm) => {
        const token = await ensureToken(form.email);
        setView(await uploadPublicPhoto(token, file));
        return null;
      },
      submit: async (form: AdmissionForm) => {
        const token = await ensureToken(form.email);
        const view = await submitPublicApplication(token);
        setView(view);
        if (batchId) {
          clearApplicationToken(batchId);
        }
        return view;
      },
      photoPath: null,
    }),
    [batchId, ensureToken, setView],
  );
}
