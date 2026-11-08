import { describe, expect, it } from "vitest";
import bn from "@/i18n/messages/bn/account";
import en from "@/i18n/messages/en/account";
import {
  PLAN_STATUSES,
  SCHEDULE_STATES,
  canOpenOfflineItem,
  isSettled,
  isVoided,
  methodLabelKey,
  offlinePills,
  offlineRowKey,
  planStatusPill,
  scheduleStatePill,
} from "@/components/student/catalog/offline-payment";
import { optionalAmount, optionalDate } from "@/lib/api/missing";

const plan = { planId: "p1", agreedTotal: 6300, paid: 2100, due: 4200, nextDueOn: "2026-12-08" };

describe("plan status pill (plan section 8)", () => {
  it.each([
    ["ACTIVE", "accent"],
    ["DUE_SOON", "warn"],
    ["OVERDUE", "warn"],
    ["COMPLETED", "ok"],
    ["ACCESS_REVOKED", "muted"],
  ])("%s has its tone", (status, tone) => {
    expect(planStatusPill(status)?.tone).toBe(tone);
  });

  it("names its copy key under the account payments offline block", () => {
    expect(planStatusPill("OVERDUE")?.key).toBe("account.payments.offline.status.OVERDUE");
  });

  it("shows no pill for a missing or unknown status, including names that exist on every object", () => {
    expect(planStatusPill(undefined)).toBeNull();
    expect(planStatusPill(null)).toBeNull();
    expect(planStatusPill("SOMETHING_NEW")).toBeNull();
    expect(planStatusPill("constructor")).toBeNull();
  });

  it("has a Bangla and an English word for every status the API sends", () => {
    for (const status of PLAN_STATUSES) {
      expect(en.payments.offline.status[status]).toBeTruthy();
      expect(bn.payments.offline.status[status]).toBeTruthy();
    }
  });
});

describe("schedule row state (plan section 8)", () => {
  it("maps each state to its copy and tone, and has both languages", () => {
    for (const state of SCHEDULE_STATES) {
      expect(scheduleStatePill(state)?.key).toBe(`account.payments.offline.panel.state.${state}`);
      expect(en.payments.offline.panel.state[state]).toBeTruthy();
      expect(bn.payments.offline.panel.state[state]).toBeTruthy();
    }
    expect(scheduleStatePill("PART_PAID")?.tone).toBe("accent");
  });

  it("shows no pill for a state it does not know, or for none", () => {
    expect(scheduleStatePill(undefined)).toBeNull();
    expect(scheduleStatePill("toString")).toBeNull();
  });
});

describe("payment method label", () => {
  it("names the copy key for each method the API sends", () => {
    expect(methodLabelKey("BKASH")).toBe("account.payments.offline.panel.methods.BKASH");
    expect(en.payments.offline.panel.methods.BKASH).toBe("bKash");
  });

  it("is null for a method this page does not know", () => {
    expect(methodLabelKey("VISA")).toBeNull();
    expect(methodLabelKey(undefined)).toBeNull();
  });
});

describe("a card whose access was removed (plan section 8)", () => {
  it("does not link to the exam or package", () => {
    expect(canOpenOfflineItem({ accessRemoved: true })).toBe(false);
  });

  it("links to the exam or package when access was not removed, or the field is left out", () => {
    expect(canOpenOfflineItem({ accessRemoved: false })).toBe(true);
    expect(canOpenOfflineItem({})).toBe(true);
    expect(canOpenOfflineItem({ accessRemoved: null })).toBe(true);
  });
});

describe("the pills at the top of a card", () => {
  it("shows the access end and no status for a card without a plan", () => {
    expect(offlinePills({})).toEqual({ showAccessEnd: true, status: null });
    expect(offlinePills({ payment: null })).toEqual({ showAccessEnd: true, status: null });
  });

  it("shows the access end and the plan's status for a live card with a plan", () => {
    const pills = offlinePills({ accessRemoved: false, payment: { ...plan, status: "DUE_SOON" } });
    expect(pills.showAccessEnd).toBe(true);
    expect(pills.status?.key).toBe("account.payments.offline.status.DUE_SOON");
  });

  it("shows only 'Access ended' for a removed card with a plan, not the end date", () => {
    const pills = offlinePills({ accessRemoved: true, payment: { ...plan, status: "ACCESS_REVOKED" } });
    expect(pills.showAccessEnd).toBe(false);
    expect(pills.status?.key).toBe("account.payments.offline.status.ACCESS_REVOKED");
  });

  it("shows 'Completed' for a removed card whose plan is completed", () => {
    const pills = offlinePills({ accessRemoved: true, payment: { ...plan, status: "COMPLETED" } });
    expect(pills.status?.key).toBe("account.payments.offline.status.COMPLETED");
  });

  it("still shows 'Access ended' for a removed card whose status was left out", () => {
    const pills = offlinePills({ accessRemoved: true, payment: { planId: "p1" } });
    expect(pills.status?.key).toBe("account.payments.offline.status.ACCESS_REVOKED");
  });
});

describe("card keys", () => {
  it("tells two cards for one exam apart by their plans", () => {
    const first = offlineRowKey("exam", "e1", { grantedAt: "2026-10-01", payment: { planId: "p1" } });
    const second = offlineRowKey("exam", "e1", { grantedAt: "2026-11-01", payment: { planId: "p2" } });
    expect(first).not.toBe(second);
  });

  it("uses the grant date when a card has no plan", () => {
    expect(offlineRowKey("package", "m1", { grantedAt: "2026-10-01" })).toBe("package-m1-2026-10-01");
  });
});

describe("payment flags", () => {
  it("reads a voided payment from its voided date only", () => {
    expect(isVoided({ voidedAt: "2026-11-09T04:00:00Z" })).toBe(true);
    expect(isVoided({ voidedAt: null })).toBe(false);
    expect(isVoided({})).toBe(false);
  });

  it("reads a plan as settled only when its due amount is zero", () => {
    expect(isSettled({ due: 0 })).toBe(true);
    expect(isSettled({ due: 4200 })).toBe(false);
    expect(isSettled({})).toBe(false);
  });
});

describe("optional fields the API may leave out", () => {
  it("keeps a calendar date and drops anything else", () => {
    expect(optionalDate("2026-12-08")).toBe("2026-12-08");
    expect(optionalDate("2026-12-08T00:00:00Z")).toBeNull();
    expect(optionalDate("")).toBeNull();
    expect(optionalDate(undefined)).toBeNull();
    expect(optionalDate(null)).toBeNull();
  });

  it("keeps a finite amount, zero included, and drops a missing or non-numeric one", () => {
    expect(optionalAmount(0)).toBe(0);
    expect(optionalAmount(2100.5)).toBe(2100.5);
    expect(optionalAmount(Number.NaN)).toBeNull();
    expect(optionalAmount(undefined)).toBeNull();
    expect(optionalAmount(null)).toBeNull();
  });
});
