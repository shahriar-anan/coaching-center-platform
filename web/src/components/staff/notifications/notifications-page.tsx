"use client";

import Link from "next/link";
import { useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { Button } from "@/components/ui/button";
import { cn } from "@/components/ui/cn";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs } from "@/components/ui/tabs";
import { useToast } from "@/components/ui/toast";
import {
  useMarkAllStaffNotificationsRead,
  useMarkStaffNotificationRead,
  useStaffNotifications,
  useStaffUnreadCount,
  type StaffNotification,
} from "@/lib/api/exam/staff-notifications";
import { formatDateTime } from "@/lib/format";
import { useRole } from "@/lib/auth/guards";
import { notificationHref, notificationText } from "./notification-text";

type Tab = "UNREAD" | "ALL" | "INSTALLMENT";

/**
 * The staff notification list: an Unread tab and an All tab, "Mark all read", and one row per notification. The Installments
 * tab (Master Admin only) keeps the direct-enrolment kinds, with its own unread badge. Each row reads from its kind and payload,
 * and links to its page. Opening a row marks it read.
 */
export function NotificationsPage() {
  const { t, locale } = useLocale();
  const { toast } = useToast();
  const { isMasterAdmin } = useRole();
  const [tab, setTab] = useState<Tab>("ALL");
  const [page, setPage] = useState(0);
  const unreadOnly = tab === "UNREAD";
  const installmentsOnly = tab === "INSTALLMENT";
  const list = useStaffNotifications(page, unreadOnly, installmentsOnly ? "INSTALLMENT" : undefined);
  const unread = useStaffUnreadCount(true);
  const markOne = useMarkStaffNotificationRead();
  const markAll = useMarkAllStaffNotificationsRead();
  const unreadCount = unread.data?.count ?? 0;
  const unreadInstallments = unread.data?.byGroup?.INSTALLMENT ?? 0;

  function chooseTab(next: string) {
    setTab(next as Tab);
    setPage(0);
  }

  function markEverything() {
    markAll.mutate(undefined, {
      onSuccess: () => toast({ title: t("staffNotifications.markedAll"), tone: "ok" }),
      onError: () => toast({ title: t("staffNotifications.markAllFailed"), tone: "danger" }),
    });
  }

  return (
    <div className="grid gap-5">
      <PageHeader
        title={t("staffNotifications.title")}
        actions={
          <Button variant="secondary" onClick={markEverything} disabled={unreadCount === 0 || markAll.isPending}>
            {t("staffNotifications.markAll")}
          </Button>
        }
      />

      <Tabs
        ariaLabel={t("staffNotifications.tabs.label")}
        value={tab}
        onChange={chooseTab}
        variant="pill"
        tabs={[
          { value: "ALL", label: t("staffNotifications.tabs.ALL") },
          { value: "UNREAD", label: t("staffNotifications.tabs.UNREAD"), badge: unreadCount || undefined },
          ...(isMasterAdmin
            ? [{ value: "INSTALLMENT", label: t("staffNotifications.tabs.INSTALLMENT"), badge: unreadInstallments || undefined }]
            : []),
        ]}
      />

      {list.isError ? (
        <ErrorState onRetry={() => void list.refetch()} error={list.error} />
      ) : !list.data ? (
        <Skeleton variant="rows" count={4} />
      ) : list.data.content.length === 0 ? (
        unreadOnly ? (
          <EmptyState title={t("staffNotifications.emptyUnread")} body={t("staffNotifications.emptyUnreadBody")} />
        ) : installmentsOnly ? (
          <EmptyState title={t("staffNotifications.emptyInstallments")} body={t("staffNotifications.emptyInstallmentsBody")} />
        ) : (
          <EmptyState title={t("staffNotifications.empty")} body={t("staffNotifications.emptyBody")} />
        )
      ) : (
        <>
          <ul className="grid gap-3" aria-label={t("staffNotifications.title")}>
            {list.data.content.map((notification) => (
              <li key={notification.id}>
                <NotificationRow
                  notification={notification}
                  onOpen={() => {
                    if (!notification.readAt) {
                      markOne.mutate(notification.id);
                    }
                  }}
                />
              </li>
            ))}
          </ul>
          <Pagination page={list.data.page} totalPages={list.data.totalPages} onPage={setPage} />
        </>
      )}
    </div>
  );
}

/** One notification: its text, its time, and an unread dot. The whole row opens its page. */
function NotificationRow({ notification, onOpen }: { notification: StaffNotification; onOpen: () => void }) {
  const { t, locale } = useLocale();
  const isUnread = !notification.readAt;
  const text = notificationText(notification, t, locale);
  return (
    <Link
      href={notificationHref(notification.link)}
      onClick={onOpen}
      className={cn(
        "flex min-h-14 items-start gap-3 rounded-card border border-border bg-card p-4 transition-colors hover:bg-bg",
        isUnread && "border-accent/40",
      )}
    >
      <span aria-hidden="true" className={cn("mt-2 size-2 shrink-0 rounded-full", isUnread ? "bg-accent" : "bg-transparent")} />
      <span className="grid min-w-0 flex-1 gap-1">
        <span className="text-body text-text">
          {isUnread ? <span className="sr-only">{t("staffNotifications.unreadDot")}: </span> : null}
          {text}
        </span>
        <time dateTime={notification.createdAt} className="text-meta text-muted">
          {formatDateTime(notification.createdAt, locale)}
        </time>
      </span>
    </Link>
  );
}
