/**
 * True when the API left a field out. The API drops null fields from its JSON, so a missing value
 * arrives as `undefined`, not `null`. Use this instead of `=== null`, and it also narrows the type.
 */
export function isMissing<T>(value: T | null | undefined): value is null | undefined {
  return value === null || value === undefined;
}

/** A calendar date (YYYY-MM-DD) from the API, or null when it is missing or not in that form. */
export function optionalDate(value: string | null | undefined): string | null {
  return !isMissing(value) && /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : null;
}

/** A money amount from the API, or null when it is missing or not a number. */
export function optionalAmount(value: number | null | undefined): number | null {
  return !isMissing(value) && Number.isFinite(value) ? value : null;
}
