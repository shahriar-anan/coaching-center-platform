"use client";

/**
 * Staff home reads: the participation summary and list, and the count of orders
 * still open at the gateway. Query keys are prefixed `staffHome`.
 */

import { useQuery } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
import type { PageResponse } from "@/lib/api/exam/types";

export type ParticipationSummary = { awaitingMarking: number; draftsAwaitingPublish: number };

export type ParticipationRow = {
  examId: string;
  moduleId: string;
  moduleTitleEn: string;
  moduleTitleBn: string;
  examType: "MCQ" | "WRITTEN";
  titleEn: string;
  titleBn: string;
  liveAt: string | null;
  endAt: string | null;
  sitting: number;
  submitted: number;
  failedToSubmit: number;
  awaitingMarking: number | null;
  draftsAwaitingPublish: number | null;
  /** Corrections to released results that wait for release. */
  correctionsWaiting?: number | null;
  published: number | null;
  retakes: number;
};

export type ParticipationParams = {
  moduleId?: string;
  type?: "MCQ" | "WRITTEN";
  /** The API sorts by `liveAt` (start time, latest first) when no sort is sent; `date` is not accepted. */
  sort?: "liveAt" | "title" | "sitting";
  size?: number;
};

export const staffHomeKeys = {
  summary: ["staffHome", "summary"] as const,
  participation: (params: ParticipationParams) => ["staffHome", "participation", params] as const,
  openOrders: ["staffHome", "openOrders"] as const,
};

export function useParticipationSummary() {
  return useQuery({
    queryKey: staffHomeKeys.summary,
    queryFn: () => apiRequest<ParticipationSummary>("/api/v1/live-exam-participation/summary"),
    refetchOnWindowFocus: true,
  });
}

export function useParticipationRows(params: ParticipationParams = {}) {
  const query = new URLSearchParams({ size: String(params.size ?? 20), sort: params.sort ?? "date" });
  if (params.moduleId) {
    query.set("moduleId", params.moduleId);
  }
  if (params.type) {
    query.set("type", params.type);
  }
  return useQuery({
    queryKey: staffHomeKeys.participation(params),
    queryFn: () => apiRequest<PageResponse<ParticipationRow>>(`/api/v1/live-exam-participation?${query}`),
    refetchOnWindowFocus: true,
  });
}

/** Orders waiting for the gateway. Only the total is used; the rows are on the payments page. */
export function useOpenOrderCount() {
  return useQuery({
    queryKey: staffHomeKeys.openOrders,
    queryFn: async () => {
      const page = await apiRequest<PageResponse<unknown>>("/api/v1/live-exam-orders?status=INITIATED&size=1");
      return page.totalElements;
    },
    refetchOnWindowFocus: true,
  });
}
