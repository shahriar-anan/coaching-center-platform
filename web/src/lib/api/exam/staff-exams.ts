import { useQuery } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";

/** One exam or package with a staged change, as the sidebar and the Exams page list it. */
export type PendingChangeItem = {
  kind: "EXAM" | "PACKAGE";
  id: string;
  titleEn: string | null;
  titleBn: string | null;
  updatedAt: string;
  updatedBy: string | null;
};

export type PendingChanges = {
  items: PendingChangeItem[];
};

export const staffExamKeys = {
  pendingChanges: ["staff-exams", "pending-changes"] as const,
};

/**
 * Every exam and package with a staged change, newest first. Only the Master Admin and the System
 * Admin may read it, so the caller turns it on for those roles only. The sidebar shows the count.
 */
export function usePendingChanges(enabled: boolean) {
  return useQuery({
    queryKey: staffExamKeys.pendingChanges,
    queryFn: () => apiRequest<PendingChanges>("/api/v1/live-exam-pending-changes"),
    enabled,
    staleTime: 30_000,
  });
}
