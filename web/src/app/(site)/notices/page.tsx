"use client";

import { ChevronRight } from "lucide-react";
import { Suspense } from "react";
import Link from "next/link";
import { useState } from "react";
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
import { usePublicModulesById } from "@/lib/api/exam/catalog";
import { useListParams } from "@/lib/url/list-params";
import { formatDateTime, formatNumber, pickTitle } from "@/lib/format";
import { Button } from "@/components/ui/button";
import { cn } from "@/components/ui/cn";
import { examPath, modulePath } from "@/components/student/catalog/catalog-helpers";
import { DoorActions } from "@/components/student/catalog/door-actions";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs } from "@/components/ui/tabs";
import { noticeHref, noticeKindLabelKey } from "./_components/notice-kind";
import { announcementTarget, announcementText, isLongBody, packageDoor, previewBody } from "./_components/announcement";
import {
  NOTICE_TABS,
  defaultNoticeTab,
  parseNoticeTab,
  paymentHref,
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

/** Reminders and starts show the exam's start time in the meta line. */
const STARTS_KINDS = new Set(["EXAM_REMINDER", "EXAM_STARTING_SOON", "EXAM_OPEN"]);

/** A written result the Master Admin released again after a correction reads "updated", not "published". */
function kindWord(kind: string, t: (key: string) => string, updated = false): string {
  return t(kind === "WRITTEN_RESULT" && updated ? "notices.kind.WRITTEN_RESULT_UPDATED" : noticeKindLabelKey(kind));
}

/** One quiet card: a thin border and a soft background. The tab colour edge and the chips are gone. */
const CARD = "rounded-card border border-border bg-card px-4 py-3";

/** The quiet action at the right of a card: accent text with a small arrow, 44 px tall to tap. */
const ACTION =
  "inline-flex min-h-11 items-center gap-1 rounded-control text-label font-semibold text-accent underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent";

/** A small dot for an unread notice; read ones keep the space so the titles line up. The words are for screen readers. */
function UnreadDot({ unread }: { unread: boolean }) {
  const { t } = useLocale();
  return (
    <>
      <span aria-hidden="true" className={cn("mt-2 size-2 shrink-0 rounded-full", unread ? "bg-accent" : "bg-transparent")} />
      {unread ? <span className="sr-only">({t("notices.unreadDot")})</span> : null}
    </>
  );
}

/** The title with the time at its right end. On a narrow screen the time drops under the title. */
function TitleLine({ title, srOnly, time }: { title: string; srOnly?: string; time: { iso: string; text: string } }) {
  return (
    <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-0.5">
      <h2 className="min-w-0 text-body font-semibold text-text">
        {srOnly ? <span className="sr-only">{srOnly}: </span> : null}
        {title}
      </h2>
      <time dateTime={time.iso} className="shrink-0 text-meta text-muted">
        {time.text}
      </time>
    </div>
  );
}

/**
 * An announcement: read in place, with no edit or delete. A package or exam announcement names its target as a chip and links to
 * its page (announcements plan, section 9). The student's language first, the other one when it is blank. Long bodies show their
 * first lines and expand; expanding or opening the link marks it read.
 */
function AnnouncementRow({ notice, onOpen }: { notice: Notice; onOpen: (notice: Notice) => void }) {
  const { t, locale } = useLocale();
  const [expanded, setExpanded] = useState(false);
  const unread = isMissing(notice.readAt);
  const { title, body } = announcementText(notice.announcement, locale);
  const long = isLongBody(body);
  // An exam's title comes with the notice; a package's title and door come from its own read (cached per package).
  const target = announcementTarget(notice.announcement);
  const [packageRead] = usePublicModulesById(target?.kind === "package" ? [target.id] : []);
  const pkg = packageRead?.data;
  const targetTitle =
    target?.kind === "exam" ? (notice.exam ? pickTitle(notice.exam, locale) : "") : pkg ? pickTitle(pkg, locale) : "";
  const href = target?.kind === "exam" ? examPath(target.id) : target ? modulePath(packageDoor(pkg), target.id) : null;
  const linkLabel = target?.kind === "exam" ? t("announcements.openExam") : t("announcements.openPackage");

  function toggle() {
    if (!expanded && unread) onOpen(notice);
    setExpanded((value) => !value);
  }

  return (
    <article className={CARD}>
      <div className="flex items-start gap-3">
        <UnreadDot unread={unread} />
        <div className="grid min-w-0 flex-1 gap-1">
          <TitleLine
            title={title || t("notices.kind.ANNOUNCEMENT")}
            srOnly={t("notices.kind.ANNOUNCEMENT")}
            time={{ iso: notice.createdAt, text: formatDateTime(notice.createdAt, locale) }}
          />
          {targetTitle ? <p className="text-meta text-muted">{targetTitle}</p> : null}
          {body ? (
            <p className="whitespace-pre-line text-body text-muted">{expanded || !long ? body : previewBody(body)}</p>
          ) : null}
          {long || href ? (
            <div className="flex flex-wrap items-center gap-x-4">
              {long ? (
                <button type="button" aria-expanded={expanded} onClick={toggle} className={ACTION}>
                  {expanded ? t("notices.readLess") : t("notices.readMore")}
                </button>
              ) : null}
              {href ? (
                <Link
                  href={href}
                  prefetch
                  onClick={() => onOpen(notice)}
                  className={ACTION}
                  aria-label={targetTitle ? `${targetTitle}. ${linkLabel}` : linkLabel}
                >
                  {linkLabel}
                  <ChevronRight aria-hidden="true" className="size-4 rtl:rotate-180" />
                </Link>
              ) : null}
            </div>
          ) : null}
        </div>
      </div>
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
  const title = notice.exam ? pickTitle(notice.exam, locale) : "";
  const kindLabel = kindWord(notice.kind, t, notice.updated === true);
  const href = noticeHref(notice);
  const startsAt = notice.exam?.liveAt && STARTS_KINDS.has(notice.kind) ? notice.exam.liveAt : null;
  const hasScore = !isMissing(notice.score) && !isMissing(notice.maximum);
  const hasPassed = !isMissing(notice.passed);
  const isPurchase = notice.kind === "PURCHASE_GRANTED";
  const actionLabel = tab === "RESULTS" ? t("notices.viewResult") : t("notices.openExamAction");

  return (
    <article className={CARD}>
      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1">
        <div className="flex min-w-0 flex-1 items-start gap-3">
          <UnreadDot unread={unread} />
          <div className="grid min-w-0 flex-1 gap-1">
            <TitleLine
              title={title || kindLabel}
              time={{ iso: notice.createdAt, text: formatDateTime(notice.createdAt, locale) }}
            />
            <p className="flex flex-wrap items-center gap-x-1.5 text-meta text-muted">
              <span>{kindLabel}</span>
              {startsAt ? (
                <>
                  <span aria-hidden="true">·</span>
                  <span>{t("notices.startsAt", { time: formatDateTime(startsAt, locale) })}</span>
                </>
              ) : null}
              {hasScore ? (
                <>
                  <span aria-hidden="true">·</span>
                  <span className="font-semibold tabular-nums text-text">
                    {t("notices.score", { score: formatNumber(notice.score ?? 0, locale), max: formatNumber(notice.maximum ?? 0, locale) })}
                  </span>
                </>
              ) : null}
              {hasPassed ? (
                <>
                  <span aria-hidden="true">·</span>
                  <span className={notice.passed ? "font-semibold text-ok" : ""}>
                    {notice.passed ? t("notices.passed") : t("notices.failed")}
                  </span>
                </>
              ) : null}
            </p>
          </div>
        </div>
        <div className="flex shrink-0 flex-wrap items-center gap-x-4 ps-5">
          {href ? (
            <Link
              href={href}
              prefetch
              onClick={() => onOpen(notice)}
              className={ACTION}
              aria-label={`${title || kindLabel}. ${actionLabel}`}
            >
              {actionLabel}
              <ChevronRight aria-hidden="true" className="size-4 rtl:rotate-180" />
            </Link>
          ) : isPurchase ? (
            <Link href="/my-modules" prefetch onClick={() => onOpen(notice)} className={ACTION}>
              {t("notices.myExams")}
              <ChevronRight aria-hidden="true" className="size-4 rtl:rotate-180" />
            </Link>
          ) : unread ? (
            <button type="button" onClick={() => onOpen(notice)} className={ACTION}>
              {t("notices.markRead")}
            </button>
          ) : null}
          {isPurchase ? (
            <Link href={paymentHref(notice)} prefetch className={ACTION}>
              {t("notices.viewPayment")}
            </Link>
          ) : null}
        </div>
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
          <ErrorState error={query.error} onRetry={() => void query.refetch()} />
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
            {query.isError ? <ErrorState error={query.error} onRetry={() => void query.refetch()} /> : null}
            <ul className="grid gap-2.5">
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
