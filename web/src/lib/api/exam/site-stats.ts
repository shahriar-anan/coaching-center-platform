"use client";

import { useQuery } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";

/** The landing page's live counts (`GET /api/v1/public/site-stats`): numbers only. */
export interface SiteStats {
  students: number;
  mentors: number;
}

/** What the landing page shows while the counts load, or if they cannot be read. */
export const SITE_STATS_FALLBACK: SiteStats = { students: 874, mentors: 2 };

/** The API caches the counts for a minute, so the page does not ask more often than that. */
const SITE_STATS_STALE = 60_000;

export function useSiteStats() {
  return useQuery<SiteStats>({
    queryKey: ["site-stats"] as const,
    staleTime: SITE_STATS_STALE,
    queryFn: ({ signal }) => apiRequest<SiteStats>("/api/v1/public/site-stats", { signal }),
  });
}
