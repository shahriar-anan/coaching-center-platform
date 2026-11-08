import { describe, expect, it } from "vitest";
import {
  EMPTY_DRAFT,
  addMonths,
  cashReference,
  grantBody,
  isDate,
  isValidReference,
  paisaToInput,
  paymentBody,
  parseMoney,
  quotePaisa,
  resolveDraft,
  todayInDhaka,
  type Context,
  type PaymentDraft,
  type PaymentPlan,
} from "@/components/staff/enrol/payment-model";

const TARGET = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d";
const TODAY = "2026-11-08";

function ctx(overrides: Partial<Context> = {}): Context {
  return { targetId: TARGET, standardPaisa: 600000, free: false, today: TODAY, ...overrides };
}

function draft(overrides: Partial<PaymentDraft> = {}): PaymentDraft {
  return { ...EMPTY_DRAFT, ...overrides };
}

/** The plan a draft produces. Fails the test when the draft is refused. */
function planOf(input: PaymentDraft, context: Context = ctx()): PaymentPlan {
  const check = resolveDraft(input, context).check;
  if (!check.ok || check.payment === null) {
    throw new Error(`expected a plan, got ${JSON.stringify(check)}`);
  }
  return check.payment;
}

/** The errors a draft produces. Fails the test when the draft is accepted. */
function errorsOf(input: PaymentDraft, context: Context = ctx()): Record<string, string | undefined> {
  const check = resolveDraft(input, context).check;
  if (check.ok) {
    throw new Error("expected the draft to be refused");
  }
  return check.errors;
}

const sum = (plan: PaymentPlan) => plan.rows.reduce((total, row) => total + row.paisa, 0);

describe("parseMoney", () => {
  it("reads whole taka and paisa as whole paisa", () => {
    expect(parseMoney("2100")).toEqual({ ok: true, paisa: 210000 });
    expect(parseMoney("2100.5")).toEqual({ ok: true, paisa: 210050 });
    expect(parseMoney("2100.50")).toEqual({ ok: true, paisa: 210050 });
    expect(parseMoney("2,100.50")).toEqual({ ok: true, paisa: 210050 });
    expect(parseMoney(" 2100 ")).toEqual({ ok: true, paisa: 210000 });
  });

  it("reads Bengali digits", () => {
    expect(parseMoney("২১০০.৫০")).toEqual({ ok: true, paisa: 210050 });
  });

  it("refuses more than two decimals, and rounds nothing (E15)", () => {
    expect(parseMoney("2100.505")).toEqual({ ok: false, code: "moneyDecimals" });
    expect(parseMoney("2100.000")).toEqual({ ok: false, code: "moneyDecimals" });
  });

  it("refuses zero, negative, blank, malformed and huge amounts (E15)", () => {
    expect(parseMoney("0")).toEqual({ ok: false, code: "moneyPositive" });
    expect(parseMoney("0.00")).toEqual({ ok: false, code: "moneyPositive" });
    expect(parseMoney("-5")).toEqual({ ok: false, code: "money" });
    expect(parseMoney("1e3")).toEqual({ ok: false, code: "money" });
    expect(parseMoney("abc")).toEqual({ ok: false, code: "money" });
    expect(parseMoney("")).toEqual({ ok: false, code: "required" });
    expect(parseMoney("   ")).toEqual({ ok: false, code: "required" });
    expect(parseMoney(null)).toEqual({ ok: false, code: "required" });
    expect(parseMoney("9999999999.99")).toEqual({ ok: true, paisa: 999999999999 });
    expect(parseMoney("10000000000")).toEqual({ ok: false, code: "moneyTooLarge" });
  });

  it("adds in whole paisa, so 0.1 and 0.2 are exact", () => {
    expect(parseMoney("0.1")).toEqual({ ok: true, paisa: 10 });
    expect(parseMoney("0.2")).toEqual({ ok: true, paisa: 20 });
    expect(parseMoney("1000.10")).toEqual({ ok: true, paisa: 100010 });
  });
});

describe("paisaToInput and quotePaisa", () => {
  it("shows a whole amount without decimals and others with two", () => {
    expect(paisaToInput(630000)).toBe("6300");
    expect(paisaToInput(210050)).toBe("2100.50");
  });

  it("reads the quote's price, which may be a number or a string, and null when there is none", () => {
    expect(quotePaisa(6300)).toBe(630000);
    expect(quotePaisa("2100.5")).toBe(210050);
    expect(quotePaisa(null)).toBeNull();
    expect(quotePaisa(undefined)).toBeNull();
    expect(quotePaisa("abc")).toBeNull();
  });
});

describe("dates", () => {
  it("accepts only real calendar dates", () => {
    expect(isDate("2026-02-28")).toBe(true);
    expect(isDate("2028-02-29")).toBe(true);
    expect(isDate("2027-02-29")).toBe(false);
    expect(isDate("2026-13-01")).toBe(false);
    expect(isDate("2026-1-1")).toBe(false);
    expect(isDate("")).toBe(false);
    expect(isDate(null)).toBe(false);
  });

  it("moves a date by whole months from the start, so month ends do not drift (E21)", () => {
    expect(addMonths("2027-01-31", 1)).toBe("2027-02-28");
    expect(addMonths("2028-01-31", 1)).toBe("2028-02-29");
    expect(addMonths("2027-01-31", 2)).toBe("2027-03-31");
    expect(addMonths("2026-11-08", 1)).toBe("2026-12-08");
    expect(addMonths("2026-12-08", 1)).toBe("2027-01-08");
    expect(addMonths("2026-11-30", 3)).toBe("2027-02-28");
    expect(addMonths("2026-11-08", 3)).toBe("2027-02-08");
  });

  it("takes today's calendar date in Dhaka, not the browser's zone", () => {
    // 17:00 UTC on 7 November is 23:00 on 7 November in Dhaka.
    expect(todayInDhaka(Date.UTC(2026, 10, 7, 17, 0))).toBe("2026-11-07");
    // 20:00 UTC on 7 November is 02:00 on 8 November in Dhaka.
    expect(todayInDhaka(Date.UTC(2026, 10, 7, 20, 0))).toBe("2026-11-08");
  });
});

describe("cash reference (C4, D13)", () => {
  it("is CASH, the first 8 characters of the ID in capitals, the date and the payment number", () => {
    expect(cashReference(TARGET, "2026-11-08")).toBe("CASH-1A2B3C4D-20261108-1");
  });

  it("is valid as a reference: printable ASCII", () => {
    expect(isValidReference(cashReference(TARGET, "2026-11-08"))).toBe(true);
    expect(isValidReference("রেফারেন্স১")).toBe(false);
    expect(isValidReference("   ")).toBe(false);
    expect(isValidReference("x".repeat(101))).toBe(false);
  });
});

describe("full payment", () => {
  it("prefills the standard price, takes the whole total today, and generates the cash reference", () => {
    const plan = planOf(draft());
    expect(plan.type).toBe("FULL");
    expect(plan.totalPaisa).toBe(600000);
    expect(plan.paidPaisa).toBe(600000);
    expect(plan.duePaisa).toBe(0);
    expect(plan.rows).toEqual([{ seq: 1, paisa: 600000, dueOn: TODAY }]);
    expect(plan.nextRow).toBeNull();
    expect(plan.method).toBe("CASH");
    expect(plan.reference).toBe("CASH-1A2B3C4D-20261108-1");
  });

  it("shows the difference from the standard price when the total is raised or lowered (E4)", () => {
    expect(resolveDraft(draft({ total: "6500" }), ctx()).differencePaisa).toBe(50000);
    expect(resolveDraft(draft({ total: "5500" }), ctx()).differencePaisa).toBe(-50000);
    expect(resolveDraft(draft(), ctx()).differencePaisa).toBe(0);
  });

  it("asks for the total when there is no standard price (E1)", () => {
    expect(errorsOf(draft(), ctx({ standardPaisa: null })).total).toBe("required");
    expect(planOf(draft({ total: "4000" }), ctx({ standardPaisa: null })).totalPaisa).toBe(400000);
  });

  it("refuses a total with more than two decimals (E15)", () => {
    expect(errorsOf(draft({ total: "5500.001" })).total).toBe("moneyDecimals");
  });

  it("refuses a payment date in the future, and accepts a backdated one into last year (E23)", () => {
    expect(errorsOf(draft({ paidOn: "2026-11-09" })).paidOn).toBe("dateFuture");
    const plan = planOf(draft({ paidOn: "2025-03-02" }));
    expect(plan.paidOn).toBe("2025-03-02");
    expect(plan.reference).toBe("CASH-1A2B3C4D-20250302-1");
  });

  it("refuses a missing or invalid date", () => {
    expect(errorsOf(draft({ paidOn: "" })).paidOn).toBe("dateInvalid");
    expect(errorsOf(draft({ paidOn: "2026-02-30" })).paidOn).toBe("dateInvalid");
  });

  it("generates the cash reference while the method is Cash, and clears it on another method", () => {
    expect(planOf(draft({ method: "CASH" })).reference).toMatch(/^CASH-/);
    expect(errorsOf(draft({ method: "BKASH" })).reference).toBe("required");
    expect(planOf(draft({ method: "BKASH", reference: "TXN8841" })).reference).toBe("TXN8841");
  });

  it("keeps a reference the admin typed, whatever the method (the typed value is left alone)", () => {
    expect(planOf(draft({ method: "NAGAD", reference: "MYREF-1" })).reference).toBe("MYREF-1");
    expect(planOf(draft({ method: "CASH", reference: "MYREF-1" })).reference).toBe("MYREF-1");
  });

  it("refuses a reference in Bangla or only spaces (E24)", () => {
    expect(errorsOf(draft({ method: "BANK", reference: "রেফারেন্স১" })).reference).toBe("referenceInvalid");
    expect(errorsOf(draft({ method: "BANK", reference: "   " })).reference).toBe("required");
  });

  it("refuses a note longer than 500 characters", () => {
    expect(errorsOf(draft({ note: "x".repeat(501) })).note).toBe("tooLong");
  });
});

describe("installments: the schedule", () => {
  const installments = (overrides: Partial<PaymentDraft> = {}) =>
    draft({ type: "INSTALLMENTS", total: "6300", paid: "2100", count: "3", ...overrides });

  it("builds today's payment, then the rest, with the next date a month after the payment by default", () => {
    const plan = planOf(installments());
    expect(plan.type).toBe("INSTALLMENTS");
    expect(plan.rows).toEqual([
      { seq: 1, paisa: 210000, dueOn: "2026-11-08" },
      { seq: 2, paisa: 210000, dueOn: "2026-12-08" },
      { seq: 3, paisa: 210000, dueOn: "2027-01-08" },
    ]);
    expect(plan.nextRow).toEqual({ seq: 2, paisa: 210000, dueOn: "2026-12-08" });
    expect(plan.duePaisa).toBe(420000);
    expect(sum(plan)).toBe(630000);
  });

  it("splits the rest evenly, and gives the last row what is left (E20)", () => {
    const plan = planOf(installments({ total: "1000", paid: "400", count: "3" }));
    expect(plan.rows.map((row) => row.paisa)).toEqual([40000, 30000, 30000]);
    expect(sum(plan)).toBe(100000);
  });

  it("keeps the rows exact when the rest does not divide evenly (E20)", () => {
    // 601 is left after 400: 300.50 each, so the two rows are equal and still add up to the total.
    const plan = planOf(installments({ total: "1001", paid: "400", count: "3" }));
    expect(plan.rows.map((row) => row.paisa)).toEqual([40000, 30050, 30050]);
    expect(sum(plan)).toBe(100100);
  });

  it("takes a typed amount per installment, with the last row as the remainder", () => {
    const plan = planOf(installments({ total: "1000", paid: "400", count: "3", perInstallment: "250" }));
    expect(plan.rows.map((row) => row.paisa)).toEqual([40000, 25000, 35000]);
  });

  it("refuses an amount that leaves the last row at zero or less (E19)", () => {
    expect(errorsOf(installments({ total: "1000", paid: "400", perInstallment: "700" })).perInstallment).toBe("remainderNotPositive");
    expect(errorsOf(installments({ total: "1000", paid: "400", perInstallment: "600" })).perInstallment).toBe("remainderNotPositive");
  });

  it("refuses paying the whole total on an installment plan (E18)", () => {
    expect(errorsOf(installments({ total: "1000", paid: "1000" })).paid).toBe("notBelowTotal");
    expect(errorsOf(installments({ total: "1000", paid: "1500" })).paid).toBe("overTotal");
  });

  it("accepts 2 and 24 installments and refuses 1 and 25 (E22)", () => {
    expect(planOf(installments({ count: "2" })).rows).toHaveLength(2);
    const twentyFour = planOf(installments({ total: "10000", paid: "100", count: "24", perInstallment: "430" }));
    expect(twentyFour.rows).toHaveLength(24);
    expect(sum(twentyFour)).toBe(1000000);
    expect(errorsOf(installments({ count: "25" })).count).toBe("countRange");
    expect(errorsOf(installments({ count: "1" })).count).toBe("countRange");
    expect(errorsOf(installments({ count: "abc" })).count).toBe("countRange");
  });

  it("uses the typed next date and moves each later row from it by whole months (E21)", () => {
    const plan = planOf(installments({ count: "4", nextOn: "2027-01-31" }));
    expect(plan.rows.map((row) => row.dueOn)).toEqual(["2026-11-08", "2027-01-31", "2027-02-28", "2027-03-31"]);
  });

  it("refuses a next date that is not after the payment date (E17)", () => {
    expect(errorsOf(installments({ nextOn: "2026-11-08" })).nextOn).toBe("notAfterPayment");
    expect(errorsOf(installments({ nextOn: "2026-11-07" })).nextOn).toBe("notAfterPayment");
  });

  it("refuses a later row in the past, so a plan never starts out overdue (E17)", () => {
    expect(errorsOf(installments({ paidOn: "2026-11-01", nextOn: "2026-11-05" })).nextOn).toBe("inPast");
  });

  it("refuses the default next date when a backdated payment puts it in the past, and accepts a later one", () => {
    expect(errorsOf(installments({ paidOn: "2026-10-01" })).nextOn).toBe("inPast");
    expect(planOf(installments({ paidOn: "2026-10-01", nextOn: "2026-12-01" })).nextRow?.dueOn).toBe("2026-12-01");
  });

  it("refuses a missing paid-today amount", () => {
    expect(errorsOf(installments({ paid: "" })).paid).toBe("required");
  });

  it("holds the rows to the total in paisa, with no float drift (the rows add up exactly)", () => {
    const plan = planOf(installments({ total: "1000.10", paid: "400.05", count: "3" }));
    expect(plan.rows.map((row) => row.paisa)).toEqual([40005, 30002, 30003]);
    expect(sum(plan)).toBe(100010);
  });

  it("uses a second installment that takes the rest when there are two", () => {
    const plan = planOf(installments({ total: "1000", paid: "400", count: "2", perInstallment: "garbage" }));
    expect(plan.rows.map((row) => row.paisa)).toEqual([40000, 60000]);
  });
});

describe("No charge and a free exam", () => {
  it("needs a reason, and passes with one", () => {
    expect(errorsOf(draft({ type: "NO_CHARGE" })).reason).toBe("required");
    expect(errorsOf(draft({ type: "NO_CHARGE", reason: "   " })).reason).toBe("required");
    const check = resolveDraft(draft({ type: "NO_CHARGE", reason: " Scholarship " }), ctx()).check;
    expect(check).toEqual({ ok: true, payment: null, reason: "Scholarship" });
  });

  it("is the only choice for a free exam, whatever the draft says", () => {
    const resolved = resolveDraft(draft({ type: "INSTALLMENTS", total: "100", paid: "50" }), ctx({ free: true, standardPaisa: null }));
    expect(resolved.type).toBe("NO_CHARGE");
    expect(resolved.check.ok).toBe(false);
    expect(errorsOf(draft({ type: "FULL" }), ctx({ free: true })).reason).toBe("required");
  });
});

describe("the request body", () => {
  it("sends the payment as JSON numbers in taka, with the schedule rows", () => {
    const plan = planOf(draft({ type: "INSTALLMENTS", total: "6300", paid: "2100", count: "3", method: "BKASH", reference: "TXN8841" }));
    expect(paymentBody(plan)).toEqual({
      agreedTotal: 6300,
      amount: 2100,
      method: "BKASH",
      reference: "TXN8841",
      paidOn: TODAY,
      note: null,
      schedule: [
        { amount: 2100, dueOn: "2026-11-08" },
        { amount: 2100, dueOn: "2026-12-08" },
        { amount: 2100, dueOn: "2027-01-08" },
      ],
    });
  });

  it("sends an empty reason with a payment, and no payment for No charge", () => {
    const plan = planOf(draft());
    const withPayment = grantBody("EXAM", TARGET, "s1", "ignored", paymentBody(plan));
    expect(withPayment).toMatchObject({ scope: "EXAM", examId: TARGET, studentId: "s1", reason: "" });
    expect(withPayment.payment).toBeDefined();

    const noCharge = grantBody("MODULE", TARGET, "s1", "Scholarship");
    expect(noCharge).toEqual({ scope: "MODULE", moduleId: TARGET, studentId: "s1", reason: "Scholarship" });
    expect("payment" in noCharge).toBe(false);
  });
});
