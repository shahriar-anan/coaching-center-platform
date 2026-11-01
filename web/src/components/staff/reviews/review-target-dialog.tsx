"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { TargetPicker, type PickedTarget } from "@/components/staff/target-picker";
import { useLocale } from "@/i18n/locale-context";
import type { ReviewKind } from "@/lib/api/exam/reviews";

/**
 * Chooses the one exam or package the list is narrowed to. The picker works on a draft: the list changes only when the
 * person presses "Show these reviews", and "Clear" removes the target. The page mounts this dialog only while it is open,
 * so each opening starts from the current target.
 */
export function ReviewTargetDialog({
  kind,
  initial,
  onApply,
  onClose,
}: {
  /** Set when the Type filter names one kind; the picker then offers only that kind. */
  kind?: ReviewKind;
  initial: PickedTarget | null;
  onApply: (target: PickedTarget | null) => void;
  onClose: () => void;
}) {
  const { t } = useLocale();
  const [draft, setDraft] = useState<PickedTarget | null>(initial);

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
      className="scroll-clean"
      title={t("staffReviews.target.title")}
      description={t("staffReviews.target.body")}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            {t("staffReviews.target.cancel")}
          </Button>
          <Button variant="secondary" onClick={() => onApply(null)}>
            {t("staffReviews.filters.targetClear")}
          </Button>
          <Button variant="primary" disabled={!draft} onClick={() => onApply(draft)}>
            {t("staffReviews.target.apply")}
          </Button>
        </>
      }
    >
      <TargetPicker kind={kind ?? "either"} value={draft} onChange={setDraft} label={t("staffReviews.filters.target")} hideLabel />
    </Dialog>
  );
}
