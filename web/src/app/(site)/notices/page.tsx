"use client";

import { Suspense } from "react";
import Link from "next/link";
import { useState } from "react";
import { BookOpen, Megaphone, Trophy, type LucideIcon } from "lucide-react";
import { RequireStudent } from "@/lib/auth/guards";
import { useLocale } from "@/i18n/locale-context";
import { useToast } from "@/components/ui/toast";
import {
  useMarkAllNoticesRead,
  useMarkNoticeRead,
  useNotices,
  useUnreadCount,
  type Notice,
  type NoticeGroup,
} from "@/lib/api/exam/notices";
import { useListParams } from "@/lib/url/list-params";
import { formatDateTime, formatNumber, pickTitle } from "@/lib/format";
import { Button } from "@/components/ui/button";
import { cn } from "@/components/ui/cn";
import { DoorActions } from "@/components/student/catalog/door-actions";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs } from "@/components/ui/tabs";
import { noticeHref, noticeKindLabelKey } from "./_components/notice-kind";
import { announcementText, isLongBody, previewBody } from "./_components/announcement";
import {
  NOTICE_TABS,
  defaultNoticeTab,
  parseNoticeTab,
  paymentHref,
  tabEdgeClass,
} from "./_components/notice-tabs";
import { isMissing } from "@/lib/api/missing";

export default function NoticesPage() {
  return (
    <Suspense fallback={<Skeleton variant="card" count={2} />}>
      <RequireStudent>
        <NoticesView />
      </RequireStudent>
    </Suspense>
  );
}

/** The icon of each tab (Phase 4, A8): a trophy for results, a book for exams, a megaphone for announcements. */
const TAB_ICON: Record<NoticeGroup, LucideIcon> = {
  RESULTS: Trophy,
  EXAMS: BookOpen,
  ANNOUNCEMENTS: Megaphone,
};

/** Reminders and starts show the exam's start time in the meta line. */
const STARTS_KINDS = new Set(["EXAM_REMINDER", "EXAM_STARTING_SOON", "EXAM_OPEN"]);

/** A written result the Master Admin released again after a correction reads "updated", not "published". */
function kindWord(kind: string, t: (key: string) => string, updated = false): string {
  return t(kind === "WRITTEN_RESULT" && updated ? "notices.kind.WRITTEN_RESULT_UPDATED" : noticeKindLabelKey(kind));
}

/**
 * An announcement: read in place, no link, no edit or delete. The student's language first, the
 * other one when it is blank. Long bodies show their first lines and expand; expanding marks it read.
 */
function AnnouncementRow({ notice, onOpen }: { notice: Notice; onOpen: (notice: Notice) => void }) {
  const { t, locale } = useLocale();
  const [expanded, setExpanded] = useState(false);
  const unread = isMissing(notice.readAt);
  const { title, body } = announcementText(notice.announcement, locale);
  const long = isLongBody(body);

  function toggle() {
    if (!expanded && unread) onOpen(notice);
    setExpanded((value) => !value);
  }

  return (
    <article className={cn("grid gap-2 rounded-card border border-l-4 border-border bg-card p-4", tabEdgeClass("ANNOUNCEMENTS"))}>
      <p className="flex flex-wrap items-center gap-2 text-meta text-muted">
        <Pill tone="muted">{t("notices.kind.ANNOUNCEMENT")}</Pill>
        <time dateTime={notice.createdAt}>{formatDateTime(notice.createdAt, locale)}</time>
        {unread ? <span className="sr-only">({t("notices.unreadDot")})</span> : null}
      </p>
      <h2 className="flex items-center gap-2 text-card-title font-bold text-text">
        {unread ? <span aria-hidden="true" className="size-2 shrink-0 rounded-full bg-accent" /> : null}
        {title || t("notices.kind.ANNOUNCEMENT")}
      </h2>
      {body ? (
        <p className="whitespace-pre-line text-body text-text">{expanded || !long ? body : previewBody(body)}</p>
      ) : null}
      {long ? (
        <button
          type="button"
          aria-expanded={expanded}
          onClick={toggle}
          className="justify-self-start font-semibold text-accent underline underline-offset-4"
        >
          {expanded ? t("notices.readLess") : t("notices.readMore")}
        </button>
      ) : null}
    </article>
  );
}

/**
 * One exam notice as a card (Phase 4, A8): the kind pill, the exam title, the meta line, the score for a result, and the
 * actions. Opening any link marks the notice read.
 */
function NoticeRow({ notice, tab, onOpen }: { notice: Notice; tab: NoticeGroup; onOpen: (notice: Notice) => void }) {
  const { t, locale } = useLocale();
  if (notice.kind === "ANNOUNCEMENT") {
    return <AnnouncementRow notice={notice} onOpen={onOpen} />;
  }
  const unread = isMissing(notice.readAt);
  const Icon = TAB_ICON[tab];
  const title = notice.exam ? pickTitle(notice.exam, locale) : "";
  const kindLabel = kindWord(notice.kind, t, notice.updated === true);
  const href = noticeHref(notice);
  const startsAt = notice.exam?.liveAt && STARTS_KINDS.has(notice.kind) ? notice.exam.liveAt : null;
  const hasScore = !isMissing(notice.score) && !isMissing(notice.maximum);
  const isPurchase = notice.kind === "PURCHASE_GRANTED";

  return (
    <article className={cn("grid gap-3 rounded-card border border-l-4 border-border bg-card p-4", tabEdgeClass(tab))}>
      <div className="flex flex-wrap items-center gap-2 text-meta text-muted">
        <Icon aria-hidden="true" className="size-4 shrink-0" />
        <Pill tone={tab === "RESULTS" ? "ok" : tab === "EXAMS" ? "accent" : "muted"}>{kindLabel}</Pill>
        <time dateTime={notice.createdAt}>{formatDateTime(notice.createdAt, locale)}</time>
        {unread ? (
          <span className="flex items-center gap-1 text-text-accent">
            <span aria-hidden="true" className="size-2 rounded-full bg-accent" />
            {t("notices.unreadDot")}
          </span>
        ) : null}
      </div>

      <p className="text-card-title font-bold text-text">{title || kindLabel}</p>

      {startsAt || hasScore || !isMissing(notice.passed) ? (
        <div className="flex flex-wrap items-center gap-2 text-meta text-muted">
          {startsAt ? <span>{t("notices.startsAt", { time: formatDateTime(startsAt, locale) })}</span> : null}
          {hasScore ? (
            <Pill tone="muted">
              {t("notices.score", { score: formatNumber(notice.score ?? 0, locale), max: formatNumber(notice.maximum ?? 0, locale) })}
            </Pill>
          ) : null}
          {isMissing(notice.passed) ? null : (
            <Pill tone={notice.passed ? "ok" : "muted"}>{notice.passed ? t("notices.passed") : t("notices.failed")}</Pill>
          )}
        </div>
      ) : null}

      <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-label">
        {href ? (
          <Link
            href={href}
            prefetch
            onClick={() => onOpen(notice)}
            className="font-semibold text-accent underline underline-offset-4"
            aria-label={`${title || kindLabel}. ${tab === "RESULTS" ? t("notices.viewResult") : t("notices.openExamAction")}`}
          >
            {tab === "RESULTS" ? t("notices.viewResult") : t("notices.openExamAction")}
          </Link>
        ) : isPurchase ? (
          <Link href="/my-modules" prefetch onClick={() => onOpen(notice)} className="font-semibold text-accent underline underline-offset-4">
            {t("notices.myExams")}
          </Link>
        ) : unread ? (
          <button
            type="button"
            onClick={() => onOpen(notice)}
            className="font-semibold text-accent underline underline-offset-4"
          >
            {t("notices.markRead")}
          </button>
        ) : null}
        {isPurchase ? (
          <Link href={paymentHref(notice)} prefetch className="font-semibold text-accent underline underline-offset-4">
            {t("notices.viewPayment")}
          </Link>
        ) : null}
      </div>
    </article>
  );
}

/** The notices of one tab, one page at a time. The tab and the page live in the URL (`?tab=`, `?page=`). */
function NoticesView() {
  const { t } = useLocale();
  const [params, setParams] = useListParams({ page: 0, tab: "" });
  const counts = useUnreadCount();
  const byGroup = counts.data?.byGroup;
  const tab: NoticeGroup = parseNoticeTab(params.tab) ?? defaultNoticeTab(byGroup);
  const query = useNotices(params.page, tab);
  const markOne = useMarkNoticeRead();
  const markAll = useMarkAllNoticesRead();
  const { toast } = useToast();
  const rows = query.data?.content ?? [];
  const anyUnread = rows.some((row) => isMissing(row.readAt));

  // The row's link opens the exam or result page; opening it also marks the notice read.
  function open(notice: Notice) {
    if (isMissing(notice.readAt)) {
      markOne.mutate(notice.id, { onError: () => toast({ title: t("common.states.errorBody"), tone: "danger" }) });
    }
  }

  // Choosing a tab writes it to the URL, so the page does not jump to another tab when the counts change.
  function selectTab(next: NoticeGroup) {
    setParams({ tab: next });
  }

  function markTab() {
    if (!params.tab) setParams({ tab });
    markAll.mutate(tab, {
      onSuccess: () => toast({ title: t("notices.markedAll"), tone: "ok" }),
      onError: () => toast({ title: t("common.states.errorBody"), tone: "danger" }),
    });
  }

  return (
    <>
      <PageHeader
        title={t("notices.title")}
        actions={
          anyUnread ? (
            <Button variant="secondary" loading={markAll.isPending} onClick={markTab} className="min-h-10">
              {t("notices.markTab")}
            </Button>
          ) : null
        }
      />

      <Tabs
        ariaLabel={t("notices.tabs.label")}
        tabs={NOTICE_TABS.map((group) => ({
          value: group,
          label: t(`notices.tabs.${group}`),
          badge: byGroup?.[group] ?? 0,
        }))}
        value={tab}
        onChange={(value) => selectTab(value as NoticeGroup)}
      />

      <div className="mt-4">
        {query.isPending ? (
          <Skeleton variant="card" count={3} />
        ) : query.isError && rows.length === 0 ? (
          <ErrorState onRetry={() => void query.refetch()} />
        ) : rows.length === 0 ? (
          params.page === 0 ? (
            <EmptyState
              title={t(`notices.emptyTab.${tab}`)}
              body={t("notices.emptyBody")}
              action={<DoorActions />}
            />
          ) : (
            <EmptyState title={t(`notices.emptyTab.${tab}`)} />
          )
        ) : (
          <div className="grid gap-4">
            {query.isError ? <ErrorState onRetry={() => void query.refetch()} /> : null}
            <ul className="grid gap-3">
              {rows.map((notice) => (
                <li key={notice.id}>
                  <NoticeRow notice={notice} tab={tab} onOpen={open} />
                </li>
              ))}
            </ul>
            <Pagination
              page={params.page}
              totalPages={query.data?.totalPages ?? 0}
              onPage={(page) => setParams({ page })}
            />
          </div>
        )}
      </div>
    </>
  );
}
