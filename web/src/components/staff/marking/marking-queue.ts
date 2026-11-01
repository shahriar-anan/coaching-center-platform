import type { TreeNode } from "@/components/ui/split-browser";
import { relativeDay } from "@/lib/format";
import { ApiError } from "@/lib/api/errors";
import type { CategoryRef, FeedRow, FeedSort, FeedStatus } from "@/lib/api/exam/staff-marking";
import type { ParticipationRow } from "@/lib/api/exam/staff-home";

/** The browser's status filter. `ALL` sends no status to the feed. */
export const FEED_FILTERS = ["AWAITING", "DRAFT", "READY", "PUBLISHED", "FAILED_TO_SUBMIT", "ALL"] as const;
export type FeedFilter = (typeof FEED_FILTERS)[number];

export const FEED_SORTS: FeedSort[] = ["submittedAt,desc", "submittedAt,asc", "studentName"];

/** The category id of papers whose exam has no category. The tree opens this group; it never filters on it. */
export const UNCATEGORISED = "none";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** A URL value that is a real UUID, or "" for anything else: a missing value, `undefined`, `null` or a word. */
export function uuidParam(value: string | null | undefined): string {
  return value && UUID_PATTERN.test(value) ? value : "";
}

/** A URL id as written: "" for a missing value, or for the literal `undefined` or `null` that a broken link leaves behind. */
export function idParam(value: string | null | undefined): string {
  return value && value !== "undefined" && value !== "null" ? value : "";
}

/** A path with a query of only the values that are set, so no empty, `undefined` or `null` reaches a link. */
export function withQuery(path: string, values: Record<string, string | null | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(values)) {
    if (value) {
      search.set(key, value);
    }
  }
  const text = search.toString();
  return text ? `${path}?${text}` : path;
}

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

/**
 * The kind of a failed list: `request` for a 4xx (the request was refused or malformed), `server` for a 5xx, and null
 * for anything else, such as a network fault, which keeps the default message.
 */
export function failureKind(error: unknown): "request" | "server" | null {
  if (!(error instanceof ApiError) || error.status < 400) {
    return null;
  }
  return error.status >= 500 ? "server" : "request";
}

type PickTitle = (record: { titleEn: string; titleBn: string }) => string;

export type BrowserTree = {
  /** Root "all" node, then the categories (nested, any depth), each with its written exams. */
  tree: TreeNode[];
  /** The category each written exam is filed under: its own category, or `UNCATEGORISED`. */
  examCategory: Map<string, string>;
  /** Every written exam under each category id, the exams of its sub-categories included. */
  categoryExams: Map<string, string[]>;
};

/**
 * The browser tree: All scripts, then categories nested to any depth, then the written exams. It is built from each
 * exam's own category path, not from packages. Each node's count is the papers still awaiting a mark under it. An exam
 * with no category sits under a root "Uncategorised" node.
 */
export function buildBrowserTree(
  exams: ParticipationRow[],
  pick: PickTitle,
  allLabel: string,
  uncategorisedLabel: string,
): BrowserTree {
  const written = exams.filter((row) => row.examType === "WRITTEN");
  const roots: TreeNode[] = [];
  const nodes = new Map<string, TreeNode>();
  const examCategory = new Map<string, string>();
  const categoryExams = new Map<string, string[]>();

  for (const row of written) {
    const waiting = row.awaitingMarking ?? 0;
    const chain =
      row.categoryPath && row.categoryPath.length > 0
        ? row.categoryPath.map((ref) => ({ id: ref.id, label: pick(ref) }))
        : [{ id: UNCATEGORISED, label: uncategorisedLabel }];

    // Walk the chain from the root, creating each category node once. Every node on the chain counts this paper.
    let siblings = roots;
    for (const link of chain) {
      const key = `cat:${link.id}`;
      let node = nodes.get(key);
      if (!node) {
        node = { id: key, label: link.label, count: 0, children: [] };
        nodes.set(key, node);
        siblings.push(node);
      }
      node.count = (node.count ?? 0) + waiting;
      categoryExams.set(link.id, [...(categoryExams.get(link.id) ?? []), row.examId]);
      siblings = node.children!;
    }
    siblings.push({ id: `exam:${row.examId}`, label: pick(row), count: waiting });
    examCategory.set(row.examId, chain[chain.length - 1]!.id);
  }

  const total = written.reduce((sum, row) => sum + (row.awaitingMarking ?? 0), 0);
  return { tree: [{ id: "all", label: allLabel, count: total, children: roots }], examCategory, categoryExams };
}

/** Rows grouped by the category path they carry, in list order. A row with no path groups under `uncategorised`. */
export type CategoryGroup<T> = { key: string; label: string; items: T[] };

export function groupByCategory<T extends { categoryPath?: CategoryRef[] }>(
  rows: T[],
  pick: PickTitle,
  uncategorised: string,
  separator = " › ",
): CategoryGroup<T>[] {
  const groups = new Map<string, CategoryGroup<T>>();
  for (const row of rows) {
    const path = row.categoryPath ?? [];
    const key = path.map((ref) => ref.id).join("/") || UNCATEGORISED;
    const group = groups.get(key) ?? {
      key,
      label: path.length > 0 ? path.map((ref) => pick(ref)).join(separator) : uncategorised,
      items: [],
    };
    group.items.push(row);
    groups.set(key, group);
  }
  return [...groups.values()];
}

/** The browser's selection in the URL: a category or an exam, or neither (every paper). */
export type Selection = { cat: string; exam: string };

/** The selection the URL asks for. The uncategorised group is only ever opened, so it never filters. */
export function selectionOf(cat: string, exam: string): Selection {
  const category = idParam(cat);
  return { cat: category === UNCATEGORISED ? "" : category, exam: idParam(exam) };
}

export function selectedNode(selection: Selection): string | null {
  if (selection.exam) return `exam:${selection.exam}`;
  if (selection.cat) return `cat:${selection.cat}`;
  return null;
}

/** The URL values a tree click sets. Choosing the uncategorised group sets nothing: it only opens. */
export function selectionFor(nodeId: string): Selection | null {
  if (nodeId === "all") return { cat: "", exam: "" };
  const [kind, id] = nodeId.split(/:(.+)/) as [string, string];
  if (kind === "cat") return id === UNCATEGORISED ? null : { cat: id, exam: "" };
  if (kind === "exam") return { cat: "", exam: id };
  return null;
}
