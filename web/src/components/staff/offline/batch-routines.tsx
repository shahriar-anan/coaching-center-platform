"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Pill } from "@/components/ui/pill";
import { PdfViewer } from "@/components/pdf/pdf-viewer";
import { Skeleton } from "@/components/ui/skeleton";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { apiBlob } from "@/lib/api/client";
import { useBatchRoutines, type RoutineSummary } from "@/lib/api/exam/offline-batch-pay";
import { RoutineUploadDialog } from "./routine-upload-dialog";

/**
 * The Routines tab (plan 8.1, changed on 13 Nov 2026). A routine counts from the day it was uploaded, so there are no months here: the
 * current routine first, then an upload waiting for its check, then the earlier routines as the record. The PDFs open in the staff viewer.
 */
export function BatchRoutines({ batchId, archived }: { batchId: string; archived: boolean }) {
  const { t } = useLocale();
  const { formatDate } = useFormat();
  const { toast } = useToast();
  const routines = useBatchRoutines(batchId);
  const [uploading, setUploading] = useState(false);
  const [viewer, setViewer] = useState<{ title: string; path: string } | null>(null);

  if (routines.isPending) {
    return <Skeleton variant="card" count={2} />;
  }
  if (routines.isError) {
    return <ErrorState onRetry={() => routines.refetch()} error={routines.error} />;
  }

  const current = routines.data.find((routine) => routine.status === "CONFIRMED");
  const waiting = routines.data.filter((routine) => routine.status === "PREVIEW");
  const earlier = routines.data.filter((routine) => routine.status === "SUPERSEDED");
  const from = (routine: RoutineSummary) => formatDate(routine.effectiveFrom, { year: "auto" });
  const openOriginal = (routine: RoutineSummary) =>
    setViewer({ title: t("batchPay.routines.fromDate", { date: from(routine) }), path: `/api/v1/offline-routines/${routine.id}/original.pdf` });
  const openStudent = (routine: RoutineSummary) =>
    setViewer({
      title: t("batchPay.routines.studentViewerTitle", { date: from(routine) }),
      path: `/api/v1/offline-routines/${routine.id}/student.pdf`,
    });

  const uploadButton = !archived ? (
    <Button variant="secondary" onClick={() => setUploading(true)}>
      {current ? t("batchPay.routines.uploadNew") : t("batchPay.routines.upload")}
    </Button>
  ) : null;

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="grid gap-1">
          <h3 className="text-card-title text-text">{t("batchPay.routines.title")}</h3>
          <p className="text-meta text-muted">{archived ? t("batchPay.routines.readOnly") : t("batchPay.routines.note")}</p>
        </div>
        {uploadButton}
      </div>

      {waiting.map((routine) => (
        <Card key={routine.id} className="grid gap-2 border-warn/60">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h4 className="text-label font-bold text-text">{t("batchPay.routines.waitingTitle", { date: from(routine) })}</h4>
            <Pill tone="warn">{t("batchPay.routines.waiting")}</Pill>
          </div>
          <p className="text-meta text-muted">{t("batchPay.routines.waitingBody", { count: routine.rowCount })}</p>
          <div className="flex flex-wrap gap-2">
            <Button size="sm" href={`/staff/offline-batches/${batchId}/routines/${routine.id}`}>
              {t("batchPay.routines.checkPreview")}
            </Button>
            <Button size="sm" variant="secondary" onClick={() => openOriginal(routine)}>
              {t("batchPay.routines.viewOriginal")}
            </Button>
          </div>
        </Card>
      ))}

      {current ? (
        <Card className="grid gap-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="grid gap-0.5">
              <span className="text-meta text-muted">{t("batchPay.routines.current")}</span>
              <h4 className="text-section text-text">{t("batchPay.routines.fromDate", { date: from(current) })}</h4>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <Pill tone="ok">{t("batchPay.routines.confirmed")}</Pill>
              {current.studentCopy ? (
                <Pill tone="ok">{t("batchPay.routines.studentCopy")}</Pill>
              ) : (
                <Pill tone="muted">{t("batchPay.routines.noStudentCopy")}</Pill>
              )}
            </div>
          </div>
          <div className="grid gap-2 text-meta text-muted sm:grid-cols-3">
            <p>{t("batchPay.routines.version", { count: current.version })}</p>
            <p>
              {current.confirmedAt
                ? t("batchPay.routines.confirmedOn", { date: formatDate(current.confirmedAt) })
                : t("batchPay.routines.notConfirmed")}
            </p>
            <p>{t("batchPay.routines.classes", { count: current.rowCount })}</p>
          </div>
          {current.legacyOriginal ? <p className="text-meta text-muted">{t("batchPay.routines.legacy")}</p> : null}
          <div className="flex flex-wrap gap-2">
            <Button size="sm" variant="secondary" onClick={() => openOriginal(current)}>
              {t("batchPay.routines.viewOriginal")}
            </Button>
            {current.studentCopy ? (
              <Button size="sm" variant="secondary" onClick={() => openStudent(current)}>
                {t("batchPay.routines.viewStudent")}
              </Button>
            ) : null}
          </div>
        </Card>
      ) : waiting.length === 0 ? (
        <EmptyState title={t("batchPay.routines.empty")} body={t("batchPay.routines.emptyBody")} action={uploadButton} />
      ) : null}

      {earlier.length > 0 ? (
        <section aria-labelledby="earlier-routines" className="grid gap-2">
          <h4 id="earlier-routines" className="text-label font-bold text-text">
            {t("batchPay.routines.earlier")}
          </h4>
          <ul className="divide-y divide-border rounded-card border border-border bg-card">
            {earlier.map((routine) => (
              <li key={routine.id} className="flex flex-wrap items-center justify-between gap-2 px-4 py-3">
                <div className="grid gap-0.5">
                  <span className="font-semibold text-text">{t("batchPay.routines.fromDate", { date: from(routine) })}</span>
                  <span className="text-meta text-muted">
                    {t("batchPay.routines.version", { count: routine.version })} · {t("batchPay.routines.classes", { count: routine.rowCount })}
                  </span>
                </div>
                <Button size="sm" variant="ghost" onClick={() => openOriginal(routine)}>
                  {t("batchPay.routines.viewOriginal")}
                </Button>
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {uploading ? (
        <RoutineUploadDialog
          batchId={batchId}
          open
          onOpenChange={(open) => {
            if (!open) {
              setUploading(false);
            }
          }}
          replacing={Boolean(current)}
        />
      ) : null}

      <Dialog
        open={viewer !== null}
        onOpenChange={(open) => {
          if (!open) {
            setViewer(null);
          }
        }}
        title={viewer?.title ?? ""}
        className="sm:max-w-3xl"
      >
        {viewer ? (
          <PdfViewer
            load={(options) => apiBlob(viewer.path, options).catch((error: unknown) => {
              if ((error as { name?: string } | null)?.name !== "AbortError") {
                toast({ title: t("batchPay.routines.viewFailed"), tone: "danger" });
              }
              throw error;
            })}
            fileKind="routine"
            label={viewer.title}
          />
        ) : null}
      </Dialog>
    </div>
  );
}
