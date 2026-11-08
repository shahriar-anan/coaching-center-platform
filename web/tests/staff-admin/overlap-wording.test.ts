import { describe, expect, it } from "vitest";
import type { OverlapItem } from "@/lib/api/exam/staff-admin";
import { isMovedFromExam, moneyLineIndexes, overlapHow } from "@/components/staff/enrol/overlap-model";

const base = (overrides: Partial<OverlapItem>): OverlapItem => ({
  kind: "EXAM",
  id: "e1",
  titleEn: "Penal Code",
  titleBn: "দণ্ডবিধি",
  via: "DIRECT",
  ...overrides,
});

describe("overlapHow (plan 14.11, P53 to P55)", () => {
  it("names the package for a roster route through a named package", () => {
    expect(overlapHow(base({ via: "ROSTER", packageTitleEn: "Bar Council" }))).toBe("ROSTER_PACKAGE");
  });

  it("keeps the short roster text when no package name came", () => {
    expect(overlapHow(base({ via: "ROSTER" }))).toBe("ROSTER");
  });

  it("keeps the package route and the others as they are", () => {
    expect(overlapHow(base({ via: "PACKAGE", packageTitleEn: "Bar Council" }))).toBe("PACKAGE");
    expect(overlapHow(base({ via: "ONLINE" }))).toBe("ONLINE");
    expect(overlapHow(base({ via: "SOMETHING_NEW" as OverlapItem["via"] }))).toBe("OTHER");
  });
});

describe("isMovedFromExam (plan 14.11, D26)", () => {
  it("is true for an exam with money owed, enrolled on a package", () => {
    expect(isMovedFromExam(base({ planId: "p1", paid: 100, due: 200 }), "MODULE")).toBe(true);
  });

  it("is false on an exam enrolment, a plan-less exam, or a package item", () => {
    expect(isMovedFromExam(base({ planId: "p1" }), "EXAM")).toBe(false);
    expect(isMovedFromExam(base({}), "MODULE")).toBe(false);
    expect(isMovedFromExam(base({ kind: "PACKAGE", planId: "p1" }), "MODULE")).toBe(false);
  });
});

describe("moneyLineIndexes (plan 14.11, P58 and P59)", () => {
  it("shows the money once per plan, on the first item that carries it", () => {
    const items = [
      base({ id: "e1", planId: "p1" }),
      base({ id: "e2", planId: "p1" }),
      base({ id: "e3", planId: "p2" }),
      base({ id: "e4" }),
    ];
    expect([...moneyLineIndexes(items)].sort()).toEqual([0, 2]);
  });

  it("shows nothing for items without a plan", () => {
    expect(moneyLineIndexes([base({}), base({ id: "e2" })]).size).toBe(0);
  });
});
