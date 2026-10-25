"use client";

import { Suspense } from "react";
import Link from "next/link";
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
import { noticeKindLabelKey } from "./_components/notice-kind";

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

function NoticeRow({ notice, onOpen }: { notice: Notice; onOpen: (notice: Notice) => void }) {
  const { t, locale } = useLocale();
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

  if (!notice.exam) {
    return <div className={className}>{content}</div>;
  }
  return (
    <Link
      href={`/exam/${notice.exam.id}`}
      prefetch
      aria-label={`${title}. ${t("notices.openExam")}`}
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
