import { describe, expect, it } from "vitest";
import { examListQuery } from "@/lib/api/exam/staff-exams";
import type { CategoryTreeNode, StaffExam } from "@/lib/api/exam/staff-catalog";
import {
  categoryNames,
  defaultGroupOpen,
  emptyStateFor,
  groupExams,
  readGroupOpen,
  writeGroupOpen,
  GROUP_OPEN_KEY,
} from "@/components/staff/exams/exam-groups";

const pkg = (id: string, titleEn: string) => ({ id, titleEn, titleBn: `${titleEn} bn` });
const exam = (id: string, extra: Partial<StaffExam> = {}) => ({ id, titleEn: id, titleBn: id, categoryId: null, packages: [], ...extra }) as StaffExam;

const categories = new Map([
  ["cat-1", { titleEn: "BJS", titleBn: "বিজেএস" }],
  ["cat-2", { titleEn: "Bar", titleBn: "বার" }],
]);

describe("grouping the exams", () => {
  const a = exam("A", { packages: [pkg("p2", "Beta"), pkg("p1", "Alpha")], categoryId: "cat-1" });
  const b = exam("B", { packages: [pkg("p2", "Beta")], categoryId: "cat-2" });
  const c = exam("C", { packages: [], categoryId: "cat-1" });

  it("by package: an exam in two packages is in both, groups sort by name, exams in none go last", () => {
    const groups = groupExams([a, b, c], "package", categories);

    expect(groups.map((g) => g.key)).toEqual(["package:p1", "package:p2", "none"]);
    expect(groups[0].exams.map((e) => e.id)).toEqual(["A"]);
    expect(groups[1].exams.map((e) => e.id)).toEqual(["A", "B"]);
    expect(groups[2].exams.map((e) => e.id)).toEqual(["C"]);
  });

  it("by category: one group per category, and a last group for exams with no category", () => {
    const groups = groupExams([a, b, c], "category", categories);

    // Groups sort by English name: "Bar" before "BJS".
    expect(groups.map((g) => g.key)).toEqual(["category:cat-2", "category:cat-1"]);
    expect(groups[1].titleEn).toBe("BJS");
    expect(groups[1].exams.map((e) => e.id)).toEqual(["A", "C"]);
    const withNone = groupExams([a, exam("D")], "category", categories);
    expect(withNone.at(-1)).toMatchObject({ key: "none", kind: "none" });
  });

  it("flat: one group with every exam, in the order the server sent them", () => {
    const groups = groupExams([c, a, b], "flat", categories);

    expect(groups).toHaveLength(1);
    expect(groups[0].exams.map((e) => e.id)).toEqual(["C", "A", "B"]);
  });

  it("the category names come from the tree at any depth", () => {
    const tree = [
      { id: "root", titleEn: "Root", titleBn: "রুট", children: [{ id: "child", titleEn: "Child", titleBn: "চাইল্ড", children: [] }] },
    ] as unknown as CategoryTreeNode[];

    expect(categoryNames(tree).get("child")).toEqual({ titleEn: "Child", titleBn: "চাইল্ড" });
  });
});

describe("the filters sent to the server", () => {
  it("sends only the filters that are set, the search trimmed, and popular and pending only when on", () => {
    expect(examListQuery({})).toBe("page=0&size=200");
    const query = new URLSearchParams(
      examListQuery({ packageId: "p1", type: "MCQ", mode: "LIVE", status: "PUBLISHED", phase: "UPCOMING", q: "  rahim  ", popular: true, pending: false }),
    );
    expect(query.get("packageId")).toBe("p1");
    expect(query.get("type")).toBe("MCQ");
    expect(query.get("phase")).toBe("UPCOMING");
    expect(query.get("q")).toBe("rahim");
    expect(query.get("popular")).toBe("true");
    expect(query.has("pending")).toBe(false);
    expect(query.has("categoryId")).toBe(false);
  });
});

describe("the groups start open or closed", () => {
  it("eight groups or fewer start open; more than eight start collapsed", () => {
    expect(defaultGroupOpen(8)).toBe(true);
    expect(defaultGroupOpen(9)).toBe(false);
  });

  it("remembers a group's open state in this browser", () => {
    const store = new Map<string, string>();
    const storage = { getItem: (k: string) => store.get(k) ?? null, setItem: (k: string, v: string) => void store.set(k, v) };
    writeGroupOpen("package:p1", false, storage);
    expect(readGroupOpen("package:p1", true, storage)).toBe(false);
    expect(readGroupOpen("package:p2", true, storage)).toBe(true);
    expect(store.has(GROUP_OPEN_KEY)).toBe(true);
  });

  it("falls back to the default when storage is blocked or holds junk", () => {
    const blocked = {
      getItem: (): string | null => {
        throw new Error("blocked");
      },
      setItem: (): void => {
        throw new Error("full");
      },
    };
    expect(readGroupOpen("package:p1", false, blocked)).toBe(false);
    expect(() => writeGroupOpen("package:p1", true, blocked)).not.toThrow();
    expect(readGroupOpen("package:p1", true, { getItem: () => "not json" })).toBe(true);
  });
});

describe("the empty and filtered-empty states", () => {
  it("no exams at all is the empty state; no exams under filters is the filtered-empty state", () => {
    expect(emptyStateFor(0, false)).toBe("empty");
    expect(emptyStateFor(0, true)).toBe("filtered");
  });

  it("rows show no empty state", () => {
    expect(emptyStateFor(3, true)).toBeNull();
    expect(emptyStateFor(3, false)).toBeNull();
  });
});
