"use client";

import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { useLocale } from "@/i18n/locale-context";
import { settleLeaving, usePendingLeave } from "@/lib/hooks/unsaved-changes-store";

/** The "সেভ না করে চলে যাবেন?" prompt. Mounted once in each shell; the guard hook fills it. */
export function UnsavedChangesDialog() {
  const { t } = useLocale();
  const pending = usePendingLeave();
  return (
    <ConfirmDialog
      open={pending !== null}
      onOpenChange={(open) => {
        if (!open) settleLeaving(false);
      }}
      title={t("common.unsaved.title")}
      body={pending?.message ?? t("common.unsaved.body")}
      confirmLabel={t("common.unsaved.leave")}
      onConfirm={async () => settleLeaving(true)}
    />
  );
}
