import type { StaffNotification, StaffNotificationPayload } from "@/lib/api/exam/staff-notifications";
import type { Locale } from "@/i18n/locale-context";
import { formatDate, formatMoney, pickTitle } from "@/lib/format";

/** The translate function the screens pass in: a key and its parameters. */
export type Translate = (key: string, params?: Record<string, string | number>) => string;

const METHODS = new Set(["BANK", "BKASH", "NAGAD", "ROCKET"]);
const CHANGES = new Set(["ADDED", "UPDATED", "REMOVED", "PREFERRED"]);
const KNOWN_KINDS = new Set([
  "PAYOUT_ACCOUNT_CHANGED",
  "MARKING_PUBLISHED_OWED",
  "MARKING_PUBLISHED_EARNED",
  "PAYMENT_RECORDED",
  "PAYMENT_VOIDED",
  "SALARY_DUE_SOON",
  "SALARY_OVERDUE",
  "MARKING_UNPAID_REMINDER",
  "EXAM_ASSIGNED",
  "EXAM_UNASSIGNED",
  "EXAM_RATE_CHANGED",
  "EXAM_PAY_TERMS_CHANGED",
]);

/** A payload value as text: empty for a missing value. */
function text(value: unknown): string {
  return value === null || value === undefined ? "" : String(value);
}

/** A payload value as a count. A missing or non-number value counts as 0. */
function count(value: unknown): number {
  const n = typeof value === "number" ? value : Number(value);
  return Number.isFinite(n) ? n : 0;
}

/** A payload amount in taka for the language, or empty when it is missing. */
function money(value: unknown, locale: Locale): string {
  if (value === null || value === undefined || value === "") {
    return "";
  }
  return formatMoney(typeof value === "number" ? value : text(value), locale) ?? "";
}

/** "1 sheet" or "10 sheets" in the language. */
function sheets(value: unknown, t: Translate): string {
  const n = count(value);
  return n === 1 ? t("staffNotifications.sheetsOne") : t("staffNotifications.sheetsMany", { count: n });
}

/** The exam's title in the language, from the two title fields the payload carries. */
function examTitle(payload: StaffNotificationPayload, locale: Locale): string {
  return pickTitle(
    { titleEn: text(payload.examTitleEn) || null, titleBn: text(payload.examTitleBn) || null },
    locale,
  );
}

/** A period such as "2026-11" as a month and year in the language. Anything else is shown as it came. */
export function monthLabel(period: unknown, locale: Locale): string {
  const match = /^(\d{4})-(\d{2})$/.exec(text(period));
  if (!match) {
    return text(period);
  }
  const date = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, 15));
  return new Intl.DateTimeFormat(locale === "bn" ? "bn-BD" : "en-GB", { month: "long", year: "numeric", timeZone: "UTC" }).format(
    date,
  );
}

/** A date such as "2026-12-05" in the language. */
function dayLabel(value: unknown, locale: Locale): string {
  const raw = text(value);
  return raw ? formatDate(raw, locale) : "";
}

function methodLabel(value: unknown, t: Translate): string {
  const method = text(value).toUpperCase();
  return METHODS.has(method) ? t(`staffNotifications.method.${method}`) : text(value);
}

function changeLabel(value: unknown, t: Translate): string {
  const change = text(value).toUpperCase();
  return CHANGES.has(change) ? t(`staffNotifications.change.${change}`) : t("staffNotifications.change.other");
}

/**
 * The sentence for one notification, in the language. The text comes from its kind and its payload; a kind this screen does
 * not know gets the generic sentence, so a kind added later still reads. Nothing here shows an account number.
 */
export function notificationText(
  notification: Pick<StaffNotification, "kind" | "payload">,
  t: Translate,
  locale: Locale,
): string {
  const { kind, payload } = notification;
  if (!KNOWN_KINDS.has(kind)) {
    return t("staffNotifications.kind.UNKNOWN");
  }
  switch (kind) {
    case "PAYOUT_ACCOUNT_CHANGED":
      return t("staffNotifications.kind.PAYOUT_ACCOUNT_CHANGED", {
        person: text(payload.personName),
        change: changeLabel(payload.change, t),
      });
    case "MARKING_PUBLISHED_OWED":
      return t("staffNotifications.kind.MARKING_PUBLISHED_OWED", {
        exam: examTitle(payload, locale),
        person: text(payload.personName),
        sheets: sheets(payload.sheets, t),
        amount: money(payload.amount, locale),
        balance: money(payload.balance, locale),
      });
    case "MARKING_PUBLISHED_EARNED":
      return t("staffNotifications.kind.MARKING_PUBLISHED_EARNED", {
        exam: examTitle(payload, locale),
        sheets: sheets(payload.sheets, t),
        amount: money(payload.amount, locale),
      });
    case "PAYMENT_RECORDED":
      return t("staffNotifications.kind.PAYMENT_RECORDED", {
        amount: money(payload.amount, locale),
        method: methodLabel(payload.method, t),
      });
    case "PAYMENT_VOIDED":
      return t("staffNotifications.kind.PAYMENT_VOIDED", {
        amount: money(payload.amount, locale),
        method: methodLabel(payload.method, t),
      });
    case "SALARY_DUE_SOON":
    case "SALARY_OVERDUE":
      return t(`staffNotifications.kind.${kind}`, {
        person: text(payload.personName),
        month: monthLabel(payload.period, locale),
        amount: money(payload.amount, locale),
        date: dayLabel(payload.dueOn, locale),
      });
    case "MARKING_UNPAID_REMINDER":
      return t("staffNotifications.kind.MARKING_UNPAID_REMINDER", {
        person: text(payload.personName),
        amount: money(payload.amount, locale),
        days: count(payload.days),
      });
    case "EXAM_ASSIGNED":
    case "EXAM_UNASSIGNED":
      return t(`staffNotifications.kind.${kind}`, { exam: examTitle(payload, locale) });
    case "EXAM_RATE_CHANGED":
      return t("staffNotifications.kind.EXAM_RATE_CHANGED", {
        exam: examTitle(payload, locale),
        from: money(payload.fromRate, locale),
        to: money(payload.toRate, locale),
      });
    case "EXAM_PAY_TERMS_CHANGED": {
      // The API leaves a missing marker or rate out of the payload; show "none" rather than an empty gap.
      const none = t("staffNotifications.kind.NO_MARKER");
      return t("staffNotifications.kind.EXAM_PAY_TERMS_CHANGED", {
        person: text(payload.personName),
        exam: examTitle(payload, locale),
        fromMarker: text(payload.previousMarkerName) || none,
        toMarker: text(payload.markerName) || none,
        from: money(payload.fromRate, locale) || none,
        to: money(payload.toRate, locale) || none,
      });
    }
    default:
      return t("staffNotifications.kind.UNKNOWN");
  }
}

/** Where a row leads: its staff page, or the notification list when the link is not a staff page. */
export function notificationHref(link: string): string {
  return link.startsWith("/staff/") ? link : "/staff/notifications";
}
