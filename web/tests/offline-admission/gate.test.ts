import { describe, expect, it } from "vitest";
import { admissionRedirect, admissionStep, admissionStepHref, isAdmissionPath, isLockedOut, isPendingAdmission } from "@/lib/admission/gate";

describe("the admission gate (plan 4.5)", () => {
  it("treats the four open states as pending and the rest as not", () => {
    for (const state of ["AWAITING_FORM", "SUBMITTED", "CHANGES_REQUESTED", "READY"] as const) {
      expect(isPendingAdmission(state)).toBe(true);
    }
    for (const state of ["ONBOARDED", "CLOSED"] as const) {
      expect(isPendingAdmission(state)).toBe(false);
    }
    expect(isPendingAdmission(null)).toBe(false);
    expect(isPendingAdmission(undefined)).toBe(false);
  });

  it("opens the admission pages (the form, the print view, the cards, apply, join, reopen) to a pending student", () => {
    expect(isAdmissionPath("/admission")).toBe(true);
    expect(isAdmissionPath("/admission/")).toBe(true);
    expect(isAdmissionPath("/admission/form")).toBe(true);
    expect(isAdmissionPath("/admission/print")).toBe(true);
    expect(isAdmissionPath("/admission/apply/b-1")).toBe(true);
    expect(isAdmissionPath("/admission/join")).toBe(true);
    expect(isAdmissionPath("/admissions")).toBe(false);
    expect(isAdmissionPath("/my-modules")).toBe(false);
    expect(isAdmissionPath("/")).toBe(false);
  });

  it("sends a held student from every other student page to the form", () => {
    for (const pathname of ["/", "/my-modules", "/results", "/notices", "/account", "/mcq/c/civil"]) {
      expect(admissionRedirect({ pathname, role: "STUDENT", locked: true })).toBe("/admission/form");
    }
  });

  it("leaves a held student on the admission pages", () => {
    expect(admissionRedirect({ pathname: "/admission/form", role: "STUDENT", locked: true })).toBeNull();
    expect(admissionRedirect({ pathname: "/admission/print", role: "STUDENT", locked: true })).toBeNull();
    expect(admissionRedirect({ pathname: "/admission/join", role: "STUDENT", locked: true })).toBeNull();
  });

  it("never moves a student who is not held, or anyone who is not a student", () => {
    expect(admissionRedirect({ pathname: "/my-modules", role: "STUDENT", locked: false })).toBeNull();
    expect(admissionRedirect({ pathname: "/my-modules", role: "MASTER_ADMIN", locked: true })).toBeNull();
    expect(admissionRedirect({ pathname: "/my-modules", role: null, locked: true })).toBeNull();
  });
});

describe("who is held on the form (the API's rule, admission-access-plan.md)", () => {
  it("holds a walk-in (desk) student whose admission is not onboarded", () => {
    for (const state of ["AWAITING_FORM", "SUBMITTED", "CHANGES_REQUESTED", "READY"] as const) {
      expect(isLockedOut([{ state, source: "DESK" }])).toBe(true);
    }
  });

  it("never holds a student who applied online or from their account, in any state", () => {
    for (const source of ["PUBLIC", "ACCOUNT"] as const) {
      for (const state of ["AWAITING_FORM", "SUBMITTED", "CHANGES_REQUESTED", "READY"] as const) {
        expect(isLockedOut([{ state, source }])).toBe(false);
      }
    }
  });

  it("never holds a student with an onboarded batch, even with a desk admission in progress", () => {
    expect(isLockedOut([{ state: "AWAITING_FORM", source: "DESK" }, { state: "ONBOARDED", source: "ACCOUNT" }])).toBe(false);
  });

  it("does not hold a student with no admission, only closed ones, or an unread list", () => {
    expect(isLockedOut([])).toBe(false);
    expect(isLockedOut([{ state: "CLOSED", source: "DESK" }])).toBe(false);
    expect(isLockedOut(undefined)).toBe(false);
    expect(isLockedOut(null)).toBe(false);
  });
});

describe("the step of an application", () => {
  it("names each step and where its action leads", () => {
    expect(admissionStep({ state: "AWAITING_FORM" })).toBe("FORM");
    expect(admissionStep({ state: "CHANGES_REQUESTED" })).toBe("CHANGES");
    expect(admissionStep({ state: "SUBMITTED" })).toBe("REVIEW");
    expect(admissionStep({ state: "READY" })).toBe("REVIEW");
    expect(admissionStep({ state: "READY", joinCodePending: true })).toBe("JOIN");
    expect(admissionStep({ state: "ONBOARDED" })).toBe("ADMITTED");
    expect(admissionStep({ state: "CLOSED" })).toBe("CLOSED");
    expect(admissionStepHref("FORM")).toBe("/admission/form");
    expect(admissionStepHref("CHANGES")).toBe("/admission/form");
    expect(admissionStepHref("REVIEW")).toBe("/admission/print");
    expect(admissionStepHref("JOIN")).toBe("/admission/join");
    expect(admissionStepHref("ADMITTED")).toBe("/offline-activities");
    expect(admissionStepHref("CLOSED")).toBeNull();
  });
});
