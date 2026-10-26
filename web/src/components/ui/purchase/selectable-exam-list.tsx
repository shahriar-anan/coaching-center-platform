"use client";

import type { ReactNode } from "react";
import { useLocale } from "@/i18n/locale-context";
import { formatMoney } from "@/lib/format";
import { StickyActionBar } from "@/components/ui/sticky-action-bar";
import { cn } from "@/components/ui/cn";

export type SelectableExam = {
  id: string;
  title: string;
  meta?: ReactNode;
  price: number | null;
  disabledReason?: string;
};

/**
 * Exams with a native checkbox each (Space toggles it without extra code). A
 * row that cannot be bought shows its reason as text and its checkbox is off.
 * The sticky bar gives the count and, when every selected exam has a price,
 * the total; `action` goes beside it.
 */
export function SelectableExamList({
  items,
  selected,
  onSelectedChange,
  summaryLabel,
  action,
}: {
  items: SelectableExam[];
  selected: string[];
  onSelectedChange: (ids: string[]) => void;
  summaryLabel: (count: number, total: number | null) => string;
  action: ReactNode;
}) {
  const { t, locale } = useLocale();
  const chosen = items.filter((item) => selected.includes(item.id));
  const total = chosen.length === 0 || chosen.some((item) => item.price === null)
    ? null
    : chosen.reduce((sum, item) => sum + (item.price ?? 0), 0);

  function toggle(id: string, on: boolean) {
    const next = on ? [...selected, id] : selected.filter((value) => value !== id);
    onSelectedChange(next);
  }

  return (
    <div className="grid gap-4 pb-24 md:pb-0">
      <ul className="grid gap-2">
        {items.map((item) => {
          const checked = selected.includes(item.id);
          const disabled = item.disabledReason !== undefined;
          const priceText = formatMoney(item.price, locale);
          return (
            <li key={item.id}>
              <label
                className={cn(
                  "flex min-h-11 cursor-pointer items-start gap-3 rounded-card border border-border bg-card p-4 transition-colors duration-150 hover:border-accent",
                  disabled && "cursor-not-allowed opacity-60 hover:border-border",
                )}
              >
                <input
                  type="checkbox"
                  className="mt-1 size-5 shrink-0 accent-action disabled:cursor-not-allowed"
                  checked={checked}
                  disabled={disabled}
                  onChange={(event) => toggle(item.id, event.target.checked)}
                  aria-describedby={disabled ? `${item.id}-reason` : undefined}
                />
                <span className="grid min-w-0 flex-1 gap-1">
                  <span className="text-label font-medium text-text">{item.title}</span>
                  {item.meta ? <span className="text-meta text-muted">{item.meta}</span> : null}
                  <span className="text-meta tabular-nums text-text">
                    {priceText ?? t("common.purchase.shared.noPrice")}
                  </span>
                  {disabled ? (
                    <span id={`${item.id}-reason`} className="text-meta text-warn">
                      {item.disabledReason}
                    </span>
                  ) : null}
                </span>
              </label>
            </li>
          );
        })}
      </ul>
      <StickyActionBar>
        <p role="status" className="me-auto text-label tabular-nums text-text">
          {summaryLabel(chosen.length, total)}
        </p>
        {action}
      </StickyActionBar>
    </div>
  );
}
