import type { PillTone } from "@/components/ui/pill";
import { dhakaParts } from "@/lib/format";
import type { AdmissionState, AdmissionForm } from "@/lib/api/exam/offline-admission";
import type {
  AdmissionQueueRow,
  DueView,
  FeeSchedule,
  FieldProblem,
  PaymentStatus,
  QueueMoneyFilter,
} from "@/lib/api/exam/offline-admin";
import type { Role } from "@/lib/api/exam/types";

/*
 * Pure rules for the staff offline admission screens: which tab a role starts on, what the ready checklist says is missing, which
 * money pills an admission shows, and the dues the setup will create. No React here, so the rules can be tested on their own.
 * The money flags of the queue come from the server; this file only turns them into pills.
 */

// ---- the queue tabs ----------------------------------------------------------------------------------------------------

export const QUEUE_TABS = ["AWAITING_FORM", "SUBMITTED", "CHANGES_REQUESTED", "READY", "ONBOARDED", "CLOSED"] as const;
export type QueueTab = (typeof QUEUE_TABS)[number];

/** The money filters of the queue, in the order the select shows them. The server applies each one. */
export const QUEUE_MONEY_FILTERS: QueueMoneyFilter[] = ["unpaid", "overdue", "pending-approval"];

/** A money filter from the URL, or none when the URL has none or an unknown one. */
export function parseMoneyFilter(raw: string | null | undefined): QueueMoneyFilter | undefined {
  return (QUEUE_MONEY_FILTERS as string[]).includes(raw ?? "") ? (raw as QueueMoneyFilter) : undefined;
}

/** The Master Admin lands on what waits for the onboarding; staff land on the forms waiting for review. */
export function defaultQueueTab(role: Role | null | undefined): QueueTab {
  return role === "MASTER_ADMIN" ? "READY" : "SUBMITTED";
}

/** A tab from the URL, or the role's default when the URL has none or an unknown one. */
export function parseQueueTab(raw: string | null | undefined, role: Role | null | undefined): QueueTab {
  return (QUEUE_TABS as readonly string[]).includes(raw ?? "") ? (raw as QueueTab) : defaultQueueTab(role);
}

// ---- the ready checklist -----------------------------------------------------------------------------------------------

/** The setup parts the checklist names, in the order it shows them. Any other problem is a form problem. */
export const CHECKLIST_KEYS = ["form", "batchId", "courseFee", "admittedOn", "firstDueOn", "courseEndsOn", "installments"] as const;
export type ChecklistKey = (typeof CHECKLIST_KEYS)[number];

export type ChecklistRow = { key: ChecklistKey; missing: FieldProblem[] };

const SETUP_FIELDS = new Set<string>(["batchId", "courseFee", "installments", "admittedOn", "firstDueOn", "courseEndsOn"]);

/**
 * One row per requirement, each with the problems the API found for it. A row with no problems is ticked. The Ready button is
 * on only when every row is ticked.
 */
export function checklistRows(problems: FieldProblem[]): ChecklistRow[] {
  return CHECKLIST_KEYS.map((key) => ({
    key,
    missing: problems.filter((problem) => (key === "form" ? !SETUP_FIELDS.has(problem.field) : problem.field === key)),
  }));
}

export function canMarkReady(problems: FieldProblem[], state: string): boolean {
  return state === "SUBMITTED" && problems.length === 0;
}

// ---- money pills -------------------------------------------------------------------------------------------------------

/**
 * The one colour code for money on every admin page: danger for overdue, accent for waiting approval, warn for no payment, ok for paid up.
 * `overdue` names the overdue amount; `overdueFlag` is the bare flag the queue shows, which the server sends without an amount.
 */
export type MoneyPillKey = "overdue" | "overdueFlag" | "pending" | "noPayment" | "owed" | "paid";

export type MoneyPill = { key: MoneyPillKey; tone: PillTone; amount: number | null };

const MONEY_TONE: Record<MoneyPillKey, PillTone> = {
  overdue: "danger",
  overdueFlag: "danger",
  pending: "accent",
  noPayment: "warn",
  owed: "muted",
  paid: "ok",
};

/** The money pills for an admission, from its schedule. An admission with no dues shows none. */
export function moneyPills(schedule: Pick<FeeSchedule, "owed" | "overdue" | "dues" | "payments">): MoneyPill[] {
  if (schedule.dues.length === 0) {
    return [];
  }
  const pills: MoneyPill[] = [];
  const overdue = toNumber(schedule.overdue);
  const owed = toNumber(schedule.owed);
  const approved = schedule.payments.filter((payment) => payment.status === "APPROVED").length;
  const pending = schedule.payments.filter((payment) => payment.status === "PENDING").length;
  if (overdue > 0) {
    pills.push({ key: "overdue", tone: MONEY_TONE.overdue, amount: overdue });
  }
  if (pending > 0) {
    pills.push({ key: "pending", tone: MONEY_TONE.pending, amount: null });
  }
  if (approved === 0 && owed > 0) {
    pills.push({ key: "noPayment", tone: MONEY_TONE.noPayment, amount: null });
  } else if (owed > 0 && overdue === 0) {
    pills.push({ key: "owed", tone: MONEY_TONE.owed, amount: owed });
  }
  if (owed === 0) {
    pills.push({ key: "paid", tone: MONEY_TONE.paid, amount: null });
  }
  return pills;
}

/**
 * The money pills of a queue row, from the flags and the owed total the server sends (plan 4.3). An onboarded or closed admission with
 * nothing owed shows Paid up; any other row with nothing owed shows none.
 */
export function queueMoneyPills(row: Pick<AdmissionQueueRow, "status" | "flags" | "owedTotal">): MoneyPill[] {
  const flags = new Set<string>(row.flags);
  const owed = toNumber(row.owedTotal);
  const pills: MoneyPill[] = [];
  if (flags.has("OVERDUE")) {
    pills.push({ key: "overdueFlag", tone: MONEY_TONE.overdueFlag, amount: null });
  }
  if (flags.has("PENDING_APPROVAL")) {
    pills.push({ key: "pending", tone: MONEY_TONE.pending, amount: null });
  }
  if (flags.has("NO_PAYMENT")) {
    pills.push({ key: "noPayment", tone: MONEY_TONE.noPayment, amount: null });
  } else if (owed > 0) {
    pills.push({ key: "owed", tone: MONEY_TONE.owed, amount: owed });
  }
  if (owed === 0 && (row.status === "ONBOARDED" || row.status === "CLOSED")) {
    pills.push({ key: "paid", tone: MONEY_TONE.paid, amount: null });
  }
  return pills;
}

export function toNumber(value: number | string | null | undefined): number {
  if (value === null || value === undefined || value === "") {
    return 0;
  }
  const n = typeof value === "number" ? value : Number(value);
  return Number.isFinite(n) ? n : 0;
}

/** The next due that is still owed: the one the Record payment dialog is prefilled with. */
export function nextOwedDue(dues: DueView[]): DueView | null {
  return dues.find((due) => toNumber(due.owed) > 0) ?? null;
}

/** The days a due is past its date, counted in Dhaka calendar days. Zero when it is not late. */
export function daysLate(dueOn: string, today: string): number {
  const due = Date.parse(`${dueOn}T00:00:00Z`);
  const now = Date.parse(`${today}T00:00:00Z`);
  if (Number.isNaN(due) || Number.isNaN(now) || now <= due) {
    return 0;
  }
  return Math.round((now - due) / 86_400_000);
}

// ---- payments ----------------------------------------------------------------------------------------------------------

/** Each payment status, with the tone its pill uses on the fee screens. */
export const PAYMENT_TONE: Record<PaymentStatus, PillTone> = {
  PENDING: "accent",
  APPROVED: "ok",
  REJECTED: "danger",
  VOIDED: "muted",
};

// ---- setup -------------------------------------------------------------------------------------------------------------

/** Adds whole calendar months to a yyyy-MM-dd date. A day that the month does not have falls on its last day. */
export function addMonths(date: string, months: number): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date);
  if (!match) {
    return date;
  }
  const year = Number(match[1]);
  const monthIndex = Number(match[2]) - 1 + months;
  const day = Number(match[3]);
  const targetYear = year + Math.floor(monthIndex / 12);
  const targetMonth = ((monthIndex % 12) + 12) % 12;
  const lastDay = new Date(Date.UTC(targetYear, targetMonth + 1, 0)).getUTCDate();
  const iso = (n: number) => String(n).padStart(2, "0");
  return `${targetYear}-${iso(targetMonth + 1)}-${iso(Math.min(day, lastDay))}`;
}

/** The first due date a desk admission gets by default: one month after it was admitted (plan D12). */
export function defaultFirstDue(admittedOn: string): string {
  return addMonths(admittedOn, 1);
}

/**
 * True when a payment may be taken before onboarding (E3): the form is in (submitted, sent back or ready) and the setup has a batch and a
 * course fee above zero. The same rule as the API's.
 */
export function payableBeforeOnboarding(admission: {
  state: string;
  setup: { batchId: string | null; courseFee: number | string | null };
}): boolean {
  return (
    ["SUBMITTED", "CHANGES_REQUESTED", "READY"].includes(admission.state) &&
    Boolean(admission.setup.batchId) &&
    toNumber(admission.setup.courseFee) > 0
  );
}

/** The most instalments a course fee may be paid in, as the API allows (course-fee-plan.md). */
export const MAX_INSTALLMENTS = 24;

/**
 * How many monthly instalments fit from the first due date to the course end: the same day each month, clamped to the month's end, as
 * the API counts them. At least 1 and at most {@link MAX_INSTALLMENTS}; 1 when a date is missing.
 */
export function fittingInstallments(firstDueOn: string, courseEndsOn: string): number {
  if (!firstDueOn || !courseEndsOn) {
    return 1;
  }
  let count = 0;
  while (count < MAX_INSTALLMENTS && addMonths(firstDueOn, count) <= courseEndsOn) {
    count += 1;
  }
  return Math.max(1, count);
}

/**
 * The instalments of a course fee, as the onboarding writes them (decision C3): one a month from the first due date, whole taka for
 * each but the last, which takes the rest. ৳7,000 in 6 is ৳1,166 five times, then ৳1,170. Empty without a date, a count or a fee.
 */
export function installmentPlan(firstDueOn: string, count: number, courseFee: number): { seq: number; dueOn: string; amount: number }[] {
  if (!firstDueOn || !(count >= 1) || !Number.isFinite(courseFee) || courseFee <= 0) {
    return [];
  }
  const totalPaisa = Math.round(courseFee * 100);
  const eachPaisa = Math.floor(totalPaisa / count / 100) * 100;
  const rows: { seq: number; dueOn: string; amount: number }[] = [];
  for (let index = 0; index < count; index += 1) {
    const last = index === count - 1;
    rows.push({ seq: index + 1, dueOn: addMonths(firstDueOn, index), amount: (last ? totalPaisa - eachPaisa * (count - 1) : eachPaisa) / 100 });
  }
  return rows;
}

// ---- where an admission stands -----------------------------------------------------------------------------------------

/** The stage an admission is in. A closed admission counts as onboarded. */
export function progressIndex(state: AdmissionState): number {
  switch (state) {
    case "AWAITING_FORM":
      return 0;
    case "SUBMITTED":
    case "CHANGES_REQUESTED":
      return 1;
    case "READY":
      return 2;
    case "ONBOARDED":
    case "CLOSED":
      return 3;
  }
}

/** Once onboarded, the setup is the fixed record: the admission shows tabs, not the setup panel. */
export function isAfterOnboarding(state: AdmissionState): boolean {
  return state === "ONBOARDED" || state === "CLOSED";
}

/**
 * Who the admission waits for, from the state and the role: the student (to fill in or correct the form), the review by staff,
 * the Master Admin's onboarding (staff are told to wait for the Master Admin), or nothing (the screen shows its own action).
 */
export function waitingFor(
  state: AdmissionState,
  role: Role | null | undefined,
): "student" | "review" | "onboard" | "masterAdmin" | "none" {
  if (state === "AWAITING_FORM" || state === "CHANGES_REQUESTED") {
    return "student";
  }
  if (state === "SUBMITTED") {
    return "review";
  }
  if (state === "READY") {
    return role === "MASTER_ADMIN" ? "onboard" : "masterAdmin";
  }
  return "none";
}

/** The form's fields in the groups the read-only view shows them in (plan 6.3). */
export const FORM_GROUPS: { key: "personal" | "education" | "contact" | "addresses"; fields: (keyof AdmissionForm)[] }[] = [
  { key: "personal", fields: ["fullName", "fatherName", "motherName", "dateOfBirth", "gender", "nid"] },
  { key: "education", fields: ["university", "semesterBatch"] },
  { key: "contact", fields: ["mobile", "emergencyMobile", "email"] },
  { key: "addresses", fields: ["hallMess", "presentAddress", "permanentAddress"] },
];

/** Today as yyyy-MM-dd in Dhaka: the default paid-on date of a payment (plan 4.3 uses Dhaka dates). */
export function dhakaToday(now: number = Date.now()): string {
  const parts = dhakaParts(now);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${parts.year}-${pad(parts.month + 1)}-${pad(parts.day)}`;
}
