import { describe, expect, it } from "vitest";
import bnStaffAdmin from "@/i18n/messages/bn/staffAdmin";
import enStaffAdmin from "@/i18n/messages/en/staffAdmin";
import { staffAdminErrorKey } from "@/lib/api/exam/staff-admin";

/** Every leaf of a message tree as `[path, text]`, such as `["access.columns.source", "কীভাবে"]`. */
function leaves(tree: Record<string, unknown>, prefix = ""): [string, string][] {
  return Object.entries(tree).flatMap(([key, value]) => {
    const path = prefix ? `${prefix}.${key}` : key;
    return typeof value === "object" && value !== null
      ? leaves(value as Record<string, unknown>, path)
      : [[path, value as string]];
  });
}

describe("staffAdmin copy", () => {
  it("has the same keys in Bangla and English", () => {
    const bnKeys = leaves(bnStaffAdmin).map(([path]) => path).sort();
    const enKeys = leaves(enStaffAdmin).map(([path]) => path).sort();
    expect(enKeys).toEqual(bnKeys);
  });

  it("has no empty strings", () => {
    for (const [path, text] of leaves(bnStaffAdmin)) {
      expect(text, path).toBeTruthy();
    }
  });
});

describe("staffAdminErrorKey", () => {
  it("maps each code the screens must explain", () => {
    expect(staffAdminErrorKey("GRANT_ALREADY_ACTIVE")).toBe("errors.grantAlreadyActive");
    expect(staffAdminErrorKey("ORDER_NOT_PENDING")).toBe("errors.orderNotPending");
    expect(staffAdminErrorKey("ACCOUNT_HAS_PAID_ORDERS")).toBe("errors.accountHasPaidOrders");
    expect(staffAdminErrorKey("ACCOUNT_HAS_MARKED_WORK")).toBe("errors.accountHasMarkedWork");
    expect(staffAdminErrorKey("ACCESS_DENIED")).toBe("errors.accessDenied");
  });

  it("falls back to the generic message for a code it does not know", () => {
    expect(staffAdminErrorKey("SOMETHING_NEW")).toBe("errors.generic");
  });
});
