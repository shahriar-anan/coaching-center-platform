/**
 * Where a student's access to an exam comes from (plan section 8, `access.via`).
 * The labels live under `result.access`. The my-modules rows read them from
 * `GET /api/v1/me/live-exam-access`.
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

/** The grant rows for a student, as `GET /api/v1/me/live-exam-access` sends them. */
export interface AccessRows {
  modules: { moduleId: string; via: string }[];
  exams: { moduleId: string; via: string }[];
}

/** A module-wide source beats an exam-level one; among module-wide ones, roster, then module, then manual. */
const RANK: readonly string[] = ["ROSTER", "MODULE", "MANUAL", "EXAM"];

function rank(via: string): number {
  const index = RANK.indexOf(via);
  return index === -1 ? RANK.length : index;
}

/**
 * The source label key for one module row: the best source the student holds in it, or null
 * when there is none, the access rows are not loaded, or the best source is not one this page knows.
 */
export function moduleAccessKey(access: AccessRows | undefined, moduleId: string): string | null {
  if (!access) return null;
  const vias = [
    ...access.modules.filter((row) => row.moduleId === moduleId).map((row) => row.via),
    ...access.exams.filter((row) => row.moduleId === moduleId).map((row) => row.via),
  ];
  const best = vias.sort((a, b) => rank(a) - rank(b))[0];
  return accessSourceKey(best);
}
