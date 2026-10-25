"use client";

import { useCallback, useMemo, useSyncExternalStore } from "react";
import { readStored, subscribe, writeStored } from "@/lib/hooks/local-store";

export type ListPrefs = Record<string, string | number | boolean>;

const PREFIX = "listPrefs:";

function parse(raw: string | null): ListPrefs {
  if (!raw) return {};
  try {
    const parsed: unknown = JSON.parse(raw);
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? (parsed as ListPrefs) : {};
  } catch {
    // Bad JSON: the list uses its defaults.
    return {};
  }
}

/**
 * Per-device list preferences (page size, last filter) under one key.
 * Kept in localStorage; the URL still wins for anything it sets. Storage is
 * read through useSyncExternalStore, so the server and the first client render match.
 */
export function useListPrefs(key: string): [ListPrefs, (next: ListPrefs) => void] {
  const raw = useSyncExternalStore(subscribe, () => readStored(PREFIX + key), () => null);
  const prefs = useMemo(() => parse(raw), [raw]);
  const setPrefs = useCallback((next: ListPrefs) => writeStored(PREFIX + key, JSON.stringify(next)), [key]);
  return [prefs, setPrefs];
}
