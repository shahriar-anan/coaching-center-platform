"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pill, type PillTone } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs } from "@/components/ui/tabs";
import { useLocale } from "@/i18n/locale-context";
import { isNotFoundError } from "@/lib/api/errors";
import { useBatch } from "@/lib/api/exam/offline-admin";
import {
  dhakaToday,
  downloadResultFile,
  uploadBlockReason,
  useSheetView,
  type ResultSheet,
  type ResultSheetView,
  type ResultState,
} from "@/lib/api/exam/offline-results";
import { useRole } from "@/lib/auth/guards";
import { useFormat } from "@/lib/format/use-format";
import { DiscardStagedDialog, PublishResultDialog } from "./result-confirms";
import { ResultTable } from "./result-table";
import { ResultUploadDialog } from "./result-upload-dialog";

const STATE_TONE: Record<ResultState, PillTone> = {
  NONE: "muted",
  STAGED: "warn",
  PUBLISHED: "ok",
  PUBLISHED_WITH_STAGED: "warn",
};

type SheetTab = "published" | "staged";

/**
 * One exam's result sheet (plan 7.7). The header says the exam, its state, who uploaded the sheet and who published it. When a published and a
 * staged sheet both exist, two tabs compare them before the Master Admin publishes. The table is the same as the upload preview, with the
 * mismatch tint. The actions: Publish (the Master Admin, on a staged sheet), Discard staged (any admin), and Upload new version.
 */
export function ExamResultSheet({ batchId, classId }: { batchId: string; classId: string }) {
  const { t } = useLocale();
  const { formatDate, pickTitle } = useFormat();
  const role = useRole();
  const batch = useBatch(batchId);
  const view = useSheetView(classId);
  const [tab, setTab] = useState<SheetTab | null>(null);
  const [uploading, setUploading] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [discarding, setDiscarding] = useState(false);

  if (view.isPending || batch.isPending) {
    return <Skeleton variant="card" count={2} />;
  }
  if (view.isError) {
    if (isNotFoundError(view.error)) {
      return <EmptyState title={t("common.notFound.title")} body={t("common.notFound.body")} />;
    }
    return <ErrorState onRetry={() => view.refetch()} error={view.error} />;
  }

  const v = view.data;
  const batchName = batch.data ? pickTitle({ titleEn: batch.data.nameEn, titleBn: batch.data.nameBn }) : "";
  const title = v.topic ? `${v.subject} · ${v.topic}` : v.subject;
  const state = stateOf(v);
  const shownTab: SheetTab = tab ?? (v.staged ? "staged" : "published");
  const sheet = (shownTab === "staged" ? v.staged : v.published) ?? v.staged ?? v.published;
  const block = uploadBlockReason(v, dhakaToday());

  return (
    <div className="grid gap-6">
      <PageHeader
        title={title}
        breadcrumbs={[
          { label: t("common.staffNav.dashboard"), href: "/staff" },
          { label: t("staffOffline.batches.title"), href: "/staff/offline-batches" },
          { label: batchName, href: `/staff/offline-batches/${batchId}?tab=results` },
          { label: title },
        ]}
      />

      <Card padding="sm" className="grid gap-4">
        <div className="flex flex-wrap items-center gap-3">
          <Pill tone={STATE_TONE[state]}>{t(`staffOfflineResults.list.states.${state}`)}</Pill>
          <span className="text-body text-muted">{t("staffOfflineResults.sheet.heldOn", { date: formatDate(v.heldOn, { year: "auto" }) })}</span>
          {v.cancelled ? <Pill tone="danger">{t("staffOfflineResults.list.cancelled")}</Pill> : null}
        </div>

        {sheet ? <SheetFacts sheet={sheet} /> : null}

        {v.published && v.staged ? (
          <Tabs
            tabs={[
              { value: "published", label: t("staffOfflineResults.sheet.tabs.published") },
              { value: "staged", label: t("staffOfflineResults.sheet.tabs.staged") },
            ]}
            value={shownTab}
            onChange={(value) => setTab(value as SheetTab)}
            ariaLabel={t("staffOfflineResults.sheet.tabs.label")}
            variant="pill"
          />
        ) : null}

        <div className="flex flex-wrap items-center gap-2 border-t border-border pt-4">
          {sheet ? <DownloadButton classId={classId} sheet={sheet} /> : null}
          {role.canPublishOfflineResult && v.staged ? (
            <Button onClick={() => setPublishing(true)}>{t("staffOfflineResults.sheet.publish")}</Button>
          ) : null}
          {role.canUploadOfflineResult && v.staged ? (
            <Button variant="secondary" onClick={() => setDiscarding(true)}>
              {t("staffOfflineResults.sheet.discard")}
            </Button>
          ) : null}
          {role.canUploadOfflineResult ? (
            <div className="grid justify-items-start gap-1">
              <Button variant="secondary" onClick={() => setUploading(true)} disabled={block !== null}>
                {v.published ? t("staffOfflineResults.list.uploadNew") : t("staffOfflineResults.list.upload")}
              </Button>
              {block ? <p className="text-meta text-muted">{t(`staffOfflineResults.list.blocked.${block}`)}</p> : null}
            </div>
          ) : null}
        </div>
      </Card>

      {sheet ? (
        <ResultTable rows={sheet.rows} examType={sheet.examType} passMark={sheet.passMark} label={t("staffOfflineResults.upload.table.label")} />
      ) : (
        <EmptyState title={t("staffOfflineResults.sheet.noSheet.title")} body={t("staffOfflineResults.sheet.noSheet.body")} />
      )}

      {uploading ? (
        <ResultUploadDialog
          classId={classId}
          examLabel={`${title}, ${formatDate(v.heldOn, { year: "auto" })}`}
          open
          onOpenChange={setUploading}
        />
      ) : null}
      {publishing ? <PublishResultDialog classId={classId} open onOpenChange={setPublishing} /> : null}
      {discarding ? <DiscardStagedDialog classId={classId} open onOpenChange={setDiscarding} /> : null}
    </div>
  );
}

/** The state of the exam, from which of its sheets exist. */
function stateOf(v: ResultSheetView): ResultState {
  if (v.published && v.staged) {
    return "PUBLISHED_WITH_STAGED";
  }
  if (v.published) {
    return "PUBLISHED";
  }
  if (v.staged) {
    return "STAGED";
  }
  return "NONE";
}

/** Who uploaded the sheet and when, and who published it and when (when it is published). */
function SheetFacts({ sheet }: { sheet: ResultSheet }) {
  const { t } = useLocale();
  const { formatDate, formatDateTime, formatNumber } = useFormat();
  const uploaded = sheet.uploadedBy
    ? t("staffOfflineResults.sheet.uploadedBy", { date: formatDateTime(sheet.uploadedAt), name: sheet.uploadedBy })
    : t("staffOfflineResults.sheet.uploadedNoName", { date: formatDateTime(sheet.uploadedAt) });
  const published =
    sheet.publishedAt === null
      ? null
      : sheet.publishedBy
        ? t("staffOfflineResults.sheet.publishedBy", { date: formatDate(sheet.publishedAt), name: sheet.publishedBy })
        : t("staffOfflineResults.sheet.publishedNoName", { date: formatDate(sheet.publishedAt) });
  const examTypeText = t(`staffOfflineResults.upload.types.${sheet.examType}.name`);
  return (
    <div className="grid gap-3">
      <div className="grid gap-1 text-body text-text">
        <p>{uploaded}</p>
        {published ? <p>{published}</p> : null}
      </div>
      <dl className="grid gap-3 sm:grid-cols-3">
        <Fact label={t("staffOfflineResults.sheet.details.examType")} value={examTypeText} />
        <Fact label={t("staffOfflineResults.sheet.details.fullMarks")} value={formatNumber(sheet.fullMarks)} />
        <Fact label={t("staffOfflineResults.sheet.details.negative")} value={formatNumber(sheet.negative)} />
        <Fact
          label={t("staffOfflineResults.sheet.details.passMark")}
          value={sheet.passMark === null ? t("staffOfflineResults.sheet.details.noPass") : formatNumber(sheet.passMark)}
        />
        <Fact label={t("staffOfflineResults.sheet.details.file")} value={sheet.fileName} />
        <Fact label={t("staffOfflineResults.sheet.details.rows")} value={formatNumber(sheet.rows.length)} />
      </dl>
    </div>
  );
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div className="grid min-w-0 gap-0.5 rounded-control border border-border bg-bg px-3 py-2">
      <dt className="text-meta text-muted">{label}</dt>
      <dd className="break-words font-semibold tabular-nums text-text">{value}</dd>
    </div>
  );
}

/** Saves the uploaded file with its own name. The download needs the bearer token, so it runs on click. */
function DownloadButton({ classId, sheet }: { classId: string; sheet: ResultSheet }) {
  const { t } = useLocale();
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);
  return (
    <span className="grid justify-items-start gap-1">
      <Button
        variant="ghost"
        loading={busy}
        onClick={async () => {
          setBusy(true);
          setFailed(false);
          try {
            await downloadResultFile(classId, sheet.id, sheet.fileName);
          } catch {
            setFailed(true);
          } finally {
            setBusy(false);
          }
        }}
      >
        {t("staffOfflineResults.sheet.download")}
      </Button>
      {failed ? (
        <span role="alert" className="text-meta text-danger">
          {t("staffOfflineResults.upload.errors.save")}
        </span>
      ) : null}
    </span>
  );
}
