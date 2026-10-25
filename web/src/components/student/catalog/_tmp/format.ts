// Stand-in for @/lib/format (WS0). Delete after the WS0 merge.
import type { Locale } from "@/i18n/locale-context";

const TZ = "Asia/Dhaka";
const BN_DIGITS = "০১২৩৪৫৬৭৮৯";

export function toBnDigits(text: string): string {
  return text.replace(/[0-9]/g, (d) => BN_DIGITS[Number(d)]);
}

function digits(text: string, locale: Locale): string {
  return locale === "bn" ? toBnDigits(text) : text;
}

export function formatNumber(value: number, locale: Locale): string {
  return digits(new Intl.NumberFormat("en-US").format(value), locale);
}

export function formatMoney(value: number, locale: Locale): string {
  const whole = Number.isInteger(value);
  const text = new Intl.NumberFormat("en-US", {
    minimumFractionDigits: whole ? 0 : 2,
    maximumFractionDigits: 2,
  }).format(value);
  return `৳${digits(text, locale)}`;
}

function parts(iso: string) {
  const formatted = new Intl.DateTimeFormat("en-US", {
    timeZone: TZ,
    year: "numeric",
    month: "numeric",
    day: "numeric",
    hour: "numeric",
    minute: "numeric",
    weekday: "short",
    hourCycle: "h23",
  }).formatToParts(new Date(iso));
  const get = (type: string) => formatted.find((p) => p.type === type)?.value ?? "";
  return {
    year: Number(get("year")),
    month: Number(get("month")),
    day: Number(get("day")),
    hour: Number(get("hour")) % 24,
    minute: Number(get("minute")),
    weekday: get("weekday"),
  };
}

const BN_MONTHS = [
  "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
  "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর",
];
const EN_MONTHS = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December",
];
const BN_WEEKDAYS: Record<string, string> = {
  Sun: "রবিবার", Mon: "সোমবার", Tue: "মঙ্গলবার", Wed: "বুধবার",
  Thu: "বৃহস্পতিবার", Fri: "শুক্রবার", Sat: "শনিবার",
};
const EN_WEEKDAYS: Record<string, string> = {
  Sun: "Sunday", Mon: "Monday", Tue: "Tuesday", Wed: "Wednesday",
  Thu: "Thursday", Fri: "Friday", Sat: "Saturday",
};

export function formatDate(
  iso: string,
  locale: Locale,
  options: { weekday?: boolean; now?: number } = {},
): string {
  const p = parts(iso);
  const thisYear = parts(new Date(options.now ?? Date.now()).toISOString()).year;
  const showYear = p.year !== thisYear;
  if (locale === "bn") {
    const base = `${toBnDigits(String(p.day))} ${BN_MONTHS[p.month - 1]}${showYear ? ` ${toBnDigits(String(p.year))}` : ""}`;
    return options.weekday ? `${BN_WEEKDAYS[p.weekday]}, ${base}` : base;
  }
  const base = `${p.day} ${EN_MONTHS[p.month - 1]}${showYear ? ` ${p.year}` : ""}`;
  return options.weekday ? `${EN_WEEKDAYS[p.weekday]}, ${base}` : base;
}

function bnPeriod(hour: number): string {
  if (hour >= 4 && hour < 6) return "ভোর";
  if (hour >= 6 && hour < 12) return "সকাল";
  if (hour >= 12 && hour < 15) return "দুপুর";
  if (hour >= 15 && hour < 18) return "বিকেল";
  if (hour >= 18 && hour < 20) return "সন্ধ্যা";
  return "রাত";
}

export function formatTime(iso: string, locale: Locale, withPeriod = true): string {
  const p = parts(iso);
  const h12 = p.hour % 12 === 0 ? 12 : p.hour % 12;
  const mm = String(p.minute).padStart(2, "0");
  if (locale === "bn") {
    const clock = toBnDigits(`${h12}:${mm}`);
    return withPeriod ? `${bnPeriod(p.hour)} ${clock}` : clock;
  }
  return `${h12}:${mm} ${p.hour < 12 ? "AM" : "PM"}`;
}

export function formatDateTime(iso: string, locale: Locale): string {
  return `${formatDate(iso, locale)}, ${formatTime(iso, locale)}`;
}

export function formatDuration(minutes: number, locale: Locale): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (locale === "bn") {
    const out: string[] = [];
    if (h) out.push(`${toBnDigits(String(h))} ঘণ্টা`);
    if (m || !h) out.push(`${toBnDigits(String(m))} মিনিট`);
    return out.join(" ");
  }
  const out: string[] = [];
  if (h) out.push(`${h} h`);
  if (m || !h) out.push(`${m} min`);
  return out.join(" ");
}

/** Calendar day key (YYYY-MM-DD) in Asia/Dhaka. */
export function dhakaDayKey(iso: string): string {
  const p = parts(iso);
  return `${p.year}-${String(p.month).padStart(2, "0")}-${String(p.day).padStart(2, "0")}`;
}

export function pickTitle(
  record: { titleEn?: string | null; titleBn?: string | null },
  locale: Locale,
): string {
  const bn = record.titleBn?.trim();
  const en = record.titleEn?.trim();
  return (locale === "bn" ? bn || en : en || bn) ?? "";
}

export function pickText<T extends object>(record: T, field: string, locale: Locale): string {
  const r = record as Record<string, unknown>;
  const bn = r[`${field}Bn`];
  const en = r[`${field}En`];
  const first = locale === "bn" ? bn : en;
  const second = locale === "bn" ? en : bn;
  if (typeof first === "string" && first.trim()) return first;
  if (typeof second === "string" && second.trim()) return second;
  return "";
}
