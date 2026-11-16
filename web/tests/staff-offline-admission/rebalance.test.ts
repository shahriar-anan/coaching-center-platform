import { describe, expect, it } from "vitest";
import { rebalanceRows, rowsTotal } from "@/components/staff/offline/setup-draft";

const rows = (...amounts: string[]) => amounts.map((amount, index) => ({ dueOn: `2026-1${index}-01`, amount }));
const amounts = (result: { rows: { amount: string }[] }) => result.rows.map((row) => row.amount);

describe("rebalancing the instalments when one amount is typed (16 Nov)", () => {
  it("keeps 12,000 in 2: the first set to 4,000 makes the second 8,000", () => {
    const result = rebalanceRows(rows("6000", "6000"), 0, "4000", 12000);
    expect(result.balanced).toBe(true);
    expect(amounts(result)).toEqual(["4000", "8000"]);
  });

  it("keeps the rows before the edited one and shares the rest over the rows after it, the rest on the last", () => {
    const result = rebalanceRows(rows("3000", "3000", "3000", "3000"), 1, "2000", 12000);
    expect(amounts(result)).toEqual(["3000", "2000", "3500", "3500"]);
    expect(amounts(rebalanceRows(rows("2500", "2500", "2500", "2500"), 0, "1000", 10000))).toEqual(["1000", "3000", "3000", "3000"]);
    expect(amounts(rebalanceRows(rows("3000", "3000", "3000"), 0, "1000", 10000))).toEqual(["1000", "4500", "4500"]);
    expect(amounts(rebalanceRows(rows("3000", "3000", "3000"), 0, "2000", 10001))).toEqual(["2000", "4000", "4001"]);
  });

  it("shares the rest over the rows before when the last row is edited", () => {
    expect(amounts(rebalanceRows(rows("4000", "4000", "4000"), 2, "2000", 12000))).toEqual(["5000", "5000", "2000"]);
  });

  it("keeps the other rows when the fee cannot be kept, or while the amount is still being typed", () => {
    expect(rebalanceRows(rows("6000", "6000"), 0, "12000", 12000).balanced).toBe(false);
    expect(amounts(rebalanceRows(rows("6000", "6000"), 0, "15000", 12000))).toEqual(["15000", "6000"]);
    expect(amounts(rebalanceRows(rows("6000", "6000"), 0, "", 12000))).toEqual(["", "6000"]);
    expect(rebalanceRows(rows("12000"), 0, "9000", 12000).balanced).toBe(false);
    expect(rowsTotal(rows("15000", "6000"))).toBe("21000");
  });
});
