import type { ReactNode } from "react";

export type Shortcut = {
  keys: string[];
  label: string;
};

/** Keyboard shortcuts in a popover. Stub: renders the list inline. */
export function ShortcutsPopover({ shortcuts }: { shortcuts: Shortcut[] }): ReactNode {
  return (
    <ul>
      {shortcuts.map((shortcut) => (
        <li key={shortcut.label}>
          {shortcut.keys.join(" + ")} {shortcut.label}
        </li>
      ))}
    </ul>
  );
}
