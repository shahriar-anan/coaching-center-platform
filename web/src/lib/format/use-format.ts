"use client";

import { useMemo } from "react";
import { useLocale } from "@/i18n/locale-context";
import {
  formatClock,
  formatDate,
  formatDateTime,
  formatDuration,
  formatMoney,
  formatNumber,
  formatRelative,
  formatTime,
  formatTimeRange,
  pickText,
  pickTitle,
  relativeDay,
  type DateInput,
} from "./index";

/** The formatting helpers bound to the current UI locale. */
export function useFormat() {
  const { locale } = useLocale();
  return useMemo(
    () => ({
      locale,
      formatNumber: (value: number, options?: { decimals?: number }) =>
        formatNumber(value, locale, options),
      formatMoney: (value: number | string | null | undefined) => formatMoney(value, locale),
      formatDate: (value: DateInput, options?: Parameters<typeof formatDate>[2]) =>
        formatDate(value, locale, options),
      formatTime: (value: DateInput) => formatTime(value, locale),
      formatDateTime: (value: DateInput, options?: Parameters<typeof formatDateTime>[2]) =>
        formatDateTime(value, locale, options),
      formatTimeRange: (start: DateInput, end: DateInput) => formatTimeRange(start, end, locale),
      formatDuration: (minutes: number) => formatDuration(minutes, locale),
      formatRelative: (ms: number) => formatRelative(ms, locale),
      formatClock: (ms: number) => formatClock(ms, locale),
      relativeDay: (value: DateInput, now?: number) => relativeDay(value, locale, now),
      pickTitle: (record: Parameters<typeof pickTitle>[0]) => pickTitle(record, locale),
      pickText: (record: Parameters<typeof pickText>[0], field: string) =>
        pickText(record, field, locale),
    }),
    [locale],
  );
}
