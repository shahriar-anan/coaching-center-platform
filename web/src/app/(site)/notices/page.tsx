"use client";

import { Suspense } from "react";
import Link from "next/link";
import { useState } from "react";
import { RequireStudent } from "@/lib/auth/guards";
import { useLocale } from "@/i18n/locale-context";
import { useMarkAllNoticesRead, useMarkNoticeRead, useNotices, type Notice } from "@/lib/api/exam/notices";
import { useListParams } from "@/lib/url/list-params";
import { formatDateTime, formatNumber, pickTitle } from "@/lib/format";
import { Button } from "@/components/ui/button";
import { DoorActions } from "@/components/student/catalog/door-actions";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/components/ui/cn";
import { noticeHref, noticeKindLabelKey } from "./_components/notice-kind";
import { announcementText, isLongBody, previewBody } from "./_components/announcement";

export default function NoticesPage() {
  return (
    <Suspense fallback={<Skeleton variant="card" count={2} />}>
      <RequireStudent>
        <NoticesView />
      </RequireStudent>
    </Suspense>
  );
}

function kindWord(kind: string, t: (key: string) => string): string {
  return t(noticeKindLabelKey(kind));
}

/**
 * An announcement: read in place, no link, no edit or delete. The student's language first, the
 * other one when it is blank. Long bodies show their first lines and expand; expanding marks it read.
 */
function AnnouncementRow({ notice, onOpen }: { notice: Notice; onOpen: (notice: Notice) => void }) {
  const { t, locale } = useLocale();
  const [expanded, setExpanded] = useState(false);
  const unread = notice.readAt === null;
  const { title, body } = announcementText(notice.announcement, locale);
  const long = isLongBody(body);

  function toggle() {
    if (!expanded && unread) onOpen(notice);
    setExpanded((value) => !value);
  }

  return (
    <article className="grid gap-2 rounded-card border border-border bg-card p-4">
      <p className="flex flex-wrap items-center gap-2 text-meta text-muted">
        <span>{t("notices.kind.ANNOUNCEMENT")}</span>
        <time dateTime={notice.createdAt}>{formatDateTime(notice.createdAt, locale)}</time>
        {unread ? <span className="sr-only">({t("notices.unreadDot")})</span> : null}
      </p>
      <h2 className="text-card-title font-bold text-text">{title || t("notices.kind.ANNOUNCEMENT")}</h2>
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

function NoticeRow({ notice, onOpen }: { notice: Notice; onOpen: (notice: Notice) => void }) {
  const { t, locale } = useLocale();
  if (notice.kind === "ANNOUNCEMENT") {
    return <AnnouncementRow notice={notice} onOpen={onOpen} />;
  }
  const unread = notice.readAt === null;
  const title = notice.exam ? pickTitle(notice.exam, locale) : "";
  const content = (
    <span className="flex min-w-0 flex-1 items-start gap-3">
      <span
        aria-hidden="true"
        className={cn("mt-2 size-2.5 shrink-0 rounded-full", unread ? "bg-accent" : "bg-transparent")}
      />
      <span className="grid min-w-0 gap-1">
        <span className="font-semibold text-text">
          {title || kindWord(notice.kind, t)}
          {unread ? <span className="sr-only"> ({t("notices.unreadDot")})</span> : null}
        </span>
        <span className="text-meta text-muted">
          {kindWord(notice.kind, t)}
          {notice.score !== null && notice.maximum !== null
            ? ` · ${t("notices.score", { score: formatNumber(notice.score, locale), max: formatNumber(notice.maximum, locale) })}`
            : ""}
        </span>
        <span className="flex flex-wrap items-center gap-2 text-meta text-muted">
          <time dateTime={notice.createdAt}>{formatDateTime(notice.createdAt, locale)}</time>
          {notice.passed === null ? null : (
            <Pill tone={notice.passed ? "ok" : "muted"}>
              {notice.passed ? t("notices.passed") : t("notices.failed")}
            </Pill>
          )}
        </span>
      </span>
    </span>
  );

  const className =
    "flex items-start gap-3 rounded-card border border-border bg-card p-4 transition-colors hover:border-accent";

  const href = noticeHref(notice);
  if (!href) {
    // An unread notice with no link is a button, so opening it marks it read.
    if (!unread) return <div className={className}>{content}</div>;
    return (
      <button type="button" onClick={() => onOpen(notice)} className={`${className} w-full text-start`}>
        {content}
      </button>
    );
  }
  return (
    <Link
      href={href}
      prefetch
      aria-label={`${title || kindWord(notice.kind, t)}. ${t("notices.openExam")}`}
      onClick={() => onOpen(notice)}
      className={className}
    >
      {content}
    </Link>
  );
}

function NoticesView() {
  const { t } = useLocale();
  const [params, setParams] = useListParams({ page: 0 });
  const query = useNotices(params.page);
  const markOne = useMarkNoticeRead();
  const markAll = useMarkAllNoticesRead();
  const rows = query.data?.content ?? [];
  const anyUnread = rows.some((row) => row.readAt === null);

  // The row is a link to the exam page; opening it also marks the notice read.
  function open(notice: Notice) {
    if (notice.readAt === null) {
      markOne.mutate(notice.id);
    }
  }

  return (
    <>
      <PageHeader
        title={t("notices.title")}
        actions={
          anyUnread ? (
            <Button
              variant="secondary"
              loading={markAll.isPending}
              onClick={() => markAll.mutate()}
              className="min-h-10"
            >
              {t("notices.markAll")}
            </Button>
          ) : null
        }
      />

      {query.isPending ? (
        <Skeleton variant="card" count={3} />
      ) : query.isError && rows.length === 0 ? (
        <ErrorState onRetry={() => void query.refetch()} />
      ) : rows.length === 0 ? (
        <EmptyState title={t("notices.empty")} body={t("notices.emptyBody")} action={<DoorActions />} />
      ) : (
        <div className="grid gap-4">
          {query.isError ? <ErrorState onRetry={() => void query.refetch()} /> : null}
          <ul className="grid gap-3">
            {rows.map((notice) => (
              <li key={notice.id}>
                <NoticeRow notice={notice} onOpen={open} />
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
    </>
  );
}
