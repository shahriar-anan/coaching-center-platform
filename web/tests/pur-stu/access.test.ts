import { describe, expect, it } from "vitest";
import { blockedMessageKey, resolveAccess } from "@/components/student/purchase/access";
import bn from "@/i18n/messages/bn/purchase";

describe("access object (S-003)", () => {
  it.each(["MODULE", "EXAM", "ROSTER", "MANUAL"] as const)("%s is owned, with its source", (state) => {
    expect(resolveAccess({ state, buyable: false, blockedReason: "ALREADY_HAS_ACCESS" }, false)).toEqual({
      owned: true,
      via: state,
      buyable: false,
      blockedReason: "ALREADY_HAS_ACCESS",
    });
  });

  it("NONE is not owned, and the object decides buyable and the reason", () => {
    expect(resolveAccess({ state: "NONE", buyable: true, blockedReason: null }, true)).toEqual({
      owned: false,
      via: null,
      buyable: true,
      blockedReason: null,
    });
  });

  it("when the object is present, the probe result is ignored", () => {
    expect(resolveAccess({ state: "NONE", buyable: true, blockedReason: null }, true).owned).toBe(false);
    expect(resolveAccess({ state: "EXAM", buyable: false, blockedReason: null }, false).owned).toBe(true);
  });

  it("when the object is absent (older API), the probe decides ownership and buyable is unknown", () => {
    expect(resolveAccess(undefined, true)).toEqual({ owned: true, via: null, buyable: null, blockedReason: null });
    expect(resolveAccess(null, false)).toEqual({ owned: false, via: null, buyable: null, blockedReason: null });
  });
});

describe("blocked reasons (copy)", () => {
  it.each(["PHONE_REQUIRED", "PRICE_REQUIRED", "ALREADY_HAS_ACCESS", "CHECKOUT_OFF"] as const)(
    "%s has a copy key with text in Bangla",
    (reason) => {
      const key = blockedMessageKey(reason) as string;
      expect(key).toBe(`purchase.blocked.${reason}`);
      expect(bn.blocked[reason]).toBeTruthy();
    },
  );

  it("no reason, no copy", () => {
    expect(blockedMessageKey(null)).toBeNull();
  });
});
