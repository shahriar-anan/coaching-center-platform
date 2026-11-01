"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { Pagination } from "@/components/ui/pagination";
import { Pill } from "@/components/ui/pill";
import { Select } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, type Column } from "@/components/ui/table";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { adminErrorText } from "@/components/staff/access/admin-error";
import {
  useAnnouncementHistory,
  useWithdrawAnnouncement,
  type AnnouncementAudience,
  type AnnouncementHistoryRow,
} from "@/lib/api/exam/staff-admin";

/** The audience filter. An empty value lists every audience. */
type AudienceFilter = AnnouncementAudience | "";

/**
 * Announcements already sent, newest first, under the send form. Each row shows the title in the reader's language (the
 * other language as fallback), the audience with its package or exam, when it went, how many students it reached, and
 * whether it was withdrawn. A row that is not withdrawn can be withdrawn, after a question. Master Admin and System Admin.
 */
export function AnnouncementHistory() {
  const { t } = useLocale();
  const { formatDateTime, formatNumber, pickTitle } = useFormat();
  const { toast } = useToast();
  const [page, setPage] = useState(0);
  const [filter, setFilter] = useState<AudienceFilter>("");
  const [withdrawing, setWithdrawing] = useState<AnnouncementHistoryRow | null>(null);
  const history = useAnnouncementHistory(page, filter || undefined);
  const withdraw = useWithdrawAnnouncement();

  function audienceText(row: AnnouncementHistoryRow): string {
    if (row.audience === "MODULE") {
      const title = pickTitle({ titleEn: row.moduleTitleEn ?? "", titleBn: row.moduleTitleBn ?? "" });
      return t("staffAdmin.history.audiencePackage", { title: title || "—" });
    }
    if (row.audience === "EXAM") {
      const title = pickTitle({ titleEn: row.examTitleEn ?? "", titleBn: row.examTitleBn ?? "" });
      return t("staffAdmin.history.audienceExam", { title: title || "—" });
    }
    if (row.audience === "SELECTED") {
      return t("staffAdmin.announce.audienceSelected");
    }
    return t("staffAdmin.announce.audienceAll");
  }

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
      cell: audienceText,
    },
    {
      key: "sent",
      header: t("staffAdmin.history.columns.sent"),
      nowrap: true,
      cell: (row) => formatDateTime(row.createdAt),
    },
    {
      key: "recipients",
      header: t("staffAdmin.history.columns.recipients"),
      align: "end",
      cell: (row) => formatNumber(row.recipients),
    },
    {
      key: "status",
      header: t("staffAdmin.history.columns.status"),
      cell: (row) =>
        row.withdrawn ? (
          <span className="grid gap-0.5">
            <span>
              <Pill tone="muted">{t("staffAdmin.history.statusWithdrawn")}</Pill>
            </span>
            {row.withdrawnAt ? (
              <span className="text-meta text-muted">
                {t("staffAdmin.history.withdrawnOn", { date: formatDateTime(row.withdrawnAt) })}
              </span>
            ) : null}
          </span>
        ) : (
          <Pill tone="ok">{t("staffAdmin.history.statusSent")}</Pill>
        ),
    },
    {
      key: "action",
      header: t("staffAdmin.history.columns.action"),
      align: "end",
      cell: (row) =>
        row.withdrawn ? null : (
          <Button variant="secondary" onClick={() => setWithdrawing(row)}>
            {t("staffAdmin.history.withdraw")}
          </Button>
        ),
    },
  ];

  async function withdrawNow() {
    if (!withdrawing) {
      return;
    }
    try {
      await withdraw.mutateAsync(withdrawing.id);
      toast({ title: t("staffAdmin.history.withdrawn"), tone: "ok" });
    } catch (e) {
      throw new Error(adminErrorText(e, t));
    }
  }

  const rows = history.data?.content ?? [];

  return (
    <section aria-labelledby="announcement-history-title" className="grid gap-4 max-w-3xl">
      <h2 id="announcement-history-title" className="text-section font-semibold text-text">
        {t("staffAdmin.history.title")}
      </h2>

      <Field label={t("staffAdmin.history.filterLabel")}>
        <Select
          value={filter}
          className="max-w-xs"
          onChange={(event) => {
            setFilter(event.target.value as AudienceFilter);
            setPage(0);
          }}
        >
          <option value="">{t("staffAdmin.history.filterAll")}</option>
          <option value="ALL">{t("staffAdmin.history.filterAllStudents")}</option>
          <option value="MODULE">{t("staffAdmin.history.filterPackage")}</option>
          <option value="EXAM">{t("staffAdmin.history.filterExam")}</option>
          <option value="SELECTED">{t("staffAdmin.history.filterSelected")}</option>
        </Select>
      </Field>

      {history.isError ? (
        <ErrorState message={t("staffAdmin.history.loadError")} onRetry={() => void history.refetch()} error={history.error} />
      ) : history.isPending ? (
        <Skeleton variant="rows" count={3} />
      ) : rows.length === 0 ? (
        <EmptyState title={t(filter ? "staffAdmin.history.emptyFiltered" : "staffAdmin.history.empty")} />
      ) : (
        <Table columns={columns} rows={rows} rowKey={(row) => row.id} loading={history.isFetching} />
      )}

      {history.data && history.data.totalPages > 1 ? (
        <Pagination page={page} totalPages={history.data.totalPages} onPage={setPage} />
      ) : null}

      <ConfirmDialog
        open={withdrawing !== null}
        onOpenChange={(open) => {
          if (!open) {
            setWithdrawing(null);
          }
        }}
        title={t("staffAdmin.history.withdrawTitle")}
        body={t("staffAdmin.history.withdrawBody")}
        confirmLabel={t("staffAdmin.history.withdraw")}
        tone="danger"
        onConfirm={withdrawNow}
      />
    </section>
  );
}
