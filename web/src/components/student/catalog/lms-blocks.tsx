import type { ReactNode } from "react";
import { cn } from "@/components/ui/cn";

/**
 * A content block in the course-page pattern: an icon, a title and an optional count
 * in a header row, then the body under a divider. The same block is used on the
 * module page and the exam page so the two read as one product.
 */
export function SectionCard({
  id,
  icon,
  title,
  count,
  children,
  className,
}: {
  id?: string;
  icon?: string;
  title: string;
  count?: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section id={id} className={cn("scroll-mt-24 overflow-hidden rounded-card border border-border bg-card", className)}>
      <header className="flex items-center justify-between gap-3 border-b border-border px-4 py-3">
        <h2 className="flex min-w-0 items-center gap-2.5 text-card-title font-bold">
          {icon ? (
            <span aria-hidden="true" className="flex size-8 shrink-0 items-center justify-center rounded-control bg-accent/15 text-body">
              {icon}
            </span>
          ) : null}
          <span className="min-w-0">{title}</span>
        </h2>
        {count ? (
          <span className="shrink-0 rounded-full bg-bg px-2.5 py-0.5 text-meta font-semibold text-muted">{count}</span>
        ) : null}
      </header>
      <div className="p-4">{children}</div>
    </section>
  );
}

/** One fact in a grid: an icon, a small label, the value, and an optional line under it. */
export function FactTile({
  icon,
  label,
  value,
  sub,
}: {
  icon: string;
  label: string;
  value: string;
  sub?: string;
}) {
  return (
    <div className="grid content-start gap-1 rounded-card border border-border border-t-4 border-t-accent bg-card p-3">
      <span aria-hidden="true" className="text-body">{icon}</span>
      <span className="text-meta text-muted">{label}</span>
      <span className="text-label font-bold tabular-nums">{value}</span>
      {sub ? <span className="text-meta text-muted">{sub}</span> : null}
    </div>
  );
}

/** Splits a multi-line rule text into its lines, so each rule can be numbered. */
export function ruleLines(text: string): string[] {
  return text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
}
