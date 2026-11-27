"use client";

import { useQuery } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
import { queryString } from "@/lib/api/exam/staff-ops";
import { accountingKeys } from "./query";
import type { Dashboard, GroupSummary, MonthlyReport, OwedReport } from "./types";

/** The home page: the cards, the groups, the owed most, the latest entries and the twelve months (plan 4.6). */
export function useDashboard() {
  return useQuery({
    queryKey: [...accountingKeys.all, "dashboard"],
    queryFn: ({ signal }) => apiRequest<Dashboard>("/api/v1/accounting/dashboard", { signal }),
  });
}

/** Owed now: every payee with a balance that is not zero, by group. */
export function useOwedReport() {
  return useQuery({
    queryKey: [...accountingKeys.all, "owed"],
    queryFn: ({ signal }) => apiRequest<OwedReport>("/api/v1/accounting/reports/owed", { signal }),
  });
}

/** Spending and income by group by month. `from` and `to` are months (`YYYY-MM`); at most 36 months. */
export function useMonthlyReport(from: string, to: string) {
  return useQuery({
    queryKey: [...accountingKeys.all, "monthly", from, to],
    queryFn: ({ signal }) => apiRequest<MonthlyReport>(`/api/v1/accounting/reports/monthly${queryString({ from, to })}`, { signal }),
    enabled: Boolean(from && to),
  });
}

/** One group over a date range: totals per type and per payee. */
export function useGroupSummary(groupId: string, from: string, to: string) {
  return useQuery({
    queryKey: [...accountingKeys.all, "summary", groupId, from, to],
    queryFn: ({ signal }) => apiRequest<GroupSummary>(`/api/v1/accounting/groups/${groupId}/summary${queryString({ from, to })}`, { signal }),
    enabled: Boolean(groupId && from && to),
  });
}
