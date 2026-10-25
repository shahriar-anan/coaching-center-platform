import type { ReactNode } from "react";

/** Exams with a checkbox each and a summary bar. Stub until P-4 lands. */
export function SelectableExamList({
  items,
  selected,
  summaryLabel,
  action,
}: {
  items: { id: string; title: string; meta?: ReactNode; price: number | null; disabledReason?: string }[];
  selected: string[];
  onSelectedChange: (ids: string[]) => void;
  summaryLabel: (count: number, total: number | null) => string;
  action: ReactNode;
}) {
  const total = items.reduce<number | null>((sum, item) => (sum === null || item.price === null ? null : sum + item.price), 0);
  return (
    <div>
      <ul>
        {items.map((item) => (
          <li key={item.id} data-selected={selected.includes(item.id) || undefined}>
            {item.title}
          </li>
        ))}
      </ul>
      <p>{summaryLabel(selected.length, total)}</p>
      {action}
    </div>
  );
}
