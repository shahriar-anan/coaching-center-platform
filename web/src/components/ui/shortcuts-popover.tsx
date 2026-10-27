"use client";

import * as Popover from "@radix-ui/react-popover";
import { Keyboard } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";

export type Shortcut = {
  keys: string[];
  label: string;
};

/** A keyboard button that opens the list of shortcuts for the current screen. */
/** `showLabel` puts the word "Shortcuts" next to the icon, so the control is found without being guessed. */
export function ShortcutsPopover({ shortcuts, showLabel = false }: { shortcuts: Shortcut[]; showLabel?: boolean }) {
  const { t } = useLocale();
  return (
    <Popover.Root>
      <Popover.Trigger
        aria-label={t("common.shortcuts.open")}
        className={
          showLabel
            ? "inline-flex min-h-10 items-center gap-2 rounded-control border border-border-control bg-card px-3 text-label text-text outline-none hover:bg-bg focus-visible:outline-accent"
            : "inline-flex size-10 items-center justify-center rounded-control text-muted outline-none hover:bg-bg focus-visible:outline-accent"
        }
      >
        <Keyboard aria-hidden="true" className="size-4" />
        {showLabel ? <span>{t("common.shortcuts.label")}</span> : null}
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Content
          align="end"
          sideOffset={6}
          className="z-50 grid w-72 gap-3 rounded-card border border-border bg-card p-4 shadow-lg"
        >
          <h2 className="text-label font-semibold text-text">{t("common.shortcuts.title")}</h2>
          <ul className="grid gap-2">
            {shortcuts.map((shortcut) => (
              <li key={shortcut.label} className="flex items-center justify-between gap-3 text-meta text-text">
                <span>{shortcut.label}</span>
                <span className="flex shrink-0 gap-1">
                  {shortcut.keys.map((key) => (
                    <kbd key={key} className="rounded border border-border bg-bg px-1.5 py-0.5 text-pill font-medium">
                      {key}
                    </kbd>
                  ))}
                </span>
              </li>
            ))}
          </ul>
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  );
}
