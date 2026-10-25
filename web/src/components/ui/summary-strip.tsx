import Link from "next/link";
import { cn } from "@/components/ui/cn";

export type SummaryItem = {
  label: string;
  value: string;
  tone?: "muted" | "ok" | "warn" | "danger";
  href?: string;
};

const VALUE_TONE: Record<NonNullable<SummaryItem["tone"]>, string> = {
  muted: "text-text",
  ok: "text-ok",
  warn: "text-warn",
  danger: "text-danger",
};

/** A row of counts, each with its label. Counts come from the server; while loading, placeholders keep the layout. */
export function SummaryStrip({
  items,
  loading,
}: {
  items: SummaryItem[];
  loading?: boolean;
}) {
  return (
    <dl
      aria-busy={loading || undefined}
      className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-[repeat(auto-fit,minmax(10rem,1fr))]"
    >
      {items.map((item) => {
        const body = (
          <>
            <dt className="text-meta text-muted">{item.label}</dt>
            <dd className={cn("text-section font-semibold tabular-nums", VALUE_TONE[item.tone ?? "muted"])}>
              {loading ? <span aria-hidden="true" className="inline-block h-6 w-12 animate-pulse rounded bg-border" /> : item.value}
            </dd>
          </>
        );
        return (
          <div
            key={item.label}
            className="grid gap-1 rounded-card border border-border bg-card px-4 py-3"
          >
            {item.href && !loading ? (
              <Link href={item.href} prefetch className="grid gap-1 rounded-control outline-none hover:underline">
                {body}
              </Link>
            ) : (
              body
            )}
          </div>
        );
      })}
    </dl>
  );
}
