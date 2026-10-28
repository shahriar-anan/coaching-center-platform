/** How long one grant lasts, for the My exams rows (NR-01, S1.3). */
export type AccessEnd = { kind: "lifetime" } | { kind: "until"; endsAt: string } | { kind: "expired" };

/** Lifetime when no end date is set, expired when the API says the grant has run out, otherwise the end date. */
export function accessEnd(grant: { endsAt?: string | null; expired?: boolean }): AccessEnd {
  if (grant.expired) return { kind: "expired" };
  if (!grant.endsAt) return { kind: "lifetime" };
  return { kind: "until", endsAt: grant.endsAt };
}

/**
 * The rows of one tab, live access first and the ones that have run out after them. Each group keeps the API's order, so the
 * list is stable between reloads.
 */
export function orderedRows<T extends { expired?: boolean }>(rows: readonly T[]): T[] {
  return [...rows.filter((row) => !row.expired), ...rows.filter((row) => row.expired)];
}
