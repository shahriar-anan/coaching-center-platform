import { dhakaToday, toNumber } from "@/components/staff/offline/offline-model";
import type { ClassRow } from "@/lib/api/exam/instructor-pay";

/*
 * Pure rules for the offline instructor pay screens: the groups by instructor, the figures on the cards and the per-instructor
 * totals, the history filter, and which ids an approval sends. No React here, so the rules can be tested on their own.
 */

/** The one-tap reasons for "Mark not taken". Each key has a sentence in the instructorPay catalogue. */
export const NOT_TAKEN_REASONS = ["instructorAbsent", "classCancelled", "holiday"] as const;
export type NotTakenReason = (typeof NOT_TAKEN_REASONS)[number];

/** A class is held once its end time has passed; the API computes it, so the screens only read the status. */
export function isHeld(row: ClassRow): boolean {
  return row.status === "HELD";
}

/** A class waiting for the Master Admin: a payment or a not-taken mark that a staff member recorded. */
export function isWaiting(row: ClassRow): boolean {
  return row.status === "PAID_PENDING" || row.status === "NOT_TAKEN_PENDING";
}

/** The amount due on the class (its rate or its override), or 0 when none is set. */
export function dueOf(row: ClassRow): number {
  return toNumber(row.amount);
}

/** A class has an amount when the API set a rate or an override. Payment is refused without one. */
export function hasAmount(row: ClassRow): boolean {
  return row.amount !== null && row.amount !== undefined && row.amount !== "";
}

/** The month of a yyyy-MM-dd date, as yyyy-MM. */
export function monthOf(date: string): string {
  return date.slice(0, 7);
}

/** This month in Dhaka, as yyyy-MM. */
export function currentMonth(now: number = Date.now()): string {
  return dhakaToday(now).slice(0, 7);
}

/** Classes in time order: by date, then by start time. */
export function byWhen(a: ClassRow, b: ClassRow): number {
  if (a.heldOn !== b.heldOn) {
    return a.heldOn < b.heldOn ? -1 : 1;
  }
  return (a.startsAt ?? "").localeCompare(b.startsAt ?? "");
}

/** A class's start as an ISO instant in Dhaka, for the time formatters. */
export function classStartIso(row: ClassRow): string {
  const time = (row.startsAt ?? "00:00").slice(0, 5);
  return `${row.heldOn}T${time}:00+06:00`;
}

/** A class's end as an ISO instant in Dhaka. A class with no end time ends where it starts. */
export function classEndIso(row: ClassRow): string {
  const time = (row.endsAt ?? row.startsAt ?? "00:00").slice(0, 5);
  return `${row.heldOn}T${time}:00+06:00`;
}

export type InstructorGroup = {
  /** The instructor's id, or null for classes with no instructor yet. */
  instructorId: string | null;
  /** The name to show: the instructor's name, else the name as the routine wrote it. */
  name: string;
  classes: ClassRow[];
  /** The amount due across the classes, in taka. */
  due: number;
};

/** The classes grouped by instructor, groups by name, classes in time order. Classes with no instructor come last. */
export function groupByInstructor(rows: ClassRow[]): InstructorGroup[] {
  const groups = new Map<string, InstructorGroup>();
  for (const row of rows) {
    const key = row.instructorId ?? "none";
    let group = groups.get(key);
    if (!group) {
      group = {
        instructorId: row.instructorId,
        name: row.instructorName ?? row.instructorText ?? "",
        classes: [],
        due: 0,
      };
      groups.set(key, group);
    }
    group.classes.push(row);
    group.due += dueOf(row);
  }
  for (const group of groups.values()) {
    group.classes.sort(byWhen);
  }
  return [...groups.values()].sort((a, b) => {
    if ((a.instructorId == null) !== (b.instructorId == null)) {
      return a.instructorId == null ? 1 : -1;
    }
    return a.name.localeCompare(b.name);
  });
}

/** The filters the history tab and the card links use; empty means no filter. */
export type HistoryFilters = {
  batchId?: string;
  instructorId?: string;
  from?: string;
  to?: string;
};

/**
 * The paid and not-taken classes, newest first. A paid class is dated by the day it was paid; a not-taken class by its class date.
 */
export function historyRows(rows: ClassRow[], filters: HistoryFilters): ClassRow[] {
  return rows
    .filter((row) => row.status === "PAID" || row.status === "NOT_TAKEN")
    .filter((row) => !filters.batchId || row.batchId === filters.batchId)
    .filter((row) => !filters.instructorId || row.instructorId === filters.instructorId)
    .filter((row) => {
      const date = historyDate(row);
      return (!filters.from || date >= filters.from) && (!filters.to || date <= filters.to);
    })
    .sort((a, b) => {
      const byDate = historyDate(b).localeCompare(historyDate(a));
      return byDate !== 0 ? byDate : byWhen(b, a);
    });
}

/** The date a history row is filed under. */
export function historyDate(row: ClassRow): string {
  return row.status === "PAID" && row.payment ? row.payment.paidOn : row.heldOn;
}

/** Paid classes this month: the approved payments dated this month, with their count and total. */
export function paidIn(rows: ClassRow[], month: string): { count: number; amount: number } {
  let count = 0;
  let amount = 0;
  for (const row of rows) {
    if (row.status === "PAID" && row.payment?.status === "APPROVED" && monthOf(row.payment.paidOn) === month) {
      count += 1;
      amount += toNumber(row.payment.amount);
    }
  }
  return { count, amount };
}

/** Classes marked not taken this month, by their class date. */
export function notTakenIn(rows: ClassRow[], month: string): number {
  return rows.filter((row) => row.status === "NOT_TAKEN" && monthOf(row.heldOn) === month).length;
}

/** What one instructor has across all batches: the batches they teach, this month's classes and the money. */
export type InstructorFigures = {
  batchIds: string[];
  classesThisMonth: number;
  /** Held classes not yet settled: the amount still owed. */
  owed: number;
  /** Approved payments. */
  paid: number;
  /** Payments waiting for the Master Admin. */
  pending: number;
};

/** The figures for each instructor, from the classes of every batch. Cancelled classes are not in the rows. */
export function instructorFigures(rows: ClassRow[], month: string): Map<string, InstructorFigures> {
  const figures = new Map<string, InstructorFigures>();
  for (const row of rows) {
    if (!row.instructorId) {
      continue;
    }
    let entry = figures.get(row.instructorId);
    if (!entry) {
      entry = { batchIds: [], classesThisMonth: 0, owed: 0, paid: 0, pending: 0 };
      figures.set(row.instructorId, entry);
    }
    if (!entry.batchIds.includes(row.batchId)) {
      entry.batchIds.push(row.batchId);
    }
    if (monthOf(row.heldOn) === month) {
      entry.classesThisMonth += 1;
    }
    if (row.status === "HELD") {
      entry.owed += dueOf(row);
    }
    if (row.status === "PAID" && row.payment?.status === "APPROVED") {
      entry.paid += toNumber(row.payment.amount);
    }
    if (row.status === "PAID_PENDING" && row.payment) {
      entry.pending += toNumber(row.payment.amount);
    }
  }
  return figures;
}

/**
 * The ids an approval sends. A waiting payment is approved by its payment id; a waiting not-taken mark by its decision id. A class
 * that waits for neither is left out.
 */
export function approvalIds(rows: ClassRow[]): { paymentIds: string[]; decisionIds: string[] } {
  const paymentIds: string[] = [];
  const decisionIds: string[] = [];
  for (const row of rows) {
    if (row.status === "PAID_PENDING" && row.payment) {
      paymentIds.push(row.payment.id);
    } else if (row.status === "NOT_TAKEN_PENDING" && row.notTakenRequest) {
      decisionIds.push(row.notTakenRequest.id);
    }
  }
  return { paymentIds, decisionIds };
}

/** The payment a waiting class carries, for the reject action. */
export function waitingPaymentId(row: ClassRow): string | null {
  return row.status === "PAID_PENDING" && row.payment ? row.payment.id : null;
}

/** The decision a waiting class carries, for the reject action. */
export function waitingDecisionId(row: ClassRow): string | null {
  return row.status === "NOT_TAKEN_PENDING" && row.notTakenRequest ? row.notTakenRequest.id : null;
}

/** The amount the payment dialog's note must explain: the difference between the amount paid and the amount due, in taka. */
export function amountDifference(paid: number, due: number): number {
  return Math.round((paid - due) * 100) / 100;
}
