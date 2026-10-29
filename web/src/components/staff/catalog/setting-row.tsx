"use client";

import type { ReactNode } from "react";
import { Switch } from "@/components/ui/switch";

/**
 * One on/off setting: the label and its one-line description on the left, the switch on the right. The rule above
 * each row is the divider between rows. Anything under the setting (a field it reveals) goes in `children`.
 */
export function SettingRow({
  id,
  label,
  description,
  checked,
  onCheckedChange,
  disabled,
  children,
}: {
  id: string;
  label: string;
  description?: string;
  checked: boolean;
  onCheckedChange: (value: boolean) => void;
  disabled?: boolean;
  children?: ReactNode;
}) {
  return (
    <div className="grid gap-3 border-t border-border py-3">
      <div className="flex items-center justify-between gap-4">
        <div className="grid min-w-0 gap-0.5">
          <span className="text-label font-medium text-text">{label}</span>
          {description ? <p className="text-meta text-muted">{description}</p> : null}
        </div>
        <Switch id={id} checked={checked} onCheckedChange={onCheckedChange} disabled={disabled} label={label} />
      </div>
      {children}
    </div>
  );
}
