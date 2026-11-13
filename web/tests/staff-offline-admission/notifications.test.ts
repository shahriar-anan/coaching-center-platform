import { readdirSync, readFileSync, statSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { translate, type Locale } from "@/i18n/locale-context";
import enStaffOffline from "@/i18n/messages/en/staffOffline";
import bnStaffOffline from "@/i18n/messages/bn/staffOffline";
import { notificationHref, notificationText, type Translate } from "@/components/staff/notifications/notification-text";
import type { StaffNotificationPayload } from "@/lib/api/exam/staff-notifications";

/** Every offline kind the API sends, with a payload shaped as the API documents it (names, amounts and dates only). */
const OFFLINE_KINDS: { kind: string; payload: StaffNotificationPayload }[] = [
  { kind: "ADMISSION_SUBMITTED", payload: { personName: "Rahim Uddin" } },
  { kind: "ADMISSION_RESUBMITTED", payload: { personName: "Rahim Uddin" } },
  { kind: "ADMISSION_READY", payload: { personName: "Rahim Uddin" } },
  { kind: "ADMISSION_RETURNED", payload: { personName: "Rahim Uddin" } },
  { kind: "ADMISSION_ONBOARDED", payload: { personName: "Rahim Uddin" } },
  { kind: "OFFLINE_PAYMENT_PENDING", payload: { personName: "Rahim Uddin", amount: 3000, method: "Cash" } },
  { kind: "OFFLINE_PAYMENT_APPROVED", payload: { personName: "Rahim Uddin", amount: 3000, method: "bKash" } },
  { kind: "OFFLINE_PAYMENT_REJECTED", payload: { personName: "Rahim Uddin", amount: 3000, method: "Bank transfer", reason: "Wrong reference" } },
  { kind: "OFFLINE_FEE_OVERDUE", payload: { personName: "Rahim Uddin", amount: 3000, dueOn: "2027-01-15" } },
];

function tFor(locale: Locale): Translate {
  return (key, params) => translate(locale, key, params);
}

describe("the staff notification text of each offline kind", () => {
  for (const locale of ["en", "bn"] as const) {
    it(`has text in ${locale} for every new kind, and no kind falls back to the generic sentence`, () => {
      const generic = translate(locale, "staffNotifications.kind.UNKNOWN");
      for (const row of OFFLINE_KINDS) {
        const text = notificationText(row, tFor(locale), locale);
        expect(text, row.kind).not.toBe("");
        expect(text, row.kind).not.toBe(generic);
        expect(text, row.kind).not.toContain("staffNotifications.");
      }
    });
  }

  it("names the student and the amount in the English sentence", () => {
    const pending = notificationText(OFFLINE_KINDS[5]!, tFor("en"), "en");
    expect(pending).toBe("Rahim Uddin's payment of ৳3,000.00 by cash is waiting for your approval.");
  });

  it("gives the reason on a rejected payment", () => {
    const rejected = notificationText(OFFLINE_KINDS[7]!, tFor("en"), "en");
    expect(rejected).toContain("Wrong reference");
  });

  it("links an admission notice to its admission and a payment notice to the fee approvals", () => {
    expect(notificationHref("/staff/admissions/abc")).toBe("/staff/admissions/abc");
    expect(notificationHref("/staff/offline-fees")).toBe("/staff/offline-fees");
  });
});

/* ---------- the catalogue ---------- */

function keysOf(tree: unknown, prefix = ""): string[] {
  if (tree === null || typeof tree !== "object") {
    return [prefix];
  }
  return Object.entries(tree as Record<string, unknown>).flatMap(([key, value]) => keysOf(value, prefix ? `${prefix}.${key}` : key));
}

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const full = path.join(dir, name);
    if (statSync(full).isDirectory()) {
      return sourceFiles(full);
    }
    return /\.(ts|tsx)$/.test(name) ? [full] : [];
  });
}

describe("the staffOffline catalogue", () => {
  it("has the same keys in English and Bangla", () => {
    expect(keysOf(bnStaffOffline).sort()).toEqual(keysOf(enStaffOffline).sort());
  });

  it("resolves every staffOffline key the screens use, in both languages", () => {
    const root = path.resolve(process.cwd(), "src");
    const missing: string[] = [];
    for (const file of sourceFiles(root)) {
      const source = readFileSync(file, "utf8");
      for (const match of source.matchAll(/staffOffline\.([A-Za-z][A-Za-z0-9_.]*[A-Za-z0-9])/g)) {
        const end = (match.index ?? 0) + match[0].length;
        // A prefix that a template completes at run time (`staffOffline.money.${key}`) is checked below, key by key.
        if (source.slice(end, end + 2) === ".$") {
          continue;
        }
        const key = `staffOffline.${match[1]}`;
        for (const locale of ["en", "bn"] as const) {
          if (translate(locale, key) === key) {
            missing.push(`${locale} ${key} (${path.relative(root, file)})`);
          }
        }
      }
    }
    expect(missing).toEqual([]);
  });

  it("resolves the keys the screens build at run time", () => {
    const dynamic = [
      "staffOffline.money.overdue",
      "staffOffline.money.overdueFlag",
      "staffOffline.money.pending",
      "staffOffline.money.noPayment",
      "staffOffline.money.owed",
      "staffOffline.money.paid",
      "staffOffline.form.groups.personal",
      "staffOffline.form.groups.education",
      "staffOffline.form.groups.contact",
      "staffOffline.form.groups.addresses",
    ];
    for (const locale of ["en", "bn"] as const) {
      for (const key of dynamic) {
        expect(translate(locale, key), `${locale} ${key}`).not.toBe(key);
      }
    }
  });
});
