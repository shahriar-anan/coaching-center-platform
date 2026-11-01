"use client";

import { useQuery } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";

// Announcements shown on a package or an exam page (announcements plan, section 9). Paths and field names follow the API contract.

/** One announcement for a package or an exam. Any of the four text fields may be null or blank. */
export interface MyAnnouncement {
  id: string;
  titleEn: string | null;
  titleBn: string | null;
  bodyEn: string | null;
  bodyBn: string | null;
  createdAt: string;
}

/** The two pages that show announcements. */
export type AnnouncementKind = "package" | "exam";

export const announcementKeys = {
  mine: (kind: AnnouncementKind, id: string) => ["announcements", "mine", kind, id] as const,
};

/**
 * The announcements of a package or an exam, newest first, at most 50. The API answers only for a signed-in student, and sends
 * an empty list to one who does not hold the target. Only asked for when `enabled`. A failed read is left out by the caller.
 */
export function useMyAnnouncements(kind: AnnouncementKind, id: string, enabled = true) {
  return useQuery({
    queryKey: announcementKeys.mine(kind, id),
    queryFn: ({ signal }) =>
      apiRequest<MyAnnouncement[]>(`/api/v1/me/announcements?kind=${kind}&id=${id}`, { signal }),
    staleTime: 60_000,
    enabled: enabled && Boolean(id),
  });
}
