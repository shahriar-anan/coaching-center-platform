"use client";

import { useCallback, useEffect, useState } from "react";

export type DraftForm<T> = {
  value: T;
  setValue: (next: T) => void;
  /** True when an unsent draft from an earlier visit waits to be restored. */
  hasDraft: boolean;
  restore: () => void;
  clear: () => void;
};

/** The draft offered after a reload. */
const DRAFT = "draft:";
/** Edits typed while a draft is waiting. The offered draft is never overwritten. */
const EDIT = "draft-edit:";

function readDraft<T>(storageKey: string): T | null {
  try {
    const raw = window.localStorage.getItem(storageKey);
    return raw === null ? null : (JSON.parse(raw) as T);
  } catch {
    return null;
  }
}

function writeDraft<T>(storageKey: string, value: T): void {
  try {
    window.localStorage.setItem(storageKey, JSON.stringify(value));
  } catch {
    // Blocked storage: the form still works, only the reload safety net is lost.
  }
}

function removeDraft(storageKey: string): void {
  try {
    window.localStorage.removeItem(storageKey);
  } catch {
    // Nothing stored, nothing to remove.
  }
}

/**
 * A long form's unsent draft, kept per record.
 *
 * Every change is saved. After an accidental reload, `hasDraft` is true and
 * `restore()` brings the newest saved draft back. While that draft waits, new
 * typing goes to a separate key, so the offered draft is kept until the user
 * restores it or clears it. Call `clear()` after a successful save.
 */
export function useDraftForm<T>(key: string, initial: T): DraftForm<T> {
  const [value, setLocal] = useState<T>(initial);
  const [saved, setSaved] = useState<T | null>(null);
  const [hasDraft, setHasDraft] = useState(false);

  // Read storage after mount, so the server and the first client render match.
  useEffect(() => {
    const edit = readDraft<T>(EDIT + key);
    const stored = edit ?? readDraft<T>(DRAFT + key);
    if (stored !== null) {
      setSaved(stored);
      setHasDraft(true);
    }
  }, [key]);

  const setValue = useCallback(
    (next: T) => {
      setLocal(next);
      writeDraft(hasDraft ? EDIT + key : DRAFT + key, next);
    },
    [key, hasDraft],
  );

  const restore = useCallback(() => {
    if (saved !== null) {
      setLocal(saved);
      writeDraft(DRAFT + key, saved);
      removeDraft(EDIT + key);
    }
    setSaved(null);
    setHasDraft(false);
  }, [key, saved]);

  const clear = useCallback(() => {
    removeDraft(DRAFT + key);
    removeDraft(EDIT + key);
    setSaved(null);
    setHasDraft(false);
  }, [key]);

  return { value, setValue, hasDraft, restore, clear };
}
