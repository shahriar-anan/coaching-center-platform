"use client";

import { useEffect, useRef, useState } from "react";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { ApiError } from "@/lib/api/errors";
import { useSaveSetup, type AdmissionDetail, type AdmissionSetup, type SetupBody } from "@/lib/api/exam/offline-admin";
import { offlineErrorText } from "./offline-error";
import { installmentPlan, toNumber } from "./offline-model";

/*
 * The setup's draft: what the form shows while it is being typed in. One hook owns it for every card, so the whole setup saves as one
 * PATCH, a moment after the last change, and one status line (Saving, Saved, Not saved) covers all of it.
 */

/** One instalment as the setup table holds it while it is typed in. */
export type PlanRow = { dueOn: string; amount: string };

/**
 * `courseFee` is the total and `installments` the number of monthly instalments it is split into evenly (course-fee-plan.md). Once the
 * admin edits a row, `custom` is true and `rows` are the instalments: the course fee is then their sum (instalment-plan-and-early-payments
 * .md, E1, E2).
 */
export type SetupDraft = {
  batchId: string;
  courseFee: string;
  installments: string;
  custom: boolean;
  rows: PlanRow[];
  admittedOn: string;
  firstDueOn: string;
  courseEndsOn: string;
};

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
    courseFee: text(setup.courseFee),
    installments: text(setup.installments),
    custom: setup.customPlan === true,
    rows: (setup.installmentPlan ?? []).map((row) => ({ dueOn: row.dueOn, amount: text(row.amount) })),
    admittedOn: text(setup.admittedOn),
    firstDueOn: text(setup.firstDueOn),
    courseEndsOn: text(setup.courseEndsOn),
  };
}

export function bodyOf(draft: SetupDraft): SetupBody {
  if (draft.custom) {
    // The admin's own rows: the fee and the count follow from them, so they are not sent (sending them means "split evenly").
    return {
      batchId: draft.batchId || null,
      installmentPlan: draft.rows.map((row) => ({ dueOn: row.dueOn, amount: row.amount.trim() })),
      admittedOn: draft.admittedOn || null,
      courseEndsOn: draft.courseEndsOn || null,
    };
  }
  return {
    batchId: draft.batchId || null,
    courseFee: draft.courseFee.trim() || null,
    installments: /^\d+$/.test(draft.installments.trim()) ? Number(draft.installments.trim()) : null,
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

/**
 * The money and dates of a draft: the instalments it will create, the course fee they add up to, and the first and last due dates.
 * `pastEnd` is true when the last instalment falls after the course end, which the API refuses.
 */
export function setupTotals(draft: SetupDraft) {
  const count = Number(draft.installments);
  const rows = draft.custom
    ? draft.rows.map((row, index) => ({ seq: index + 1, dueOn: row.dueOn, amount: toNumber(row.amount) }))
    : installmentPlan(draft.firstDueOn, Number.isInteger(count) ? count : 0, toNumber(draft.courseFee));
  const fee = draft.custom ? rows.reduce((sum, row) => sum + (Number.isFinite(row.amount) ? row.amount : 0), 0) : toNumber(draft.courseFee);
  const total = rows.reduce((sum, row) => sum + row.amount, 0);
  const lastDue = rows.length > 0 ? rows[rows.length - 1].dueOn : null;
  return {
    fee,
    months: rows.length,
    total,
    rows,
    dueDates: rows.map((row) => row.dueOn),
    firstDue: rows.length > 0 ? rows[0].dueOn : null,
    lastDue,
    pastEnd: Boolean(lastDue && draft.courseEndsOn && lastDue > draft.courseEndsOn),
    rowIssues: draft.custom ? rowIssuesOf(draft) : [],
  };
}

/** What is wrong with one instalment row, as message keys (E1): the same rules the API checks on save. */
export type RowIssue = "amount" | "date" | "order" | "beforeAdmitted" | "afterEnd";

/** The problems of each row of the admin's own instalments, by row index. A row with none has an empty list. */
export function rowIssuesOf(draft: SetupDraft): RowIssue[][] {
  return draft.rows.map((row, index) => {
    const issues: RowIssue[] = [];
    const amount = Number(row.amount.trim());
    if (!row.amount.trim() || !Number.isFinite(amount) || amount < 1 || !/^\d+(\.\d{1,2})?$/.test(row.amount.trim())) {
      issues.push("amount");
    }
    if (!/^\d{4}-\d{2}-\d{2}$/.test(row.dueOn)) {
      issues.push("date");
      return issues;
    }
    const previous = index > 0 ? draft.rows[index - 1].dueOn : "";
    if (previous && row.dueOn <= previous) issues.push("order");
    if (index === 0 && draft.admittedOn && row.dueOn < draft.admittedOn) issues.push("beforeAdmitted");
    if (draft.courseEndsOn && row.dueOn > draft.courseEndsOn) issues.push("afterEnd");
    return issues;
  });
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
