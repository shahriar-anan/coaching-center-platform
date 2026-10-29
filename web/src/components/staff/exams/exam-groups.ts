import type { CategoryTreeNode, PackageRef, StaffExam } from "@/lib/api/exam/staff-catalog";

/** How the Exams page lists its rows. */
export type ExamView = "package" | "category" | "flat";

/** One group on the page. An exam in several packages is in each of their groups. */
export type ExamGroup = {
  /** Stable across renders and visits: `package:<id>`, `category:<id>`, `none`, or `all` for the flat list. */
  key: string;
  kind: "package" | "category" | "none" | "all";
  titleEn: string;
  titleBn: string;
  exams: StaffExam[];
};

/** More groups than this start collapsed, so a long list opens as headings. */
export const GROUP_COLLAPSE_OVER = 8;
/** Rows shown per group before "Load more". */
export const GROUP_PAGE = 20;

type Names = { titleEn: string; titleBn: string };

const byTitle = (a: ExamGroup, b: ExamGroup) => a.titleEn.localeCompare(b.titleEn);

/**
 * The groups for a view. By package: one group per package an exam is in, and a last group for exams in
 * no package. By category: one group per category, and a last group for exams with no category. Flat:
 * one group with every exam. Exams keep the order the server sent them in.
 */
export function groupExams(exams: StaffExam[], view: ExamView, categories: Map<string, Names>): ExamGroup[] {
  if (view === "flat") {
    return [{ key: "all", kind: "all", titleEn: "", titleBn: "", exams }];
  }

  if (view === "category") {
    const byCategory = new Map<string, StaffExam[]>();
    const rest: StaffExam[] = [];
    for (const exam of exams) {
      if (!exam.categoryId) {
        rest.push(exam);
        continue;
      }
      byCategory.set(exam.categoryId, [...(byCategory.get(exam.categoryId) ?? []), exam]);
    }
    const groups: ExamGroup[] = [...byCategory].map(([id, list]) => ({
      key: `category:${id}`,
      kind: "category",
      titleEn: categories.get(id)?.titleEn ?? "",
      titleBn: categories.get(id)?.titleBn ?? "",
      exams: list,
    }));
    groups.sort(byTitle);
    if (rest.length > 0) {
      groups.push({ key: "none", kind: "none", titleEn: "", titleBn: "", exams: rest });
    }
    return groups;
  }

  const byPackage = new Map<string, { ref: PackageRef; exams: StaffExam[] }>();
  const rest: StaffExam[] = [];
  for (const exam of exams) {
    const refs = exam.packages ?? [];
    if (refs.length === 0) {
      rest.push(exam);
      continue;
    }
    for (const ref of refs) {
      const entry = byPackage.get(ref.id) ?? { ref, exams: [] };
      entry.exams.push(exam);
      byPackage.set(ref.id, entry);
    }
  }
  const groups: ExamGroup[] = [...byPackage.values()].map(({ ref, exams: list }) => ({
    key: `package:${ref.id}`,
    kind: "package",
    titleEn: ref.titleEn,
    titleBn: ref.titleBn,
    exams: list,
  }));
  groups.sort(byTitle);
  if (rest.length > 0) {
    groups.push({ key: "none", kind: "none", titleEn: "", titleBn: "", exams: rest });
  }
  return groups;
}

/** The category names by id, from the tree, at any depth. */
export function categoryNames(nodes: CategoryTreeNode[] | undefined): Map<string, Names> {
  const names = new Map<string, Names>();
  const walk = (list: CategoryTreeNode[]) => {
    for (const node of list) {
      names.set(node.id, { titleEn: node.titleEn, titleBn: node.titleBn });
      walk(node.children);
    }
  };
  walk(nodes ?? []);
  return names;
}

/** Groups start open when there are few of them, and collapsed when there are many. */
export function defaultGroupOpen(groupCount: number): boolean {
  return groupCount <= GROUP_COLLAPSE_OVER;
}

/** The key the open and closed state of the groups is stored under, in this browser. */
export const GROUP_OPEN_KEY = "cc-staff-exam-groups";

function browserStorage(): Storage | null {
  try {
    return typeof window === "undefined" ? null : window.localStorage;
  } catch {
    // Some browsers throw on access when storage is blocked.
    return null;
  }
}

/** The stored open state of one group, or the fallback when none is stored or storage is blocked. */
export function readGroupOpen(key: string, fallback: boolean, storage: Pick<Storage, "getItem"> | null = browserStorage()): boolean {
  try {
    const raw = storage?.getItem(GROUP_OPEN_KEY);
    const map = raw ? (JSON.parse(raw) as Record<string, unknown>) : {};
    const value = map[key];
    return typeof value === "boolean" ? value : fallback;
  } catch {
    return fallback;
  }
}

/** Stores the open state of one group. Blocked or full storage is ignored: the choice then lasts for this page. */
export function writeGroupOpen(key: string, open: boolean, storage: Pick<Storage, "getItem" | "setItem"> | null = browserStorage()): void {
  try {
    const raw = storage?.getItem(GROUP_OPEN_KEY);
    const map = raw ? (JSON.parse(raw) as Record<string, unknown>) : {};
    storage?.setItem(GROUP_OPEN_KEY, JSON.stringify({ ...map, [key]: open }));
  } catch {
    // Nothing to do: the group still opens and closes for this visit.
  }
}

/**
 * What the page shows in place of rows: nothing when there are rows; the empty state when there are no
 * exams at all; the filtered-empty state when the filters leave none.
 */
export function emptyStateFor(rowCount: number, filtered: boolean): "empty" | "filtered" | null {
  if (rowCount > 0) {
    return null;
  }
  return filtered ? "filtered" : "empty";
}
