"use client";

import { NoticePhotos } from "@/components/notice-board/notice-photos";
import { noticeBody, noticeTitle } from "@/components/notice-board/notice-text";
import { PdfAttachmentCard } from "@/components/ui/file/pdf-attachment-card";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { useLocale } from "@/i18n/locale-context";
import { isNotFoundError } from "@/lib/api/errors";
import { usePublicNotice } from "@/lib/api/exam/site-notices";
import { useFormat } from "@/lib/format/use-format";

/** One published notice in full. Line breaks are kept; the text is shown as text, never as HTML. */
export function NoticeBoardDetail({ id }: { id: string }) {
  const { t, locale } = useLocale();
  const { formatDate } = useFormat();
  const notice = usePublicNotice(id);
  const listLabel = t("notices.board.publicTitle");

  if (notice.isPending) {
    return <Skeleton variant="card" count={2} />;
  }

  if (notice.isError && isNotFoundError(notice.error)) {
    return (
      <div className="grid gap-5">
        <PageHeader title={listLabel} breadcrumbs={[{ label: t("common.nav.home"), href: "/" }, { label: listLabel, href: "/notice-board" }]} />
        <EmptyState
          title={t("notices.board.notFound")}
          body={t("notices.board.notFoundBody")}
          action={<Button href="/notice-board">{t("notices.board.allNotices")}</Button>}
        />
      </div>
    );
  }

  if (notice.isError || !notice.data) {
    return <ErrorState onRetry={() => notice.refetch()} error={notice.error} />;
  }

  const data = notice.data;
  const heading = noticeTitle(data, locale) || listLabel;
  return (
    <div className="grid gap-5">
      <PageHeader
        title={heading}
        breadcrumbs={[
          { label: t("common.nav.home"), href: "/" },
          { label: listLabel, href: "/notice-board" },
          { label: heading },
        ]}
      />
      <p className="text-meta text-muted">{t("notices.board.publishedOn", { date: formatDate(data.publishedAt) })}</p>
      <div className="whitespace-pre-line text-body text-text">{noticeBody(data, locale)}</div>
      {data.photos && data.photos.length > 0 ? <NoticePhotos title={heading} photos={data.photos} size="page" /> : null}
      {data.pdf ? (
        <PdfAttachmentCard
          url={data.pdf.url}
          fileName={data.pdf.fileName}
          byteSize={data.pdf.byteSize}
          title={t("notices.board.pdfTitle")}
        />
      ) : null}
      <Button href="/notice-board" variant="secondary" className="justify-self-start">
        {t("notices.board.allNotices")}
      </Button>
    </div>
  );
}
