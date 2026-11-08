/**
 * The money rules for a direct enrolment's payment (plan direct-enrolment-installments, sections 4 and 8). Pure: no React and
 * no fetch, so the form, its confirmation and the tests all read the same rules.
 *
 * Money is whole paisa everywhere. The text the admin types is parsed once, here, and refused when it has more than two
 * decimals (E15). Dates are Dhaka calendar dates as YYYY-MM-DD, compared as text and moved by whole months (E21).
 *
 * Each field of {@link PaymentDraft} is either a typed value or `null`. A `null` means "use the suggestion until the admin
 * types": the agreed total is the standard price, the payment date is today, the next date is a month after the payment,
 * the per-installment amount is the even split, and the cash reference is generated. So a suggestion follows the form
 * until it is typed over (plan section 8).
 */
import { dhakaParts } from "@/lib/format";

export type PaymentType = "FULL" | "INSTALLMENTS" | "NO_CHARGE";
export type PaymentMethod = "CASH" | "BKASH" | "NAGAD" | "ROCKET" | "BANK";
export type EnrolScope = "MODULE" | "EXAM";

export const PAYMENT_TYPES: readonly PaymentType[] = ["FULL", "INSTALLMENTS", "NO_CHARGE"];
export const PAYMENT_METHODS: readonly PaymentMethod[] = ["CASH", "BKASH", "NAGAD", "ROCKET", "BANK"];

/** The count includes today's payment (D11). */
export const MIN_INSTALLMENTS = 2;
export const MAX_INSTALLMENTS = 24;

/** The largest amount the database keeps: NUMERIC(12,2), so 9,999,999,999.99 in paisa. */
export const MAX_PAISA = 999_999_999_999;

/** Printable ASCII only. The receipt PDF prints Latin letters, so anything else would vanish from it (the API rule too). */
const REFERENCE = /^[\x20-\x7E]{1,100}$/;
const MAX_NOTE = 500;
const MAX_REASON = 500;
const DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

/** What the admin has typed or chosen. `null` means the suggestion (see the file comment). */
export type PaymentDraft = {
  type: PaymentType;
  total: string | null;
  /** The amount paid today. Used by Installments only; Full payment pays the whole total. */
  paid: string;
  paidOn: string | null;
  method: PaymentMethod;
  reference: string | null;
  note: string;
  /** The number of installments, including today's payment, as typed. */
  count: string;
  perInstallment: string | null;
  nextOn: string | null;
  /** The reason for a No charge enrolment. */
  reason: string;
};

export const EMPTY_DRAFT: PaymentDraft = {
  type: "FULL",
  total: null,
  paid: "",
  paidOn: null,
  method: "CASH",
  reference: null,
  note: "",
  count: "3",
  perInstallment: null,
  nextOn: null,
  reason: "",
};

/** The fields a check can name. The form shows the error code under the matching field. */
export type PaymentField =
  | "type"
  | "total"
  | "paid"
  | "paidOn"
  | "method"
  | "reference"
  | "note"
  | "count"
  | "perInstallment"
  | "nextOn"
  | "reason";

/** Field to error code. The codes are the keys under `staffAdmin.enrol.payment.error`. */
export type FieldErrors = Partial<Record<PaymentField, string>>;

/** One row of the schedule. Row 1 is today's payment. */
export type ScheduleRow = { seq: number; paisa: number; dueOn: string };

export type PaymentPlan = {
  type: "FULL" | "INSTALLMENTS";
  totalPaisa: number;
  paidPaisa: number;
  duePaisa: number;
  rows: ScheduleRow[];
  /** The first row after today's payment, or null for a plan paid in full. */
  nextRow: ScheduleRow | null;
  method: PaymentMethod;
  reference: string;
  paidOn: string;
  note: string | null;
};

export type Check = { ok: true; payment: PaymentPlan | null; reason: string } | { ok: false; errors: FieldErrors };

export type Context = {
  targetId: string;
  /** The standard price in paisa, or null when the target has none. */
  standardPaisa: number | null;
  /** A free exam takes only No charge. */
  free: boolean;
  /** Today in Dhaka, as YYYY-MM-DD. */
  today: string;
};

/** What the form shows: the effective text of each field, the difference from the standard price, and the check. */
export type Resolved = {
  type: PaymentType;
  totalText: string;
  paidText: string;
  paidOnText: string;
  referenceText: string;
  countText: string;
  perText: string;
  nextOnText: string;
  /** Total minus standard price in paisa, when both are known. */
  differencePaisa: number | null;
  /** Installments: the suggested amount per installment, shown while the field is not typed. */
  suggestedPerText: string;
  check: Check;
};

/** The `payment` part of `POST /api/v1/live-exam-grants`. Amounts are JSON numbers with at most two decimals. */
export type GrantPayment = {
  agreedTotal: number;
  amount: number;
  method: PaymentMethod;
  reference: string;
  paidOn: string;
  note: string | null;
  schedule: { amount: number; dueOn: string }[];
};

/** The body of `POST /api/v1/live-exam-grants`. The reason is sent empty when a payment is. */
export type GrantBody = {
  scope: EnrolScope;
  moduleId?: string;
  examId?: string;
  studentId: string;
  reason: string;
  payment?: GrantPayment;
};

export type Parsed =
  | { ok: true; paisa: number }
  | { ok: false; code: "required" | "money" | "moneyDecimals" | "moneyPositive" | "moneyTooLarge" };

/**
 * Reads an amount the admin typed, in taka, and returns whole paisa. Commas, spaces and Bengali digits are accepted. More than
 * two decimals are refused, never rounded (E15). Zero, negative and huge amounts are refused too.
 */
export function parseMoney(text: string | null | undefined): Parsed {
  const cleaned = (text ?? "")
    .replace(/[০-৯]/g, (digit) => String(digit.charCodeAt(0) - 0x09e6))
    .replace(/[,\s]/g, "");
  if (cleaned === "") {
    return { ok: false, code: "required" };
  }
  const match = /^(\d+)(?:\.(\d*))?$/.exec(cleaned);
  if (!match) {
    return { ok: false, code: "money" };
  }
  const whole = match[1];
  const fraction = match[2] ?? "";
  if (fraction.length > 2) {
    return { ok: false, code: "moneyDecimals" };
  }
  const paisa = Number(whole) * 100 + Number(fraction.padEnd(2, "0"));
  if (!Number.isSafeInteger(paisa) || paisa > MAX_PAISA) {
    return { ok: false, code: "moneyTooLarge" };
  }
  if (paisa <= 0) {
    return { ok: false, code: "moneyPositive" };
  }
  return { ok: true, paisa };
}

/** Whole paisa as the text of a money field: "6300" for a whole amount, "2100.50" otherwise. */
export function paisaToInput(paisa: number): string {
  const whole = Math.floor(paisa / 100);
  const fraction = paisa % 100;
  return fraction === 0 ? String(whole) : `${whole}.${pad2(fraction)}`;
}

/** Whole paisa as a JSON number for the API. Two decimals at most, so the number is exact. */
export function paisaToNumber(paisa: number): number {
  return paisa / 100;
}

/** The standard price from the quote, in paisa. The API may send a number or a string, and null when there is no price. */
export function quotePaisa(value: number | string | null | undefined): number | null {
  if (value === null || value === undefined || value === "") {
    return null;
  }
  const parsed = parseMoney(String(value));
  return parsed.ok ? parsed.paisa : null;
}

/** True for a real calendar date written YYYY-MM-DD. */
export function isDate(text: string | null | undefined): text is string {
  if (!text) {
    return false;
  }
  const match = DATE.exec(text);
  if (!match) {
    return false;
  }
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  return month >= 1 && month <= 12 && day >= 1 && day <= daysInMonth(year, month);
}

function daysInMonth(year: number, month: number): number {
  return new Date(Date.UTC(year, month, 0)).getUTCDate();
}

function pad2(value: number): string {
  return String(value).padStart(2, "0");
}

/**
 * The date moved by whole months. Each call starts from the date it is given, so the caller passes the first date and a count,
 * never a chain: 31 January plus 1 is 28 or 29 February, and plus 2 is 31 March (E21).
 */
export function addMonths(date: string, months: number): string {
  const [year, month, day] = date.split("-").map(Number);
  const index = year * 12 + (month - 1) + months;
  const nextYear = Math.floor(index / 12);
  const nextMonth = (index % 12) + 1;
  const nextDay = Math.min(day, daysInMonth(nextYear, nextMonth));
  return `${String(nextYear).padStart(4, "0")}-${pad2(nextMonth)}-${pad2(nextDay)}`;
}

/** Today's calendar date in Dhaka, as YYYY-MM-DD. The browser's own zone is never used for a calendar day. */
export function todayInDhaka(now: number = Date.now()): string {
  const parts = dhakaParts(now);
  return `${parts.year}-${pad2(parts.month + 1)}-${pad2(parts.day)}`;
}

/**
 * The generated cash reference (plan C4, D13): CASH, the first 8 characters of the exam or package ID in capitals, the payment
 * date without dashes, and the payment number on the plan. A new plan's first payment is number 1; a later cash payment on
 * the same plan passes its own number (plan section 0, Phase 4 deviation).
 */
export function cashReference(targetId: string, paidOn: string, number = 1): string {
  return `CASH-${targetId.slice(0, 8).toUpperCase()}-${paidOn.replace(/-/g, "")}-${number}`;
}

/** A reference is valid when, once trimmed, it is printable ASCII of 1 to 100 characters. */
export function isValidReference(text: string): boolean {
  return REFERENCE.test(text.trim());
}

/** The installment count the admin typed, when it is a whole number from 2 to 24. */
function parseCount(text: string): number | null {
  const trimmed = text.trim();
  if (!/^\d+$/.test(trimmed)) {
    return null;
  }
  const count = Number(trimmed);
  return count >= MIN_INSTALLMENTS && count <= MAX_INSTALLMENTS ? count : null;
}

/** The even split of what is left after today's payment, rounded down, so the last row takes the remainder (E20). */
function suggestedPerPaisa(remaining: number, count: number): number {
  return count >= 3 ? Math.floor(remaining / (count - 1)) : remaining;
}

/** A resolved draft with nothing worked out yet. Each path fills in what applies to it. */
function blankResolved(type: PaymentType, totalText: string, paidOnText: string): Resolved {
  return {
    type,
    totalText,
    paidText: "",
    paidOnText,
    referenceText: "",
    countText: "",
    perText: "",
    nextOnText: "",
    differencePaisa: null,
    suggestedPerText: "",
    check: { ok: false, errors: {} },
  };
}

/**
 * Works out what the form shows and whether the enrolment may be saved. Every rule is checked here, so a field that breaks a
 * rule shows its own error, and the form and the API agree (plan section 4).
 */
export function resolveDraft(draft: PaymentDraft, ctx: Context): Resolved {
  const type: PaymentType = ctx.free ? "NO_CHARGE" : draft.type;
  const standardText = ctx.standardPaisa === null ? "" : paisaToInput(ctx.standardPaisa);
  const totalText = draft.total ?? standardText;
  const paidOnText = draft.paidOn ?? ctx.today;
  const total = parseMoney(totalText);
  const difference = total.ok && ctx.standardPaisa !== null ? total.paisa - ctx.standardPaisa : null;

  if (type === "NO_CHARGE") {
    const reason = draft.reason.trim();
    const errors: FieldErrors = {};
    if (!reason) {
      errors.reason = "required";
    } else if (reason.length > MAX_REASON) {
      errors.reason = "tooLong";
    }
    return {
      ...blankResolved(type, totalText, paidOnText),
      check: Object.keys(errors).length === 0 ? { ok: true, reason, payment: null } : { ok: false, errors },
    };
  }

  const errors: FieldErrors = {};
  if (!total.ok) {
    errors.total = total.code;
  }
  if (!isDate(paidOnText)) {
    errors.paidOn = "dateInvalid";
  } else if (paidOnText > ctx.today) {
    errors.paidOn = "dateFuture";
  }
  const referenceText =
    draft.reference ?? (draft.method === "CASH" && isDate(paidOnText) ? cashReference(ctx.targetId, paidOnText) : "");
  if (!referenceText.trim()) {
    errors.reference = "required";
  } else if (!isValidReference(referenceText)) {
    errors.reference = "referenceInvalid";
  }
  const note = draft.note.trim();
  if (note.length > MAX_NOTE) {
    errors.note = "tooLong";
  }
  const base: Resolved = {
    ...blankResolved(type, totalText, paidOnText),
    referenceText,
    differencePaisa: difference,
  };

  if (type === "FULL") {
    if (Object.keys(errors).length > 0 || !total.ok || !isDate(paidOnText)) {
      return { ...base, paidText: totalText, check: { ok: false, errors } };
    }
    const plan: PaymentPlan = {
      type: "FULL",
      totalPaisa: total.paisa,
      paidPaisa: total.paisa,
      duePaisa: 0,
      rows: [{ seq: 1, paisa: total.paisa, dueOn: paidOnText }],
      nextRow: null,
      method: draft.method,
      reference: referenceText.trim(),
      paidOn: paidOnText,
      note: note || null,
    };
    return { ...base, paidText: totalText, check: { ok: true, reason: "", payment: plan } };
  }

  // Installments. The schedule is worked out only once the total, the payment and the count are all known.
  const count = parseCount(draft.count);
  if (count === null) {
    errors.count = "countRange";
  }
  const paid = parseMoney(draft.paid);
  if (!paid.ok) {
    errors.paid = paid.code;
  } else if (total.ok && paid.paisa > total.paisa) {
    errors.paid = "overTotal";
  } else if (total.ok && paid.paisa === total.paisa) {
    errors.paid = "notBelowTotal";
  }
  const remaining = total.ok && paid.ok ? total.paisa - paid.paisa : 0;
  const suggestedPerText = count === null || remaining <= 0 ? "" : paisaToInput(suggestedPerPaisa(remaining, count));
  const perText = draft.perInstallment ?? suggestedPerText;
  const nextOnText = draft.nextOn ?? (isDate(paidOnText) ? addMonths(paidOnText, 1) : "");
  if (!nextOnText) {
    errors.nextOn = "required";
  } else if (!isDate(nextOnText)) {
    errors.nextOn = "dateInvalid";
  } else if (isDate(paidOnText) && nextOnText <= paidOnText) {
    errors.nextOn = "notAfterPayment";
  } else if (nextOnText < ctx.today) {
    errors.nextOn = "inPast";
  }

  const schedulable = total.ok && paid.ok && !errors.paid && count !== null && isDate(paidOnText);
  let perPaisa = 0;
  let lastPaisa = 0;
  if (schedulable && count !== null && count >= 3) {
    const per = parseMoney(perText);
    if (!per.ok) {
      errors.perInstallment = per.code;
    } else {
      perPaisa = per.paisa;
      lastPaisa = remaining - perPaisa * (count - 2);
      if (lastPaisa <= 0) {
        errors.perInstallment = "remainderNotPositive";
      }
    }
  } else if (schedulable && count === 2) {
    lastPaisa = remaining;
  }

  const plan =
    schedulable && count !== null && total.ok && paid.ok && Object.keys(errors).length === 0
      ? buildInstallments({
          totalPaisa: total.paisa,
          paidPaisa: paid.paisa,
          paidOn: paidOnText,
          count,
          perPaisa,
          lastPaisa,
          nextOn: nextOnText,
          method: draft.method,
          reference: referenceText.trim(),
          note: note || null,
        })
      : null;

  return {
    ...base,
    paidText: draft.paid,
    countText: draft.count,
    perText,
    nextOnText,
    suggestedPerText,
    check: plan ? { ok: true, reason: "", payment: plan } : { ok: false, errors },
  };
}

/** The schedule of an installment plan: today's payment, then the rows, the last one taking what is left. */
function buildInstallments(input: {
  totalPaisa: number;
  paidPaisa: number;
  paidOn: string;
  count: number;
  perPaisa: number;
  lastPaisa: number;
  nextOn: string;
  method: PaymentMethod;
  reference: string;
  note: string | null;
}): PaymentPlan {
  const rows: ScheduleRow[] = [{ seq: 1, paisa: input.paidPaisa, dueOn: input.paidOn }];
  for (let seq = 2; seq <= input.count; seq += 1) {
    const isLast = seq === input.count;
    // Each date is counted from the first installment, never from the one before, so month ends do not drift (E21).
    rows.push({
      seq,
      paisa: isLast ? input.lastPaisa : input.perPaisa,
      dueOn: addMonths(input.nextOn, seq - 2),
    });
  }
  return {
    type: "INSTALLMENTS",
    totalPaisa: input.totalPaisa,
    paidPaisa: input.paidPaisa,
    duePaisa: input.totalPaisa - input.paidPaisa,
    rows,
    nextRow: rows[1] ?? null,
    method: input.method,
    reference: input.reference,
    paidOn: input.paidOn,
    note: input.note,
  };
}

/** The `payment` object the API expects, from a checked plan. */
export function paymentBody(plan: PaymentPlan): GrantPayment {
  return {
    agreedTotal: paisaToNumber(plan.totalPaisa),
    amount: paisaToNumber(plan.paidPaisa),
    method: plan.method,
    reference: plan.reference,
    paidOn: plan.paidOn,
    note: plan.note,
    schedule: plan.rows.map((row) => ({ amount: paisaToNumber(row.paisa), dueOn: row.dueOn })),
  };
}

/** The enrolment body for a package or an exam. A payment sends an empty reason; No charge sends the reason and no payment. */
export function grantBody(scope: EnrolScope, targetId: string, studentId: string, reason: string, payment?: GrantPayment): GrantBody {
  const target = scope === "MODULE" ? { scope, moduleId: targetId } : { scope, examId: targetId };
  return payment ? { ...target, studentId, reason: "", payment } : { ...target, studentId, reason };
}

/** The error code for a field, or undefined. The form shows it only after the admin has tried to save. */
export function errorFor(check: Check, field: PaymentField): string | undefined {
  return check.ok ? undefined : check.errors[field];
}
