import { useCallback, useState } from "react";

export type DraftForm<T> = {
  value: T;
  setValue: (next: T) => void;
  hasDraft: boolean;
  restore: () => void;
  clear: () => void;
};

const PREFIX = "draft:";

function readDraft<T>(key: string): T | null {
  try {
    const raw = window.localStorage.getItem(PREFIX + key);
    return raw === null ? null : (JSON.parse(raw) as T);
  } catch {
    return null;
  }
}

function writeDraft<T>(key: string, value: T): void {
  try {
    window.localStorage.setItem(PREFIX + key, JSON.stringify(value));
  } catch {
    // Blocked storage: the form still works, only the reload safety net is lost.
  }
}

function removeDraft(key: string): void {
  try {
    window.localStorage.removeItem(PREFIX + key);
  } catch {
    // Nothing stored, nothing to remove.
  }
}

/**
 * A long form's unsent draft, kept per record. Every change is written to
 * localStorage. After an accidental reload, `hasDraft` is true and `restore()`
 * brings the draft back. Call `clear()` after a successful save.
 */
export function useDraftForm<T>(key: string, initial: T): DraftForm<T> {
  const [saved] = useState<T | null>(() => (typeof window === "undefined" ? null : readDraft<T>(key)));
  const [value, setLocal] = useState<T>(initial);
  const [hasDraft, setHasDraft] = useState<boolean>(saved !== null);

  const setValue = useCallback(
    (next: T) => {
      setLocal(next);
      writeDraft(key, next);
      setHasDraft(false);
    },
    [key],
  );

  const restore = useCallback(() => {
    if (saved !== null) setLocal(saved);
    setHasDraft(false);
  }, [saved]);

  const clear = useCallback(() => {
    removeDraft(key);
    setHasDraft(false);
  }, [key]);

  return { value, setValue, hasDraft, restore, clear };
}
