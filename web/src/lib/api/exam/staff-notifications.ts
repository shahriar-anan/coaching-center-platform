"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
import type { PageResponse } from "@/lib/api/exam/types";

// The staff notification centre (staff pay plan, phase 1). Paths and field names follow the API contract.
// Each route acts on the signed-in person's own notifications only. Keys live in this file.

/**
 * The kinds the API sends. A kind not in this list still lists, with the generic text, so a kind added later shows before
 * the screen learns its wording.
 */
export type StaffNotificationKind =
  | "PAYOUT_ACCOUNT_CHANGED"
  | "MARKING_PUBLISHED_OWED"
  | "MARKING_PUBLISHED_EARNED"
  | "PAYMENT_RECORDED"
  | "PAYMENT_VOIDED"
  | "SALARY_DUE_SOON"
  | "SALARY_OVERDUE"
  | "MARKING_UNPAID_REMINDER"
  | "INSTALLMENT_PAYMENT_RECORDED"
  | "INSTALLMENT_DUE_SOON"
  | "INSTALLMENT_OVERDUE"
  | (string & {});

/** The tabs of the notification centre that the API filters on. `OTHER` is every kind not in `INSTALLMENT`. */
export type StaffNotificationGroup = "INSTALLMENT" | "OTHER";

/** The values a row's text needs. Names, amounts, counts and dates only; never an account number. */
export type StaffNotificationPayload = Record<string, string | number | boolean | null>;

export interface StaffNotification {
  id: string;
  kind: StaffNotificationKind;
  payload: StaffNotificationPayload;
  /** A staff page, such as "/staff/pay". */
  link: string;
  createdAt: string;
  /** Null while unread. */
  readAt: string | null;
}

/**
 * The bell's number. `byGroup` counts unread notifications per tab; the API may leave it out on an older build, so a
 * screen reads it with `?.`.
 */
export interface StaffUnreadCount {
  count: number;
  byGroup?: Partial<Record<StaffNotificationGroup, number>>;
}

/** The list page size. The API default is 20. */
export const STAFF_NOTIFICATION_PAGE = 20;

export const staffNotificationKeys = {
  all: ["staffNotifications"] as const,
  list: (page: number, unreadOnly: boolean, group?: StaffNotificationGroup) =>
    ["staffNotifications", "list", page, unreadOnly ? "UNREAD" : "ALL", group ?? "ANY"] as const,
  unread: () => ["staffNotifications", "unread"] as const,
};

/** One page of the signed-in person's notifications, newest first. `group` keeps one tab; absent means every kind. */
export function staffNotificationsPath(page: number, unreadOnly: boolean, group?: StaffNotificationGroup): string {
  const query = new URLSearchParams({ page: String(page), size: String(STAFF_NOTIFICATION_PAGE) });
  if (unreadOnly) {
    query.set("unread", "true");
  }
  if (group) {
    query.set("group", group);
  }
  return `/api/v1/me/staff-notifications?${query}`;
}

export function useStaffNotifications(page: number, unreadOnly: boolean, group?: StaffNotificationGroup) {
  return useQuery({
    queryKey: staffNotificationKeys.list(page, unreadOnly, group),
    queryFn: ({ signal }) =>
      apiRequest<PageResponse<StaffNotification>>(staffNotificationsPath(page, unreadOnly, group), { signal }),
  });
}

/**
 * The bell's unread count. Asked only for staff (`enabled`), refreshed on focus and every two minutes. A student never
 * asks: the route is staff-only.
 */
export function useStaffUnreadCount(enabled: boolean) {
  return useQuery({
    queryKey: staffNotificationKeys.unread(),
    queryFn: ({ signal }) => apiRequest<StaffUnreadCount>("/api/v1/me/staff-notifications/unread-count", { signal }),
    enabled,
    refetchOnWindowFocus: true,
    refetchInterval: 2 * 60 * 1000,
    staleTime: 30_000,
  });
}

function invalidateStaffNotifications(client: ReturnType<typeof useQueryClient>) {
  return client.invalidateQueries({ queryKey: staffNotificationKeys.all });
}

/** Marks one notification read. The list and the bell count refresh after. */
export function useMarkStaffNotificationRead() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      apiRequest<StaffNotification>(`/api/v1/me/staff-notifications/${id}/read`, { method: "POST", body: {} }),
    retry: false,
    onSettled: () => invalidateStaffNotifications(client),
  });
}

/** Marks every unread notification of the signed-in person read. */
export function useMarkAllStaffNotificationsRead() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiRequest<{ updated: number }>("/api/v1/me/staff-notifications/read-all", { method: "POST", body: {} }),
    retry: false,
    onSettled: () => invalidateStaffNotifications(client),
  });
}
