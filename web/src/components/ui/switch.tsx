"use client";

import { cn } from "@/components/ui/cn";
import { useLocale } from "@/i18n/locale-context";

type SwitchProps = {
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
  /** Accessible name, such as the setting it controls. */
  label: string;
  disabled?: boolean;
  id?: string;
  /** Writes the label beside the switch too. Without it the words are only read aloud, and a sighted person sees a bare toggle. */
  showLabel?: boolean;
};

/** An on/off switch. The state is also written in words for screen readers. */
export function Switch({ checked, onCheckedChange, label, disabled, id, showLabel }: SwitchProps) {
  const { t } = useLocale();
  const control = (
    <button
      type="button"
      id={id}
      role="switch"
      aria-checked={checked}
      aria-label={`${label}: ${checked ? t("common.states.on") : t("common.states.off")}`}
      disabled={disabled}
      onClick={() => onCheckedChange(!checked)}
      className={cn(
        "relative inline-flex h-7 w-12 min-h-11 items-center rounded-full transition-colors duration-150 disabled:opacity-60",
        checked ? "bg-action" : "bg-muted/60",
      )}
    >
      <span
        aria-hidden="true"
        className={cn(
          "absolute top-1/2 size-5 -translate-y-1/2 rounded-full bg-card shadow transition-transform duration-150",
          checked ? "translate-x-6" : "translate-x-1",
        )}
      />
    </button>
  );
  if (!showLabel) return control;
  return (
    <span className="flex min-w-0 items-center justify-between gap-3">
      <span className="text-label text-text">{label}</span>
      {control}
    </span>
  );
}
