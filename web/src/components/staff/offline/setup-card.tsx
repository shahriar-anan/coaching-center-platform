"use client";

import type { ReactNode } from "react";
import { cn } from "@/components/ui/cn";

/** One numbered card of the setup. Each card is one step: a number, a title, its own content and an optional action. */
export function SetupCard({
  id,
  step,
  title,
  action,
  children,
}: {
  id: string;
  step?: number;
  title: string;
  action?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section aria-labelledby={id} className={cn("grid min-w-0 gap-4 rounded-card border border-border-control bg-card p-4 sm:p-5")}>
      <header className="flex flex-wrap items-center gap-3">
        {step !== undefined ? (
          <span aria-hidden="true" className="grid size-7 shrink-0 place-items-center rounded-full bg-accent/15 text-meta font-semibold text-text-accent">
            {step}
          </span>
        ) : null}
        <h2 id={id} className="min-w-0 flex-1 text-card-title text-text">
          {title}
        </h2>
        {action ? <div className="shrink-0">{action}</div> : null}
      </header>
      {children}
    </section>
  );
}
