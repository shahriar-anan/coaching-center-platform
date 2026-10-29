/**
 * Pure logic behind the MCQ paper: the selection map, the local backup, the merge with the server's
 * copy, the save payload, and the autosave queue. No React here, so each rule is tested on its own.
 */

/** questionId → the chosen optionIds. A question with no entry (or an empty list) is blank. One id for a single-answer question, any number for a multi-answer one. */
export type Selection = Record<string, string[]>;

export type Backup = {
  selection: Selection;
  flagged: string[];
  savedAt: number;
};

export function backupKey(examId: string, studentId: string, startedAt: string) {
  return `sitting:${examId}:${studentId}:${startedAt}`;
}

/**
 * A selection read from storage. Backups written before multi-answer support hold one option id per
 * question as a string; they are read as a one-item list. Anything else is dropped.
 */
export function normaliseSelection(raw: unknown): Selection {
  const selection: Selection = {};
  if (!raw || typeof raw !== "object") {
    return selection;
  }
  for (const [questionId, value] of Object.entries(raw as Record<string, unknown>)) {
    if (typeof value === "string" && value) {
      selection[questionId] = [value];
    } else if (Array.isArray(value)) {
      const ids = value.filter((id): id is string => typeof id === "string" && id.length > 0);
      if (ids.length > 0) {
        selection[questionId] = ids;
      }
    }
  }
  return selection;
}

/** Reads the backup, or null when it is missing or unreadable. Storage can throw in private windows. */
export function readBackup(key: string): Backup | null {
  try {
    const raw = window.localStorage.getItem(key);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as { selection?: unknown; flagged?: unknown; savedAt?: unknown };
    if (!parsed || typeof parsed.selection !== "object" || parsed.selection === null) {
      return null;
    }
    return {
      selection: normaliseSelection(parsed.selection),
      flagged: Array.isArray(parsed.flagged) ? parsed.flagged.filter((id): id is string => typeof id === "string") : [],
      savedAt: typeof parsed.savedAt === "number" ? parsed.savedAt : 0,
    };
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

/**
 * The bulk save body. On an exam where answers can change, every question is sent. Where they cannot,
 * a multi-answer question is left out until the student confirms it: the bulk route would treat it as
 * confirmed. Single-answer questions are final once sent, so they are sent.
 */
export function answerPayload(
  selection: Selection,
  multiAnswer: ReadonlySet<string>,
  answersChangeable: boolean,
): { questionId: string; optionIds: string[] }[] {
  return Object.entries(selection)
    .filter(([questionId, optionIds]) => optionIds.length > 0 && (answersChangeable || !multiAnswer.has(questionId)))
    .map(([questionId, optionIds]) => ({ questionId, optionIds: [...optionIds] }));
}

/**
 * Taps on one question. A single-answer question keeps one option: tapping the chosen option clears
 * it, any other option replaces it. A multi-answer question toggles each option on its own.
 */
export function toggleOption(
  selection: Selection,
  questionId: string,
  optionId: string,
  multiAnswer = false,
): Selection {
  const next = { ...selection };
  const current = next[questionId] ?? [];
  let chosen: string[];
  if (multiAnswer) {
    chosen = current.includes(optionId) ? current.filter((id) => id !== optionId) : [...current, optionId];
  } else {
    chosen = current.length === 1 && current[0] === optionId ? [] : [optionId];
  }
  if (chosen.length === 0) {
    delete next[questionId];
  } else {
    next[questionId] = chosen;
  }
  return next;
}

export function toggleFlag(flagged: string[], questionId: string): string[] {
  return flagged.includes(questionId) ? flagged.filter((id) => id !== questionId) : [...flagged, questionId];
}

/**
 * The message key for a save error the student can act on, or null for the rest. `ANSWER_LOCKED`
 * means an answer that can no longer change was sent again.
 */
export function answerErrorKey(code: string | undefined): "sitting.paper.answerLocked" | null {
  return code === "ANSWER_LOCKED" ? "sitting.paper.answerLocked" : null;
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
