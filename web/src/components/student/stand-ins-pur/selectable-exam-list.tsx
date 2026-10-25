// Stand-in for PUR-FND's SelectableExamList. Delete at the switch to @/components/ui/purchase.
import type { ReactNode } from "react";
import { useLocale } from "@/i18n/locale-context";

export type SelectableItem = {
  id: string;
  title: string;
  meta?: ReactNode;
  price: number | null;
  disabledReason?: string;
};

export function SelectableExamList({
  items,
  selected,
  onSelectedChange,
  summaryLabel,
  action,
}: {
  items: SelectableItem[];
  selected: string[];
  onSelectedChange: (ids: string[]) => void;
  summaryLabel: (count: number, total: number | null) => string;
  action: ReactNode;
}) {
  const { t } = useLocale();
  const chosen = items.filter((item) => selected.includes(item.id) && !item.disabledReason);
  const total = chosen.some((item) => item.price === null)
    ? null
    : chosen.reduce((sum, item) => sum + (item.price ?? 0), 0);
  return (
    <div className="grid gap-3">
      <ul className="grid gap-2">
        {items.map((item) => (
          <li key={item.id}>
            <label className="flex items-start gap-3 rounded-card border border-border bg-card p-3">
              <input
                type="checkbox"
                className="mt-1"
                disabled={Boolean(item.disabledReason)}
                checked={selected.includes(item.id)}
                onChange={(event) =>
                  onSelectedChange(
                    event.target.checked
                      ? [...selected, item.id]
                      : selected.filter((id) => id !== item.id),
                  )
                }
              />
              <span className="grid gap-1">
                <span className="font-semibold">{item.title}</span>
                {item.meta}
                {item.disabledReason ? <span className="text-meta text-muted">{item.disabledReason}</span> : null}
              </span>
            </label>
          </li>
        ))}
      </ul>
      <div className="flex flex-wrap items-center justify-between gap-2" aria-live="polite">
        <p className="text-body">{summaryLabel(chosen.length, total)}</p>
        {action}
      </div>
      <span className="sr-only">{t("purchase.shared.selectionHint")}</span>
    </div>
  );
}
