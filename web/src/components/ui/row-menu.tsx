"use client";

import Link from "next/link";
import * as DropdownMenu from "@radix-ui/react-dropdown-menu";
import { MoreHorizontal } from "lucide-react";
import { cn } from "@/components/ui/cn";

export type RowMenuItem = {
  label: string;
  href?: string;
  onClick?: () => void;
  tone?: "danger";
  /** Kept in the menu, greyed out and not selectable, while the action is not allowed yet. */
  disabled?: boolean;
};

const itemClass = (tone?: "danger", disabled?: boolean) =>
  cn(
    "flex min-h-10 cursor-pointer items-center rounded-control px-3 text-label outline-none data-[highlighted]:bg-bg data-[disabled]:cursor-not-allowed data-[disabled]:opacity-50",
    tone === "danger" ? "text-danger" : "text-text",
    disabled && "text-muted opacity-50",
  );

/** A row's action menu, opened from one button. `label` names the button for screen readers. */
export function RowMenu({
  items,
  label,
}: {
  items: RowMenuItem[];
  label: string;
}) {
  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger
        aria-label={label}
        className="inline-flex size-11 items-center justify-center rounded-control text-muted outline-none hover:bg-bg focus-visible:outline-accent"
      >
        <MoreHorizontal aria-hidden="true" className="size-4" />
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content
          align="end"
          sideOffset={4}
          className="z-50 grid min-w-44 gap-0.5 rounded-card border border-border bg-card p-1 shadow-lg"
        >
          {items.map((item) =>
            item.href && !item.disabled ? (
              <DropdownMenu.Item key={item.label} asChild className={itemClass(item.tone, item.disabled)}>
                <Link href={item.href} prefetch>
                  {item.label}
                </Link>
              </DropdownMenu.Item>
            ) : (
              <DropdownMenu.Item
                key={item.label}
                disabled={item.disabled}
                onSelect={() => item.onClick?.()}
                className={itemClass(item.tone, item.disabled)}
              >
                {item.label}
              </DropdownMenu.Item>
            ),
          )}
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}
