"use client";

import { useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { cn } from "@/components/ui/cn";
import { paperClass } from "./paper-theme";

/** Question text sizes in px, smallest first. The paper's content never goes under 16 px. */
const STEPS = [16, 18, 20] as const;
const STORAGE_KEY = "paperTextSize";

function readStep(): number {
  if (typeof window === "undefined") return 0;
  try {
    const value = Number(window.localStorage.getItem(STORAGE_KEY));
    return Number.isInteger(value) && value >= 0 && value < STEPS.length ? value : 0;
  } catch {
    return 0;
  }
}

/** The chosen text size for the paper, remembered on this device. */
export function usePaperTextSize() {
  const [step, setStep] = useState(readStep);

  function change(next: number) {
    const clamped = Math.min(STEPS.length - 1, Math.max(0, next));
    setStep(clamped);
    try {
      window.localStorage.setItem(STORAGE_KEY, String(clamped));
    } catch {
      // Private windows can refuse storage; the size still applies for this visit.
    }
  }

  return {
    size: STEPS[step],
    /** Sets the CSS variable that question stems and options read. */
    style: { "--paper-font": `${STEPS[step]}px` } as React.CSSProperties,
    canIncrease: step < STEPS.length - 1,
    canDecrease: step > 0,
    increase: () => change(step + 1),
    decrease: () => change(step - 1),
  };
}

type Size = ReturnType<typeof usePaperTextSize>;

/** The result page's look: the app's own buttons. */
const DEFAULT_BUTTON = "min-h-11 min-w-11 rounded-control border border-border bg-card px-3 font-semibold text-text disabled:opacity-40 focus-visible:outline-2 focus-visible:outline-accent";
/** The printed paper's look: plain black outline, no radius. */
const PAPER_BUTTON = `min-h-11 min-w-11 border px-3 font-semibold disabled:opacity-40 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[color:var(--paper-ink)] ${paperClass.line} ${paperClass.paper} ${paperClass.ink}`;

/** A − and A + buttons for the paper. The paper variant is the plain one used on the sitting page's bar. */
export function PaperTextSizeControl({
  size,
  className,
  variant = "default",
}: {
  size: Size;
  className?: string;
  variant?: "default" | "paper";
}) {
  const { t } = useLocale();
  const button = variant === "paper" ? PAPER_BUTTON : DEFAULT_BUTTON;
  return (
    <div role="group" aria-label={t("sitting.textSize.label")} className={cn("flex items-center gap-2", className)}>
      <button type="button" className={button} onClick={size.decrease} disabled={!size.canDecrease} aria-label={t("sitting.textSize.smaller")}>
        <span aria-hidden="true">A−</span>
      </button>
      <button type="button" className={button} onClick={size.increase} disabled={!size.canIncrease} aria-label={t("sitting.textSize.larger")}>
        <span aria-hidden="true">A+</span>
      </button>
    </div>
  );
}
