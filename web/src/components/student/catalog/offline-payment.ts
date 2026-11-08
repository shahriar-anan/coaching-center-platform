/**
 * The rules for the Offline purchases cards and the payment panel (plan direct-enrolment-installments, section 8). Pure: no React and
 * no fetch, so the screens and the tests read the same rules. Every field may be left out by the API, so each read goes through
 * `isMissing`, `optionalAmount` or `optionalDate`.
 */
import type { PillTone } from "@/components/ui/pill";
import type { OfflinePayment } from "@/lib/api/exam/account";
import type { StudentPayment } from "@/lib/api/exam/student-installments";
import { isMissing } from "@/lib/api/missing";

/** The plan statuses the API sends (plan section 4). */
export const PLAN_STATUSES = ["ACTIVE", "DUE_SOON", "OVERDUE", "COMPLETED", "ACCESS_REVOKED"] as const;
export type PlanStatus = (typeof PLAN_STATUSES)[number];

/** A pill: its tone, and the copy key of its word. */
export type CopyPill = { tone: PillTone; key: string };

/** Overdue and due soon share the warning tone, as the notices do (plan section 8). */
const STATUS_TONE: Record<PlanStatus, PillTone> = {
  ACTIVE: "accent",
  DUE_SOON: "warn",
  OVERDUE: "warn",
  COMPLETED: "ok",
  ACCESS_REVOKED: "muted",
};

/** The pill for a plan status. Null when the status is left out or is one this page does not know, so no pill is shown. */
export function planStatusPill(status: string | null | undefined): CopyPill | null {
  if (isMissing(status) || !(PLAN_STATUSES as readonly string[]).includes(status)) {
    return null;
  }
  return { tone: STATUS_TONE[status as PlanStatus], key: `account.payments.offline.status.${status}` };
}

/** The states a schedule row can be in (plan section 4). */
export const SCHEDULE_STATES = ["PAID", "PART_PAID", "DUE", "OVERDUE", "UPCOMING"] as const;
export type ScheduleState = (typeof SCHEDULE_STATES)[number];

const SCHEDULE_STATE_TONE: Record<ScheduleState, PillTone> = {
  PAID: "ok",
  PART_PAID: "accent",
  DUE: "warn",
  OVERDUE: "warn",
  UPCOMING: "muted",
};

/** The pill for an installment row's state. Null for a state this page does not know. */
export function scheduleStatePill(state: string | null | undefined): CopyPill | null {
  if (isMissing(state) || !(SCHEDULE_STATES as readonly string[]).includes(state)) {
    return null;
  }
  return { tone: SCHEDULE_STATE_TONE[state as ScheduleState], key: `account.payments.offline.panel.state.${state}` };
}

/** The payment methods a direct payment can use. */
const METHODS = ["CASH", "BKASH", "NAGAD", "ROCKET", "BANK"] as const;

/** The copy key of a payment method, or null for one this page does not know. */
export function methodLabelKey(method: string | null | undefined): string | null {
  if (isMissing(method) || !(METHODS as readonly string[]).includes(method)) {
    return null;
  }
  return `account.payments.offline.panel.methods.${method}`;
}

/** A row that staff took the access back from. Its receipts stay, but it does not open the exam or package. */
export function isAccessRemoved(row: { accessRemoved?: boolean | null }): boolean {
  return row.accessRemoved === true;
}

/**
 * Whether a card's title links to the exam or package. A row whose access was removed does not, so it never reads as open
 * (plan section 8). Cards without a plan are never removed, so they keep their links.
 */
export function canOpenOfflineItem(row: { accessRemoved?: boolean | null }): boolean {
  return !isAccessRemoved(row);
}

/**
 * The pills at the top right of a card. A card with no plan shows the access end, as before. A card with a plan shows its status
 * too. A removed card shows no end date: its status says the access has ended, or that it is completed.
 */
export function offlinePills(row: { accessRemoved?: boolean | null; payment?: OfflinePayment | null }): {
  showAccessEnd: boolean;
  status: CopyPill | null;
} {
  const payment = isMissing(row.payment) ? null : row.payment;
  if (!payment) {
    return { showAccessEnd: true, status: null };
  }
  if (!isAccessRemoved(row)) {
    return { showAccessEnd: true, status: planStatusPill(payment.status) };
  }
  return {
    showAccessEnd: false,
    status: payment.status === "COMPLETED" ? planStatusPill("COMPLETED") : planStatusPill("ACCESS_REVOKED"),
  };
}

/**
 * A stable key for a card. An exam can appear twice (a revoked plan and a new one), so the plan is part of the key, with the grant
 * date when there is no plan.
 */
export function offlineRowKey(
  kind: "exam" | "package",
  id: string,
  row: { grantedAt?: string | null; payment?: OfflinePayment | null },
): string {
  const planId = isMissing(row.payment) ? undefined : row.payment.planId;
  return `${kind}-${id}-${planId ?? row.grantedAt ?? "none"}`;
}

/** Whether a payment has been voided. The API sends `voidedAt` only then. */
export function isVoided(payment: Pick<StudentPayment, "voidedAt">): boolean {
  return Boolean(payment.voidedAt);
}

/** Whether a plan has nothing left to pay. A missing amount is not settled, so the screen does not claim it is. */
export function isSettled(payment: Pick<OfflinePayment, "due">): boolean {
  return !isMissing(payment.due) && payment.due === 0;
}
