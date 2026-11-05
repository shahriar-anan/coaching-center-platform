"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { Pill, type PillTone } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs } from "@/components/ui/tabs";
import { NoticeEditor } from "@/components/staff/notice-board/notice-editor";
import { noticeTitle } from "@/components/notice-board/notice-text";
import { useLocale } from "@/i18n/locale-context";
import { useRole } from "@/lib/auth/guards";
import { useFormat } from "@/lib/format/use-format";
import { useStaffNotices, type SiteNoticeStatus, type StaffNotice } from "@/lib/api/exam/site-notices";

/** The filter tabs. "ALL" is no status filter. */
type Filter = "ALL" | SiteNoticeStatus;

const FILTERS: Filter[] = ["ALL", "DRAFT", "PUBLISHED", "UNPUBLISHED"];

const TONES: Record<SiteNoticeStatus, PillTone> = {
  DRAFT: "muted",
  PUBLISHED: "ok",
  UNPUBLISHED: "warn",
};

/**
 * The notice board for staff: a list with status filters, and an editor for one notice or a new draft. System Admins and the
 * Master Admin both draft; only the Master Admin publishes, unpublishes and deletes published notices (the editor checks it).
 */
export function NoticeBoardPage() {
  const { t, locale } = useLocale();
  const { formatDateTime } = useFormat();
  const { isMasterAdmin } = useRole();
  const [filter, setFilter] = useState<Filter>("ALL");
  const [page, setPage] = useState(0);
  // "new" opens an empty draft; a notice opens its editor; null shows the list.
  const [editing, setEditing] = useState<StaffNotice | "new" | null>(null);
  const notices = useStaffNotices(filter === "ALL" ? null : filter, page);

  function chooseFilter(next: Filter) {
    setFilter(next);
    setPage(0);
  }

  const back = () => setEditing(null);

  return (
    <div className="grid gap-5">
      <PageHeader
        title={t("notices.board.staff.title")}
        breadcrumbs={[{ label: t("common.nav.home"), href: "/staff" }, { label: t("notices.board.staff.title") }]}
        actions={
          editing ? null : (
            <Button variant="primary" size="sm" onClick={() => setEditing("new")}>
              {t("notices.board.staff.newNotice")}
            </Button>
          )
        }
      />
      {editing ? (
        <>
          <Button variant="ghost" size="sm" className="justify-self-start" onClick={back}>
            {t("notices.board.staff.back")}
          </Button>
          <NoticeEditor
            key={editing === "new" ? "new" : editing.id}
            notice={editing === "new" ? null : editing}
            isMasterAdmin={isMasterAdmin}
            onClose={back}
          />
        </>
      ) : (
        <>
          <p className="text-meta text-muted">{t("notices.board.staff.intro")}</p>
          <Tabs
            ariaLabel={t("notices.board.staff.title")}
            value={filter}
            onChange={(value) => chooseFilter(value as Filter)}
            wrap
            tabs={FILTERS.map((value) => ({ value, label: t(`notices.board.staff.filter.${value}`) }))}
          />
          {notices.isPending ? <Skeleton variant="rows" count={4} /> : null}
          {notices.isError ? <ErrorState onRetry={() => notices.refetch()} error={notices.error} /> : null}
          {notices.data && notices.data.content.length === 0 ? (
            <EmptyState title={t("notices.board.staff.empty")} />
          ) : null}
          {notices.data && notices.data.content.length > 0 ? (
            <ul className="divide-y divide-border overflow-hidden rounded-card border border-border bg-card">
              {notices.data.content.map((notice) => (
                <li key={notice.id}>
                  <button
                    type="button"
                    onClick={() => setEditing(notice)}
                    className="flex min-h-14 w-full items-center gap-3 px-4 py-3 text-start transition-colors hover:bg-text/[0.06] focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent"
                  >
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-label font-medium text-text">
                        {noticeTitle(notice, locale) || t("notices.board.staff.untitled")}
                      </span>
                      <span className="block text-meta text-muted">
                        {t("notices.board.staff.changed", { date: formatDateTime(notice.updatedAt) })}
                      </span>
                      {notice.publishedAt ? (
                        <span className="block text-meta text-muted">
                          {t("notices.board.publishedOn", { date: formatDateTime(notice.publishedAt) })}
                        </span>
                      ) : null}
                    </span>
                    <Pill tone={TONES[notice.status]}>{t(`notices.board.staff.status.${notice.status}`)}</Pill>
                  </button>
                </li>
              ))}
            </ul>
          ) : null}
          {notices.data ? (
            <Pagination page={page} totalPages={notices.data.totalPages} onPage={setPage} />
          ) : null}
        </>
      )}
    </div>
  );
}
