/**
 * The rules the Installments screens show (plan direct-enrolment-installments, sections 4 and 8). Pure: no React and no fetch,
 * so the list, the detail, the two dialogs and the tests read the same rules.
 *
 * Money is whole paisa for every sum. The API sends taka as JSON numbers; they are converted once, here. Dates are Dhaka
 * calendar dates as YYYY-MM-DD. A reference is shown in full to the Master Admin, who checks it against bKash or the bank; it is
 * never put in a URL or a log.
 */
import {
  addMonths,
  cashReference,
  isDate,
  isValidReference,
  paisaToInput,
  paisaToNumber,
  parseMoney,
  type PaymentMethod,
} from "@/components/staff/enrol/payment-model";
import type { PillTone } from "@/components/ui/pill";
import type { InstallmentRow, InstallmentStatus, InstallmentSummary } from "@/lib/api/exam/staff-installments";

/** The most installments a plan keeps, counting the paid ones (the API's limit, plan section 4). */
export const MAX_PLAN_ROWS = 24;

/** The filter chips of the list. ALL shows every plan. */
export const INSTALLMENT_FILTERS = ["ALL", "OVERDUE", "DUE_SOON", "COMPLETED", "ACCESS_REVOKED"] as const;
export type InstallmentFilter = (typeof INSTALLMENT_FILTERS)[number];

/** The list's filter from the URL. Anything unknown is ALL. */
export function filterFromParam(value: string | null | undefined): InstallmentFilter {
  return (INSTALLMENT_FILTERS as readonly string[]).includes(value ?? "") ? (value as InstallmentFilter) : "ALL";
}

/** The API status a filter asks for, or undefined when it asks for every plan. */
export function statusForFilter(filter: InstallmentFilter): InstallmentStatus | undefined {
  return filter === "ALL" ? undefined : filter;
}

/** The URL keys that narrow the list to one student, exam or package (the links from other screens, D18). */
export const SCOPE_KEYS = ["studentId", "examId", "moduleId"] as const;
export type ScopeKey = (typeof SCOPE_KEYS)[number];

/** Which narrowing filters are set. An empty value is not set. */
export function activeScopes(values: Partial<Record<ScopeKey, string | null>>): ScopeKey[] {
  return SCOPE_KEYS.filter((key) => (values[key] ?? "") !== "");
}

const STATUS_TONE: Partial<Record<InstallmentStatus, PillTone>> = {
  ACTIVE: "accent",
  DUE_SOON: "warn",
  OVERDUE: "danger",
  COMPLETED: "ok",
  ACCESS_REVOKED: "muted",
};

/** The pill tone of a plan status. A status the screens do not know reads as muted. */
export function statusTone(status: InstallmentStatus): PillTone {
  return STATUS_TONE[status] ?? "muted";
}

/** The copy key of a plan status, under `staffAdmin.installments.status`. */
export function statusKey(status: InstallmentStatus): string {
  return `staffAdmin.installments.status.${status}`;
}

/** The summary band's figures. Each is null while the summary is loading, so the band can show a placeholder. */
export type SummaryFigures = { totalDue: number | null; overdue: number | null; dueSoon: number | null };

export function summaryFigures(summary: InstallmentSummary | null | undefined): SummaryFigures {
  if (!summary) {
    return { totalDue: null, overdue: null, dueSoon: null };
  }
  return { totalDue: summary.totalDue, overdue: summary.overdueCount, dueSoon: summary.dueSoonCount };
}

/** Taka as whole paisa. Taka from the API have at most two decimals, so the rounding is exact. */
export function toPaisa(taka: number): number {
  return Math.round(taka * 100);
}

/** One row of a schedule for display. `isNext` marks the oldest row that is not fully paid. */
export type ScheduleLine = InstallmentRow & { isNext: boolean };

/**
 * The schedule with the "next" row marked. The state of each row (paid, part paid, due, overdue, upcoming) comes from the API;
 * the next one is the oldest row with something still owed, which can be overdue as well as due.
 */
export function scheduleLines(rows: InstallmentRow[]): ScheduleLine[] {
  const next = rows.find((row) => toPaisa(row.uncovered) > 0);
  return rows.map((row) => ({ ...row, isNext: next !== undefined && row.seq === next.seq }));
}

/**
 * How many rows, counted from the first, are fully paid. Those rows cannot change in a schedule change. The API counts the
 * same way: a row is covered once the payments reach it.
 */
export function frozenCount(rows: InstallmentRow[]): number {
  let count = 0;
  for (const row of rows) {
    if (toPaisa(row.uncovered) > 0) {
      break;
    }
    count += 1;
  }
  return count;
}

/**
 * The amount a new payment starts at: the next installment's uncovered amount, never more than the whole amount still due.
 * A plan with nothing due suggests zero.
 */
export function suggestedPaymentPaisa(input: { due: number; nextDueAmount?: number | null }): number {
  const due = toPaisa(input.due);
  const next = input.nextDueAmount === null || input.nextDueAmount === undefined ? due : toPaisa(input.nextDueAmount);
  return Math.max(0, Math.min(next, due));
}

/**
 * A cash reference that no payment on the plan already uses (plan C4, E33). The number goes up until one is free, so two cash
 * payments on one plan on one day get different references. Case and outer spaces do not count, as in the API.
 */
export function freeCashReference(itemId: string, paidOn: string, takenReferences: readonly string[]): string {
  const taken = new Set(takenReferences.map((reference) => reference.trim().toUpperCase()));
  let number = 1;
  while (taken.has(cashReference(itemId, paidOn, number).toUpperCase())) {
    number += 1;
  }
  return cashReference(itemId, paidOn, number);
}


/** The fields of the record-payment form, as typed. */
export type RecordForm = {
  amountText: string;
  method: PaymentMethod;
  referenceText: string;
  paidOnText: string;
  note: string;
};

export type RecordField = "amount" | "reference" | "paidOn" | "note";

/** The error code per field. The amount codes are the payment-model codes, plus `over`; the reference codes are as listed. */
export type RecordErrors = Partial<Record<RecordField, string>>;

export type RecordCheck =
  | { ok: true; amountPaisa: number; reference: string; note: string | null }
  | { ok: false; errors: RecordErrors };

/**
 * Checks a payment before it is sent. The amount may not be more than the whole amount still due. The reference is required,
 * printable ASCII, and new on this plan (ignoring case and outer spaces). The date is a real date, not in the future in Dhaka.
 */
export function checkRecordPayment(
  form: RecordForm,
  context: { duePaisa: number; today: string; takenReferences: readonly string[] },
): RecordCheck {
  const errors: RecordErrors = {};
  let amountPaisa = 0;
  const amount = parseMoney(form.amountText);
  if (!amount.ok) {
    errors.amount = amount.code;
  } else if (amount.paisa > context.duePaisa) {
    errors.amount = "over";
  } else {
    amountPaisa = amount.paisa;
  }

  const reference = form.referenceText.trim();
  const upper = reference.toUpperCase();
  if (!reference) {
    errors.reference = "required";
  } else if (!isValidReference(reference)) {
    errors.reference = "referenceInvalid";
  } else if (context.takenReferences.some((taken) => taken.trim().toUpperCase() === upper)) {
    errors.reference = "referenceTaken";
  }

  if (!isDate(form.paidOnText)) {
    errors.paidOn = "dateInvalid";
  } else if (form.paidOnText > context.today) {
    errors.paidOn = "dateFuture";
  }

  const note = form.note.trim();
  if (note.length > 500) {
    errors.note = "tooLong";
  }

  if (Object.keys(errors).length > 0) {
    return { ok: false, errors };
  }
  return { ok: true, amountPaisa, reference, note: note || null };
}

/** One unpaid row as the schedule form holds it, before it is checked. */
export type EditableRow = { amountText: string; dueOn: string };

/** A row that is paid and cannot change, in paisa. */
export type FrozenLine = { paisa: number; dueOn: string };

export type ScheduleError = { row?: number; code: "required" | "money" | "moneyDecimals" | "moneyPositive" | "moneyTooLarge" | "dateInvalid" | "datePast" | "dateOrder" | "count" | "total" };

export type ScheduleCheck =
  | { ok: true; schedule: { amount: number; dueOn: string }[] }
  | { ok: false; errors: ScheduleError[] };

/**
 * Checks a schedule change before it is sent. The whole schedule is the paid rows, unchanged, then the unpaid rows the admin
 * wants. The rows must add up to the agreed total, the dates must rise, no unpaid date may be in the past, and the count must
 * fit the plan's limit (the API's rules, plan section 4).
 */
export function checkScheduleChange(input: {
  totalPaisa: number;
  frozen: FrozenLine[];
  rows: EditableRow[];
  today: string;
}): ScheduleCheck {
  const errors: ScheduleError[] = [];
  if (input.rows.length === 0 || input.frozen.length + input.rows.length > MAX_PLAN_ROWS) {
    errors.push({ code: "count" });
  }

  let previous: string | null = input.frozen.length > 0 ? input.frozen[input.frozen.length - 1].dueOn : null;
  let sum = input.frozen.reduce((total, line) => total + line.paisa, 0);
  const amounts: number[] = [];
  input.rows.forEach((row, index) => {
    const amount = parseMoney(row.amountText);
    if (amount.ok) {
      sum += amount.paisa;
      amounts.push(amount.paisa);
    } else {
      errors.push({ row: index, code: amount.code });
    }
    if (!isDate(row.dueOn)) {
      errors.push({ row: index, code: "dateInvalid" });
      return;
    }
    if (row.dueOn < input.today) {
      errors.push({ row: index, code: "datePast" });
    }
    if (previous !== null && row.dueOn <= previous) {
      errors.push({ row: index, code: "dateOrder" });
    }
    previous = row.dueOn;
  });

  if (errors.length === 0 && sum !== input.totalPaisa) {
    errors.push({ code: "total" });
  }
  if (errors.length > 0) {
    return { ok: false, errors };
  }
  return {
    ok: true,
    schedule: [
      ...input.frozen.map((line) => ({ amount: paisaToNumber(line.paisa), dueOn: line.dueOn })),
      ...input.rows.map((row, index) => ({ amount: paisaToNumber(amounts[index]), dueOn: row.dueOn })),
    ],
  };
}

/** The unpaid rows as the schedule form starts them: each row's amount and date, as the plan has them now. */
export function editableRowsFrom(schedule: InstallmentRow[]): EditableRow[] {
  return schedule.slice(frozenCount(schedule)).map((row) => ({ amountText: paisaToInput(toPaisa(row.amount)), dueOn: row.dueOn }));
}

/** The paid rows as the schedule check needs them. */
export function frozenLinesFrom(schedule: InstallmentRow[]): FrozenLine[] {
  return schedule.slice(0, frozenCount(schedule)).map((row) => ({ paisa: toPaisa(row.amount), dueOn: row.dueOn }));
}

/** The date a newly added unpaid row starts from: one month after the row before it, or one month after `fallback` when there is none. */
export function nextRowDate(rows: EditableRow[], fallback: string): string {
  const last = rows.length > 0 ? rows[rows.length - 1].dueOn : fallback;
  return isDate(last) ? addMonths(last, 1) : fallback;
}
