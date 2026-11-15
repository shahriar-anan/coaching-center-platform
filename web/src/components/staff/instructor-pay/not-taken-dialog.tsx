"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useMarkNotTaken, type ClassRow } from "@/lib/api/exam/instructor-pay";
import { offlineErrorText } from "@/components/staff/offline/offline-error";
import { NOT_TAKEN_REASONS, dueOf } from "./pay-model";

/**
 * Marks a held class as not taken. The reason is required; three one-tap reasons fill it in and can be edited. Staff's mark waits for
 * the Master Admin; the Master Admin's counts at once.
 */
export function NotTakenDialog({
  row,
  batchName,
  open,
  onOpenChange,
  canApprove,
}: {
  row: ClassRow;
  batchName: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  canApprove: boolean;
}) {
  const { t } = useLocale();
  const { formatDate, formatMoney } = useFormat();
  const { toast } = useToast();
  const markNotTaken = useMarkNotTaken();
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    const text = reason.trim();
    if (text === "") {
      setError(t("instructorPay.notTaken.errors.reason"));
      return;
    }
    try {
      const updated = await markNotTaken.mutateAsync({ classId: row.id, reason: text });
      toast({
        title: updated.status === "NOT_TAKEN" ? t("instructorPay.notTaken.marked") : t("instructorPay.notTaken.pending"),
        tone: "ok",
      });
      onOpenChange(false);
    } catch (failure) {
      setError(offlineErrorText(failure, t));
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={t("instructorPay.notTaken.title")}
      description={canApprove ? t("instructorPay.notTaken.masterNote") : t("instructorPay.notTaken.staffNote")}
      footer={
        <>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            {t("common.actions.cancel")}
          </Button>
          <Button onClick={() => void submit()} loading={markNotTaken.isPending}>
            {t("instructorPay.notTaken.submit")}
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        <p className="grid gap-0.5 rounded-control bg-bg px-3 py-2 text-meta text-muted">
          <span className="text-body text-text">
            {row.instructorName ?? row.instructorText ?? ""} · {row.subject}
          </span>
          <span>
            {formatDate(row.heldOn)} · {batchName}
          </span>
          <span>{t("instructorPay.notTaken.owedNote", { amount: formatMoney(dueOf(row)) ?? "" })}</span>
        </p>
        <div className="grid gap-2">
          <span className="text-meta text-muted">{t("instructorPay.notTaken.reasonsLabel")}</span>
          <div className="flex flex-wrap gap-2">
            {NOT_TAKEN_REASONS.map((key) => {
              const label = t(`instructorPay.notTaken.reasons.${key}`);
              return (
                <Button
                  key={key}
                  variant="secondary"
                  size="sm"
                  aria-pressed={reason === label}
                  onClick={() => {
                    setReason(label);
                    setError(null);
                  }}
                >
                  {label}
                </Button>
              );
            })}
          </div>
        </div>
        <Field label={t("instructorPay.notTaken.reason")} error={error ?? undefined} required>
          <Textarea
            rows={2}
            value={reason}
            onChange={(event) => {
              setReason(event.target.value);
              setError(null);
            }}
            invalid={Boolean(error)}
          />
        </Field>
      </div>
    </Dialog>
  );
}
