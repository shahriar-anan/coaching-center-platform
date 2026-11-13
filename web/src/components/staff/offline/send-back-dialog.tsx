"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/components/ui/cn";
import { useLocale } from "@/i18n/locale-context";

/** The one-tap reasons (plan 6.3, principle 6). Each adds its sentence to the reason the student reads. */
export const SEND_BACK_REASONS = [
  { key: "photo", labelKey: "staffOffline.sendBack.reasons.photo", textKey: "staffOffline.sendBack.reasonText.photo" },
  { key: "nid", labelKey: "staffOffline.sendBack.reasons.nid", textKey: "staffOffline.sendBack.reasonText.nid" },
  { key: "name", labelKey: "staffOffline.sendBack.reasons.name", textKey: "staffOffline.sendBack.reasonText.name" },
  { key: "address", labelKey: "staffOffline.sendBack.reasons.address", textKey: "staffOffline.sendBack.reasonText.address" },
] as const;

/**
 * Sends the form back to the student with a reason. The reason is required: the student sees exactly what the office wrote. One-tap
 * reasons fill the text in; the text can then be edited.
 */
export function SendBackDialog({
  open,
  onOpenChange,
  onSubmit,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (reason: string) => Promise<void>;
}) {
  const { t } = useLocale();
  const [text, setText] = useState("");
  const [chosen, setChosen] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function toggle(key: string, sentence: string) {
    setChosen((current) => {
      const next = current.includes(key) ? current.filter((item) => item !== key) : [...current, key];
      return next;
    });
    setText((current) => {
      if (chosen.includes(key)) {
        return current.replace(`${sentence} `, "").replace(sentence, "").trim();
      }
      return current.trim() ? `${current.trim()} ${sentence}` : sentence;
    });
    setError(null);
  }

  async function submit() {
    const reason = text.trim();
    if (!reason) {
      setError(t("staffOffline.sendBack.required"));
      return;
    }
    setBusy(true);
    try {
      await onSubmit(reason);
      setText("");
      setChosen([]);
      onOpenChange(false);
    } catch {
      setError(t("staffOffline.errors.generic"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) {
          setError(null);
        }
        onOpenChange(next);
      }}
      title={t("staffOffline.sendBack.title")}
      description={t("staffOffline.sendBack.body")}
      footer={
        <>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            {t("common.actions.cancel")}
          </Button>
          <Button onClick={() => void submit()} loading={busy}>
            {t("staffOffline.sendBack.submit")}
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        <div className="flex flex-wrap gap-2" role="group" aria-label={t("staffOffline.sendBack.reasonsLabel")}>
          {SEND_BACK_REASONS.map((reason) => {
            const on = chosen.includes(reason.key);
            return (
              <button
                key={reason.key}
                type="button"
                aria-pressed={on}
                onClick={() => toggle(reason.key, t(reason.textKey))}
                className={cn(
                  "min-h-10 rounded-full border px-3.5 text-meta transition-colors",
                  on ? "border-accent bg-accent/15 text-accent-text" : "border-border-control bg-card text-text hover:bg-bg",
                )}
              >
                {t(reason.labelKey)}
              </button>
            );
          })}
        </div>
        <Field label={t("staffOffline.sendBack.textLabel")} hint={t("staffOffline.sendBack.textHint")} error={error ?? undefined} required>
          <Textarea
            rows={4}
            value={text}
            onChange={(event) => {
              setText(event.target.value);
              setError(null);
            }}
            invalid={Boolean(error)}
          />
        </Field>
      </div>
    </Dialog>
  );
}
