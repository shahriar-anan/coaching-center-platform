import type { ReactNode } from "react";
import { cn } from "@/components/ui/cn";

export type PillTone = "muted" | "accent" | "warn" | "ok" | "danger";

const tones: Record<PillTone, string> = {
  muted: "bg-bg text-muted border-border",
  accent: "bg-accent/15 text-accent-text border-accent/40",
  warn: "bg-warn/10 text-warn border-warn/40",
  ok: "bg-ok/10 text-ok-text border-ok/40",
  danger: "bg-danger/10 text-danger border-danger/40",
};

/** A status word with a tone. Status is always a word; the color only reinforces it. */
export function Pill({
  tone,
  pulse,
  children,
}: {
  tone: PillTone;
  pulse?: boolean;
  children: ReactNode;
}) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-pill",
        tones[tone],
      )}
    >
      {pulse ? (
        <span
          aria-hidden="true"
          className="size-2 animate-pulse rounded-full bg-current motion-reduce:animate-none"
        />
      ) : null}
      {children}
    </span>
  );
}
