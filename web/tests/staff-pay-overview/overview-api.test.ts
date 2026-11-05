import { describe, expect, it } from "vitest";
import { payAnalyticsPath, payOverviewPath, staffPayAnalyticsKeys } from "@/lib/api/exam/staff-pay-analytics";
import { staffPayKeys } from "@/lib/api/exam/staff-pay";

describe("the staff pay overview and analytics paths", () => {
  it("builds the overview path and the analytics path with both months", () => {
    expect(payOverviewPath()).toBe("/api/v1/staff-pay/overview");
    expect(payAnalyticsPath("2025-12", "2026-11")).toBe("/api/v1/staff-pay/analytics?from=2025-12&to=2026-11");
  });
});

describe("the overview and analytics query keys", () => {
  it("start with the same prefix as the other pay screens, so one invalidation refreshes them after a payment", () => {
    const keys = [
      staffPayAnalyticsKeys.overview(),
      staffPayAnalyticsKeys.analytics("2025-12", "2026-11"),
      staffPayAnalyticsKeys.analytics(null, null),
    ];
    for (const key of keys) {
      expect(key[0]).toBe(staffPayKeys.people("ALL", false)[0]);
    }
  });

  it("keep one range's answer apart from another's", () => {
    expect(staffPayAnalyticsKeys.analytics("2025-12", "2026-11")).not.toEqual(
      staffPayAnalyticsKeys.analytics("2026-01", "2026-11"),
    );
  });
});
