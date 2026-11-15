"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Pill, type PillTone } from "@/components/ui/pill";
import { Table, type Column } from "@/components/ui/table";
import { useLocale } from "@/i18n/locale-context";
import { useRole } from "@/lib/auth/guards";
import {
  dhakaToday,
  uploadBlockReason,
  useBatchResults,
  type BatchExamRow,
  type ResultState,
} from "@/lib/api/exam/offline-results";
import { useFormat } from "@/lib/format/use-format";
import { PublishResultDialog } from "./result-confirms";
import { ResultUploadDialog } from "./result-upload-dialog";

const STATE_TONE: Record<ResultState, PillTone> = {
  NONE: "muted",
  STAGED: "warn",
  PUBLISHED: "ok",
  PUBLISHED_WITH_STAGED: "warn",
};

/**
 * The Results tab of a batch (plan 7.5): the batch's exams, newest first, each with its result state. Any admin can upload a result for an
 * exam that has been held and is not cancelled; the Master Admin can publish a staged one. Review opens the sheet view. On a phone each row
 * is a card (the Table does that).
 */
export function BatchResults({ batchId, onGo }: { batchId: string; onGo: (tab: string) => void }) {
  const { t } = useLocale();
  const { formatDate } = useFormat();
  const role = useRole();
  const results = useBatchResults(batchId);
  const [uploading, setUploading] = useState<BatchExamRow | null>(null);
  const [publishing, setPublishing] = useState<BatchExamRow | null>(null);
  const today = dhakaToday();

  if (results.isError) {
    return <ErrorState onRetry={() => results.refetch()} error={results.error} />;
  }

  const rows = results.data ?? [];
  const columns: Column<BatchExamRow>[] = [
    {
      key: "date",
      header: t("staffOfflineResults.list.columns.date"),
      mobileLabel: t("staffOfflineResults.list.columns.date"),
      nowrap: true,
      cell: (row) => formatDate(row.heldOn, { year: "auto" }),
    },
    {
      key: "exam",
      header: t("staffOfflineResults.list.columns.exam"),
      mobileLabel: t("staffOfflineResults.list.columns.exam"),
      cell: (row) => <ExamTitle row={row} />,
    },
    {
      key: "instructor",
      header: t("staffOfflineResults.list.columns.instructor"),
      mobileLabel: t("staffOfflineResults.list.columns.instructor"),
      cell: (row) => <span className="break-words">{row.instructorName ?? t("staffOfflineResults.list.noInstructor")}</span>,
    },
    {
      key: "state",
      header: t("staffOfflineResults.list.columns.state"),
      mobileLabel: t("staffOfflineResults.list.columns.state"),
      cell: (row) => <StateCell row={row} />,
    },
    {
      key: "actions",
      header: t("staffOfflineResults.list.columns.actions"),
      mobileLabel: t("staffOfflineResults.list.columns.actions"),
      cell: (row) => (
        <ExamActions
          row={row}
          batchId={batchId}
          today={today}
          canUpload={role.canUploadOfflineResult}
          canPublish={role.canPublishOfflineResult}
          onUpload={() => setUploading(row)}
          onPublish={() => setPublishing(row)}
        />
      ),
    },
  ];

  return (
    <section aria-labelledby="batch-results" className="grid gap-4">
      <div className="grid gap-1">
        <h3 id="batch-results" className="text-card-title text-text">
          {t("staffOfflineResults.tab")}
        </h3>
        <p className="text-meta text-muted">{t("staffOfflineResults.list.intro")}</p>
      </div>

      <Table
        columns={columns}
        rows={rows}
        rowKey={(row) => row.classId}
        loading={results.isPending}
        empty={
          <EmptyState
            title={t("staffOfflineResults.list.empty.title")}
            body={t("staffOfflineResults.list.empty.body")}
            action={
              <Button variant="secondary" onClick={() => onGo("classes")}>
                {t("staffOfflineResults.list.empty.goClasses")}
              </Button>
            }
          />
        }
      />

      {uploading ? (
        <ResultUploadDialog
          key={uploading.classId}
          classId={uploading.classId}
          examLabel={`${examTitle(uploading)}, ${formatDate(uploading.heldOn, { year: "auto" })}`}
          open
          onOpenChange={(open) => {
            if (!open) {
              setUploading(null);
            }
          }}
        />
      ) : null}

      {publishing ? (
        <PublishResultDialog
          key={publishing.classId}
          classId={publishing.classId}
          open
          onOpenChange={(open) => {
            if (!open) {
              setPublishing(null);
            }
          }}
        />
      ) : null}
    </section>
  );
}

/** "Constitution · Part 1" */
function examTitle(row: BatchExamRow): string {
  return row.topic ? `${row.subject} · ${row.topic}` : row.subject;
}

function ExamTitle({ row }: { row: BatchExamRow }) {
  const { t } = useLocale();
  return (
    <span className="grid min-w-0 gap-1">
      <span className="break-words font-medium text-text">{examTitle(row)}</span>
      {row.cancelled ? (
        <span>
          <Pill tone="danger">{t("staffOfflineResults.list.cancelled")}</Pill>
        </span>
      ) : null}
    </span>
  );
}

/** The result state, with the date of the published sheet under it. */
function StateCell({ row }: { row: BatchExamRow }) {
  const { t } = useLocale();
  const { formatDate } = useFormat();
  return (
    <span className="grid justify-items-start gap-1">
      <Pill tone={STATE_TONE[row.state]}>{t(`staffOfflineResults.list.states.${row.state}`)}</Pill>
      {row.publishedAt ? (
        <span className="text-meta text-muted">{t("staffOfflineResults.list.publishedOn", { date: formatDate(row.publishedAt) })}</span>
      ) : null}
    </span>
  );
}

/**
 * The row's actions by role and state. Upload is off for a future or cancelled exam, with the reason under it (so a phone shows it too).
 * Review opens the sheet view once a sheet exists. Publish is the Master Admin's, on a row with a staged sheet.
 */
function ExamActions({
  row,
  batchId,
  today,
  canUpload,
  canPublish,
  onUpload,
  onPublish,
}: {
  row: BatchExamRow;
  batchId: string;
  today: string;
  canUpload: boolean;
  canPublish: boolean;
  onUpload: () => void;
  onPublish: () => void;
}) {
  const { t } = useLocale();
  const block = uploadBlockReason(row, today);
  const hasPublished = row.state === "PUBLISHED" || row.state === "PUBLISHED_WITH_STAGED";
  const hasStaged = row.state === "STAGED" || row.state === "PUBLISHED_WITH_STAGED";
  const hasSheet = row.state !== "NONE";

  return (
    <div className="grid justify-items-start gap-2">
      <div className="flex flex-wrap items-center gap-2">
        {canUpload ? (
          <Button size="sm" variant="secondary" onClick={onUpload} disabled={block !== null}>
            {hasPublished ? t("staffOfflineResults.list.uploadNew") : t("staffOfflineResults.list.upload")}
          </Button>
        ) : null}
        {hasSheet ? (
          <Button size="sm" variant="ghost" href={`/staff/offline-batches/${batchId}/results/${row.classId}`}>
            {t("staffOfflineResults.list.review")}
          </Button>
        ) : null}
        {canPublish && hasStaged ? (
          <Button size="sm" onClick={onPublish}>
            {t("staffOfflineResults.list.publish")}
          </Button>
        ) : null}
      </div>
      {canUpload && block ? <p className="text-meta text-muted">{t(`staffOfflineResults.list.blocked.${block}`)}</p> : null}
    </div>
  );
}
