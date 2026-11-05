import { describe, expect, it } from "vitest";
import { translate, type Locale } from "@/i18n/locale-context";
import {
  monthLabel,
  notificationHref,
  notificationText,
  type Translate,
} from "@/components/staff/notifications/notification-text";
import type { StaffNotificationPayload } from "@/lib/api/exam/staff-notifications";

// The sentences come from the real catalogs, so a missing key shows up here as the key itself.
function tFor(locale: Locale): Translate {
  return (key, params) => translate(locale, key, params);
}

function row(kind: string, payload: StaffNotificationPayload) {
  return { kind, payload };
}

describe("the text of each staff notification kind", () => {
  it("tells the Master Admin a payout account changed, without any number", () => {
    const text = notificationText(
      row("PAYOUT_ACCOUNT_CHANGED", { personName: "Rahim", change: "UPDATED" }),
      tFor("en"),
      "en",
    );
    expect(text).toBe("Rahim changed a payout account.");
  });

  it("names the owed amount, the sheets and the balance for the Master Admin", () => {
    const text = notificationText(
      row("MARKING_PUBLISHED_OWED", {
        examTitleEn: "Muslim Law",
        examTitleBn: "মুসলিম আইন",
        personName: "Rahim",
        sheets: 10,
        amount: 500,
        balance: 1200,
      }),
      tFor("en"),
      "en",
    );
    expect(text).toBe("Results published for Muslim Law. You owe Rahim ৳500.00 for 10 sheets. Balance now ৳1,200.00.");
  });

  it("uses the singular for one sheet", () => {
    const text = notificationText(
      row("MARKING_PUBLISHED_EARNED", { examTitleEn: "Muslim Law", examTitleBn: "মুসলিম আইন", sheets: 1, amount: 50 }),
      tFor("en"),
      "en",
    );
    expect(text).toBe("Published for Muslim Law: 1 sheet. You earned ৳50.00.");
  });

  it("gives the instructor the Bangla title and the Bangla count in Bangla", () => {
    const text = notificationText(
      row("MARKING_PUBLISHED_EARNED", { examTitleEn: "Muslim Law", examTitleBn: "মুসলিম আইন", sheets: 10, amount: 500 }),
      tFor("bn"),
      "bn",
    );
    expect(text).toContain("মুসলিম আইন");
    expect(text).toContain("১০টি খাতা");
    expect(text).toContain("৳৫০০");
  });

  it("says how a payment was made, and that a payment was cancelled", () => {
    expect(notificationText(row("PAYMENT_RECORDED", { amount: 500, method: "BKASH" }), tFor("en"), "en")).toBe(
      "You were paid ৳500.00 by bKash.",
    );
    expect(notificationText(row("PAYMENT_VOIDED", { amount: 200, method: "BANK" }), tFor("en"), "en")).toBe(
      "The payment of ৳200.00 by bank transfer was cancelled.",
    );
  });

  it("names the salary month and the due date, and calls an overdue salary overdue", () => {
    const due = notificationText(
      row("SALARY_DUE_SOON", { personName: "Karim", period: "2026-11", amount: 20000, dueOn: "2026-12-05" }),
      tFor("en"),
      "en",
    );
    expect(due).toContain("Karim's salary for November 2026 (৳20,000.00) is due on");
    expect(due).toContain("December");

    const overdue = notificationText(
      row("SALARY_OVERDUE", { personName: "Karim", period: "2026-11", amount: 20000, dueOn: "2026-12-05" }),
      tFor("en"),
      "en",
    );
    expect(overdue).toContain("is overdue");
  });

  it("tells the Master Admin how long an instructor has waited", () => {
    expect(
      notificationText(row("MARKING_UNPAID_REMINDER", { personName: "Rahim", amount: 500, days: 14 }), tFor("en"), "en"),
    ).toBe("Rahim has been waiting 14 days to be paid ৳500.00 for marked sheets.");
  });

  it("reads a kind this screen does not know with the generic sentence", () => {
    expect(notificationText(row("SOMETHING_NEW", { amount: 1 }), tFor("en"), "en")).toBe("You have a new notification.");
  });

  it("writes a missing value as nothing rather than as a broken sentence", () => {
    const text = notificationText(row("MARKING_UNPAID_REMINDER", {}), tFor("en"), "en");
    expect(text).toContain("has been waiting 0 days");
    expect(text).not.toContain("{");
  });
});

describe("month and link helpers", () => {
  it("shows a period as a month and year, in each language", () => {
    expect(monthLabel("2026-11", "en")).toBe("November 2026");
    expect(monthLabel("not a period", "en")).toBe("not a period");
  });

  it("opens a staff page, and falls back to the list for any other link", () => {
    expect(notificationHref("/staff/pay")).toBe("/staff/pay");
    expect(notificationHref("https://elsewhere.example/staff")).toBe("/staff/notifications");
    expect(notificationHref("/notices")).toBe("/staff/notifications");
  });
});
