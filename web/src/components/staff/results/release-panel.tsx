"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { ApiError } from "@/lib/api/errors";
import { useReleaseResults, useReleaseStatus } from "@/lib/api/exam/staff-results";
import { useFormat } from "@/lib/format/use-format";

/**
 * The release panel for one written exam (Master Admin only). It shows what blocks a release and what waits for one,
 * then releases either the papers the markers flagged ready (no completeness gate) or every result (the gate applies).
 * A refused release shows the counts the server sends back.
 */
export function ReleasePanel({ examId, examTitle }: { examId: string; examTitle: string }) {
  const { t } = useLocale();
  const format = useFormat();
  const { toast } = useToast();
  const status = useReleaseStatus(examId, true);
  const release = useReleaseResults(examId);
  const [confirm, setConfirm] = useState<{ ready: boolean } | null>(null);
  const [refusal, setRefusal] = useState<string | null>(null);

  const counts = status.data;
  const count = (value: number) => format.formatNumber(value);

  async function releaseNow(ready: boolean) {
    setRefusal(null);
    setConfirm(null);
    try {
      const result = await release.mutateAsync({ ready });
      toast({ title: t("staffResults.release.done", { count: count(result.published) }), tone: "ok" });
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === "EXAM_MARKING_INCOMPLETE") {
        const details = caught.details as { awaiting?: number; open?: number } | undefined;
        setRefusal(
          t("staffResults.release.refused", {
            awaiting: count(details?.awaiting ?? 0),
            open: count(details?.open ?? 0),
          }),
        );
      } else {
        setRefusal(t("staffResults.release.failed"));
      }
      void status.refetch();
    }
  }

  const readyCount = counts?.ready ?? 0;
  const draftCount = counts?.drafts ?? 0;
  const releaseAllDisabled = !counts || !counts.canRelease || draftCount === 0 || release.isPending;
  const releaseReadyDisabled = !counts || readyCount === 0 || release.isPending;

  return (
    <Card>
      <div className="grid gap-3">
        <h2 className="text-label font-semibold text-text">{t("staffResults.release.title")}</h2>
        {counts ? (
          <ul className="flex flex-wrap gap-x-4 gap-y-1 text-meta text-muted">
            <li>{t("staffResults.release.summaryAwaiting", { count: count(counts.awaiting) })}</li>
            <li>{t("staffResults.release.summaryOpen", { count: count(counts.open) })}</li>
            <li>{t("staffResults.release.summaryReady", { count: count(counts.ready) })}</li>
            <li>{t("staffResults.release.summaryDrafts", { count: count(counts.drafts) })}</li>
            <li>{t("staffResults.release.summaryZeros", { count: count(counts.failedToSubmit) })}</li>
          </ul>
        ) : null}
        {counts && !counts.canRelease ? (
          <p className="text-meta text-warn">
            {t("staffResults.release.blocked", { awaiting: count(counts.awaiting), open: count(counts.open) })}
          </p>
        ) : null}
        {refusal ? (
          <p role="alert" className="text-meta text-danger">
            {refusal}
          </p>
        ) : null}
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" disabled={releaseReadyDisabled} onClick={() => setConfirm({ ready: true })}>
            {t("staffResults.release.releaseReady", { count: count(readyCount) })}
          </Button>
          <Button variant="primary" disabled={releaseAllDisabled} onClick={() => setConfirm({ ready: false })}>
            {t("staffResults.release.releaseAll", { count: count(draftCount) })}
          </Button>
        </div>
        {counts && readyCount === 0 ? <p className="text-meta text-muted">{t("staffResults.release.noneReady")}</p> : null}
        {counts && draftCount === 0 ? <p className="text-meta text-muted">{t("staffResults.release.noneWaiting")}</p> : null}
      </div>
      <ConfirmDialog
        open={confirm !== null}
        onOpenChange={(open) => {
          if (!open) {
            setConfirm(null);
          }
        }}
        title={
          confirm?.ready
            ? t("staffResults.release.confirmReadyTitle", { exam: examTitle })
            : t("staffResults.release.confirmAllTitle", { exam: examTitle })
        }
        body={
          confirm?.ready
            ? t("staffResults.release.confirmReadyBody", { count: count(readyCount) })
            : t("staffResults.release.confirmAllBody", { count: count(draftCount) })
        }
        confirmLabel={t("staffResults.release.confirm")}
        onConfirm={() => releaseNow(confirm?.ready ?? false)}
      />
    </Card>
  );
}
