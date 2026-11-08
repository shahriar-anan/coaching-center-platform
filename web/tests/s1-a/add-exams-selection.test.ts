import { describe, expect, it } from "vitest";
import {
  deselectIds,
  isSelectable,
  selectAllVisible,
  selectableIds,
  summarise,
  toggleOne,
} from "@/components/staff/catalog/add-exams-selection";
import type { StaffExam } from "@/lib/api/exam/staff-catalog";

/** A minimal exam row; only the fields the selection reads are set. */
function row(id: string, status: StaffExam["status"] = "PUBLISHED"): StaffExam {
  return { id, status } as StaffExam;
}

describe("selectable exams", () => {
  it("refuses an exam already in the package, and a withdrawn one", () => {
    const members = new Set(["in"]);
    expect(isSelectable(row("in"), members)).toBe(false);
    expect(isSelectable(row("gone", "ARCHIVED"), members)).toBe(false);
    expect(isSelectable(row("draft", "DRAFT"), members)).toBe(true);
    expect(isSelectable(row("open"), members)).toBe(true);
  });

  it("keeps row order and lists each id once", () => {
    const rows = [row("a"), row("in"), row("b"), row("a"), row("gone", "ARCHIVED"), row("c", "DRAFT")];
    expect(selectableIds(rows, new Set(["in"]))).toEqual(["a", "b", "c"]);
  });
});

describe("selection changes", () => {
  it("toggles one id without changing the set it was given", () => {
    const before = new Set(["a"]);
    const added = toggleOne(before, "b");
    expect(Array.from(added)).toEqual(["a", "b"]);
    expect(Array.from(before)).toEqual(["a"]);
    expect(Array.from(toggleOne(added, "a"))).toEqual(["b"]);
  });

  it("selects every selectable row, keeping what was already picked", () => {
    const rows = [row("a"), row("in"), row("b", "ARCHIVED"), row("c")];
    const result = selectAllVisible(new Set(["old"]), rows, new Set(["in"]));
    expect(Array.from(result).sort()).toEqual(["a", "c", "old"]);
  });

  it("removes only the given ids", () => {
    expect(Array.from(deselectIds(new Set(["a", "b", "c"]), ["b", "z"])).sort()).toEqual(["a", "c"]);
  });
});

describe("selection summary", () => {
  it("counts the selection and the drafts among it", () => {
    const rows = [row("a", "DRAFT"), row("b"), row("c", "DRAFT")];
    expect(summarise(new Set(["a", "b"]), rows)).toEqual({ count: 2, drafts: 1 });
  });

  it("counts an id that is not in the rows as no draft", () => {
    expect(summarise(new Set(["a", "unseen"]), [row("a", "DRAFT")])).toEqual({ count: 2, drafts: 1 });
    expect(summarise(new Set(["unseen"]), [])).toEqual({ count: 1, drafts: 0 });
  });
});
