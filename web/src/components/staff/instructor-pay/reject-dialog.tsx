"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { Textarea } from "@/components/ui/textarea";
import { useLocale } from "@/i18n/locale-context";
import { offlineErrorText } from "@/components/staff/offline/offline-error";

/**
 * Rejects one waiting entry. A reason is required: the class goes back to held, and the person who recorded the entry sees it.
 */
export function RejectDialog({
  open,
  onOpenChange,
  summary,
  onReject,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** The entry in one line, such as "Milon · Evidence Act · ৳400". */
  summary: string;
  onReject: (reason: string) => Promise<void>;
}) {
  const { t } = useLocale();
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit() {
    const text = reason.trim();
    if (text === "") {
      setError(t("instructorPay.reject.errors.reason"));
      return;
    }
    setBusy(true);
    try {
      await onReject(text);
      setReason("");
      onOpenChange(false);
    } catch (failure) {
      setError(offlineErrorText(failure, t));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!busy) {
          setError(null);
          onOpenChange(next);
        }
      }}
      title={t("instructorPay.reject.title")}
      description={summary}
      footer={
        <>
          <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={busy}>
            {t("common.actions.cancel")}
          </Button>
          <Button variant="danger" onClick={() => void submit()} loading={busy}>
            {t("instructorPay.reject.submit")}
          </Button>
        </>
      }
    >
      <Field label={t("instructorPay.reject.reason")} error={error ?? undefined} required>
        <Textarea
          rows={3}
          value={reason}
          onChange={(event) => {
            setReason(event.target.value);
            setError(null);
          }}
          invalid={Boolean(error)}
        />
      </Field>
    </Dialog>
  );
}
