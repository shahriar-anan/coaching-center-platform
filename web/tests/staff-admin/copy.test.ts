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

/** The text at a dotted path, such as `errors.generic`, or undefined when there is none. */
function textAt(tree: Record<string, unknown>, path: string): string | undefined {
  const found = leaves(tree).find(([key]) => key === path);
  return found?.[1];
}

/**
 * Every error code the admin routes can return (AG-09). The API sends these from
 * the grant, order, announcement, account and system status services, and from
 * the global handler that answers for every route. A code added on the API side
 * must be added here, and then it needs its own message.
 */
const ADMIN_ROUTE_ERROR_CODES = [
  "GRANT_ALREADY_ACTIVE",
  "ORDER_NOT_PENDING",
  "ACCOUNT_HAS_PAID_ORDERS",
  "ACCOUNT_HAS_MARKED_WORK",
  "MASTER_ADMIN_PROTECTED",
  "RESOURCE_NOT_FOUND",
  "ACCESS_DENIED",
  "UNAUTHENTICATED",
  "VALIDATION_FAILED",
  "MALFORMED_REQUEST",
  "METHOD_NOT_ALLOWED",
  "UNSUPPORTED_MEDIA_TYPE",
  "INTERNAL_ERROR",
];

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

describe("admin route error codes (AG-09)", () => {
  it.each(ADMIN_ROUTE_ERROR_CODES)("%s has its own message in both languages", (code) => {
    const key = staffAdminErrorKey(code);

    // The generic message is the fallback for unknown codes, so it does not count as a message here.
    expect(key, `${code} has no message of its own`).not.toBe("errors.generic");
    expect(textAt(bnStaffAdmin, key), `${code} has no Bangla message`).toBeTruthy();
    expect(textAt(enStaffAdmin, key), `${code} has no English message`).toBeTruthy();
  });

  it("never puts the raw code in a message", () => {
    for (const code of ADMIN_ROUTE_ERROR_CODES) {
      for (const [, text] of leaves(enStaffAdmin).concat(leaves(bnStaffAdmin))) {
        expect(text).not.toContain(code);
      }
    }
  });
});
