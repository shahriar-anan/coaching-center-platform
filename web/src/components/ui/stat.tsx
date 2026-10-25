import type { ReactNode } from "react";
import { cn } from "@/components/ui/cn";

/** One cell of a `StatRow`: a big value and a small label. */
export function Stat({ value, label }: { value: ReactNode; label: ReactNode }) {
  return (
    <div className="flex min-w-0 flex-1 flex-col items-center gap-0.5 px-2 py-3 text-center">
      <span className="text-section font-bold tabular-nums text-text">{value}</span>
      <span className="text-meta text-muted">{label}</span>
    </div>
  );
}

/** The three-cell result row from the mockup. Cells are separated by a hairline. */
export function StatRow({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div
      className={cn(
        "flex divide-x divide-border rounded-card border border-border bg-card",
        className,
      )}
    >
      {children}
    </div>
  );
}
