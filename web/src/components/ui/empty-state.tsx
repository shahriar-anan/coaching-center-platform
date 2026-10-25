import type { ReactNode } from "react";
import { cn } from "@/components/ui/cn";

/** What is missing, and what to do next. Never a blank page. */
export function EmptyState({
  title,
  body,
  action,
  icon,
  className,
}: {
  title: string;
  body?: string;
  action?: ReactNode;
  icon?: ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "grid justify-items-center gap-3 rounded-card border border-dashed border-border bg-card px-6 py-10 text-center",
        className,
      )}
    >
      {icon ? <div className="text-muted">{icon}</div> : null}
      <h2 className="text-lg font-semibold text-text">{title}</h2>
      {body ? <p className="max-w-prose text-[15px] text-muted">{body}</p> : null}
      {action ? <div className="pt-1">{action}</div> : null}
    </div>
  );
}
