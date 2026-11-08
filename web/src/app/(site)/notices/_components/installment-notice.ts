/**
 * The rules for a direct-enrolment money notice (plan direct-enrolment-installments, sections 7 and 8). Pure: no React, so the rows
 * and the tests read the same rules. The API may leave out any field of the `installment` block, and may leave out the block.
 */
import type { Locale } from "@/i18n/locale-context";
import { formatDate, formatMoney, pickTitle } from "@/lib/format";
import type { NoticeInstallment } from "@/lib/api/exam/notices";
import { isMissing, optionalAmount, optionalDate } from "@/lib/api/missing";

/** The five money kinds (plan section 7). They all sit under the Exams tab. */
export const INSTALLMENT_KINDS = [
  "INSTALLMENT_PAYMENT_RECEIVED",
  "INSTALLMENT_DUE_SOON",
  "INSTALLMENT_OVERDUE",
  "INSTALLMENT_PAYMENT_VOIDED",
  "INSTALLMENT_SCHEDULE_CHANGED",
] as const;

export type InstallmentKind = (typeof INSTALLMENT_KINDS)[number];

export function isInstallmentKind(kind: string): kind is InstallmentKind {
  return (INSTALLMENT_KINDS as readonly string[]).includes(kind);
}

/** Every money notice opens the student's payments on the Offline purchases tab (plan section 3 and 7). */
export const INSTALLMENT_HREF = "/my-modules?tab=offline";

/** The tone of a money notice's icon and tile. Overdue, a void and a changed date take the warning tone. */
export type InstallmentTone = "accent" | "ok" | "warn";

const TONE: Record<InstallmentKind, InstallmentTone> = {
  INSTALLMENT_PAYMENT_RECEIVED: "ok",
  INSTALLMENT_DUE_SOON: "accent",
  INSTALLMENT_OVERDUE: "warn",
  INSTALLMENT_PAYMENT_VOIDED: "warn",
  INSTALLMENT_SCHEDULE_CHANGED: "warn",
};

export function installmentTone(kind: InstallmentKind): InstallmentTone {
  return TONE[kind];
}

/** One figure on a notice's row: a copy key and the values it fills in, already formatted. */
export type InstallmentFact = { key: string; params: Record<string, string> };

/** The title of the plan's exam or package, in the student's language. Empty when neither title came. */
export function installmentTitle(installment: NoticeInstallment | null | undefined, locale: Locale): string {
  return isMissing(installment) ? "" : pickTitle(installment, locale);
}

/**
 * The figures a notice's row shows, in order. A payment notice shows what was taken (or voided), the next date, what is paid so
 * far and what is still owed, and its receipt number. A reminder shows what is due now on its installment, its date, and what is
 * still owed on the plan. A schedule change shows what is still owed. A figure the API left out is not shown.
 */
export function installmentFacts(
  kind: string,
  installment: NoticeInstallment | null | undefined,
  locale: Locale,
): InstallmentFact[] {
  if (!isInstallmentKind(kind) || isMissing(installment)) {
    return [];
  }
  const facts: InstallmentFact[] = [];
  const isPayment = kind === "INSTALLMENT_PAYMENT_RECEIVED" || kind === "INSTALLMENT_PAYMENT_VOIDED";
  const amount = formatMoney(optionalAmount(installment.amount), locale);
  if (amount) {
    const key = kind === "INSTALLMENT_PAYMENT_VOIDED" ? "notices.installment.voided" : "notices.installment.received";
    facts.push({ key: isPayment ? key : "notices.installment.amountDue", params: { amount } });
  }
  const date = optionalDate(installment.dueOn);
  if (date) {
    facts.push({
      key: isPayment ? "notices.installment.nextOn" : "notices.installment.dueOn",
      params: { date: formatDate(date, locale) },
    });
  }
  const paid = formatMoney(optionalAmount(installment.paid), locale);
  if (paid) {
    facts.push({ key: "notices.installment.paidSoFar", params: { amount: paid } });
  }
  const due = formatMoney(optionalAmount(installment.due), locale);
  if (due) {
    facts.push({ key: "notices.installment.stillOwed", params: { amount: due } });
  }
  if (!isMissing(installment.receiptNo) && installment.receiptNo.trim() !== "") {
    facts.push({ key: "notices.installment.receipt", params: { number: installment.receiptNo } });
  }
  return facts;
}
