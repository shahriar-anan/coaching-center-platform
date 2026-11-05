"use client";

import { useQuery } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";

// Instructor marking pay (staff pay plan, phase 4). Paths and field names follow the API contract. Money comes as JSON numbers.
// Keys start with "staffPay", so the pay screens' invalidation refreshes these figures too.

/** The totals across every exam. */
export interface MarkingTotals {
  marked: number;
  waiting: number;
  ready: number;
  published: number;
  earned: number;
  expected: number;
}

/** One exam's marking for the instructor. */
export interface MarkingExamRow {
  examId: string;
  titleEn: string;
  titleBn: string;
  marked: number;
  waiting: number;
  ready: number;
  published: number;
  earned: number;
  expected: number;
}

/** The figures. `sheetRate` is the instructor's rate now: the override, else the default. */
export interface MarkingView {
  sheetRate: number;
  totals: MarkingTotals;
  exams: MarkingExamRow[];
}

export const MY_MARKING_PATH = "/api/v1/me/pay/marking";

export const staffPayMarkingKeys = {
  me: () => ["staffPay", "me", "marking"] as const,
  person: (userId: string) => ["staffPay", "marking", userId] as const,
};

export function personMarkingPath(userId: string): string {
  return `/api/v1/staff-pay/people/${userId}/marking`;
}

/** The signed-in instructor's own marking figures. Only an instructor may call it, so the screen mounts it for one. */
export function useMyMarking() {
  return useQuery({
    queryKey: staffPayMarkingKeys.me(),
    queryFn: ({ signal }) => apiRequest<MarkingView>(MY_MARKING_PATH, { signal }),
  });
}

/** One instructor's marking figures, for the Master Admin's person page. */
export function usePersonMarking(userId: string) {
  return useQuery({
    queryKey: staffPayMarkingKeys.person(userId),
    queryFn: ({ signal }) => apiRequest<MarkingView>(personMarkingPath(userId), { signal }),
  });
}
