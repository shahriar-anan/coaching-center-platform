"use client";

import { useMemo } from "react";
import { cn } from "@/components/ui/cn";
import { controlBase, controlBorder } from "@/components/ui/control";
import { useLocale } from "@/i18n/locale-context";
import { dhakaParts, fromDhakaParts } from "@/lib/format";

/** `YYYY-MM-DDTHH:mm` in Dhaka time, the format `datetime-local` uses. */
function toDhakaLocal(iso: string): string {
  const p = dhakaParts(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${p.year}-${pad(p.month + 1)}-${pad(p.day)}T${pad(p.hour)}:${pad(p.minute)}`;
}

function fromDhakaLocal(local: string): string | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(local);
  if (!match) {
    return null;
  }
  const [, y, mo, d, h, mi] = match.map(Number);
  return fromDhakaParts({ year: y, month: mo - 1, day: d, hour: h, minute: mi }).toISOString();
}

/**
 * A date and time typed in Asia/Dhaka. The value and `onChange` use ISO UTC,
 * so the API always gets an instant. An empty field gives `null`.
 */
export function DateTimeInput({
  value,
  onChange,
  invalid,
  id,
  name,
  disabled,
  "aria-describedby": describedBy,
  className,
}: {
  value: string | null;
  onChange: (iso: string | null) => void;
  invalid?: boolean;
  id?: string;
  name?: string;
  disabled?: boolean;
  "aria-describedby"?: string;
  className?: string;
}) {
  const { t } = useLocale();
  const local = useMemo(() => (value ? toDhakaLocal(value) : ""), [value]);
  return (
    <div className={cn("grid gap-1", className)}>
      <input
        id={id}
        name={name}
        type="datetime-local"
        value={local}
        disabled={disabled}
        aria-describedby={describedBy}
        aria-label={t("common.dateTime.date")}
        onChange={(e) => onChange(e.target.value ? fromDhakaLocal(e.target.value) : null)}
        className={cn(controlBase, controlBorder(invalid), "tabular-nums")}
      />
      <span className="text-meta text-muted">{t("common.dateTime.zone")}</span>
    </div>
  );
}
