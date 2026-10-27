/**
 * True when the API left a field out. The API drops null fields from its JSON, so a missing value
 * arrives as `undefined`, not `null`. Use this instead of `=== null`, and it also narrows the type.
 */
export function isMissing<T>(value: T | null | undefined): value is null | undefined {
  return value === null || value === undefined;
}
