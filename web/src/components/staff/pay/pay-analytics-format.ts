import type { Locale } from "@/i18n/locale-context";
import { formatNumber } from "@/lib/format";
import { todayInDhaka } from "@/components/staff/pay/pay-format";

// Pure helpers for the staff pay overview and analytics (staff pay plan, phase 6). No React here, so the month rules and the
// chart arithmetic can be tested directly. The API does the real checks; these only keep the screen from asking for nonsense.

/** The longest range the API answers. Mirrors the server's limit. */
export const MAX_RANGE_MONTHS = 36;

/** How many months the analytics range covers by default: the last twelve, this month included. */
export const DEFAULT_RANGE_MONTHS = 12;

export type RangeProblem = "invalid" | "backwards" | "long";

const MONTH = /^(\d{4})-(0[1-9]|1[0-2])$/;

/** True for a month written YYYY-MM. */
export function isMonth(value: string): boolean {
  return MONTH.test(value);
}

/** A month as a running count, so two months can be subtracted. Only meaningful for a valid YYYY-MM. */
function monthIndex(month: string): number {
  const [year, monthNumber] = month.split("-").map(Number);
  return year * 12 + (monthNumber - 1);
}

/** The month {@code delta} months away from {@code month} (YYYY-MM), across year ends. */
export function shiftMonth(month: string, delta: number): string {
  const index = monthIndex(month) + delta;
  const year = Math.floor(index / 12);
  const monthNumber = (index % 12) + 1;
  return `${year}-${String(monthNumber).padStart(2, "0")}`;
}

/**
 * Why a range cannot be asked for, or null when it is fine. A blank or malformed month is "invalid"; an end before the start is
 * "backwards"; more than the server's limit is "long".
 */
export function rangeProblem(from: string, to: string): RangeProblem | null {
  if (!isMonth(from) || !isMonth(to)) {
    return "invalid";
  }
  const span = monthIndex(to) - monthIndex(from) + 1;
  if (span < 1) {
    return "backwards";
  }
  if (span > MAX_RANGE_MONTHS) {
    return "long";
  }
  return null;
}

/** The default range: the last twelve months up to this month in Dhaka. */
export function defaultRange(now: number = Date.now()): { from: string; to: string } {
  const to = todayInDhaka(now).slice(0, 7);
  return { from: shiftMonth(to, -(DEFAULT_RANGE_MONTHS - 1)), to };
}

/** A short month name, with the year on January only, in the viewer's language. Dhaka time, so the month never slips. */
export function monthLabel(month: string, locale: Locale): string {
  const [year, monthNumber] = month.split("-").map(Number);
  const date = new Date(Date.UTC(year, monthNumber - 1, 15, 12));
  const options: Intl.DateTimeFormatOptions = { month: "short", timeZone: "Asia/Dhaka" };
  if (monthNumber === 1) {
    options.year = "2-digit";
  }
  return new Intl.DateTimeFormat(locale === "bn" ? "bn-BD" : "en-GB", options).format(date);
}

/** The scale an empty chart shows (৳0 to ৳1,000), so its three ticks still read 0, 500 and 1,000. */
export const EMPTY_CHART_SCALE = 1000;

/**
 * The top of a chart's scale: the largest value rounded up to 1, 2 or 5 times a power of ten (20 for 15, 5,000 for 3,200),
 * so the middle tick is a round half of it. Everything zero gives {@link EMPTY_CHART_SCALE}, never a 0.5 tick that rounds
 * to a second "1".
 */
export function chartScale(values: number[]): number {
  const largest = Math.max(0, ...values);
  if (largest <= 0) {
    return EMPTY_CHART_SCALE;
  }
  const magnitude = 10 ** Math.floor(Math.log10(largest));
  for (const step of [1, 2, 5]) {
    if (step * magnitude >= largest) {
      return step * magnitude;
    }
  }
  return 10 * magnitude;
}

/** A value as a percentage of the scale, kept between 0 and 100. */
export function percentOf(value: number, scale: number): number {
  return Math.min(100, Math.max(0, (value / scale) * 100));
}

/** A y-axis tick: in full below 10,000, and in thousands (10K) from there. */
export function axisLabel(value: number, locale: Locale): string {
  if (Math.abs(value) >= 10000) {
    return `${formatNumber(Math.round(value / 1000), locale)}K`;
  }
  return formatNumber(Math.round(value), locale);
}
