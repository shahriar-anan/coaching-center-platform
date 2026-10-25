/**
 * Pure logic behind the MCQ paper: the selection map, the local backup, the merge with the server's
 * copy, and the autosave queue. No React here, so each rule is tested on its own.
 */

/** questionId → optionId. A question with no entry is blank. */
export type Selection = Record<string, string>;

export type Backup = {
  selection: Selection;
  flagged: string[];
  savedAt: number;
};

export function backupKey(examId: string, studentId: string, startedAt: string) {
  return `sitting:${examId}:${studentId}:${startedAt}`;
}

/** Reads the backup, or null when it is missing or unreadable. Storage can throw in private windows. */
export function readBackup(key: string): Backup | null {
  try {
    const raw = window.localStorage.getItem(key);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as Backup;
    return parsed && typeof parsed.selection === "object" ? parsed : null;
  } catch {
    return null;
  }
}

export function writeBackup(key: string, backup: Backup): void {
  try {
    window.localStorage.setItem(key, JSON.stringify(backup));
  } catch {
    /* the page still works without the backup */
  }
}

export function clearBackup(key: string): void {
  try {
    window.localStorage.removeItem(key);
  } catch {
    /* nothing to clear */
  }
}

/**
 * The selection to show after a load. The server holds the saved answers; the backup holds the
 * student's latest taps on this device. The server wins when it is at least as new (it has the
 * copy that was saved); the backup wins only when this device is newer and the server is behind.
 */
export function mergeSelection(
  server: Selection,
  serverUpdatedAt: number | null,
  backup: Backup | null,
): Selection {
  if (!backup) {
    return server;
  }
  if (serverUpdatedAt === null || backup.savedAt > serverUpdatedAt) {
    return { ...backup.selection, ...server };
  }
  return server;
}

/** The selection as the API expects it: a list with one entry per answered question. */
export function toAnswerList(selection: Selection): { questionId: string; optionId: string }[] {
  return Object.entries(selection).map(([questionId, optionId]) => ({ questionId, optionId }));
}

/** Tapping the selected option again clears it. Any other tap sets it. */
export function toggleOption(selection: Selection, questionId: string, optionId: string): Selection {
  const next = { ...selection };
  if (next[questionId] === optionId) {
    delete next[questionId];
  } else {
    next[questionId] = optionId;
  }
  return next;
}

export function toggleFlag(flagged: string[], questionId: string): string[] {
  return flagged.includes(questionId) ? flagged.filter((id) => id !== questionId) : [...flagged, questionId];
}

/**
 * Autosave policy: one request in flight, the latest selection always wins, and a failed save is
 * retried after 1 s, 2 s, 4 s. After the last retry the caller shows a manual retry button.
 */
export const RETRY_DELAYS_MS = [1000, 2000, 4000] as const;
export const SAVE_DEBOUNCE_MS = 400;

export function nextRetryDelay(attempt: number): number | null {
  return attempt < RETRY_DELAYS_MS.length ? RETRY_DELAYS_MS[attempt] : null;
}
