import { describe, expect, it } from "vitest";
import { destinationFor, safeStudentNext } from "@/lib/auth/session-context";
import { roleInfo } from "@/lib/auth/guards";
import { fixtures } from "../helpers/fixtures";

describe("safeStudentNext", () => {
  it("accepts same-site student paths", () => {
    expect(safeStudentNext("/exam/abc/sit")).toBe("/exam/abc/sit");
    expect(safeStudentNext("/mcq?page=2")).toBe("/mcq?page=2");
  });

  it("refuses external, protocol-relative and staff targets", () => {
    expect(safeStudentNext("https://evil.example.com")).toBeNull();
    expect(safeStudentNext("//evil.example.com")).toBeNull();
    expect(safeStudentNext("/\\evil.example.com")).toBeNull();
    expect(safeStudentNext("/staff/students")).toBeNull();
  });

  it("refuses auth pages, so login does not loop", () => {
    expect(safeStudentNext("/login")).toBeNull();
    expect(safeStudentNext("/register?x=1")).toBeNull();
  });

  it("refuses an empty value", () => {
    expect(safeStudentNext(null)).toBeNull();
    expect(safeStudentNext("")).toBeNull();
  });
});

describe("destinationFor", () => {
  it("sends a student to ?next= when it is safe, else the student home", () => {
    expect(destinationFor(fixtures.student, "/exam/abc")).toBe("/exam/abc");
    expect(destinationFor(fixtures.student, "/staff")).toBe("/");
  });

  it("sends staff to the staff home whatever ?next= says", () => {
    expect(destinationFor(fixtures.masterAdmin, "/exam/abc")).toBe("/staff");
    expect(destinationFor(fixtures.instructor, null)).toBe("/staff");
  });
});

describe("roleInfo", () => {
  it("lets only the Master Admin price, delete and see audit", () => {
    expect(roleInfo("MASTER_ADMIN")).toMatchObject({ canEditPrice: true, canDelete: true, canSeeAudit: true });
    expect(roleInfo("SYSTEM_ADMIN")).toMatchObject({ canEditPrice: false, canDelete: false, canSeeAudit: false });
  });

  it("lets the System Admin author and see payments, but not publish", () => {
    expect(roleInfo("SYSTEM_ADMIN")).toMatchObject({ canAuthor: true, canSeePayments: true, canPublishResult: false });
  });

  it("lets the instructor evaluate and nothing else", () => {
    const info = roleInfo("INSTRUCTOR");
    expect(info).toMatchObject({ canEvaluate: true, canAuthor: false, canSeePayments: false, canManageRoster: false });
  });

  it("gives a student no staff capability", () => {
    const info = roleInfo("STUDENT");
    expect(info.isStudent).toBe(true);
    expect(info.isStaff).toBe(false);
    expect(info.canEvaluate).toBe(false);
  });

  it("gives the roster to the Master Admin only", () => {
    expect(roleInfo("MASTER_ADMIN").canManageRoster).toBe(true);
    expect(roleInfo("SYSTEM_ADMIN").canManageRoster).toBe(false);
  });
});
