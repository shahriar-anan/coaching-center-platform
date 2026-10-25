import type { ReactNode } from "react";

export type SummaryItem = {
  label: string;
  value: string;
  tone?: "muted" | "ok" | "warn" | "danger";
  href?: string;
};

/** A row of counts with labels. Stub: renders the items as plain text. */
export function SummaryStrip({
  items,
  loading,
}: {
  items: SummaryItem[];
  loading?: boolean;
}): ReactNode {
  return (
    <dl data-loading={loading ? "true" : undefined} className="flex flex-wrap gap-4">
      {items.map((item) => (
        <div key={item.label}>
          <dt>{item.label}</dt>
          <dd>{item.value}</dd>
        </div>
      ))}
    </dl>
  );
}
