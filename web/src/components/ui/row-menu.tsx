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
};

const itemClass = (tone?: "danger") =>
  cn(
    "flex min-h-10 cursor-pointer items-center rounded-control px-3 text-label outline-none data-[highlighted]:bg-bg",
    tone === "danger" ? "text-danger" : "text-text",
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
        className="inline-flex size-10 items-center justify-center rounded-control text-muted outline-none hover:bg-bg focus-visible:outline-accent"
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
            item.href ? (
              <DropdownMenu.Item key={item.label} asChild className={itemClass(item.tone)}>
                <Link href={item.href} prefetch>
                  {item.label}
                </Link>
              </DropdownMenu.Item>
            ) : (
              <DropdownMenu.Item key={item.label} onSelect={() => item.onClick?.()} className={itemClass(item.tone)}>
                {item.label}
              </DropdownMenu.Item>
            ),
          )}
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}
