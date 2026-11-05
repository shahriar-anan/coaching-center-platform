"use client";

import Link from "next/link";
import { useState } from "react";
import { noticeBody, noticeTitle } from "@/components/notice-board/notice-text";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { Skeleton } from "@/components/ui/skeleton";
import { useLocale } from "@/i18n/locale-context";
import { usePublicNotices, type PublicNotice } from "@/lib/api/exam/site-notices";
import { useFormat } from "@/lib/format/use-format";

/** The public notice board: published notices, newest first, ten to a page. Text is shown as text, never as HTML. */
export function NoticeBoardList() {
  const { t } = useLocale();
  const [page, setPage] = useState(0);
  const notices = usePublicNotices(page);
  const title = t("notices.board.publicTitle");

  return (
    <div className="grid gap-5">
      <PageHeader title={title} breadcrumbs={[{ label: t("common.nav.home"), href: "/" }, { label: title }]} />
      <p className="text-body text-muted">{t("notices.board.publicIntro")}</p>
      {notices.isPending ? <Skeleton variant="card" count={3} /> : null}
      {notices.isError ? <ErrorState onRetry={() => notices.refetch()} error={notices.error} /> : null}
      {notices.data && notices.data.content.length === 0 ? (
        <EmptyState title={t("notices.board.empty")} body={t("notices.board.emptyBody")} />
      ) : null}
      {notices.data && notices.data.content.length > 0 ? (
        <ul className="grid gap-3">
          {notices.data.content.map((notice) => (
            <li key={notice.id}>
              <NoticeCard notice={notice} />
            </li>
          ))}
        </ul>
      ) : null}
      {notices.data ? <Pagination page={page} totalPages={notices.data.totalPages} onPage={setPage} /> : null}
    </div>
  );
}

function NoticeCard({ notice }: { notice: PublicNotice }) {
  const { t, locale } = useLocale();
  const { formatDate } = useFormat();
  const heading = noticeTitle(notice, locale);
  return (
    <article className="grid gap-2 rounded-card border border-border bg-card p-4 shadow-[var(--card-shadow)] sm:p-5">
      <p className="text-meta text-muted">{t("notices.board.publishedOn", { date: formatDate(notice.publishedAt) })}</p>
      <h2 className="text-section font-semibold text-text">{heading}</h2>
      <p className="line-clamp-3 whitespace-pre-line text-body text-text">{noticeBody(notice, locale)}</p>
      <Link
        href={`/notice-board/${notice.id}`}
        prefetch={false}
        className="inline-flex min-h-11 items-center text-label font-medium text-accent-text underline-offset-4 hover:underline"
      >
        {t("notices.board.readMore")}
        <span className="sr-only">: {heading}</span>
      </Link>
    </article>
  );
}
