"use client";

import { formatNumber } from "@/lib/format";
import { useLocale } from "@/i18n/locale-context";

/** A count on a tab or a menu item. Hidden at zero. */
export function Badge({ count }: { count: number }) {
  const { locale } = useLocale();
  if (count <= 0) {
    return null;
  }
  const text = formatNumber(count, locale);
  return (
    <span
      aria-label={text}
      className="ms-1.5 inline-flex min-w-5 items-center justify-center rounded-full bg-warn-fill px-1.5 text-xs font-semibold text-white tabular-nums"
    >
      {text}
    </span>
  );
}
