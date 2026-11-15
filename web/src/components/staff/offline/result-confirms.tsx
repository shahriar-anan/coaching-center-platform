"use client";

import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useDiscardStagedResult, usePublishResult, useSheetView, type ResultChange } from "@/lib/api/exam/offline-results";
import { offlineErrorText } from "./offline-error";

/**
 * Publishes the staged sheet of one exam (rule R9: the Master Admin only; the caller checks the flag). The confirm names how many students
 * will be told in the app (the staged sheet's `notifyCount`), and lists the matched students whose state changed since the save: the
 * publish still goes ahead. The dialog stays open with the error when the publish fails.
 */
export function PublishResultDialog({
  classId,
  open,
  onOpenChange,
}: {
  classId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useLocale();
  const { toast } = useToast();
  const view = useSheetView(classId);
  const publish = usePublishResult(classId);
  const staged = view.data?.staged ?? null;
  const notifyCount = staged?.notifyCount;
  const changed = staged?.changedSinceSave ?? [];

  return (
    <ConfirmDialog
      open={open}
      onOpenChange={onOpenChange}
      title={t("staffOfflineResults.sheet.publishTitle")}
      body={
        notifyCount === undefined
          ? undefined
          : notifyCount === 1
            ? t("staffOfflineResults.sheet.publishBodyOne")
            : t("staffOfflineResults.sheet.publishBody", { count: notifyCount })
      }
      confirmLabel={t("staffOfflineResults.sheet.publishConfirm")}
      onConfirm={async () => {
        try {
          await publish.mutateAsync();
          toast({ title: t("staffOfflineResults.sheet.publishedToast"), tone: "ok" });
        } catch (error) {
          throw new Error(offlineErrorText(error, t));
        }
      }}
    >
      {changed.length > 0 ? <ChangedList changed={changed} /> : null}
    </ConfirmDialog>
  );
}

/** The students whose state changed after the save: a closed admission, or a new roll number. */
function ChangedList({ changed }: { changed: ResultChange[] }) {
  const { t } = useLocale();
  return (
    <div className="grid gap-2 rounded-control border border-warn/50 bg-warn/10 p-3">
      <p className="font-semibold text-warn">{t("staffOfflineResults.sheet.changedTitle")}</p>
      <ul className="grid gap-1 text-body text-text">
        {changed.map((change) => (
          <li key={change.admissionId} className="break-words">
            {change.closed
              ? t("staffOfflineResults.sheet.changedClosed", { name: change.name })
              : t("staffOfflineResults.sheet.changedRoll", { name: change.name, oldRoll: change.oldRoll, newRoll: change.newRoll ?? "" })}
          </li>
        ))}
      </ul>
    </div>
  );
}

/** Removes the staged sheet. The published one stays, and the dialog says so (any admin may discard). */
export function DiscardStagedDialog({
  classId,
  open,
  onOpenChange,
}: {
  classId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useLocale();
  const { toast } = useToast();
  const discard = useDiscardStagedResult(classId);
  return (
    <ConfirmDialog
      open={open}
      onOpenChange={onOpenChange}
      title={t("staffOfflineResults.sheet.discardTitle")}
      body={t("staffOfflineResults.sheet.discardBody")}
      confirmLabel={t("staffOfflineResults.sheet.discardConfirm")}
      tone="danger"
      onConfirm={async () => {
        try {
          await discard.mutateAsync();
          toast({ title: t("staffOfflineResults.sheet.discarded"), tone: "ok" });
        } catch (error) {
          throw new Error(offlineErrorText(error, t));
        }
      }}
    />
  );
}
