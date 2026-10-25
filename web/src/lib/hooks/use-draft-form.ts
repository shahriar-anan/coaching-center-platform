"use client";

import { useCallback, useState, useSyncExternalStore } from "react";
import { readStored, removeStored, subscribe, writeStored } from "@/lib/hooks/local-store";

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
  // Set once the user restores, clears, or types with no draft waiting.
  const [dismissed, setDismissed] = useState(false);
  const stored = useSyncExternalStore(
    subscribe,
    () => readStored(EDIT + key) ?? readStored(DRAFT + key),
    () => null,
  );
  const hasDraft = !dismissed && stored !== null;

  const setValue = useCallback(
    (next: T) => {
      setLocal(next);
      const raw = JSON.stringify(next);
      if (hasDraft) {
        writeStored(EDIT + key, raw);
      } else {
        setDismissed(true);
        writeStored(DRAFT + key, raw);
      }
    },
    [key, hasDraft],
  );

  const restore = useCallback(() => {
    if (stored !== null) {
      try {
        setLocal(JSON.parse(stored) as T);
        writeStored(DRAFT + key, stored);
      } catch {
        // A damaged draft stays in storage; the form keeps its current value.
      }
    }
    removeStored(EDIT + key);
    setDismissed(true);
  }, [key, stored]);

  const clear = useCallback(() => {
    removeStored(DRAFT + key);
    removeStored(EDIT + key);
    setDismissed(true);
  }, [key]);

  return { value, setValue, hasDraft, restore, clear };
}
