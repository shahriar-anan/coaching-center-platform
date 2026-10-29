import type { MyOrder, OrderFilter, PaymentStatus } from "@/lib/api/exam/account";
import { examPath, modulePath } from "@/components/student/catalog/catalog-helpers";

/** The filters in the URL (Phase 4, S4.6). An empty string means "not set". */
export interface PaymentFilters {
  status: "" | PaymentStatus;
  scope: "" | "MODULE" | "EXAM" | "BUNDLE";
  from: string;
  to: string;
  examId: string;
  moduleId: string;
}

export const NO_FILTERS: PaymentFilters = { status: "", scope: "", from: "", to: "", examId: "", moduleId: "" };

const STATUSES: readonly PaymentStatus[] = ["PENDING", "PAID", "FAILED", "CANCELLED"];
const SCOPES: readonly PaymentFilters["scope"][] = ["", "MODULE", "EXAM", "BUNDLE"];

/** A value from the URL, kept only when it is one the filter knows. */
export function parseStatusFilter(value: string): PaymentFilters["status"] {
  return (STATUSES as readonly string[]).includes(value) ? (value as PaymentStatus) : "";
}

export function parseScopeFilter(value: string): PaymentFilters["scope"] {
  return (SCOPES as readonly string[]).includes(value) ? (value as PaymentFilters["scope"]) : "";
}

/** The API filter for the chosen values (P1). Dates are Bangladesh days; the API reads them so. */
export function toOrderFilter(filters: PaymentFilters): OrderFilter {
  return {
    status: filters.status,
    scope: filters.scope,
    from: filters.from,
    to: filters.to,
    examId: filters.examId,
    moduleId: filters.moduleId,
  };
}

/** True when any filter is set, so "Clear filters" has something to clear. */
export function hasFilters(filters: PaymentFilters): boolean {
  return Object.values(filters).some((value) => value !== "");
}

/**
 * What a row's actions are, by its status (plan S4.6): a paid order gets its receipt and the item; a pending order can be
 * paid or cancelled; a failed or cancelled one can be bought again.
 */
export type PaymentAction = "downloadPdf" | "printReceipt" | "open" | "continue" | "cancel" | "buyAgain";

export function actionsFor(status: PaymentStatus): PaymentAction[] {
  if (status === "PAID") return ["downloadPdf", "printReceipt", "open"];
  if (status === "PENDING") return ["continue", "cancel"];
  return ["buyAgain"];
}

/** The page of the item the order is for: the exam for an exam order, the package for a package or bundle order. */
export function itemHref(order: Pick<MyOrder, "scope" | "moduleId" | "examId">): string | null {
  if (order.scope === "EXAM") return order.examId ? examPath(order.examId) : null;
  return order.moduleId ? modulePath("mcq", order.moduleId) : null;
}

/** The public status word, as the page shows it (the translation key). */
export function statusKey(status: PaymentStatus): string {
  return `account.payments.status${status.charAt(0)}${status.slice(1).toLowerCase()}`;
}

/** The date a row shows: the paid time for a paid order, the created time otherwise (P2). */
export function rowDate(order: Pick<MyOrder, "paidAt" | "createdAt">): string {
  return order.paidAt ?? order.createdAt;
}

/** Whether a row has a discount to show: the struck list price and the percent (R19). */
export function hasDiscount(order: Pick<MyOrder, "listPrice" | "discountPercent" | "amount">): boolean {
  return order.listPrice !== null && order.discountPercent !== null && order.listPrice > order.amount;
}
