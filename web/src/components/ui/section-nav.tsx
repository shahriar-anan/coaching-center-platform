"use client";

import { Check, CircleAlert, Circle } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { cn } from "@/components/ui/cn";

export type SectionNavItem = {
  id: string;
  label: string;
  state: "done" | "todo" | "error";
};

const ICON = {
  done: Check,
  todo: Circle,
  error: CircleAlert,
} as const;

const TONE = {
  done: "text-ok",
  todo: "text-muted",
  error: "text-danger",
} as const;

/**
 * Jump links to the sections of a long form. Sticky beside the form on desktop;
 * on phones it is a plain list above the form, so it scrolls away with the page.
 */
export function SectionNav({ sections }: { sections: SectionNavItem[] }) {
  const { t } = useLocale();
  return (
    <nav aria-label={t("common.sectionNav.label")} className="md:sticky md:top-20 md:self-start">
      <ol className="grid gap-1">
        {sections.map((section) => {
          const Icon = ICON[section.state];
          return (
            <li key={section.id} data-state={section.state}>
              <a
                href={`#${section.id}`}
                className="flex min-h-10 items-center gap-2 rounded-control px-3 text-label text-text outline-none hover:bg-bg focus-visible:outline-accent"
              >
                <Icon aria-hidden="true" className={cn("size-4 shrink-0", TONE[section.state])} />
                <span className="min-w-0 truncate">{section.label}</span>
              </a>
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
