import { describe, expect, it } from "vitest";
import { translate, type Locale } from "@/i18n/locale-context";
import { notificationText, type Translate } from "@/components/staff/notifications/notification-text";
import en from "@/i18n/messages/en/instructorPay";
import bn from "@/i18n/messages/bn/instructorPay";
import type { StaffNotificationPayload } from "@/lib/api/exam/staff-notifications";

function tFor(locale: Locale): Translate {
  return (key, params) => translate(locale, key, params);
}

/** Every leaf key of a message tree, as dotted paths. */
function leafKeys(tree: Record<string, unknown>, prefix = ""): string[] {
  return Object.entries(tree).flatMap(([key, value]) => {
    const path = prefix ? `${prefix}.${key}` : key;
    return typeof value === "object" && value !== null ? leafKeys(value as Record<string, unknown>, path) : [path];
  });
}

/** The three kinds this work adds, and the offline kinds already in the catalogue, each with a payload that fills its sentence. */
const KINDS: { kind: string; payload: StaffNotificationPayload }[] = [
  { kind: "OFFLINE_CLASSES_TO_SETTLE", payload: { count: 4 } },
  { kind: "OFFLINE_CLASSES_TO_SETTLE", payload: { count: 4, waiting: 2 } },
  { kind: "OFFLINE_CLASSES_OVERDUE", payload: { count: 3 } },
  { kind: "OFFLINE_ENTRIES_DECIDED", payload: { approved: 2, rejected: 1 } },
  { kind: "OFFLINE_PAYMENT_PENDING", payload: { personName: "Rahim", amount: 500, method: "CASH" } },
  { kind: "OFFLINE_PAYMENT_APPROVED", payload: { personName: "Rahim", amount: 500, method: "CASH" } },
  { kind: "OFFLINE_PAYMENT_REJECTED", payload: { personName: "Rahim", amount: 500, method: "CASH", reason: "Wrong amount" } },
  { kind: "OFFLINE_FEE_OVERDUE", payload: { personName: "Rahim", amount: 500, dueOn: "2026-11-05" } },
  { kind: "ADMISSION_SUBMITTED", payload: { personName: "Rahim" } },
  { kind: "ADMISSION_RESUBMITTED", payload: { personName: "Rahim" } },
  { kind: "ADMISSION_READY", payload: { personName: "Rahim" } },
  { kind: "ADMISSION_RETURNED", payload: { personName: "Rahim" } },
  { kind: "ADMISSION_ONBOARDED", payload: { personName: "Rahim" } },
];

describe("staff notification text for offline instructor pay", () => {
  it("writes the to-settle notice in English, and adds the waiting line for the Master Admin", () => {
    expect(notificationText({ kind: "OFFLINE_CLASSES_TO_SETTLE", payload: { count: 4 } }, tFor("en"), "en")).toBe(
      "4 offline classes are waiting for payment or a not-taken mark.",
    );
    expect(
      notificationText({ kind: "OFFLINE_CLASSES_TO_SETTLE", payload: { count: 4, waiting: 2 } }, tFor("en"), "en"),
    ).toBe("4 offline classes are waiting for payment or a not-taken mark. 2 entries wait for your approval.");
  });

  it("writes the overdue and the decided notices in English", () => {
    expect(notificationText({ kind: "OFFLINE_CLASSES_OVERDUE", payload: { count: 3 } }, tFor("en"), "en")).toBe(
      "3 offline classes have waited more than a week.",
    );
    expect(
      notificationText({ kind: "OFFLINE_ENTRIES_DECIDED", payload: { approved: 2, rejected: 1 } }, tFor("en"), "en"),
    ).toBe("2 of your entries were approved, 1 rejected.");
  });

  it("writes the same notices in Bangla, with Bangla digits", () => {
    const decided = notificationText(
      { kind: "OFFLINE_ENTRIES_DECIDED", payload: { approved: 2, rejected: 1 } },
      tFor("bn"),
      "bn",
    );
    expect(decided).toContain("২টি এন্ট্রি অনুমোদন");
    expect(decided).toContain("১টি বাতিল");

    const settle = notificationText({ kind: "OFFLINE_CLASSES_TO_SETTLE", payload: { count: 4 } }, tFor("bn"), "bn");
    expect(settle).toContain("৪টি অফলাইন ক্লাস");
  });

  it.each(KINDS.map((item) => [item.kind, item.payload] as const))("%s has text in English and Bangla", (kind, payload) => {
    const unknown = { en: translate("en", "staffNotifications.kind.UNKNOWN"), bn: translate("bn", "staffNotifications.kind.UNKNOWN") };
    const english = notificationText({ kind, payload }, tFor("en"), "en");
    const bangla = notificationText({ kind, payload }, tFor("bn"), "bn");
    expect(english).not.toBe("");
    expect(bangla).not.toBe("");
    expect(english).not.toBe(unknown.en);
    expect(bangla).not.toBe(unknown.bn);
    // A missing catalogue key shows the key itself, so no text may contain a dotted key.
    expect(english).not.toMatch(/staffNotifications\./);
    expect(bangla).not.toMatch(/staffNotifications\./);
  });

  it("has a catalogue entry in both languages for each new kind and its extra line", () => {
    for (const key of ["OFFLINE_CLASSES_TO_SETTLE", "OFFLINE_CLASSES_OVERDUE", "OFFLINE_ENTRIES_DECIDED", "OFFLINE_ENTRIES_WAITING"]) {
      expect(translate("en", `staffNotifications.kind.${key}`)).not.toBe(`staffNotifications.kind.${key}`);
      expect(translate("bn", `staffNotifications.kind.${key}`)).not.toBe(`staffNotifications.kind.${key}`);
    }
  });
});

describe("instructor pay catalogue", () => {
  it("has the same keys in Bangla as in English", () => {
    expect(leafKeys(bn as Record<string, unknown>).sort()).toEqual(leafKeys(en as Record<string, unknown>).sort());
  });
});
