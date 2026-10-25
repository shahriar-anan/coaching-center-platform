import { describe, expect, it } from "vitest";
import {
  accessCopyKey,
  auditActionKey,
  paymentStatusKey,
  paymentStatusTone,
  scopeKey,
} from "@/components/staff/ops/ops-copy";

describe("payment status", () => {
  it("maps each API word to the screen word and tone", () => {
    expect(paymentStatusKey("INITIATED")).toBe("pending");
    expect(paymentStatusKey("SUCCEEDED")).toBe("paid");
    expect(paymentStatusKey("FAILED")).toBe("failed");
    expect(paymentStatusKey("CANCELLED")).toBe("cancelled");
    expect(paymentStatusTone("pending")).toBe("warn");
    expect(paymentStatusTone("paid")).toBe("ok");
    expect(paymentStatusTone("failed")).toBe("danger");
    expect(paymentStatusTone("cancelled")).toBe("muted");
  });
});

describe("access line", () => {
  it("says what a pending order will do, and that only a paid order grants access", () => {
    expect(accessCopyKey({ status: "INITIATED", scope: "EXAM" })).toBe("awaitingGateway");
    expect(accessCopyKey({ status: "SUCCEEDED", scope: "MODULE" })).toBe("wholeModule");
    expect(accessCopyKey({ status: "SUCCEEDED", scope: "EXAM" })).toBe("thisExam");
    expect(accessCopyKey({ status: "FAILED", scope: "EXAM" })).toBe("noAccess");
    expect(accessCopyKey({ status: "CANCELLED", scope: "MODULE" })).toBe("noAccess");
  });

  it("names the scope", () => {
    expect(scopeKey("MODULE")).toBe("module");
    expect(scopeKey("EXAM")).toBe("exam");
  });
});

describe("audit action labels", () => {
  it("translates known actions and leaves unknown codes to show as stored", () => {
    expect(auditActionKey("PHONE_CHANGED")).toBe("staffOps.audit.action.PHONE_CHANGED");
    expect(auditActionKey("SOMETHING_NEW")).toBeNull();
  });
});
