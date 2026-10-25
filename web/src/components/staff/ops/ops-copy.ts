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
  "PHONE_CHANGED",
  "GOOGLE_ACCOUNT_CREATED",
  "GOOGLE_ACCOUNT_LINKED",
  "PAYMENT_SUCCESS",
  "PAYMENT_FAILED",
  "LIVE_EXAM_CATEGORY_DELETE",
  "LIVE_EXAM_CATEGORY_ARCHIVE",
  "LIVE_EXAM_MODULE_DELETE",
  "LIVE_EXAM_MODULE_ARCHIVE",
  "LIVE_EXAM_DELETE",
  "LIVE_EXAM_QUESTION_DELETE",
  "LIVE_EXAM_INSTRUCTOR_UNASSIGN",
]);

/** The i18n key for an audit action, or null when the screen should show the stored code. */
export function auditActionKey(action: string): string | null {
  return AUDIT_ACTIONS.has(action) ? `staffOps.audit.action.${action}` : null;
}
