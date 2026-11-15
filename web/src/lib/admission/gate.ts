import type { AdmissionState } from "@/lib/api/exam/offline-admission";

/*
 * The admission gate for the student site (plan 4.5). A student whose admission is not yet onboarded is "pending": the
 * student may use the form and its print view, and nothing else on the student site. Pure functions, so the shell and
 * the tests share one rule.
 */

export const ADMISSION_HOME = "/admission/form";

const PENDING_STATES: readonly AdmissionState[] = ["AWAITING_FORM", "SUBMITTED", "CHANGES_REQUESTED", "READY"];

/**
 * The admission pages: the student's form and its print view, the batch cards and batch pages, the apply and reopen forms, and the join
 * page. A pending student may open these; every other student page sends them to the form.
 */
const OPEN_TO_PENDING = /^\/admission(\/.*)?$/;

export function isPendingAdmission(state: AdmissionState | null | undefined): boolean {
  return state != null && PENDING_STATES.includes(state);
}

export function isAdmissionPath(pathname: string): boolean {
  return OPEN_TO_PENDING.test(pathname);
}

/**
 * Where a student must be sent from this page, or null to stay. Only a pending student is moved, and only off a page
 * other than the form and its print view. Anyone else (staff, visitors, a student with no admission) is unaffected.
 */
export function admissionRedirect({
  pathname,
  role,
  state,
}: {
  pathname: string;
  role: string | null | undefined;
  state: AdmissionState | null | undefined;
}): string | null {
  if (role !== "STUDENT" || !isPendingAdmission(state) || isAdmissionPath(pathname)) {
    return null;
  }
  return ADMISSION_HOME;
}
