import { describe, expect, it } from "vitest";
import { accountLabel, balanceSide, moveId, money, writeErrorText } from "@/components/staff/accounting/accounting-model";
import { ApiError } from "@/lib/api/errors";

/** A translator that answers the key itself, so the tests see which key was used. */
const key = (text: string) => text;

describe("money and balances", () => {
  it("shows money in the site's format, and a dash when there is none", () => {
    expect(money("1250.00", "en")).toBe("৳1,250.00");
    expect(money("1250.00", "bn")).toBe("৳১,২৫০");
    expect(money(null, "en")).toBe("—");
  });

  it("reads a balance as owed, advance or settled", () => {
    expect(balanceSide("2000.00")).toBe("owed");
    expect(balanceSide("-500.00")).toBe("advance");
    expect(balanceSide("0.00")).toBe("settled");
  });
});

describe("account names", () => {
  it("shows a seeded account in Bangla by its kind", () => {
    expect(accountLabel({ name: "bKash", kind: "BKASH" }, "bn", key)).toBe("accounting.accountKind.BKASH");
    expect(accountLabel({ name: "Cash box", kind: null }, "bn", key)).toBe("accounting.accountKind.CASH");
  });

  it("keeps a name the admin typed, and any name on the English site", () => {
    expect(accountLabel({ name: "bKash 2", kind: "BKASH" }, "bn", key)).toBe("bKash 2");
    expect(accountLabel({ name: "bKash", kind: "BKASH" }, "en", key)).toBe("bKash");
  });
});

describe("moving an item in a list", () => {
  it("swaps it with its neighbour", () => {
    expect(moveId(["a", "b", "c"], "b", -1)).toEqual(["b", "a", "c"]);
    expect(moveId(["a", "b", "c"], "b", 1)).toEqual(["a", "c", "b"]);
  });

  it("does nothing past either end", () => {
    expect(moveId(["a", "b"], "a", -1)).toBeNull();
    expect(moveId(["a", "b"], "b", 1)).toBeNull();
    expect(moveId(["a", "b"], "z", 1)).toBeNull();
  });
});

describe("failed writes", () => {
  it("shows the refusal's own message", () => {
    const error = new ApiError({ code: "ACC_REFUSED", message: "accounting.errors.voided", requestId: "t", timestamp: "2026-11-27T00:00:00Z" }, 409);
    expect(writeErrorText(error, key)).toBe("accounting.errors.voided");
  });

  it("asks for a reload on a stale version", () => {
    const error = new ApiError({ code: "STALE_VERSION", message: "changed", requestId: "t", timestamp: "2026-11-27T00:00:00Z" }, 409);
    expect(writeErrorText(error, key)).toBe("accounting.common.stale");
  });

  it("gives the generic text for anything else", () => {
    expect(writeErrorText(new Error("boom"), key)).toBe("accounting.common.failed");
  });
});
