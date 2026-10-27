import type { TreeNode } from "@/components/ui/split-browser";
import { relativeDay } from "@/lib/format";
import type { FeedRow, FeedSort, FeedStatus, MarkingModule } from "@/lib/api/exam/staff-marking";
import type { ParticipationRow } from "@/lib/api/exam/staff-home";

/** The browser's status filter. `ALL` sends no status to the feed. */
export const FEED_FILTERS = ["AWAITING", "DRAFT", "READY", "PUBLISHED", "FAILED_TO_SUBMIT", "ALL"] as const;
export type FeedFilter = (typeof FEED_FILTERS)[number];

export const FEED_SORTS: FeedSort[] = ["submittedAt,desc", "submittedAt,asc", "studentName"];

/** The filter as the feed expects it: `ALL` is no status at all. */
/** The feed status a tab asks for. "READY" is a draft the markers flagged ready (see `feedReady`). */
export function feedStatus(filter: string): string {
  if (filter === "ALL") {
    return "";
  }
  return filter === "READY" ? "DRAFT" : filter;
}

/** Only a paper with an answer and no mark yet is in the marking queue. */
export function isAwaiting(row: Pick<FeedRow, "status">): boolean {
  return row.status === "AWAITING";
}

/** The step of the marking queue: the row before or after the current one, in list order. */
export function neighbour(rows: FeedRow[], currentId: string, direction: -1 | 1): FeedRow | null {
  const index = rows.findIndex((row) => row.participantId === currentId);
  if (index < 0) {
    return null;
  }
  return rows[index + direction] ?? null;
}

/**
 * The next paper still waiting for a mark, after `currentId`, in list order. Wraps to the
 * start, so "সেভ করে পরেরটি" keeps working. `null` when no paper waits any more.
 */
export function nextToMark(rows: FeedRow[], currentId: string): FeedRow | null {
  const start = rows.findIndex((row) => row.participantId === currentId);
  for (let step = 1; step <= rows.length; step++) {
    const candidate = rows[(start + step) % rows.length];
    if (candidate && candidate.participantId !== currentId && isAwaiting(candidate)) {
      return candidate;
    }
  }
  return null;
}

/** One paper's status, worked out the way the feed does, so the screen and the list agree. */
export function paperStatus(paper: {
  status: string;
  published: boolean;
  evaluatedPdf: boolean | null;
}): FeedStatus {
  if (paper.status === "FAILED_TO_SUBMIT") return "FAILED_TO_SUBMIT";
  if (paper.published) return "PUBLISHED";
  return paper.evaluatedPdf === true ? "DRAFT" : "AWAITING";
}

/**
 * The label key for a row. A marked paper the marker flagged ready reads "Ready for release"; a released result with a
 * correction waiting reads "Change waiting for release"; otherwise the row's own status.
 */
export function statusWordKey(row: Pick<FeedRow, "status" | "releasePending" | "readyForRelease">): string {
  if (row.status === "DRAFT" && row.readyForRelease) {
    return "READY";
  }
  if (row.status === "DRAFT" && row.releasePending) {
    return "CHANGE_WAITING";
  }
  return row.status;
}

/** A status word's pill tone. Status is always a word; the colour only reinforces it. */
export function statusTone(status: FeedStatus): "warn" | "accent" | "ok" | "muted" | "danger" {
  switch (status) {
    case "AWAITING":
      return "warn";
    case "DRAFT":
      return "accent";
    case "PUBLISHED":
      return "ok";
    case "FAILED_TO_SUBMIT":
      return "muted";
  }
}

/** True when the day of `iso` is today on the Dhaka calendar. */
export function isTodayInDhaka(iso: string, now: number = Date.now()): boolean {
  return relativeDay(iso, "en", now) === "Today";
}

type PickTitle = (record: { titleEn: string; titleBn: string }) => string;

export type BrowserTree = {
  /** Root "all" node, then categories (nested), modules, and written exams. */
  tree: TreeNode[];
  /** Which module each written exam belongs to, so an exam selection also sets the module. */
  examModule: Map<string, string>;
  /** Every module under each category id, so a category selection can read them all. */
  categoryModules: Map<string, string[]>;
};

/**
 * The category → module → exam tree for the browser. Only modules with written
 * exams appear: a marker has nothing to mark in a module that has none. Each node's
 * count is the papers still waiting for a mark under it.
 */
export function buildBrowserTree(
  modules: MarkingModule[],
  exams: ParticipationRow[],
  pick: PickTitle,
  allLabel: string,
): BrowserTree {
  const written = exams.filter((row) => row.examType === "WRITTEN");
  const moduleById = new Map(modules.map((module) => [module.id, module]));
  const roots: TreeNode[] = [];
  const categories = new Map<string, TreeNode>();
  const examModule = new Map<string, string>();
  const categoryModules = new Map<string, string[]>();

  const moduleIds = [...new Set(written.map((row) => row.moduleId))];
  const moduleNodes: TreeNode[] = [];
  for (const moduleId of moduleIds) {
    const found = moduleById.get(moduleId);
    const rows = written.filter((row) => row.moduleId === moduleId);
    const waiting = rows.reduce((sum, row) => sum + (row.awaitingMarking ?? 0), 0);
    const first = rows[0];
    const moduleNode: TreeNode = {
      id: `mod:${moduleId}`,
      label: found ? pick(found) : pick({ titleEn: first?.moduleTitleEn ?? "", titleBn: first?.moduleTitleBn ?? "" }),
      count: waiting,
      children: rows.map((row) => {
        examModule.set(row.examId, moduleId);
        return { id: `exam:${row.examId}`, label: pick(row), count: row.awaitingMarking ?? 0 };
      }),
    };
    moduleNodes.push(moduleNode);

    // Walk the category chain from the root, creating each category node once.
    let siblings = roots;
    for (const ref of found?.categoryPath ?? []) {
      const key = `cat:${ref.id}`;
      let category = categories.get(key);
      if (!category) {
        category = { id: key, label: pick(ref), count: 0, children: [] };
        categories.set(key, category);
        siblings.push(category);
      }
      category.count = (category.count ?? 0) + waiting;
      categoryModules.set(ref.id, [...(categoryModules.get(ref.id) ?? []), moduleId]);
      siblings = category.children!;
    }
    siblings.push(moduleNode);
  }

  const total = moduleNodes.reduce((sum, node) => sum + (node.count ?? 0), 0);
  return {
    tree: [{ id: "all", label: allLabel, count: total, children: roots }],
    examModule,
    categoryModules,
  };
}

/** A module group for the instructor home: the modules under one category path, in server order. */
export type CategoryGroup = { key: string; label: string; modules: MarkingModule[] };

export function groupByCategory(modules: MarkingModule[], pick: PickTitle, separator = " › "): CategoryGroup[] {
  const groups = new Map<string, CategoryGroup>();
  for (const entry of modules) {
    const key = entry.categoryPath.map((ref) => ref.id).join("/");
    const group = groups.get(key) ?? {
      key,
      label: entry.categoryPath.map((ref) => pick(ref)).join(separator),
      modules: [],
    };
    group.modules.push(entry);
    groups.set(key, group);
  }
  return [...groups.values()];
}

/** The browser's selection in the URL: a category, a module or an exam, or none (every paper). */
export type Selection = { cat: string; module: string; exam: string };

export function selectedNode(selection: Selection): string | null {
  if (selection.exam) return `exam:${selection.exam}`;
  if (selection.module) return `mod:${selection.module}`;
  if (selection.cat) return `cat:${selection.cat}`;
  return null;
}

/** The URL values a tree click sets. An exam also sets its module, so the feed can scope to both. */
export function selectionFor(nodeId: string, index: BrowserTree): Selection | null {
  if (nodeId === "all") return { cat: "", module: "", exam: "" };
  const [kind, id] = nodeId.split(/:(.+)/) as [string, string];
  if (kind === "cat") return { cat: id, module: "", exam: "" };
  if (kind === "mod") return { cat: "", module: id, exam: "" };
  if (kind === "exam") {
    const moduleId = index.examModule.get(id);
    return moduleId ? { cat: "", module: moduleId, exam: id } : null;
  }
  return null;
}

/** Sorts merged rows the way the feed sorts one module's rows, for category views. */
export function sortRows(rows: FeedRow[], sort: FeedSort): FeedRow[] {
  const copy = [...rows];
  if (sort === "studentName") {
    return copy.sort((a, b) => a.studentName.localeCompare(b.studentName));
  }
  const direction = sort === "submittedAt,asc" ? 1 : -1;
  return copy.sort((a, b) => {
    const left = a.submittedAt ? Date.parse(a.submittedAt) : null;
    const right = b.submittedAt ? Date.parse(b.submittedAt) : null;
    if (left === right) return 0;
    if (left === null) return 1;
    if (right === null) return -1;
    return (left - right) * direction;
  });
}
