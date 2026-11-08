import { describe, expect, it } from "vitest";
import {
  activeScopes,
  checkRecordPayment,
  checkScheduleChange,
  editableRowsFrom,
  filterFromParam,
  frozenCount,
  frozenLinesFrom,
  freeCashReference,
  nextRowDate,
  scheduleLines,
  statusForFilter,
  statusKey,
  statusTone,
  suggestedPaymentPaisa,
  summaryFigures,
} from "@/components/staff/installments/installment-model";
import type { InstallmentRow } from "@/lib/api/exam/staff-installments";

const TODAY = "2026-11-08";
const ITEM = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d";

function row(seq: number, amount: number, dueOn: string, uncovered: number): InstallmentRow {
  const paid = amount - uncovered;
  const state = uncovered === 0 ? "PAID" : paid > 0 ? "PART_PAID" : dueOn < TODAY ? "OVERDUE" : "UPCOMING";
  return { seq, amount, dueOn, paid, uncovered, state };
}

describe("the list filters and the URL", () => {
  it("reads a known filter from the URL and treats anything else as All", () => {
    expect(filterFromParam("OVERDUE")).toBe("OVERDUE");
    expect(filterFromParam("ACCESS_REVOKED")).toBe("ACCESS_REVOKED");
    expect(filterFromParam("overdue")).toBe("ALL");
    expect(filterFromParam("")).toBe("ALL");
    expect(filterFromParam(null)).toBe("ALL");
  });

  it("asks the API for the status a filter names, and for every plan under All", () => {
    expect(statusForFilter("ALL")).toBeUndefined();
    expect(statusForFilter("DUE_SOON")).toBe("DUE_SOON");
    expect(statusForFilter("COMPLETED")).toBe("COMPLETED");
  });

  it("names only the narrowing filters that are set", () => {
    expect(activeScopes({ studentId: "s1", examId: "", moduleId: undefined })).toEqual(["studentId"]);
    expect(activeScopes({ studentId: "", examId: "e1", moduleId: "m1" })).toEqual(["examId", "moduleId"]);
    expect(activeScopes({})).toEqual([]);
  });
});

describe("the status label mapping", () => {
  it("gives each plan status a key and a tone", () => {
    expect(statusKey("ACCESS_REVOKED")).toBe("staffAdmin.installments.status.ACCESS_REVOKED");
    expect(statusTone("OVERDUE")).toBe("danger");
    expect(statusTone("DUE_SOON")).toBe("warn");
    expect(statusTone("COMPLETED")).toBe("ok");
    expect(statusTone("ACCESS_REVOKED")).toBe("muted");
  });
});

describe("the summary band", () => {
  it("shows placeholders while the summary is loading", () => {
    expect(summaryFigures(undefined)).toEqual({ totalDue: null, overdue: null, dueSoon: null });
  });

  it("maps the API totals to the band", () => {
    expect(summaryFigures({ totalDue: 8400, overdueCount: 2, dueSoonCount: 1 })).toEqual({
      totalDue: 8400,
      overdue: 2,
      dueSoon: 1,
    });
  });
});

describe("the schedule state", () => {
  const schedule = [row(1, 2100, "2026-11-08", 0), row(2, 2100, "2026-10-08", 1700), row(3, 2100, "2027-01-08", 2100)];

  it("counts the paid rows at the front, which a change cannot touch", () => {
    expect(frozenCount(schedule)).toBe(1);
    expect(frozenCount([row(1, 2100, TODAY, 2100)])).toBe(0);
  });

  it("marks the oldest row with something still owed as next, even when it is overdue", () => {
    const lines = scheduleLines(schedule);
    expect(lines.map((line) => line.isNext)).toEqual([false, true, false]);
    expect(lines[1].state).toBe(schedule[1].state);
  });

  it("marks no row next once every row is paid", () => {
    const paid = [row(1, 2100, TODAY, 0), row(2, 2100, "2026-12-08", 0)];
    expect(scheduleLines(paid).every((line) => !line.isNext)).toBe(true);
  });

  it("splits the schedule into the frozen rows and the editable rows", () => {
    expect(frozenLinesFrom(schedule)).toEqual([{ paisa: 210000, dueOn: TODAY }]);
    expect(editableRowsFrom(schedule)).toEqual([
      { amountText: "2100", dueOn: "2026-10-08" },
      { amountText: "2100", dueOn: "2027-01-08" },
    ]);
  });
});

describe("the amount a payment starts at", () => {
  it("starts at the next installment's uncovered amount", () => {
    expect(suggestedPaymentPaisa({ due: 4200, nextDueAmount: 2100 })).toBe(210000);
  });

  it("never suggests more than the whole amount still due, and nothing when nothing is due", () => {
    expect(suggestedPaymentPaisa({ due: 500, nextDueAmount: 2100 })).toBe(50000);
    expect(suggestedPaymentPaisa({ due: 0, nextDueAmount: null })).toBe(0);
    expect(suggestedPaymentPaisa({ due: 1000 })).toBe(100000);
  });
});

describe("cash references and masking", () => {
  it("numbers a cash reference past any that a payment on the plan already uses", () => {
    expect(freeCashReference(ITEM, "2026-11-08", [])).toBe("CASH-1A2B3C4D-20261108-1");
    expect(freeCashReference(ITEM, "2026-11-08", [" cash-1a2b3c4d-20261108-1 "])).toBe("CASH-1A2B3C4D-20261108-2");
  });
});

describe("checking a payment before it is sent", () => {
  const base = { amountText: "2100", method: "BKASH" as const, referenceText: "TXN8841", paidOnText: TODAY, note: "" };
  const context = { duePaisa: 420000, today: TODAY, takenReferences: [] as string[] };

  it("accepts a payment within the amount due, with a new reference", () => {
    expect(checkRecordPayment(base, context)).toEqual({ ok: true, amountPaisa: 210000, reference: "TXN8841", note: null });
  });

  it("refuses more than the amount still due", () => {
    const result = checkRecordPayment({ ...base, amountText: "5000" }, context);
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.errors.amount).toBe("over");
    }
  });

  it("refuses a reference already used on this plan, ignoring case and outer spaces", () => {
    const result = checkRecordPayment(base, { ...context, takenReferences: ["  txn8841 "] });
    expect(!result.ok && result.errors.reference).toBe("referenceTaken");
  });

  it("refuses a blank reference and one that is not printable ASCII", () => {
    expect(!checkRecordPayment({ ...base, referenceText: "   " }, context).ok).toBe(true);
    const bangla = checkRecordPayment({ ...base, referenceText: "বিকাশ123" }, context);
    expect(!bangla.ok && bangla.errors.reference).toBe("referenceInvalid");
  });

  it("refuses a date in the future and an amount with more than two decimals", () => {
    const future = checkRecordPayment({ ...base, paidOnText: "2026-11-09" }, context);
    expect(!future.ok && future.errors.paidOn).toBe("dateFuture");
    const decimals = checkRecordPayment({ ...base, amountText: "10.555" }, context);
    expect(!decimals.ok && decimals.errors.amount).toBe("moneyDecimals");
  });
});

describe("checking a schedule change before it is sent", () => {
  const frozen = [{ paisa: 210000, dueOn: TODAY }];

  it("accepts rows that add up to the agreed total, after the paid row", () => {
    const result = checkScheduleChange({
      totalPaisa: 630000,
      frozen,
      rows: [
        { amountText: "2100", dueOn: "2026-12-08" },
        { amountText: "2100", dueOn: "2027-01-08" },
      ],
      today: TODAY,
    });
    expect(result).toEqual({
      ok: true,
      schedule: [
        { amount: 2100, dueOn: TODAY },
        { amount: 2100, dueOn: "2026-12-08" },
        { amount: 2100, dueOn: "2027-01-08" },
      ],
    });
  });

  it("refuses rows that do not add up to the total, even by a paisa", () => {
    const result = checkScheduleChange({
      totalPaisa: 630000,
      frozen,
      rows: [
        { amountText: "2100", dueOn: "2026-12-08" },
        { amountText: "2099.99", dueOn: "2027-01-08" },
      ],
      today: TODAY,
    });
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.errors).toContainEqual({ code: "total" });
    }
  });

  it("refuses dates that do not rise, including one on or before the last paid date", () => {
    const repeated = checkScheduleChange({
      totalPaisa: 630000,
      frozen,
      rows: [
        { amountText: "2100", dueOn: TODAY },
        { amountText: "2100", dueOn: "2027-01-08" },
      ],
      today: TODAY,
    });
    expect(!repeated.ok && repeated.errors).toContainEqual({ row: 0, code: "dateOrder" });
    const backwards = checkScheduleChange({
      totalPaisa: 630000,
      frozen,
      rows: [
        { amountText: "2100", dueOn: "2027-01-08" },
        { amountText: "2100", dueOn: "2026-12-08" },
      ],
      today: TODAY,
    });
    expect(!backwards.ok && backwards.errors).toContainEqual({ row: 1, code: "dateOrder" });
  });

  it("refuses an unpaid date in the past, and a schedule with no unpaid row", () => {
    const past = checkScheduleChange({
      totalPaisa: 210000,
      frozen: [],
      rows: [{ amountText: "2100", dueOn: "2026-11-01" }],
      today: TODAY,
    });
    expect(!past.ok && past.errors).toContainEqual({ row: 0, code: "datePast" });
    const none = checkScheduleChange({ totalPaisa: 210000, frozen, rows: [], today: TODAY });
    expect(!none.ok && none.errors).toContainEqual({ code: "count" });
  });
});

describe("dates for an added row", () => {
  it("moves one month on from the row before, and keeps month ends", () => {
    expect(nextRowDate([{ amountText: "100", dueOn: "2026-01-31" }], TODAY)).toBe("2026-02-28");
    expect(nextRowDate([], TODAY)).toBe("2026-12-08");
  });
});
