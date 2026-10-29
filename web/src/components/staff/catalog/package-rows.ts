import type { StaffExam } from "@/lib/api/exam/staff-catalog";

/** A discount is blank (none), or a whole percentage from 1 to 99 (NR-01, the same rule as the API). */
export function parseDiscount(text: string): { ok: true; value: number | null } | { ok: false } {
  const trimmed = text.trim();
  if (trimmed === "") {
    return { ok: true, value: null };
  }
  if (!/^\d+$/.test(trimmed)) {
    return { ok: false };
  }
  const value = Number(trimmed);
  return value >= 1 && value <= 99 ? { ok: true, value } : { ok: false };
}

/**
 * The price shown on an exam row: the list price, and what a student pays. A discounted row shows the list price
 * struck through (plan 7.3). A free exam has no price.
 */
export function priceOfRow(exam: StaffExam): { list: number | null; current: number | null; discounted: boolean; free: boolean } {
  if (exam.free) {
    return { list: null, current: null, discounted: false, free: true };
  }
  const pricing = exam.pricing;
  if (pricing && pricing.discountPercent) {
    return {
      list: pricing.price,
      current: pricing.effectivePrice,
      discounted: pricing.effectivePrice !== null && pricing.effectivePrice !== pricing.price,
      free: false,
    };
  }
  return { list: exam.price, current: exam.price, discounted: false, free: false };
}

/** How many other packages hold the exam: the "also in N packages" note. Zero means this package is the only one. */
export function alsoInCount(exam: StaffExam, moduleId: string): number {
  return (exam.packages ?? []).filter((item) => item.id !== moduleId).length;
}

/** The pending dot on a row: a staged change to the exam waits for the Master Admin. */
export function hasPendingDot(exam: StaffExam): boolean {
  return exam.pendingChanges === true;
}

/**
 * The order after one exam moves up (step -1) or down (step 1). A move past either end leaves the order as it is, so
 * the button can stay enabled without sending a no-op.
 */
export function movedOrder(ids: string[], index: number, step: -1 | 1): string[] {
  const target = index + step;
  if (index < 0 || index >= ids.length || target < 0 || target >= ids.length) {
    return ids;
  }
  const next = [...ids];
  [next[index], next[target]] = [next[target], next[index]];
  return next;
}
