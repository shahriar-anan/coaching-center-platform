import type { ReactNode } from "react";
import { cn } from "@/components/ui/cn";

/**
 * The primary action row. Pinned to the bottom of the viewport on phones (with
 * the safe-area inset), inline on desktop.
 */
export function StickyActionBar({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div
      className={cn(
        "fixed inset-x-0 bottom-0 z-30 flex items-center justify-end gap-2 border-t border-border bg-card px-4 py-3 pb-[calc(0.75rem+env(safe-area-inset-bottom))]",
        "md:static md:z-auto md:border-0 md:bg-transparent md:p-0",
        className,
      )}
    >
      {children}
    </div>
  );
}
