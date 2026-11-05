import { describe, expect, it } from "vitest";
import { MY_MARKING_PATH, personMarkingPath, staffPayMarkingKeys } from "@/lib/api/exam/staff-pay-marking";
import { staffPayKeys } from "@/lib/api/exam/staff-pay";

describe("the marking paths", () => {
  it("builds the instructor's own path and the Master Admin's path for one person", () => {
    expect(MY_MARKING_PATH).toBe("/api/v1/me/pay/marking");
    expect(personMarkingPath("u1")).toBe("/api/v1/staff-pay/people/u1/marking");
  });
});

describe("the marking query keys", () => {
  it("start with the pay prefix, so one invalidation refreshes them with the other pay screens", () => {
    expect(staffPayMarkingKeys.me()[0]).toBe(staffPayKeys.all[0]);
    expect(staffPayMarkingKeys.person("u1")[0]).toBe(staffPayKeys.all[0]);
  });

  it("keeps each person's figures under their own key", () => {
    expect(staffPayMarkingKeys.person("u1")).not.toEqual(staffPayMarkingKeys.person("u2"));
    expect(staffPayMarkingKeys.person("u1")).not.toEqual(staffPayMarkingKeys.me());
  });
});
