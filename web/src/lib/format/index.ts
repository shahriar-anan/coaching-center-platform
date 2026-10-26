/**
 * Locale-aware formatting. Every date and time is shown in Asia/Dhaka; Bangla
 * uses Bengali digits and the Bangla day periods (ভোর, সকাল, দুপুর, বিকেল,
 * সন্ধ্যা, রাত), never AM/PM. Pass the locale from `useLocale()`.
 */
import type { Locale } from "@/i18n/locale-context";


/** Dhaka has no daylight saving: UTC+6 all year. */
const DHAKA_OFFSET_MS = 6 * 60 * 60 * 1000;

const BN_DIGITS = ["০", "১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯"];

const BN_MONTHS = [
  "জানুয়ারি",
  "ফেব্রুয়ারি",
  "মার্চ",
  "এপ্রিল",
  "মে",
  "জুন",
  "জুলাই",
  "আগস্ট",
  "সেপ্টেম্বর",
  "অক্টোবর",
  "নভেম্বর",
  "ডিসেম্বর",
];

const EN_MONTHS = [
  "January",
  "February",
  "March",
  "April",
  "May",
  "June",
  "July",
  "August",
  "September",
  "October",
  "November",
  "December",
];

const BN_WEEKDAYS = ["রবিবার", "সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার", "শুক্রবার", "শনিবার"];
const EN_WEEKDAYS = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];

export type DateInput = string | number | Date;

/** Replaces Latin digits with Bengali digits. */
export function toBengaliDigits(text: string): string {
  return text.replace(/\d/g, (d) => BN_DIGITS[Number(d)]);
}

function digits(text: string, locale: Locale): string {
  return locale === "bn" ? toBengaliDigits(text) : text;
}

function toMs(value: DateInput): number {
  if (value instanceof Date) {
    return value.getTime();
  }
  return typeof value === "number" ? value : Date.parse(value);
}

/** Calendar parts of an instant as seen in Dhaka. */
export function dhakaParts(value: DateInput): {
  year: number;
  month: number;
  day: number;
  weekday: number;
  hour: number;
  minute: number;
} {
  const d = new Date(toMs(value) + DHAKA_OFFSET_MS);
  return {
    year: d.getUTCFullYear(),
    month: d.getUTCMonth(),
    day: d.getUTCDate(),
    weekday: d.getUTCDay(),
    hour: d.getUTCHours(),
    minute: d.getUTCMinutes(),
  };
}

/** The UTC instant for a wall-clock time in Dhaka. */
export function fromDhakaParts(parts: {
  year: number;
  month: number;
  day: number;
  hour?: number;
  minute?: number;
}): Date {
  return new Date(
    Date.UTC(parts.year, parts.month, parts.day, parts.hour ?? 0, parts.minute ?? 0) -
      DHAKA_OFFSET_MS,
  );
}

function group(intPart: string): string {
  return intPart.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
}

export function formatNumber(
  value: number,
  locale: Locale,
  options: { decimals?: number } = {},
): string {
  const fixed =
    options.decimals === undefined ? String(value) : value.toFixed(options.decimals);
  const negative = fixed.startsWith("-");
  const [intPart, fraction] = (negative ? fixed.slice(1) : fixed).split(".");
  const text = `${negative ? "-" : ""}${group(intPart)}${fraction ? `.${fraction}` : ""}`;
  return digits(text, locale);
}

/**
 * `৳২,৫০০` for whole taka and `৳২,৫০০.৫০` with paisa (Bangla);
 * `৳2,500.00` in English. Returns `null` for a missing price, so the caller
 * writes a sentence ("মূল্য এখনো ঠিক হয়নি") instead of a zero.
 */
export function formatMoney(value: number | string | null | undefined, locale: Locale): string | null {
  if (value === null || value === undefined || value === "") {
    return null;
  }
  const amount = typeof value === "string" ? Number(value) : value;
  if (Number.isNaN(amount)) {
    return null;
  }
  if (locale === "en") {
    return `৳${formatNumber(amount, "en", { decimals: 2 })}`;
  }
  const whole = Math.round(amount * 100) % 100 === 0;
  return `৳${formatNumber(amount, "bn", { decimals: whole ? 0 : 2 })}`;
}

export function formatDate(
  value: DateInput,
  locale: Locale,
  options: { weekday?: boolean; year?: "always" | "auto"; now?: DateInput } = {},
): string {
  const p = dhakaParts(value);
  const nowYear = dhakaParts(options.now ?? Date.now()).year;
  // The year is left out only on request ("auto") and only for the current year.
  const showYear = options.year === "auto" ? p.year !== nowYear : true;
  if (locale === "bn") {
    const base = `${toBengaliDigits(String(p.day))} ${BN_MONTHS[p.month]}${
      showYear ? ` ${toBengaliDigits(String(p.year))}` : ""
    }`;
    return options.weekday ? `${BN_WEEKDAYS[p.weekday]}, ${base}` : base;
  }
  const base = `${p.day} ${EN_MONTHS[p.month]}${showYear ? ` ${p.year}` : ""}`;
  return options.weekday ? `${EN_WEEKDAYS[p.weekday]}, ${base}` : base;
}

/** The Bangla day period for an hour in Dhaka (0–23). */
export function bnDayPeriod(hour: number): string {
  if (hour >= 4 && hour < 6) return "ভোর";
  if (hour >= 6 && hour < 12) return "সকাল";
  if (hour >= 12 && hour < 15) return "দুপুর";
  if (hour >= 15 && hour < 18) return "বিকেল";
  if (hour >= 18 && hour < 20) return "সন্ধ্যা";
  return "রাত";
}

function twelveHour(hour: number): number {
  const h = hour % 12;
  return h === 0 ? 12 : h;
}

export function formatTime(
  value: DateInput,
  locale: Locale,
  options: { period?: boolean } = {},
): string {
  const p = dhakaParts(value);
  const clock = `${twelveHour(p.hour)}:${String(p.minute).padStart(2, "0")}`;
  if (locale === "bn") {
    const text = toBengaliDigits(clock);
    return options.period === false ? text : `${bnDayPeriod(p.hour)} ${text}`;
  }
  return options.period === false ? clock : `${clock} ${p.hour < 12 ? "AM" : "PM"}`;
}

/** "১২ নভেম্বর, দুপুর ১২:০০" / "12 November, 12:00 PM". */
export function formatDateTime(
  value: DateInput,
  locale: Locale,
  options: { weekday?: boolean; year?: "always" | "auto"; now?: DateInput } = {},
): string {
  return `${formatDate(value, locale, { year: "auto", ...options })}, ${formatTime(value, locale)}`;
}

/** "দুপুর ১২:০০ – ১২:৪০"; the day period repeats only when it changes. */
export function formatTimeRange(start: DateInput, end: DateInput, locale: Locale): string {
  const a = formatTime(start, locale);
  const samePeriod =
    locale === "bn"
      ? bnDayPeriod(dhakaParts(start).hour) === bnDayPeriod(dhakaParts(end).hour)
      : (dhakaParts(start).hour < 12) === (dhakaParts(end).hour < 12);
  const b = formatTime(end, locale, { period: locale === "bn" ? !samePeriod : true });
  return `${a} – ${b}`;
}

/** "৪০ মিনিট", "১ ঘণ্টা ৩০ মিনিট" / "40 minutes", "1 hour 30 minutes". */
export function formatDuration(minutes: number, locale: Locale): string {
  const total = Math.max(0, Math.round(minutes));
  const h = Math.floor(total / 60);
  const m = total % 60;
  if (locale === "bn") {
    const parts: string[] = [];
    if (h) parts.push(`${toBengaliDigits(String(h))} ঘণ্টা`);
    if (m || !h) parts.push(`${toBengaliDigits(String(m))} মিনিট`);
    return parts.join(" ");
  }
  const parts: string[] = [];
  if (h) parts.push(`${h} ${h === 1 ? "hour" : "hours"}`);
  if (m || !h) parts.push(`${m} ${m === 1 ? "minute" : "minutes"}`);
  return parts.join(" ");
}

/** "০০:৩২:১০" for a sitting timer. */
export function formatClock(ms: number, locale: Locale): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const text = [h, m, s].map((n) => String(n).padStart(2, "0")).join(":");
  return digits(text, locale);
}

/**
 * A span of time in words for countdowns: "২ ঘণ্টা ১৪ মিনিট", "৩ দিন ২ ঘণ্টা",
 * "৪৫ সেকেন্ড" under one minute.
 */
export function formatRelative(ms: number, locale: Locale): string {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000));
  const days = Math.floor(totalSeconds / 86400);
  const hours = Math.floor((totalSeconds % 86400) / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const bn = locale === "bn";
  const n = (v: number) => digits(String(v), locale);
  const unit = (v: number, bnWord: string, enWord: string) =>
    bn ? `${n(v)} ${bnWord}` : `${v} ${v === 1 ? enWord : `${enWord}s`}`;
  if (days > 0) {
    return [unit(days, "দিন", "day"), hours ? unit(hours, "ঘণ্টা", "hour") : null]
      .filter(Boolean)
      .join(" ");
  }
  if (hours > 0) {
    return [unit(hours, "ঘণ্টা", "hour"), minutes ? unit(minutes, "মিনিট", "minute") : null]
      .filter(Boolean)
      .join(" ");
  }
  if (minutes > 0) {
    return unit(minutes, "মিনিট", "minute");
  }
  return unit(seconds, "সেকেন্ড", "second");
}

/** "আজ", "আগামীকাল", "গতকাল", or `null` for any other day (Dhaka calendar). */
export function relativeDay(value: DateInput, locale: Locale, now: DateInput = Date.now()): string | null {
  const a = dhakaParts(value);
  const b = dhakaParts(now);
  const dayA = Date.UTC(a.year, a.month, a.day);
  const dayB = Date.UTC(b.year, b.month, b.day);
  const diff = Math.round((dayA - dayB) / 86400000);
  const words =
    locale === "bn"
      ? { 0: "আজ", 1: "আগামীকাল", [-1]: "গতকাল" }
      : { 0: "Today", 1: "Tomorrow", [-1]: "Yesterday" };
  return (words as Record<number, string>)[diff] ?? null;
}

type TitledRecord = { titleEn?: string | null; titleBn?: string | null };

/** The title in the UI language, falling back to the other one. */
export function pickTitle(record: TitledRecord | null | undefined, locale: Locale): string {
  if (!record) {
    return "";
  }
  const primary = locale === "bn" ? record.titleBn : record.titleEn;
  const fallback = locale === "bn" ? record.titleEn : record.titleBn;
  return (primary?.trim() || fallback?.trim() || "") as string;
}

/**
 * `pickText(exam, "description", locale)` reads `descriptionBn` or
 * `descriptionEn`, falling back to the other language.
 */
export function pickText(
  record: Record<string, unknown> | null | undefined,
  field: string,
  locale: Locale,
): string {
  if (!record) {
    return "";
  }
  const bn = record[`${field}Bn`];
  const en = record[`${field}En`];
  const primary = locale === "bn" ? bn : en;
  const fallback = locale === "bn" ? en : bn;
  const text = (v: unknown) => (typeof v === "string" ? v.trim() : "");
  return text(primary) || text(fallback);
}
