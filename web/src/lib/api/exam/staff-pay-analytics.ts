"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
import type { PayRole } from "@/lib/api/exam/staff-pay";

// The Master Admin's staff pay overview and analytics (staff pay plan, phase 6). Read only. Paths and field names follow the
// API contract. Money comes as JSON numbers; months are YYYY-MM; dates are YYYY-MM-DD in Dhaka.
// Keys start with "staffPay", so the one invalidation the pay screens use also refreshes these after a payment.

/** An amount split by kind. `total` is salaries plus marking plus adjustments. */
export interface PaySplit {
  total: number;
  salaries: number;
  marking: number;
  adjustments: number;
}

/** One person who is owed money. `nextDueOn` is the earliest due date still unpaid. */
export interface PayOwingRow {
  userId: string;
  fullName: string;
  role: PayRole;
  owedNow: PaySplit;
  overdue: PaySplit;
  nextDueOn: string | null;
}

/** One unpaid salary due in the next thirty days. */
export interface PayDueItem {
  userId: string;
  fullName: string;
  dueOn: string;
  amount: number;
}

export interface PayOverview {
  asOf: string;
  owedNow: PaySplit;
  overdue: PaySplit;
  paidThisMonth: number;
  dueNext30Days: {
    from: string;
    to: string;
    amount: number;
    items: PayDueItem[];
  };
  owing: PayOwingRow[];
}

/** Earned or due by kind, and paid, in one month. `earned` is salary plus marking plus adjustments. */
export interface PayPayrollMonth {
  month: string;
  salary: number;
  marking: number;
  adjustments: number;
  earned: number;
  paid: number;
}

export interface PayMonthSheets {
  month: string;
  sheets: number;
  amount: number;
}

export interface PayInstructorSheets {
  userId: string;
  fullName: string;
  total: number;
  byMonth: PayMonthSheets[];
}

/** One person's earned or due, paid and balance over the range. The balance is at the end of the range. */
export interface PayRangePerson {
  userId: string;
  fullName: string;
  role: PayRole;
  earned: number;
  paid: number;
  balance: number;
}

export interface PayAnalytics {
  from: string;
  to: string;
  months: string[];
  payroll: PayPayrollMonth[];
  sheets: PayInstructorSheets[];
  people: PayRangePerson[];
}

export const staffPayAnalyticsKeys = {
  overview: () => ["staffPay", "overview"] as const,
  analytics: (from: string | null, to: string | null) => ["staffPay", "analytics", from ?? "", to ?? ""] as const,
};

export function payOverviewPath(): string {
  return "/api/v1/staff-pay/overview";
}

/** The analytics path for a range. Both ends are YYYY-MM. */
export function payAnalyticsPath(from: string, to: string): string {
  const query = new URLSearchParams({ from, to });
  return `/api/v1/staff-pay/analytics?${query.toString()}`;
}

export function usePayOverview() {
  return useQuery({
    queryKey: staffPayAnalyticsKeys.overview(),
    queryFn: ({ signal }) => apiRequest<PayOverview>(payOverviewPath(), { signal }),
    placeholderData: keepPreviousData,
  });
}

/** The analytics for a range. A null end means the range is not valid yet, so nothing is asked for. */
export function usePayAnalytics(from: string | null, to: string | null) {
  return useQuery({
    queryKey: staffPayAnalyticsKeys.analytics(from, to),
    queryFn: ({ signal }) => apiRequest<PayAnalytics>(payAnalyticsPath(from ?? "", to ?? ""), { signal }),
    enabled: from !== null && to !== null,
    placeholderData: keepPreviousData,
  });
}
