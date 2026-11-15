import { describe, expect, it } from "vitest";
import { admissionRedirect, isAdmissionPath, isPendingAdmission } from "@/lib/admission/gate";

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

  it("sends a pending student from every other student page to the form", () => {
    for (const pathname of ["/", "/my-modules", "/results", "/notices", "/account", "/mcq/c/civil"]) {
      expect(admissionRedirect({ pathname, role: "STUDENT", state: "AWAITING_FORM" })).toBe("/admission/form");
    }
  });

  it("leaves a pending student on the form and the print view", () => {
    expect(admissionRedirect({ pathname: "/admission/form", role: "STUDENT", state: "CHANGES_REQUESTED" })).toBeNull();
    expect(admissionRedirect({ pathname: "/admission/print", role: "STUDENT", state: "SUBMITTED" })).toBeNull();
  });

  it("does not move a student whose admission is onboarded or closed", () => {
    expect(admissionRedirect({ pathname: "/my-modules", role: "STUDENT", state: "ONBOARDED" })).toBeNull();
    expect(admissionRedirect({ pathname: "/my-modules", role: "STUDENT", state: "CLOSED" })).toBeNull();
  });

  it("does not move a student with no admission (the query gives null), or anyone who is not a student", () => {
    expect(admissionRedirect({ pathname: "/my-modules", role: "STUDENT", state: undefined })).toBeNull();
    expect(admissionRedirect({ pathname: "/my-modules", role: "MASTER_ADMIN", state: "AWAITING_FORM" })).toBeNull();
    expect(admissionRedirect({ pathname: "/my-modules", role: null, state: "AWAITING_FORM" })).toBeNull();
  });
});
