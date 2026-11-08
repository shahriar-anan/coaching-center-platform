import type { StaffExam } from "@/lib/api/exam/staff-catalog";

/** An exam can be picked when it is not in the package already and has not been withdrawn. */
export function isSelectable(exam: StaffExam, memberIds: Set<string>): boolean {
  return !memberIds.has(exam.id) && exam.status !== "ARCHIVED";
}

/** The ids that can be picked among `rows`, in row order, each once. */
export function selectableIds(rows: StaffExam[], memberIds: Set<string>): string[] {
  const seen = new Set<string>();
  const ids: string[] = [];
  for (const exam of rows) {
    if (isSelectable(exam, memberIds) && !seen.has(exam.id)) {
      seen.add(exam.id);
      ids.push(exam.id);
    }
  }
  return ids;
}

/** Ticks or unticks one exam. Returns a new set; the one passed in is not changed. */
export function toggleOne(selected: Set<string>, id: string): Set<string> {
  const next = new Set(selected);
  if (next.has(id)) {
    next.delete(id);
  } else {
    next.add(id);
  }
  return next;
}

/** Adds every selectable row to the selection. Rows the caller passes in are all of the matching exams, not one page. */
export function selectAllVisible(selected: Set<string>, rows: StaffExam[], memberIds: Set<string>): Set<string> {
  return new Set([...Array.from(selected), ...selectableIds(rows, memberIds)]);
}

/** Removes the given ids from the selection. Returns a new set. */
export function deselectIds(selected: Set<string>, ids: string[]): Set<string> {
  const drop = new Set(ids);
  return new Set(Array.from(selected).filter((id) => !drop.has(id)));
}

/**
 * The count of the selection, and how many of the selected exams are drafts. An id that is not in `rows` counts as no draft,
 * so pass every row seen so far to get the draft count across filters.
 */
export function summarise(selected: Set<string>, rows: StaffExam[]): { count: number; drafts: number } {
  const status = new Map(rows.map((exam) => [exam.id, exam.status] as const));
  let drafts = 0;
  for (const id of Array.from(selected)) {
    if (status.get(id) === "DRAFT") {
      drafts += 1;
    }
  }
  return { count: selected.size, drafts };
}
