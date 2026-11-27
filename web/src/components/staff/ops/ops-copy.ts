import type { PillTone } from "@/components/ui/pill";
import type { OrderKind, OrderStatus } from "@/lib/api/exam/types";
import type { StaffOrderRow } from "@/lib/api/exam/staff-ops";

/**
 * Pure mapping from API words to the words staff read on screen. The API keeps
 * `INITIATED`, `SUCCEEDED`, `FAILED` and `CANCELLED`; the screen says অপেক্ষমাণ,
 * পরিশোধিত, ব্যর্থ and বাতিল, and the status is always a word, never a colour alone.
 */

export type PaymentStatusKey = "pending" | "paid" | "failed" | "cancelled";

export function paymentStatusKey(status: OrderStatus): PaymentStatusKey {
  switch (status) {
    case "INITIATED":
      return "pending";
    case "SUCCEEDED":
      return "paid";
    case "FAILED":
      return "failed";
    case "CANCELLED":
      return "cancelled";
  }
}

export function paymentStatusTone(key: PaymentStatusKey): PillTone {
  switch (key) {
    case "pending":
      return "warn";
    case "paid":
      return "ok";
    case "failed":
      return "danger";
    case "cancelled":
      return "muted";
  }
}

/**
 * The refund chip on a row (plan 5.3): "Refunded" when the whole amount came back, "Partly refunded" for less. Null when no
 * refund was recorded. The status stays paid; the chip is the only sign on the list.
 */
export function refundChipKey(row: Pick<StaffOrderRow, "refund" | "amount">): "refunded" | "partlyRefunded" | null {
  if (!row.refund) {
    return null;
  }
  return row.refund.amount >= row.amount ? "refunded" : "partlyRefunded";
}

/** The access line on a payment row. Only a paid order grants access. */
export type AccessCopyKey = "awaitingGateway" | "wholeModule" | "thisExam" | "noAccess";

export function accessCopyKey(order: Pick<StaffOrderRow, "status" | "scope">): AccessCopyKey {
  if (order.status === "INITIATED") {
    return "awaitingGateway";
  }
  if (order.status === "SUCCEEDED") {
    return order.scope === "MODULE" ? "wholeModule" : "thisExam";
  }
  return "noAccess";
}

export function scopeKey(scope: OrderKind): "module" | "exam" {
  return scope === "MODULE" ? "module" : "exam";
}

/** The status filter options, in the order of the spec. The empty value means every status. */
export const PAYMENT_STATUS_FILTERS: { value: "" | OrderStatus; key: "all" | PaymentStatusKey }[] = [
  { value: "", key: "all" },
  { value: "INITIATED", key: "pending" },
  { value: "SUCCEEDED", key: "paid" },
  { value: "FAILED", key: "failed" },
  { value: "CANCELLED", key: "cancelled" },
];

/**
 * Audit action codes the screen translates. Anything else shows the stored code,
 * so a new action is visible before its label is written.
 */
const AUDIT_ACTIONS = new Set([
  "USER_DELETED",
  "USER_STATUS_CHANGED",
  "ACTIVATION_CODE_ISSUED",
  "PASSWORD_RESET",
  "PASSWORD_CHANGED",
  "PHONE_CHANGED",
  "GOOGLE_ACCOUNT_CREATED",
  "GOOGLE_ACCOUNT_LINKED",
  "PAYMENT_SUCCESS",
  "PAYMENT_FAILED",
  "LIVE_EXAM_CATEGORY_DELETE",
  "LIVE_EXAM_CATEGORY_ARCHIVE",
  "LIVE_EXAM_MODULE_DELETE",
  "LIVE_EXAM_MODULE_ARCHIVE",
  "LIVE_EXAM_ARCHIVE",
  "LIVE_EXAM_RESTORE",
  "LIVE_EXAM_DELETE",
  "LIVE_EXAM_QUESTION_DELETE",
  "LIVE_EXAM_INSTRUCTOR_UNASSIGN",
  "ACC_GROUP_CREATED",
  "ACC_GROUP_UPDATED",
  "ACC_GROUP_ARCHIVED",
  "ACC_GROUP_RESTORED",
  "ACC_TYPE_CREATED",
  "ACC_TYPE_UPDATED",
  "ACC_TYPE_ARCHIVED",
  "ACC_TYPE_RESTORED",
  "ACC_PAYEE_CREATED",
  "ACC_PAYEE_UPDATED",
  "ACC_PAYEE_ARCHIVED",
  "ACC_PAYEE_RESTORED",
  "ACC_ACCOUNT_CREATED",
  "ACC_ACCOUNT_UPDATED",
  "ACC_ACCOUNT_ARCHIVED",
  "ACC_ACCOUNT_RESTORED",
  "ACC_ENTRY_CREATED",
  "ACC_ENTRIES_CREATED",
  "ACC_ENTRY_EDITED",
  "ACC_ENTRY_VOIDED",
]);

/** The i18n key for an audit action, or null when the screen should show the stored code. */
export function auditActionKey(action: string): string | null {
  return AUDIT_ACTIONS.has(action) ? `staffOps.audit.action.${action}` : null;
}
