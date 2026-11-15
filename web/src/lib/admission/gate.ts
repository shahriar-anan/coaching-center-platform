import type { AdmissionState, AdmissionView } from "@/lib/api/exam/offline-admission";

/*
 * The admission gate for the student site (plan 4.5, admission-access-plan.md). It follows the API's rule
 * (OfflineAdmissionGate): only a walk-in (desk) admission that is not onboarded holds the student on the form, and never once
 * any of their batches is onboarded. An online application, or one from an account, takes nothing away. Pure functions, so
 * the shell, the pages and the tests share one rule.
 */

export const ADMISSION_HOME = "/admission/form";

const PENDING_STATES: readonly AdmissionState[] = ["AWAITING_FORM", "SUBMITTED", "CHANGES_REQUESTED", "READY"];

/**
 * The admission pages: the student's form and its print view, the batch cards and batch pages, the apply and reopen forms, and the join
 * page. A held student may open these; every other student page sends them to the form.
 */
const OPEN_TO_PENDING = /^\/admission(\/.*)?$/;

/** True for the states before onboarding: the form, the review, and Ready. */
export function isPendingAdmission(state: AdmissionState | null | undefined): boolean {
  return state != null && PENDING_STATES.includes(state);
}

export function isAdmissionPath(pathname: string): boolean {
  return OPEN_TO_PENDING.test(pathname);
}

type GateAdmission = Pick<AdmissionView, "state" | "source">;

/**
 * True when the student is held on the form: they have a desk admission that is not onboarded, and no onboarded batch. The same rule
 * as the API's, which refuses exams and checkout to such a student.
 */
export function isLockedOut(admissions: readonly GateAdmission[] | null | undefined): boolean {
  if (!Array.isArray(admissions) || admissions.some((admission) => admission.state === "ONBOARDED")) {
    return false;
  }
  return admissions.some((admission) => admission.source === "DESK" && isPendingAdmission(admission.state));
}

/**
 * Where a student must be sent from this page, or null to stay. Only a held student is moved, and only off a page other than the
 * admission pages. Anyone else (staff, visitors, a student with no admission or an online application) is unaffected.
 */
export function admissionRedirect({
  pathname,
  role,
  locked,
}: {
  pathname: string;
  role: string | null | undefined;
  locked: boolean;
}): string | null {
  if (role !== "STUDENT" || !locked || isAdmissionPath(pathname)) {
    return null;
  }
  return ADMISSION_HOME;
}

/** The step an application is at, as the student's screens name it (admission-access-plan.md section 3). */
export type AdmissionStep = "FORM" | "CHANGES" | "REVIEW" | "JOIN" | "ADMITTED" | "CLOSED";

export function admissionStep(admission: Pick<AdmissionView, "state" | "joinCodePending">): AdmissionStep {
  switch (admission.state) {
    case "AWAITING_FORM":
      return "FORM";
    case "CHANGES_REQUESTED":
      return "CHANGES";
    case "READY":
      return admission.joinCodePending ? "JOIN" : "REVIEW";
    case "ONBOARDED":
      return "ADMITTED";
    case "CLOSED":
      return "CLOSED";
    default:
      return "REVIEW";
  }
}

/** Where the action of a step leads, or null when the step has no action of its own. */
export function admissionStepHref(step: AdmissionStep): string | null {
  switch (step) {
    case "FORM":
    case "CHANGES":
      return ADMISSION_HOME;
    case "REVIEW":
      return "/admission/print";
    case "JOIN":
      return "/admission/join";
    case "ADMITTED":
      return "/offline-activities";
    default:
      return null;
  }
}
