import type { PillTone } from "@/components/ui/pill";
import type { Locale } from "@/i18n/locale-context";
import type { ClassStatus, RoutineStatus } from "@/lib/api/exam/offline-batch-pay";

/*
 * Labels and small formats for the batch pay screens. Message keys are written out in full so the catalogue check can find them.
 */

/** The month label of a yyyy-MM value: "November 2026", or in Bangla digits and month names. */
export function monthLabel(month: string, locale: Locale): string {
  const [year, monthNumber] = month.split("-").map(Number);
  if (!year || !monthNumber) {
    return month;
  }
  return new Intl.DateTimeFormat(locale === "bn" ? "bn-BD" : "en-GB", {
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(Date.UTC(year, monthNumber - 1, 1)));
}

/** The weekday name of a yyyy-MM-dd date. */
export function weekdayName(date: string, locale: Locale): string {
  const [year, month, day] = date.split("-").map(Number);
  if (!year || !month || !day) {
    return "";
  }
  return new Intl.DateTimeFormat(locale === "bn" ? "bn-BD" : "en-GB", {
    weekday: "long",
    timeZone: "UTC",
  }).format(new Date(Date.UTC(year, month - 1, day)));
}

/** The Dhaka instant of a date and an HH:mm or HH:mm:ss time, for the formatters that take an instant. */
export function dhakaInstant(date: string, time: string): string {
  const clock = time.length === 5 ? `${time}:00` : time;
  return `${date}T${clock}+06:00`;
}

export function classStatusTone(status: ClassStatus): PillTone {
  switch (status) {
    case "SCHEDULED":
      return "muted";
    case "HELD":
      return "warn";
    case "PAID_PENDING":
    case "NOT_TAKEN_PENDING":
      return "accent";
    case "PAID":
      return "ok";
    case "NOT_TAKEN":
      return "muted";
    case "CANCELLED":
      return "danger";
  }
}

export function classStatusKey(status: ClassStatus): string {
  switch (status) {
    case "SCHEDULED":
      return "batchPay.classes.status.SCHEDULED";
    case "HELD":
      return "batchPay.classes.status.HELD";
    case "PAID_PENDING":
      return "batchPay.classes.status.PAID_PENDING";
    case "PAID":
      return "batchPay.classes.status.PAID";
    case "NOT_TAKEN_PENDING":
      return "batchPay.classes.status.NOT_TAKEN_PENDING";
    case "NOT_TAKEN":
      return "batchPay.classes.status.NOT_TAKEN";
    case "CANCELLED":
      return "batchPay.classes.status.CANCELLED";
  }
}

export function routineStatusKey(status: RoutineStatus): string {
  switch (status) {
    case "CONFIRMED":
      return "batchPay.routines.confirmed";
    case "PREVIEW":
      return "batchPay.routines.waiting";
    case "SUPERSEDED":
      return "batchPay.preview.readOnlySuperseded";
  }
}

export function routineStatusTone(status: RoutineStatus): PillTone {
  return status === "CONFIRMED" ? "ok" : status === "PREVIEW" ? "warn" : "muted";
}

/** The message key of a routine flag code, or null for a code the screens do not know (the English reason is shown then). */
export function flagKey(code: string): string | null {
  switch (code) {
    case "DATE_UNREADABLE":
      return "batchPay.preview.flags.DATE_UNREADABLE";
    case "DATE_PAST":
      return "batchPay.preview.flags.DATE_PAST";
    case "DATE_OUT_OF_ORDER":
      return "batchPay.preview.flags.DATE_OUT_OF_ORDER";
    case "WEEKDAY_MISMATCH":
      return "batchPay.preview.flags.WEEKDAY_MISMATCH";
    case "TIME_UNREADABLE":
      return "batchPay.preview.flags.TIME_UNREADABLE";
    case "NO_INSTRUCTOR":
      return "batchPay.preview.flags.NO_INSTRUCTOR";
    case "INSTRUCTOR_UNKNOWN":
      return "batchPay.preview.flags.INSTRUCTOR_UNKNOWN";
    case "EXAM_ROW":
      return "batchPay.preview.flags.EXAM_ROW";
    case "DUPLICATE_LECTURE":
      return "batchPay.preview.flags.DUPLICATE_LECTURE";
    default:
      return null;
  }
}

export function kindKey(kind: string): string {
  switch (kind) {
    case "EXAM":
      return "batchPay.preview.kind.EXAM";
    case "OTHER":
      return "batchPay.preview.kind.OTHER";
    default:
      return "batchPay.preview.kind.LECTURE";
  }
}

/** Money as a number: the API sends amounts as JSON numbers or strings. */
export function amountOf(value: number | string | null | undefined): number | null {
  if (value === null || value === undefined || value === "") {
    return null;
  }
  const amount = typeof value === "string" ? Number(value) : value;
  return Number.isFinite(amount) ? amount : null;
}
