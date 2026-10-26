"use client";

import Link from "next/link";
import type { ReactNode } from "react";

/**
 * One exam in a package's exam list: a title on one line, a meta line (type and date), a summary clamped
 * to two lines, and the status or price on the right. The text column takes the free width and the
 * right column keeps its size, so a long line can never push the status or price out of the row.
 */
export function ExamRow({
  href,
  title,
  meta,
  summary,
  right,
}: {
  href: string;
  title: string;
  meta?: ReactNode;
  summary?: string | null;
  right: ReactNode;
}) {
  return (
    <Link
      href={href}
      prefetch
      className="group flex min-h-11 min-w-0 items-center gap-3 rounded-card border border-border bg-card p-4 transition-colors hover:border-accent"
    >
      <span className="min-w-0 flex-1">
        {meta ? <span className="mb-1 flex min-w-0 flex-wrap items-center gap-2 text-meta text-muted">{meta}</span> : null}
        <span className="line-clamp-1 block text-card-title font-bold group-hover:text-text-accent">{title}</span>
        {summary ? <span className="mt-1 line-clamp-2 block text-body leading-relaxed text-muted">{summary}</span> : null}
      </span>
      <span className="shrink-0 text-end">{right}</span>
    </Link>
  );
}
