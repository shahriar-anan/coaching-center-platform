/**
 * Where a student's access to an exam comes from (plan section 8, `access.via`).
 * The labels live under `result.access`. The access endpoint is not live yet, so no
 * screen calls this with real data; the rows show no source label until it is.
 */
export type AccessVia = "ROSTER" | "MODULE" | "EXAM" | "MANUAL";

const KEYS: Record<AccessVia, string> = {
  ROSTER: "result.access.roster",
  MODULE: "result.access.module",
  EXAM: "result.access.exam",
  MANUAL: "result.access.manual",
};

/** The label key for a source, or null when the source is missing or not one this page knows. */
export function accessSourceKey(via: string | null | undefined): string | null {
  if (!via || !Object.prototype.hasOwnProperty.call(KEYS, via)) return null;
  return KEYS[via as AccessVia];
}
