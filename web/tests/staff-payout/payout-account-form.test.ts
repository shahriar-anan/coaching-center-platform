import { describe, expect, it } from "vitest";
import {
  checkDraft,
  compactNumber,
  draftFrom,
  emptyDraft,
  inputFrom,
} from "@/components/staff/payout/payout-account-form";
import { PAYOUT_ACCOUNT_LIMIT, PAYOUT_ACCOUNTS_PATH, type PayoutAccount } from "@/lib/api/exam/payout-accounts";

const bank = {
  ...emptyDraft("BANK"),
  accountName: " Rahim Uddin ",
  accountNumber: "1234-5678 90",
  bankName: "Sonali Bank",
  branch: "Dhanmondi",
  routingNumber: "200 262 123",
};

describe("the payout account form checks", () => {
  it("accepts a bank entry and sends the numbers without spaces or dashes", () => {
    expect(checkDraft(bank)).toEqual({});
    expect(inputFrom(bank)).toEqual({
      kind: "BANK",
      accountName: "Rahim Uddin",
      accountNumber: "1234567890",
      bankName: "Sonali Bank",
      branch: "Dhanmondi",
      routingNumber: "200262123",
    });
  });

  it("names each bank field that is missing or malformed", () => {
    expect(checkDraft({ ...bank, accountName: " " })).toEqual({ accountName: "required" });
    expect(checkDraft({ ...bank, accountNumber: "12345" })).toEqual({ accountNumber: "accountNumber" });
    expect(checkDraft({ ...bank, bankName: "" })).toEqual({ bankName: "required" });
    expect(checkDraft({ ...bank, branch: "" })).toEqual({ branch: "required" });
    expect(checkDraft({ ...bank, routingNumber: "2002621" })).toEqual({ routingNumber: "routingNumber" });
  });

  it("accepts a Bangladesh mobile number for each wallet and sends only the wallet number", () => {
    for (const kind of ["BKASH", "NAGAD", "ROCKET"] as const) {
      const draft = { ...emptyDraft(kind), walletNumber: "017 1234-5678" };
      expect(checkDraft(draft)).toEqual({});
      expect(inputFrom(draft)).toEqual({ kind, walletNumber: "01712345678" });
    }
    expect(checkDraft({ ...emptyDraft("BKASH"), walletNumber: "+8801712345678" })).toEqual({});
  });

  it("refuses a wallet number that is not a mobile number", () => {
    for (const walletNumber of ["0171234567", "02123456789", "017123456789", ""]) {
      expect(checkDraft({ ...emptyDraft("NAGAD"), walletNumber })).toEqual({ walletNumber: "walletNumber" });
    }
  });

  it("starts an edit from the stored numbers in full", () => {
    const account: PayoutAccount = {
      id: "a1",
      kind: "BANK",
      accountName: "Rahim Uddin",
      accountNumber: "1234567890",
      bankName: "Sonali Bank",
      branch: "Dhanmondi",
      routingNumber: "200262123",
      walletNumber: null,
      maskedNumber: "•••• 7890",
      preferred: true,
      createdAt: "2026-11-01T10:00:00+06:00",
      updatedAt: "2026-11-01T10:00:00+06:00",
    };
    expect(draftFrom(account)).toMatchObject({ kind: "BANK", accountNumber: "1234567890", routingNumber: "200262123" });
    expect(compactNumber(" 12-34 ")).toBe("1234");
  });

  it("keeps the account list path and the five-account limit the API uses", () => {
    expect(PAYOUT_ACCOUNTS_PATH).toBe("/api/v1/me/payout-accounts");
    expect(PAYOUT_ACCOUNT_LIMIT).toBe(5);
  });
});
