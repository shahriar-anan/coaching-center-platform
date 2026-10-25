"use client";

import * as RadixTabs from "@radix-ui/react-tabs";
import { cn } from "@/components/ui/cn";
import { Badge } from "@/components/ui/badge";

export type TabItem = {
  value: string;
  label: string;
  badge?: number;
};

/** Tabs with a count badge. Arrow keys move between tabs; the content is rendered by the caller. */
export function Tabs({
  tabs,
  value,
  onChange,
  ariaLabel,
}: {
  tabs: TabItem[];
  value: string;
  onChange: (value: string) => void;
  ariaLabel: string;
}) {
  return (
    <RadixTabs.Root value={value} onValueChange={onChange}>
      <RadixTabs.List
        aria-label={ariaLabel}
        className="flex gap-1 overflow-x-auto border-b border-border [scrollbar-width:none]"
      >
        {tabs.map((tab) => (
          <RadixTabs.Trigger
            key={tab.value}
            value={tab.value}
            className={cn(
              "-mb-px inline-flex min-h-11 shrink-0 items-center border-b-2 px-3 text-label text-muted",
              "data-[state=active]:border-action data-[state=active]:text-text",
              "border-transparent hover:text-text",
            )}
          >
            {tab.label}
            {tab.badge ? <Badge count={tab.badge} /> : null}
          </RadixTabs.Trigger>
        ))}
      </RadixTabs.List>
    </RadixTabs.Root>
  );
}
