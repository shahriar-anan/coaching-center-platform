"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { TargetPicker, type PickedTarget } from "@/components/staff/target-picker";
import { useAddItem } from "@/lib/api/exam/offline-admin";
import { offlineErrorText } from "./offline-error";

/**
 * Picks published exams and packages and adds them to the admission, one request each, in the order they were ticked. The first
 * error stops the run: the items added before it stay, and the ones still ticked stay ticked for a second try. Items the admission
 * already holds show ticked and cannot change. The dialog is mounted only while it is open.
 */
export function ItemAddDialog({
  admissionId,
  heldIds,
  onOpenChange,
}: {
  admissionId: string;
  /** The ids of the exams and packages the admission already has. */
  heldIds: string[];
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useLocale();
  const { toast } = useToast();
  const add = useAddItem(admissionId);
  const [picked, setPicked] = useState<PickedTarget[]>([]);
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  async function addPicked() {
    setBusy(true);
    setFailure(null);
    let added = 0;
    let problem: string | null = null;
    for (const target of picked) {
      try {
        await add.mutateAsync(target.kind === "exam" ? { scope: "EXAM", examId: target.id } : { scope: "MODULE", moduleId: target.id });
        added += 1;
        setPicked((current) => current.filter((item) => item.id !== target.id));
      } catch (error) {
        problem = offlineErrorText(error, t);
        break;
      }
    }
    setBusy(false);
    if (added > 0) {
      toast({ title: t("staffOffline.setupScreen.items.added", { count: added }), tone: "ok" });
    }
    if (problem) {
      setFailure(problem);
      return;
    }
    onOpenChange(false);
  }

  return (
    <Dialog
      open
      onOpenChange={onOpenChange}
      title={t("staffOffline.setupScreen.items.dialogTitle")}
      description={t("staffOffline.setupScreen.items.dialogBody")}
      // The Dialog's own width comes later in the stylesheet, so this one has to be important to win.
      className="sm:max-w-3xl!"
      footer={
        <div className="flex flex-wrap items-center justify-end gap-2">
          <Button variant="secondary" onClick={() => onOpenChange(false)} disabled={busy}>
            {t("common.actions.cancel")}
          </Button>
          <Button onClick={() => void addPicked()} disabled={picked.length === 0} loading={busy}>
            {t("staffOffline.setupScreen.items.addCount", { count: picked.length })}
          </Button>
        </div>
      }
    >
      <div className="grid gap-3">
        <TargetPicker
          kind="either"
          status="PUBLISHED"
          multiple
          selected={picked}
          onSelectedChange={setPicked}
          disabledIds={heldIds}
          disabledNote={t("staffOffline.setupScreen.items.alreadyAdded")}
          label={t("staffOffline.setupScreen.items.dialogTitle")}
          hideLabel
        />
        {failure ? (
          <p role="alert" className="text-meta text-danger">
            {failure}
          </p>
        ) : null}
      </div>
    </Dialog>
  );
}
