import { describe, expect, it } from "vitest";
import {
  directAlreadyActive,
  heldHow,
  heldWarning,
  overlapItems,
  rosterBlocks,
} from "@/components/staff/enrol/overlap-model";
import type { GrantQuote, OverlapItem } from "@/lib/api/exam/staff-admin";

const BASE: GrantQuote = { free: false, moduleOnly: false };

const item = (overrides: Partial<OverlapItem> = {}): OverlapItem => ({
  kind: "EXAM",
  id: "e1",
  titleEn: "CrPC",
  titleBn: "সিআরপিসি",
  via: "ONLINE",
  planId: null,
  paid: null,
  due: null,
  ...overrides,
});

describe("heldHow", () => {
  it("maps each way of holding access to its copy key", () => {
    expect(heldHow("ONLINE")).toBe("ONLINE");
    expect(heldHow("DIRECT")).toBe("DIRECT");
    expect(heldHow("BUNDLE")).toBe("BUNDLE");
    expect(heldHow("ROSTER")).toBe("ROSTER");
    expect(heldHow("PACKAGE")).toBe("PACKAGE");
  });

  it("reads an unknown or missing way as OTHER, never as a made-up one", () => {
    expect(heldHow("SOMETHING_NEW")).toBe("OTHER");
    expect(heldHow(null)).toBe("OTHER");
    expect(heldHow(undefined)).toBe("OTHER");
  });
});

describe("heldWarning", () => {
  it("shows the overlap block whenever there are overlap items, even when access is also held", () => {
    expect(heldWarning({ ...BASE, accessHeld: true, overlaps: [item()] }, false)).toBe("OVERLAPS");
    expect(heldWarning({ ...BASE, overlaps: [item()] }, false)).toBe("OVERLAPS");
  });

  it("falls back to the generic note when access is held but there is no overlap item", () => {
    expect(heldWarning({ ...BASE, accessHeld: true, overlaps: [] }, false)).toBe("ACCESS_HELD");
    expect(heldWarning({ ...BASE, accessHeld: true }, false)).toBe("ACCESS_HELD");
  });

  it("does not show the generic note for a free exam", () => {
    expect(heldWarning({ ...BASE, accessHeld: true }, true)).toBe("NONE");
  });

  it("shows nothing when the quote has no warnings, or no quote has arrived", () => {
    expect(heldWarning(BASE, false)).toBe("NONE");
    expect(heldWarning(undefined, false)).toBe("NONE");
  });
});

describe("overlapItems", () => {
  it("returns an empty list when the quote has none", () => {
    expect(overlapItems(undefined)).toEqual([]);
    expect(overlapItems(BASE)).toEqual([]);
    expect(overlapItems({ ...BASE, overlaps: null })).toEqual([]);
  });

  it("leaves out an item with no id", () => {
    const good = item({ id: "e1" });
    const broken = item({ id: "" });
    expect(overlapItems({ ...BASE, overlaps: [good, broken] })).toEqual([good]);
  });
});

describe("rosterBlocks", () => {
  it("blocks a package enrolment while the student is on its roster", () => {
    expect(rosterBlocks({ ...BASE, onRoster: true }, "MODULE")).toBe(true);
  });

  it("never blocks an exam enrolment, because the roster belongs to the package", () => {
    expect(rosterBlocks({ ...BASE, onRoster: true }, "EXAM")).toBe(false);
  });

  it("does not block when the student is not on the roster, or the field is absent", () => {
    expect(rosterBlocks({ ...BASE, onRoster: false }, "MODULE")).toBe(false);
    expect(rosterBlocks(BASE, "MODULE")).toBe(false);
    expect(rosterBlocks(undefined, "MODULE")).toBe(false);
  });
});

describe("directAlreadyActive", () => {
  it("is true only when a direct grant is active on the target", () => {
    expect(directAlreadyActive({ ...BASE, directGrantActive: true })).toBe(true);
    expect(directAlreadyActive({ ...BASE, directGrantActive: false })).toBe(false);
    expect(directAlreadyActive(BASE)).toBe(false);
    expect(directAlreadyActive(undefined)).toBe(false);
  });
});
