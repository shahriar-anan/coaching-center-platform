"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { TextInput } from "@/components/ui/text-input";
import { Field } from "@/components/ui/field";
import { useLocale } from "@/i18n/locale-context";

/**
 * Asks before anything that cannot be undone. It names the object in `title`.
 * With `typeToConfirm` the confirm button stays off until the name is typed.
 * `onConfirm` may throw; the dialog then stays open with the error inside it.
 */
export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  body,
  confirmLabel,
  tone,
  typeToConfirm,
  onConfirm,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  body?: string;
  confirmLabel: string;
  tone?: "danger";
  typeToConfirm?: string;
  onConfirm: () => Promise<void>;
}) {
  const { t } = useLocale();
  const [typed, setTyped] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const matches = !typeToConfirm || typed.trim() === typeToConfirm.trim();

  async function confirm() {
    setBusy(true);
    setError(null);
    try {
      await onConfirm();
      onOpenChange(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : t("common.states.errorBody"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!busy) {
          setTyped("");
          setError(null);
          onOpenChange(next);
        }
      }}
      title={title}
      description={body}
      footer={
        <>
          <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={busy}>
            {t("common.actions.cancel")}
          </Button>
          <Button
            variant={tone === "danger" ? "danger" : "primary"}
            loading={busy}
            disabled={!matches}
            onClick={confirm}
          >
            {confirmLabel}
          </Button>
        </>
      }
    >
      {typeToConfirm ? (
        <Field label={t("common.confirm.typeToConfirm", { name: typeToConfirm })}>
          <TextInput
            value={typed}
            onChange={(e) => setTyped(e.target.value)}
            autoComplete="off"
          />
        </Field>
      ) : null}
      {error ? (
        <p role="alert" className="text-meta text-danger">
          {error}
        </p>
      ) : null}
    </Dialog>
  );
}
