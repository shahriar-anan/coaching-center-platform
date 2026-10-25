"use client";

import { useCallback, useEffect, useState } from "react";

export type ListPrefs = Record<string, string | number | boolean>;

const PREFIX = "listPrefs:";

function read(key: string): ListPrefs {
  try {
    const raw = window.localStorage.getItem(PREFIX + key);
    const parsed: unknown = raw ? JSON.parse(raw) : {};
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? (parsed as ListPrefs) : {};
  } catch {
    // Storage can be blocked or hold bad JSON; the list then uses its defaults.
    return {};
  }
}

/**
 * Per-device list preferences (page size, last filter) under one key.
 * Kept in localStorage; the URL still wins for anything it sets. The stored
 * value is read after mount, so the server and the first client render match.
 */
export function useListPrefs(key: string): [ListPrefs, (next: ListPrefs) => void] {
  const [prefs, setState] = useState<ListPrefs>({});

  useEffect(() => {
    setState(read(key));
  }, [key]);

  const setPrefs = useCallback(
    (next: ListPrefs) => {
      setState(next);
      try {
        window.localStorage.setItem(PREFIX + key, JSON.stringify(next));
      } catch {
        // Blocked storage: the preference lasts for this page view only.
      }
    },
    [key],
  );

  return [prefs, setPrefs];
}
