"use client";

import { useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { publishPaper, useInvalidateMarking } from "@/lib/api/exam/staff-marking";
import { useFormat } from "@/lib/format/use-format";

/**
 * Publishes the rows ticked in the browser, one request each, in list order. Shows the
 * progress and a stop button. A stop lets the request in flight finish, then stops.
 */
export function BulkPublishSelected({
  ids,
  onDone,
}: {
  ids: string[];
  /** Called once the run ends, so the browser can clear the ticks. */
  onDone: () => void;
}) {
  const { t } = useLocale();
  const format = useFormat();
  const { toast } = useToast();
  const invalidate = useInvalidateMarking();
  const stopRequested = useRef(false);
  const [open, setOpen] = useState(false);
  const [progress, setProgress] = useState<{ done: number; total: number } | null>(null);

  async function run() {
    stopRequested.current = false;
    setOpen(false);
    let published = 0;
    let failed = 0;
    setProgress({ done: 0, total: ids.length });
    for (const id of ids) {
      if (stopRequested.current) {
        break;
      }
      try {
        await publishPaper(id);
        published += 1;
      } catch {
        failed += 1;
      }
      setProgress({ done: published + failed, total: ids.length });
    }
    setProgress(null);
    await invalidate();
    onDone();
    toast({
      title: t("staffMarking.bulk.publishedSelected", {
        published: format.formatNumber(published),
        failed: format.formatNumber(failed),
      }),
      tone: failed > 0 ? "danger" : "ok",
    });
  }

  // Nothing ticked and nothing running: no control to show.
  if (ids.length === 0 && !progress) {
    return null;
  }

  return (
    <div className="flex flex-wrap items-center gap-3">
      {progress ? (
        <>
          <p aria-live="polite" className="text-meta text-muted">
            {t("staffMarking.bulk.progress", {
              done: format.formatNumber(progress.done),
              total: format.formatNumber(progress.total),
            })}
          </p>
          <Button
            variant="secondary"
            onClick={() => {
              stopRequested.current = true;
            }}
          >
            {t("staffMarking.bulk.stop")}
          </Button>
        </>
      ) : (
        <Button variant="secondary" disabled={ids.length === 0} onClick={() => setOpen(true)}>
          {t("staffMarking.bulk.publishSelected", { count: format.formatNumber(ids.length) })}
        </Button>
      )}
      <ConfirmDialog
        open={open}
        onOpenChange={setOpen}
        title={t("staffMarking.bulk.confirmSelectedTitle")}
        body={t("staffMarking.bulk.confirmSelectedBody", { count: format.formatNumber(ids.length) })}
        confirmLabel={t("staffMarking.bulk.confirmAction")}
        onConfirm={run}
      />
    </div>
  );
}
