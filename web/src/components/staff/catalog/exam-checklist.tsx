"use client";

import { CheckCircle2, Circle } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";

export type ChecklistItem = {
  key: string;
  label: string;
  done: boolean;
  /** The id of the field or control that fixes this item. */
  anchor?: string;
};

/** What is still missing before publish. Each open item links to its field. */
export function ExamChecklist({ items }: { items: ChecklistItem[] }) {
  const { t } = useLocale();
  const open = items.filter((item) => !item.done);
  return (
    <div className="grid gap-2">
      <p className="text-label font-medium">
        {open.length === 0 ? t("staffCatalog.checklist.allDone") : t("staffCatalog.checklist.missing")}
      </p>
      <ul className="grid gap-1.5">
        {items.map((item) => (
          <li key={item.key} className="flex items-center gap-2 text-meta">
            {item.done ? (
              <CheckCircle2 aria-hidden="true" className="size-4 shrink-0 text-ok" />
            ) : (
              <Circle aria-hidden="true" className="size-4 shrink-0 text-warn" />
            )}
            <span className="sr-only">{item.done ? t("staffCatalog.checklist.doneWord") : t("staffCatalog.checklist.openWord")}</span>
            {!item.done && item.anchor ? (
              <a href={`#${item.anchor}`} className="text-action underline-offset-2 hover:underline">
                {item.label}
              </a>
            ) : (
              <span className={item.done ? "text-muted" : undefined}>{item.label}</span>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}
