"use client";

import { useEffect, useRef, useState } from "react";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { ApiError } from "@/lib/api/errors";
import { useSaveSetup, type AdmissionDetail, type AdmissionSetup, type SetupBody } from "@/lib/api/exam/offline-admin";
import { offlineErrorText } from "./offline-error";
import { duePreview, toNumber } from "./offline-model";

/*
 * The setup's draft: what the form shows while it is being typed in. One hook owns it for every card, so the whole setup saves as one
 * PATCH, a moment after the last change, and one status line (Saving, Saved, Not saved) covers all of it.
 */

export type SetupDraft = { batchId: string; monthlyFee: string; admittedOn: string; firstDueOn: string; courseEndsOn: string };

export type SaveStatus = "idle" | "saving" | "saved" | "error";

/** How long after the last change the setup is saved. */
export const SAVE_DELAY_MS = 800;
/** How long "Saved" stays on screen before it goes quiet again. */
const SAVED_FOR_MS = 2500;

/** A value for a text input: an empty string for null or missing, never the word "undefined". */
export function text(value: string | number | null | undefined): string {
  return value === null || value === undefined ? "" : String(value);
}

export function draftOf(setup: AdmissionSetup): SetupDraft {
  return {
    batchId: text(setup.batchId),
    monthlyFee: text(setup.monthlyFee),
    admittedOn: text(setup.admittedOn),
    firstDueOn: text(setup.firstDueOn),
    courseEndsOn: text(setup.courseEndsOn),
  };
}

export function bodyOf(draft: SetupDraft): SetupBody {
  return {
    batchId: draft.batchId || null,
    monthlyFee: draft.monthlyFee.trim() || null,
    admittedOn: draft.admittedOn || null,
    firstDueOn: draft.firstDueOn || null,
    courseEndsOn: draft.courseEndsOn || null,
  };
}

/** Field errors the API named, keyed by field. */
function fieldErrorsOf(error: unknown): Record<string, string> {
  if (error instanceof ApiError && error.fieldErrors) {
    return Object.fromEntries(error.fieldErrors.map((problem) => [problem.field, problem.message]));
  }
  return {};
}

/** The money and dates of a draft: the dues it will create, their total, and the first and last due dates. */
export function setupTotals(draft: SetupDraft) {
  const fee = toNumber(draft.monthlyFee);
  const rows = duePreview(draft.firstDueOn, draft.courseEndsOn, fee);
  const total = rows.reduce((sum, row) => sum + row.amount, 0);
  return {
    fee,
    months: rows.length,
    total,
    dueDates: rows.map((row) => row.dueOn),
    firstDue: rows.length > 0 ? rows[0].dueOn : null,
    lastDue: rows.length > 0 ? rows[rows.length - 1].dueOn : null,
  };
}

/**
 * The setup draft of one admission. The server's copy comes in again whenever it changes, unless the person is typing. Each change
 * is saved SAVE_DELAY_MS later with the whole setup. A failed save is not retried by itself: the next change tries again.
 */
export function useSetupDraft(admission: AdmissionDetail, editable: boolean) {
  const { t } = useLocale();
  const { toast } = useToast();
  const save = useSaveSetup(admission.id);
  const saveSetup = save.mutate;
  const [draft, setDraft] = useState<SetupDraft>(() => draftOf(admission.setup));
  const [dirty, setDirty] = useState(false);
  const [status, setStatus] = useState<SaveStatus>("idle");
  const [errors, setErrors] = useState<Record<string, string>>({});

  // Compared during render, so no extra render pass.
  const [seenSetup, setSeenSetup] = useState(admission.setup);
  if (admission.setup !== seenSetup) {
    setSeenSetup(admission.setup);
    if (!dirty) {
      setDraft(draftOf(admission.setup));
    }
  }

  // The newest draft, so a save that finishes after another edit does not clear the edit that is still unsaved.
  const latestDraft = useRef(draft);
  useEffect(() => {
    latestDraft.current = draft;
  }, [draft]);

  useEffect(() => {
    if (!dirty || !editable) {
      return;
    }
    const timer = window.setTimeout(() => {
      const sent = bodyOf(draft);
      setStatus("saving");
      saveSetup(sent, {
        onSuccess: () => {
          setErrors({});
          setStatus("saved");
          if (JSON.stringify(bodyOf(latestDraft.current)) === JSON.stringify(sent)) {
            setDirty(false);
          }
        },
        onError: (error) => {
          setStatus("error");
          setErrors(fieldErrorsOf(error));
          toast({ title: offlineErrorText(error, t), tone: "danger" });
        },
      });
    }, SAVE_DELAY_MS);
    return () => window.clearTimeout(timer);
  }, [draft, dirty, editable, saveSetup, t, toast]);

  useEffect(() => {
    if (status !== "saved") {
      return;
    }
    const timer = window.setTimeout(() => setStatus("idle"), SAVED_FOR_MS);
    return () => window.clearTimeout(timer);
  }, [status]);

  function edit(next: Partial<SetupDraft>) {
    setDraft((current) => ({ ...current, ...next }));
    setDirty(true);
    setErrors({});
  }

  return { draft, edit, status, errors, pending: dirty || status === "saving" };
}
