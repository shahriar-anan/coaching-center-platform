import type { ReactNode } from "react";
import type { LucideIcon } from "lucide-react";
import { cn } from "@/components/ui/cn";
import { dhakaParts, type DateInput } from "@/lib/format";

/** Each section's soft icon tint, the same family as the home page doors, so the sections read apart at a glance. */
export const SECTION_TINT = {
  routine: "bg-blue-50 text-blue-600 dark:bg-blue-500/15 dark:text-blue-300",
  payments: "bg-amber-50 text-amber-600 dark:bg-amber-500/15 dark:text-amber-300",
  items: "bg-emerald-50 text-emerald-600 dark:bg-emerald-500/15 dark:text-emerald-300",
  form: "bg-violet-50 text-violet-600 dark:bg-violet-500/15 dark:text-violet-300",
  results: "bg-rose-50 text-rose-600 dark:bg-rose-500/15 dark:text-rose-300",
} as const;

export type SectionTint = keyof typeof SECTION_TINT;

/** The icon tile of a section header or a list row. */
export function IconTile({ icon: Icon, tint, size = "md" }: { icon: LucideIcon; tint: SectionTint; size?: "sm" | "md" }) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        "flex shrink-0 items-center justify-center rounded-xl",
        size === "sm" ? "size-8" : "size-9",
        SECTION_TINT[tint],
      )}
    >
      <Icon className={size === "sm" ? "size-4" : "size-5"} strokeWidth={1.75} />
    </span>
  );
}

/**
 * One section of My admission, as the mockup's `.sec` card: a header with the icon, the title and an optional action, then the
 * body. `flush` drops the body padding, for a list whose rows run edge to edge. The id is the jump target of the glance strip.
 */
export function SectionCard({
  id,
  icon,
  tint,
  title,
  action,
  flush = false,
  children,
}: {
  id: string;
  icon: LucideIcon;
  tint: SectionTint;
  title: string;
  action?: ReactNode;
  flush?: boolean;
  children: ReactNode;
}) {
  return (
    <section
      id={id}
      aria-labelledby={`${id}-title`}
      className="mt-5 scroll-mt-20 overflow-hidden rounded-card border border-border bg-card text-text shadow-[var(--card-shadow)]"
    >
      <header className="flex flex-wrap items-center justify-between gap-x-3 gap-y-2 border-b border-border px-4 py-3 sm:px-5">
        <div className="flex min-w-0 items-center gap-2.5">
          <IconTile icon={icon} tint={tint} />
          <h2 id={`${id}-title`} className="text-card-title font-bold">
            {title}
          </h2>
        </div>
        {action ? <div className="flex shrink-0 items-center gap-2">{action}</div> : null}
      </header>
      <div className={flush ? undefined : "p-4 sm:p-5"}>{children}</div>
    </section>
  );
}

/** A muted line for a section with nothing to show yet. */
export function SectionEmpty({ children }: { children: ReactNode }) {
  return <p className="text-body text-muted">{children}</p>;
}

/** The Dhaka calendar date (yyyy-MM-dd) of an instant, to compare with the API's plain dates. */
export function dhakaDate(value: DateInput): string {
  const p = dhakaParts(value);
  return `${p.year}-${String(p.month + 1).padStart(2, "0")}-${String(p.day).padStart(2, "0")}`;
}

/** Whole days from one plain date (yyyy-MM-dd) to another; negative when `to` is earlier. */
export function daysBetween(from: string, to: string): number {
  const at = (date: string) => {
    const [y, m, d] = date.split("-").map(Number);
    return Date.UTC(y, m - 1, d);
  };
  return Math.round((at(to) - at(from)) / 86_400_000);
}
