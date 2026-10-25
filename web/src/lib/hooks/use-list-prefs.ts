import { useState } from "react";

export type ListPrefs = Record<string, string | number | boolean>;

/**
 * Per-device list preferences (page size, last filter) under one key.
 * Stub: keeps the value in memory only; localStorage comes with the real version.
 */
export function useListPrefs(key: string): [ListPrefs, (next: ListPrefs) => void] {
  void key;
  const [prefs, setPrefs] = useState<ListPrefs>({});
  return [prefs, setPrefs];
}
