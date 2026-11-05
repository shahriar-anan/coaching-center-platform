import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/errors";
import type { PayLedgerRow } from "@/lib/api/exam/staff-pay";
import {
  defaultPaymentAmount,
  isAdvance,
  isVoidable,
  kindKey,
  methodKey,
  parseAmount,
  parseSignedAmount,
  payErrorKey,
  todayInDhaka,
} from "@/components/staff/pay/pay-format";

function row(overrides: Partial<PayLedgerRow>): Pick<PayLedgerRow, "kind" | "voided"> {
  return { kind: "PAYMENT", voided: false, ...overrides } as Pick<PayLedgerRow, "kind" | "voided">;
}

function apiFailure(code: string): ApiError {
  return new ApiError({ code, message: "server text", timestamp: "2026-11-05T00:00:00Z", requestId: "r" } as ConstructorParameters<typeof ApiError>[0], 409);
}

describe("reading the amounts the Master Admin types", () => {
  it("accepts a positive amount with at most two decimals, and drops commas", () => {
    expect(parseAmount("10,000")).toBe(10000);
    expect(parseAmount(" 12.5 ")).toBe(12.5);
    expect(parseAmount("0.01")).toBe(0.01);
  });

  it("refuses zero, negatives, extra decimals and words", () => {
    for (const text of ["0", "-5", "1.234", "abc", "", "1e3"]) {
      expect(parseAmount(text)).toBeNull();
    }
  });

  it("reads an adjustment's sign: plus or minus, never zero", () => {
    expect(parseSignedAmount("-500")).toBe(-500);
    expect(parseSignedAmount("250.50")).toBe(250.5);
    expect(parseSignedAmount("0")).toBeNull();
    expect(parseSignedAmount("+5")).toBeNull();
  });

  it("starts a payment at what is owed, and calls a payment above the balance an advance", () => {
    expect(defaultPaymentAmount(600)).toBe("600.00");
    expect(defaultPaymentAmount(0)).toBe("");
    expect(isAdvance(700, 600)).toBe(true);
    expect(isAdvance(600, 600)).toBe(false);
  });
});

describe("the ledger's rules on screen", () => {
  it("only a live payment or adjustment can be voided", () => {
    expect(isVoidable(row({ kind: "PAYMENT" }))).toBe(true);
    expect(isVoidable(row({ kind: "ADJUSTMENT" }))).toBe(true);
    expect(isVoidable(row({ kind: "SALARY" }))).toBe(false);
    expect(isVoidable(row({ kind: "MARKING" }))).toBe(false);
    expect(isVoidable(row({ kind: "PAYMENT", voided: true }))).toBe(false);
  });

  it("names the keys for kinds and methods", () => {
    expect(kindKey("ADJUSTMENT")).toBe("staffPay.kind.ADJUSTMENT");
    expect(methodKey("BKASH")).toBe("staffPay.method.BKASH");
    expect(methodKey(null)).toBeNull();
  });

  it("maps the API's error codes to the message to show, and anything else to the generic one", () => {
    expect(payErrorKey(apiFailure("PAYMENT_EXCEEDS_BALANCE"))).toBe("staffPay.errors.exceedsBalance");
    expect(payErrorKey(apiFailure("LEDGER_NOT_VOIDABLE"))).toBe("staffPay.errors.notVoidable");
    expect(payErrorKey(apiFailure("LEDGER_ALREADY_VOIDED"))).toBe("staffPay.errors.alreadyVoided");
    expect(payErrorKey(apiFailure("VALIDATION_FAILED"))).toBe("staffPay.errors.check");
    expect(payErrorKey(apiFailure("SOMETHING_NEW"))).toBe("staffPay.errors.generic");
    expect(payErrorKey(new Error("network"))).toBe("staffPay.errors.generic");
  });
});

describe("today in Dhaka", () => {
  it("is the Dhaka date, which is six hours ahead of UTC", () => {
    // 20:00 UTC on 5 November is already 6 November in Dhaka.
    expect(todayInDhaka(Date.UTC(2026, 10, 5, 20, 0))).toBe("2026-11-06");
    expect(todayInDhaka(Date.UTC(2026, 10, 5, 10, 0))).toBe("2026-11-05");
  });
});
