"use client";

import { useState } from "react";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Pagination } from "@/components/ui/pagination";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, type Column } from "@/components/ui/table";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useAnnouncementHistory, type AnnouncementHistoryRow } from "@/lib/api/exam/staff-admin";

/**
 * Announcements already sent, newest first, under the send form. Each row shows
 * the title in the reader's language (the other language as fallback), who it
 * went to, when, and how many students received it. Master Admin only.
 */
export function AnnouncementHistory() {
  const { t } = useLocale();
  const { formatDateTime, formatNumber, pickTitle } = useFormat();
  const [page, setPage] = useState(0);
  const history = useAnnouncementHistory(page);

  const columns: Column<AnnouncementHistoryRow>[] = [
    {
      key: "title",
      header: t("staffAdmin.history.columns.title"),
      cell: (row) => {
        const title = pickTitle({ titleEn: row.titleEn, titleBn: row.titleBn });
        return title || t("staffAdmin.history.untitled");
      },
    },
    {
      key: "audience",
      header: t("staffAdmin.history.columns.audience"),
      cell: (row) =>
        row.audience === "ALL"
          ? t("staffAdmin.announce.audienceAll")
          : pickTitle({ titleEn: row.moduleTitleEn ?? "", titleBn: row.moduleTitleBn ?? "" }) || "—",
    },
    {
      key: "sent",
      header: t("staffAdmin.history.columns.sent"),
      cell: (row) => formatDateTime(row.createdAt),
    },
    {
      key: "recipients",
      header: t("staffAdmin.history.columns.recipients"),
      align: "end",
      cell: (row) => formatNumber(row.recipients),
    },
  ];

  const rows = history.data?.content ?? [];

  return (
    <section aria-labelledby="announcement-history-title" className="grid gap-4 max-w-3xl">
      <h2 id="announcement-history-title" className="text-section font-semibold text-text">
        {t("staffAdmin.history.title")}
      </h2>

      {history.isError ? (
        <ErrorState message={t("staffAdmin.history.loadError")} onRetry={() => void history.refetch()} />
      ) : history.isPending ? (
        <Skeleton variant="rows" count={3} />
      ) : rows.length === 0 ? (
        <EmptyState title={t("staffAdmin.history.empty")} />
      ) : (
        <Table columns={columns} rows={rows} rowKey={(row) => row.id} loading={history.isFetching} />
      )}

      {history.data && history.data.totalPages > 1 ? (
        <Pagination page={page} totalPages={history.data.totalPages} onPage={setPage} />
      ) : null}
    </section>
  );
}
