"use client";

import { useState, type ReactNode } from "react";
import { CheckCircle2, Circle, CircleAlert } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { cn } from "@/components/ui/cn";

export type SectionState = "done" | "todo" | "error";

const STATE_ICON = {
  done: CheckCircle2,
  todo: Circle,
  error: CircleAlert,
} as const;

const STATE_TONE = {
  done: "text-ok",
  todo: "text-muted",
  error: "text-danger",
} as const;

/**
 * One numbered part of a long form. On desktop the body is always open. On phones
 * the header is a button that folds the body away, so a long editor stays short.
 */
export function SectionBlock({
  id,
  number,
  title,
  state,
  children,
}: {
  id: string;
  number: string;
  title: string;
  state: SectionState;
  children: ReactNode;
}) {
  const { t } = useLocale();
  const [open, setOpen] = useState(true);
  const StateIcon = STATE_ICON[state];
  const bodyId = `${id}-body`;
  const headingId = `${id}-heading`;

  return (
    <section id={id} aria-labelledby={headingId} className="grid scroll-mt-24 gap-4">
      <div className="flex items-center justify-between gap-3">
        <h2 id={headingId} className="flex min-w-0 items-center gap-2 text-card font-semibold">
          <span className="tabular-nums text-muted">{number}</span>
          <span className="min-w-0">{title}</span>
          <StateIcon aria-hidden="true" className={cn("size-4 shrink-0", STATE_TONE[state])} />
          <span className="sr-only">{t(`staffCatalog.sections.state.${state}`)}</span>
        </h2>
        <button
          type="button"
          aria-expanded={open}
          aria-controls={bodyId}
          onClick={() => setOpen((value) => !value)}
          className="min-h-10 rounded-control px-3 text-label text-action outline-none hover:bg-bg focus-visible:outline-accent md:hidden"
        >
          {open ? t("staffCatalog.sections.fold") : t("staffCatalog.sections.unfold")}
        </button>
      </div>
      <div id={bodyId} className={cn("grid gap-4", !open && "hidden md:grid")}>
        {children}
      </div>
    </section>
  );
}
