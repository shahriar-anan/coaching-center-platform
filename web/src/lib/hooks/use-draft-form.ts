import { useState } from "react";

export type DraftForm<T> = {
  value: T;
  setValue: (next: T) => void;
  hasDraft: boolean;
  restore: () => void;
  clear: () => void;
};

/**
 * A long form's unsent draft, kept per record. Stub: in-memory only, no draft is ever found.
 */
export function useDraftForm<T>(key: string, initial: T): DraftForm<T> {
  void key;
  const [value, setValue] = useState<T>(initial);
  return {
    value,
    setValue,
    hasDraft: false,
    restore: () => undefined,
    clear: () => undefined,
  };
}
