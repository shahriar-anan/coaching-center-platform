/**
 * The rules for the warnings on the direct enrolment form (plan 14.1 D21 to D23, 14.5, 14.11 D25 and D26). Pure: no React and no
 * fetch, so the form, its confirmation and the tests read the same rules. The API decides what is held; these functions only
 * choose which warning the form shows and how it reads.
 */
import type { GrantQuote, OverlapItem } from "@/lib/api/exam/staff-admin";
import type { EnrolScope } from "@/components/staff/enrol/payment-model";

/** How the student holds the other item, as the copy names it. A value the API adds later reads as OTHER. */
export type HeldHow = "ONLINE" | "DIRECT" | "BUNDLE" | "ROSTER" | "PACKAGE" | "OTHER";

/** The copy key for a held item. A roster route through a named package reads "on the roster of the package {package}". */
export type OverlapHow = HeldHow | "ROSTER_PACKAGE";

const KNOWN_HOWS: readonly HeldHow[] = ["ONLINE", "DIRECT", "BUNDLE", "ROSTER", "PACKAGE"];

export function heldHow(via: string | null | undefined): HeldHow {
  return KNOWN_HOWS.find((known) => known === via) ?? "OTHER";
}

/** The copy key for one overlap item (plan 14.11, P53 to P55). */
export function overlapHow(item: OverlapItem): OverlapHow {
  const via = heldHow(item.via);
  const named = Boolean(item.packageTitleEn || item.packageTitleBn);
  return via === "ROSTER" && named ? "ROSTER_PACKAGE" : via;
}

/**
 * Which warning the form shows about what the student already holds. The overlap block wins when there are overlap items.
 * Otherwise the generic note shows when access is held by some route, for a paid target (a free exam has no price to warn about).
 */
export type HeldWarning = "OVERLAPS" | "ACCESS_HELD" | "NONE";

export function heldWarning(quote: GrantQuote | undefined, free: boolean): HeldWarning {
  if ((quote?.overlaps?.length ?? 0) > 0) {
    return "OVERLAPS";
  }
  if (quote?.accessHeld === true && !free) {
    return "ACCESS_HELD";
  }
  return "NONE";
}

/** The overlap items to list. Anything without an id is left out, so a half-built row never shows as a line. */
export function overlapItems(quote: GrantQuote | undefined): OverlapItem[] {
  return (quote?.overlaps ?? []).filter((item) => typeof item.id === "string" && item.id.length > 0);
}

/**
 * The moved-from-exam case (plan 14.11, D26): a package enrolment while the student still owes money on a plan for an exam inside
 * the package. The plan is not closed by the enrolment, so the warning must say so.
 */
export function isMovedFromExam(item: OverlapItem, scope: EnrolScope): boolean {
  return item.kind === "EXAM" && Boolean(item.planId) && scope === "MODULE";
}

/**
 * Which items show the paid and due line. A plan can appear on more than one item (P58), and its money must be shown once, so the
 * line goes on the first item that carries each plan. Returns the indexes that show it.
 */
export function moneyLineIndexes(items: OverlapItem[]): Set<number> {
  const seen = new Set<string>();
  const indexes = new Set<number>();
  items.forEach((item, index) => {
    if (!item.planId || seen.has(item.planId)) {
      return;
    }
    seen.add(item.planId);
    indexes.add(index);
  });
  return indexes;
}

/**
 * The roster block (plan D22): a package enrolment is blocked while the student is on the package's roster with live access.
 * An exam is never blocked here, because the roster belongs to the package.
 */
export function rosterBlocks(quote: GrantQuote | undefined, scope: EnrolScope): boolean {
  return scope === "MODULE" && quote?.onRoster === true;
}

/** A direct enrolment is already active on this target, so the form cannot add another one. */
export function directAlreadyActive(quote: GrantQuote | undefined): boolean {
  return quote?.directGrantActive === true;
}
