"use client";

import Link from "next/link";
import type { ReactElement } from "react";
import { Bell } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { useStaffUnreadCount } from "@/lib/api/exam/staff-notifications";
import { formatNumber } from "@/lib/format";

/**
 * The staff header's bell: the unread count and a link to the notification list. Separate from the student bell. The count
 * is asked for only when `enabled` (a staff member). The link is shown at once, so the header never shifts.
 */
export function StaffNotificationBell({ enabled }: { enabled: boolean }): ReactElement {
  const { t, locale } = useLocale();
  const { data } = useStaffUnreadCount(enabled);
  const count = data?.count ?? 0;
  const label = count > 0 ? t("staffNotifications.bellCount", { count }) : t("staffNotifications.bell");

  return (
    <Link
      href="/staff/notifications"
      prefetch
      aria-label={label}
      className="relative inline-flex size-11 shrink-0 items-center justify-center rounded-full border border-border bg-card text-text hover:bg-bg"
    >
      <Bell aria-hidden="true" className="size-5" />
      {count > 0 ? (
        <span
          aria-hidden="true"
          className="absolute -end-0.5 -top-0.5 flex min-w-5 items-center justify-center rounded-full bg-warn px-1.5 text-meta font-bold text-on-warn tabular-nums"
        >
          {formatNumber(count, locale)}
        </span>
      ) : null}
    </Link>
  );
}
