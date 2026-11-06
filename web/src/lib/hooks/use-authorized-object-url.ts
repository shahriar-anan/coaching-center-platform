"use client";

import { useEffect, useState } from "react";
import { apiBlob } from "@/lib/api/client";

/**
 * An object URL for a file that needs the bearer token, which a plain image tag cannot send. `path` is the API path,
 * with its version when it has one: a new version is fetched again. The URL is revoked when the path changes or the
 * component unmounts. It is null while loading, when there is no path, and when the fetch fails (the caller shows its
 * fallback).
 */
export function useAuthorizedObjectUrl(path: string | null): string | null {
  const [loaded, setLoaded] = useState<{ path: string; url: string } | null>(null);

  useEffect(() => {
    if (!path) return;
    let objectUrl: string | null = null;
    let active = true;
    const controller = new AbortController();
    apiBlob(path, { signal: controller.signal })
      .then((blob) => {
        if (!active) return;
        objectUrl = URL.createObjectURL(blob);
        setLoaded({ path, url: objectUrl });
      })
      .catch(() => {
        // Nothing to show: the caller falls back to its placeholder.
      });
    return () => {
      active = false;
      controller.abort();
      if (objectUrl) {
        const revoked = objectUrl;
        URL.revokeObjectURL(revoked);
        setLoaded((current) => (current?.url === revoked ? null : current));
      }
    };
  }, [path]);

  return path && loaded?.path === path ? loaded.url : null;
}
