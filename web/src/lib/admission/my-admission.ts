import type { AdmissionState } from "@/lib/api/exam/offline-admission";
import type { StudentDue, StudentPayment } from "@/lib/api/exam/my-admission";

/*
 * Rules for My admission (plan 6.4), as pure functions so the page, the links and the tests share one answer.
 */

/**
 * The links to My admission (the home card, the account menu, the account row) show only for an onboarded admission.
 * A pending admission is sent to the form instead (plan 4.5), and a closed one has no links (plan 6.4).
 */
export function showsMyAdmissionLinks(state: AdmissionState | null | undefined): boolean {
  return state === "ONBOARDED";
}

/** The earliest unpaid due, in schedule order, or null when every due is paid. */
export function nextDueOf(dues: readonly StudentDue[]): StudentDue | null {
  const open = dues.filter((due) => due.owed > 0).sort((a, b) => a.seq - b.seq);
  return open[0] ?? null;
}

/**
 * The one overdue line (D21): the total past-due amount and the earliest date one of them was due. Null when nothing is
 * overdue.
 */
export function overdueLineOf(schedule: { overdue: number; dues: readonly StudentDue[] }): { amount: number; dueOn: string } | null {
  if (!(schedule.overdue > 0)) {
    return null;
  }
  const late = schedule.dues.filter((due) => due.overdue).sort((a, b) => a.dueOn.localeCompare(b.dueOn));
  if (late.length === 0) {
    return null;
  }
  return { amount: schedule.overdue, dueOn: late[0].dueOn };
}

/** The payments the student may see: approved ones only. */
export function visiblePayments(payments: readonly StudentPayment[]): StudentPayment[] {
  return payments.filter((payment) => payment.status === undefined || payment.status === "APPROVED");
}
