"use client";

import Link from "next/link";
import type { ReactElement } from "react";
import { Bell } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { useUnreadCount } from "@/lib/api/exam/notices";
import { formatNumber } from "@/lib/format";

/**
 * The header bell for signed-in students. The count comes from the unread
 * endpoint; the link opens the notice list. Renders the link alone when the
 * count is not known yet, so the header never shifts.
 */
export function NoticeBell(): ReactElement | null {
  const { t, locale } = useLocale();
  const { data } = useUnreadCount();
  const count = data?.count ?? 0;
  const label =
    count > 0
      ? `${t("common.nav.notices")}, ${t("notices.unreadCount", { count: formatNumber(count, locale) })}`
      : t("common.nav.notices");

  return (
    <Link
      href="/notices"
      prefetch
      aria-label={label}
      className="relative inline-flex size-11 items-center justify-center rounded-full border border-border bg-card text-text hover:bg-bg"
    >
      <Bell aria-hidden="true" className="size-5" />
      {count > 0 ? (
        <span
          aria-hidden="true"
          className="absolute -end-0.5 -top-0.5 flex min-w-5 items-center justify-center rounded-full bg-warn-fill px-1.5 text-xs font-bold text-white tabular-nums"
        >
          {formatNumber(count, locale)}
        </span>
      ) : null}
    </Link>
  );
}
