import type { PillTone } from "@/components/ui/pill";
import type { InstructorPayment, PayKind, PayMethod, PaymentStatus } from "@/lib/api/exam/instructor-pay";

/*
 * Small helpers for the instructor pay screens (24 Nov, instructor-pay-simple-plan.md). Message keys are written out in full so the
 * catalogue check can find them.
 */

export const PAY_KINDS: PayKind[] = ["LECTURE", "EXAM", "OTHER"];

export const PAY_METHODS: PayMethod[] = ["CASH", "BKASH", "NAGAD", "ROCKET", "BANK"];

export const STATUS_TONE: Record<PaymentStatus, PillTone> = { PENDING: "accent", APPROVED: "ok", REJECTED: "danger", VOIDED: "muted" };

export function kindKey(kind: PayKind): string {
  switch (kind) {
    case "LECTURE":
      return "instructorPay.kinds.LECTURE";
    case "EXAM":
      return "instructorPay.kinds.EXAM";
    default:
      return "instructorPay.kinds.OTHER";
  }
}

export function statusKey(status: PaymentStatus): string {
  switch (status) {
    case "PENDING":
      return "instructorPay.history.status.PENDING";
    case "APPROVED":
      return "instructorPay.history.status.APPROVED";
    case "REJECTED":
      return "instructorPay.history.status.REJECTED";
    default:
      return "instructorPay.history.status.VOIDED";
  }
}

/** "4 lectures", "1 exam", "3 classes": the number of classes with the word its type takes. Null when no number was given. */
export function classCountText(
  payment: Pick<InstructorPayment, "kind" | "classCount">,
  t: (key: string, params?: Record<string, string | number>) => string,
  formatNumber: (value: number) => string,
): string | null {
  if (payment.classCount == null) {
    return null;
  }
  if (payment.classCount === 1) {
    return t(
      payment.kind === "LECTURE"
        ? "instructorPay.classCount.one.LECTURE"
        : payment.kind === "EXAM"
          ? "instructorPay.classCount.one.EXAM"
          : "instructorPay.classCount.one.OTHER",
    );
  }
  const key =
    payment.kind === "LECTURE"
      ? "instructorPay.classCount.LECTURE"
      : payment.kind === "EXAM"
        ? "instructorPay.classCount.EXAM"
        : "instructorPay.classCount.OTHER";
  return t(key, { count: formatNumber(payment.classCount) });
}

/** The first and last day of the current Dhaka month (yyyy-MM-dd), for the default dates of the payments list. */
export function monthRange(today: string): { from: string; to: string } {
  const [year, month] = today.split("-").map(Number);
  const last = new Date(Date.UTC(year, month, 0)).getUTCDate();
  const pad = (n: number) => String(n).padStart(2, "0");
  return { from: `${year}-${pad(month)}-01`, to: `${year}-${pad(month)}-${pad(last)}` };
}
