import { describe, expect, it } from "vitest";
import {
  alsoInCount,
  hasPendingDot,
  movedOrder,
  parseDiscount,
  priceOfRow,
} from "@/components/staff/catalog/package-rows";
import { membershipRequest, type StaffExam } from "@/lib/api/exam/staff-catalog";

/** A minimal exam row; the fields the row model reads are set, the rest are left out. */
function row(overrides: Partial<StaffExam> = {}): StaffExam {
  return { id: "e1", price: null, packages: [], pendingChanges: false, ...overrides } as StaffExam;
}

describe("exam row price", () => {
  it("shows the list price with a discounted price beside it, when a discount applies", () => {
    const discounted = row({
      price: 500,
      pricing: { price: 500, discountPercent: 10, discountEndsAt: null, effectivePrice: 450, free: false, moduleOnly: false },
    });
    expect(priceOfRow(discounted)).toEqual({ list: 500, current: 450, discounted: true, free: false });
  });

  it("shows one price when there is no discount", () => {
    const plain = row({ price: 300, pricing: { price: 300, discountPercent: null, discountEndsAt: null, effectivePrice: 300, free: false, moduleOnly: false } });
    expect(priceOfRow(plain)).toEqual({ list: 300, current: 300, discounted: false, free: false });
  });

  it("marks a free exam as free, with no price", () => {
    expect(priceOfRow(row({ free: true, price: null }))).toEqual({ list: null, current: null, discounted: false, free: true });
  });
});

describe("exam row in several packages", () => {
  const a = { id: "pkg-a", titleEn: "A", titleBn: "ক" };
  const b = { id: "pkg-b", titleEn: "B", titleBn: "খ" };

  it("counts the other packages, not this one", () => {
    expect(alsoInCount(row({ packages: [a, b] }), "pkg-a")).toBe(1);
    expect(alsoInCount(row({ packages: [a] }), "pkg-a")).toBe(0);
  });

  it("counts none when the exam has no package list", () => {
    expect(alsoInCount(row({ packages: undefined }), "pkg-a")).toBe(0);
  });

  it("shows the pending dot only when a staged change waits", () => {
    expect(hasPendingDot(row({ pendingChanges: true }))).toBe(true);
    expect(hasPendingDot(row({ pendingChanges: false }))).toBe(false);
    expect(hasPendingDot(row({ pendingChanges: undefined }))).toBe(false);
  });
});

describe("reordering a package's exams", () => {
  const ids = ["a", "b", "c"];

  it("moves an exam one place up or down", () => {
    expect(movedOrder(ids, 1, -1)).toEqual(["b", "a", "c"]);
    expect(movedOrder(ids, 1, 1)).toEqual(["a", "c", "b"]);
  });

  it("leaves the order as it is past either end, and does not change the input", () => {
    expect(movedOrder(ids, 0, -1)).toBe(ids);
    expect(movedOrder(ids, 2, 1)).toBe(ids);
    expect(ids).toEqual(["a", "b", "c"]);
  });
});

describe("discount entry", () => {
  it("accepts an empty entry (no discount) and whole percentages from 1 to 99", () => {
    expect(parseDiscount("")).toEqual({ ok: true, value: null });
    expect(parseDiscount("1")).toEqual({ ok: true, value: 1 });
    expect(parseDiscount(" 99 ")).toEqual({ ok: true, value: 99 });
  });

  it("refuses zero, 100, decimals and text", () => {
    for (const text of ["0", "100", "12.5", "abc", "-5"]) {
      expect(parseDiscount(text)).toEqual({ ok: false });
    }
  });
});

describe("membership request shapes", () => {
  it("adds an exam by its id in the path", () => {
    expect(membershipRequest("pkg-1", { kind: "add", examId: "e-9" })).toEqual({
      method: "POST",
      path: "/api/v1/live-exam-modules/pkg-1/exams/e-9",
    });
  });

  it("removes an exam by its id in the path", () => {
    expect(membershipRequest("pkg-1", { kind: "remove", examId: "e-9" })).toEqual({
      method: "DELETE",
      path: "/api/v1/live-exam-modules/pkg-1/exams/e-9",
    });
  });

  it("reorders with the whole list, in the new order", () => {
    expect(membershipRequest("pkg-1", { kind: "reorder", examIds: ["b", "a"] })).toEqual({
      method: "PATCH",
      path: "/api/v1/live-exam-modules/pkg-1/exams/order",
      body: { examIds: ["b", "a"] },
    });
  });
});
