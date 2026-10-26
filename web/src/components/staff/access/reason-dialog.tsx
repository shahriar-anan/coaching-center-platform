"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { Textarea } from "@/components/ui/textarea";
import { useLocale } from "@/i18n/locale-context";

/**
 * Asks for the reason before a change the audit log keeps (revoke, confirm,
 * reject). The reason is required and up to 500 characters. `onConfirm` gets
 * the trimmed reason; if it throws, the message stays in the dialog.
 */
export function ReasonDialog({
  open,
  onOpenChange,
  title,
  body,
  label,
  requiredMessage,
  confirmLabel,
  tone,
  onConfirm,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  body?: string;
  label: string;
  requiredMessage: string;
  confirmLabel: string;
  tone?: "danger";
  onConfirm: (reason: string) => Promise<void>;
}) {
  const { t } = useLocale();
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [missing, setMissing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function close(next: boolean) {
    if (busy) {
      return;
    }
    setReason("");
    setMissing(false);
    setError(null);
    onOpenChange(next);
  }

  async function confirm() {
    const trimmed = reason.trim();
    if (!trimmed) {
      setMissing(true);
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await onConfirm(trimmed);
      close(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : t("common.states.errorBody"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={close}
      title={title}
      description={body}
      footer={
        <>
          <Button variant="ghost" onClick={() => close(false)} disabled={busy}>
            {t("common.actions.cancel")}
          </Button>
          <Button variant={tone === "danger" ? "danger" : "primary"} loading={busy} onClick={confirm}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      <form
        className="grid gap-3"
        onSubmit={(event) => {
          event.preventDefault();
          void confirm();
        }}
      >
        <Field label={label} required error={missing ? requiredMessage : undefined}>
          <Textarea
            value={reason}
            maxLength={500}
            rows={3}
            invalid={missing}
            onChange={(event) => {
              setReason(event.target.value);
              setMissing(false);
            }}
          />
        </Field>
        {error ? (
          <p role="alert" className="text-meta text-danger">
            {error}
          </p>
        ) : null}
      </form>
    </Dialog>
  );
}
