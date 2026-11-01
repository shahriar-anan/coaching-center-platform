import Link from "next/link";
import type { ReactNode } from "react";
import { ChevronRight } from "lucide-react";
import { cn } from "@/components/ui/cn";

/**
 * One bordered card that holds a list: the rows sit under each other with a thin divider, instead of one bordered card per
 * item. The usual pattern for dashboards and schedules, and about half the height of separate cards.
 */
export function ListCard({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <ul
      className={cn(
        "divide-y divide-border overflow-hidden rounded-card border border-border bg-card shadow-[var(--card-shadow)]",
        className,
      )}
    >
      {children}
    </ul>
  );
}

/** A row that is one link: the content on the left, an optional `trailing` block (status, price) and a chevron on the right. */
export function ListRow({ href, children, trailing }: { href: string; children: ReactNode; trailing?: ReactNode }) {
  return (
    <li>
      <Link
        href={href}
        prefetch
        className="flex min-h-14 items-center gap-3 px-4 py-3 transition-colors hover:bg-text/[0.06] focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent"
      >
        <span className="min-w-0 flex-1">{children}</span>
        {trailing ? <span className="shrink-0 text-end">{trailing}</span> : null}
        <ChevronRight aria-hidden="true" className="size-4 shrink-0 text-muted" />
      </Link>
    </li>
  );
}

/** A single muted line inside a list card: "No exams coming up". */
export function ListNote({ children }: { children: ReactNode }) {
  return <li className="px-4 py-3 text-body text-muted">{children}</li>;
}
