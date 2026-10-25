// Stand-in for @/lib/format (WS0). Delete after WS0 merges.
import type { Locale } from "./locale";

const BN_DIGITS = "০১২৩৪৫৬৭৮৯";

function digits(text: string, locale: Locale): string {
  return locale === "bn" ? text.replace(/[0-9]/g, (d) => BN_DIGITS[Number(d)]) : text;
}

export function formatNumber(value: number, locale: Locale): string {
  return digits(new Intl.NumberFormat("en-US", { maximumFractionDigits: 2 }).format(value), locale);
}

function dhakaParts(iso: string) {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Asia/Dhaka",
    year: "numeric",
    month: "numeric",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(new Date(iso));
  const get = (type: string) => Number(parts.find((part) => part.type === type)?.value ?? 0);
  return { year: get("year"), month: get("month"), day: get("day"), hour: get("hour"), minute: get("minute") };
}

const BN_MONTHS = ["জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"];
const EN_MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];

function bnPeriod(hour: number): string {
  if (hour >= 4 && hour < 6) return "ভোর";
  if (hour >= 6 && hour < 12) return "সকাল";
  if (hour >= 12 && hour < 15) return "দুপুর";
  if (hour >= 15 && hour < 18) return "বিকেল";
  if (hour >= 18 && hour < 20) return "সন্ধ্যা";
  return "রাত";
}

export function formatDate(iso: string, locale: Locale): string {
  const { year, month, day } = dhakaParts(iso);
  return locale === "bn"
    ? digits(`${day} ${BN_MONTHS[month - 1]} ${year}`, locale)
    : `${day} ${EN_MONTHS[month - 1]} ${year}`;
}

export function formatTime(iso: string, locale: Locale): string {
  const { hour, minute } = dhakaParts(iso);
  const mm = String(minute).padStart(2, "0");
  if (locale === "bn") {
    const h12 = hour % 12 === 0 ? 12 : hour % 12;
    return digits(`${bnPeriod(hour)} ${h12}:${mm}`, locale);
  }
  return `${hour % 12 === 0 ? 12 : hour % 12}:${mm} ${hour < 12 ? "am" : "pm"}`;
}

export function formatDateTime(iso: string, locale: Locale): string {
  return `${formatDate(iso, locale)}, ${formatTime(iso, locale)}`;
}

export function pickTitle(record: { titleBn?: string | null; titleEn?: string | null }, locale: Locale): string {
  return (locale === "bn" ? record.titleBn || record.titleEn : record.titleEn || record.titleBn) ?? "";
}
