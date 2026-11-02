"use client";

import * as RadixTabs from "@radix-ui/react-tabs";
import { cn } from "@/components/ui/cn";
import { Badge } from "@/components/ui/badge";

export type TabItem = {
  value: string;
  label: string;
  badge?: number;
};

/**
 * Tabs with a count badge. Arrow keys move between tabs; the content is rendered by the caller. A row of tabs that does not fit
 * scrolls sideways by default; `wrap` lets the tabs take a second line instead, so none is cut off (Phase 4, S4.4).
 * `variant="pill"` shows the selected tab as a filled pill instead of an underline.
 */
export function Tabs({
  tabs,
  value,
  onChange,
  ariaLabel,
  wrap = false,
  variant = "underline",
}: {
  tabs: TabItem[];
  value: string;
  onChange: (value: string) => void;
  ariaLabel: string;
  wrap?: boolean;
  variant?: "underline" | "pill";
}) {
  const pill = variant === "pill";
  return (
    <RadixTabs.Root value={value} onValueChange={onChange}>
      <RadixTabs.List
        aria-label={ariaLabel}
        className={cn(
          "flex gap-1 [scrollbar-width:none]",
          pill ? "inline-flex max-w-full self-start rounded-full border border-border bg-card p-1" : "border-b border-border",
          wrap ? "flex-wrap" : "overflow-x-auto",
        )}
      >
        {tabs.map((tab) => (
          <RadixTabs.Trigger
            key={tab.value}
            value={tab.value}
            className={cn(
              "inline-flex min-h-11 shrink-0 items-center text-label transition-colors duration-150 !outline-none focus-visible:bg-bg disabled:opacity-60",
              pill
                ? "min-h-9 rounded-full px-4 text-meta font-medium text-muted hover:text-text focus-visible:rounded-full data-[state=active]:bg-action data-[state=active]:text-on-action"
                : "-mb-px border-b-2 border-transparent px-3 text-muted hover:text-text focus-visible:rounded-t-control data-[state=active]:border-action data-[state=active]:text-text",
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
