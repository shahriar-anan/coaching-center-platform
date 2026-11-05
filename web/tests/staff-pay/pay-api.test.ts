import { describe, expect, it } from "vitest";
import {
  PAY_LEDGER_PAGE,
  myLedgerPath,
  payLedgerPath,
  payPeoplePath,
  payPersonPath,
  staffPayKeys,
} from "@/lib/api/exam/staff-pay";

describe("the staff pay paths", () => {
  it("builds the people path with the role and owing filters only when they are set", () => {
    expect(payPeoplePath("ALL", false)).toBe("/api/v1/staff-pay/people");
    expect(payPeoplePath("INSTRUCTOR", true)).toBe("/api/v1/staff-pay/people?role=INSTRUCTOR&owing=true");
    expect(payPeoplePath("SYSTEM_ADMIN", false)).toBe("/api/v1/staff-pay/people?role=SYSTEM_ADMIN");
  });

  it("builds the person and ledger paths, with the page size", () => {
    expect(payPersonPath("u1")).toBe("/api/v1/staff-pay/people/u1");
    expect(payLedgerPath("u1", 2)).toBe(`/api/v1/staff-pay/people/u1/ledger?page=2&size=${PAY_LEDGER_PAGE}`);
    expect(myLedgerPath(0)).toBe(`/api/v1/me/pay/ledger?page=0&size=${PAY_LEDGER_PAGE}`);
    expect(PAY_LEDGER_PAGE).toBe(20);
  });
});

describe("the staff pay query keys", () => {
  it("all start with one prefix, so a single invalidation refreshes every pay screen", () => {
    const keys = [
      staffPayKeys.people("ALL", false),
      staffPayKeys.person("u1"),
      staffPayKeys.ledger("u1", 0),
      staffPayKeys.accounts("u1"),
      staffPayKeys.defaults(),
      staffPayKeys.mySummary(),
      staffPayKeys.myLedger(0),
    ];
    for (const key of keys) {
      expect(key[0]).toBe(staffPayKeys.all[0]);
    }
  });

  it("keeps the owing list and the full list under different keys", () => {
    expect(staffPayKeys.people("ALL", true)).not.toEqual(staffPayKeys.people("ALL", false));
    expect(staffPayKeys.ledger("u1", 0)).not.toEqual(staffPayKeys.ledger("u1", 1));
  });
});
