"use client";

import { cn } from "@/components/ui/cn";

export type SegmentOption = { value: string; label: string };

/**
 * A row of mutually exclusive choices as one segmented control, the pattern of the student "All · MCQ · Written" filter.
 * The pressed choice is announced with aria-pressed. Each choice is 44 px tall, so it is a full touch target.
 */
export function SegmentedControl({
  options,
  value,
  onChange,
  ariaLabel,
}: {
  options: SegmentOption[];
  value: string;
  onChange: (next: string) => void;
  ariaLabel: string;
}) {
  return (
    <div
      role="group"
      aria-label={ariaLabel}
      className="inline-flex max-w-full flex-wrap gap-1 rounded-full border border-border bg-card p-1 text-meta"
    >
      {options.map((option) => {
        const active = option.value === value;
        return (
          <button
            key={option.value || "all"}
            type="button"
            aria-pressed={active}
            onClick={() => onChange(option.value)}
            className={cn(
              "min-h-11 rounded-full px-4 font-medium transition-colors duration-150",
              active ? "bg-action text-on-action" : "text-muted hover:text-text",
            )}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
