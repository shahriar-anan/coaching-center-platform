import { describe, expect, it } from "vitest";
import bn from "@/i18n/messages/bn/notices";
import en from "@/i18n/messages/en/notices";
import {
  INSTALLMENT_HREF,
  INSTALLMENT_KINDS,
  installmentFacts,
  installmentTitle,
  installmentTone,
  isInstallmentKind,
} from "@/app/(site)/notices/_components/installment-notice";
import { NOTICE_KINDS, noticeHref } from "@/app/(site)/notices/_components/notice-kind";

const block = {
  titleEn: "Package one",
  titleBn: "কিস্তির প্যাকেজ",
  amount: 2100,
  dueOn: "2026-12-08",
  paid: 2100,
  due: 4200,
  receiptNo: "RCPT-2026-000140",
};

describe("money notice kinds (plan section 7)", () => {
  it("are the five kinds, all listed as notice kinds", () => {
    expect(INSTALLMENT_KINDS).toHaveLength(5);
    for (const kind of INSTALLMENT_KINDS) {
      expect(NOTICE_KINDS).toContain(kind);
      expect(isInstallmentKind(kind)).toBe(true);
    }
  });

  it("have a label in Bangla and in English", () => {
    for (const kind of INSTALLMENT_KINDS) {
      expect(en.kind[kind]).toBeTruthy();
      expect(bn.kind[kind]).toBeTruthy();
    }
  });

  it("leave other kinds alone", () => {
    expect(isInstallmentKind("EXAM_OPEN")).toBe(false);
    expect(isInstallmentKind("INSTALLMENT_UNKNOWN")).toBe(false);
  });
});

describe("where a money notice opens", () => {
  it("opens the payments for every money kind, with or without an exam", () => {
    for (const kind of INSTALLMENT_KINDS) {
      expect(noticeHref({ kind, exam: null })).toBe(INSTALLMENT_HREF);
      expect(noticeHref({ kind, exam: { id: "e1" } })).toBe(INSTALLMENT_HREF);
    }
  });

  it("is the offline tab of My exams", () => {
    expect(INSTALLMENT_HREF).toBe("/my-modules?tab=offline");
  });

  it("leaves the exam and announcement links as they were", () => {
    expect(noticeHref({ kind: "EXAM_OPEN", exam: { id: "e1" } })).toBe("/exam/e1");
    expect(noticeHref({ kind: "MCQ_SCORE", exam: { id: "e1" } })).toBe("/exam/e1/result");
    expect(noticeHref({ kind: "ANNOUNCEMENT", exam: null })).toBeNull();
  });
});

describe("tone of a money notice", () => {
  it("reads overdue in the warning tone", () => {
    expect(installmentTone("INSTALLMENT_OVERDUE")).toBe("warn");
  });

  it("reads a received payment as good news, and a due-soon reminder in the accent", () => {
    expect(installmentTone("INSTALLMENT_PAYMENT_RECEIVED")).toBe("ok");
    expect(installmentTone("INSTALLMENT_DUE_SOON")).toBe("accent");
  });

  it("reads a void and a changed date in the warning tone, as a changed exam time is", () => {
    expect(installmentTone("INSTALLMENT_PAYMENT_VOIDED")).toBe("warn");
    expect(installmentTone("INSTALLMENT_SCHEDULE_CHANGED")).toBe("warn");
  });
});

describe("the title of a money notice", () => {
  it("is the plan's title in the student's language, with the other as fallback", () => {
    expect(installmentTitle(block, "en")).toBe("Package one");
    expect(installmentTitle(block, "bn")).toBe("কিস্তির প্যাকেজ");
    expect(installmentTitle({ titleEn: "Package one", titleBn: null }, "bn")).toBe("Package one");
  });

  it("is empty when the block or both titles are left out", () => {
    expect(installmentTitle(null, "en")).toBe("");
    expect(installmentTitle({}, "en")).toBe("");
  });
});

describe("the figures on a money notice's row", () => {
  it("shows a payment's amount, next date, paid so far, still owed and receipt", () => {
    const facts = installmentFacts("INSTALLMENT_PAYMENT_RECEIVED", block, "en");
    expect(facts.map((fact) => fact.key)).toEqual([
      "notices.installment.received",
      "notices.installment.nextOn",
      "notices.installment.paidSoFar",
      "notices.installment.stillOwed",
      "notices.installment.receipt",
    ]);
    expect(facts[0].params).toEqual({ amount: "৳2,100.00" });
    expect(facts[1].params).toEqual({ date: "8 December 2026" });
    expect(facts[4].params).toEqual({ number: "RCPT-2026-000140" });
  });

  it("shows a voided payment as voided, not received", () => {
    const facts = installmentFacts("INSTALLMENT_PAYMENT_VOIDED", block, "en");
    expect(facts[0].key).toBe("notices.installment.voided");
  });

  it("shows a reminder's amount due now and its date, not a next date", () => {
    const facts = installmentFacts("INSTALLMENT_OVERDUE", { ...block, receiptNo: null }, "en");
    expect(facts.map((fact) => fact.key)).toEqual([
      "notices.installment.amountDue",
      "notices.installment.dueOn",
      "notices.installment.paidSoFar",
      "notices.installment.stillOwed",
    ]);
    expect(facts[1].params).toEqual({ date: "8 December 2026" });
  });

  it("shows a schedule change's position only, since it has no single row", () => {
    const facts = installmentFacts("INSTALLMENT_SCHEDULE_CHANGED", { paid: 2100, due: 4200 }, "en");
    expect(facts.map((fact) => fact.key)).toEqual(["notices.installment.paidSoFar", "notices.installment.stillOwed"]);
  });

  it("leaves out every figure the API did not send, and a blank receipt number", () => {
    expect(installmentFacts("INSTALLMENT_DUE_SOON", {}, "en")).toEqual([]);
    const facts = installmentFacts("INSTALLMENT_PAYMENT_RECEIVED", { amount: 2100, receiptNo: "  " }, "en");
    expect(facts.map((fact) => fact.key)).toEqual(["notices.installment.received"]);
  });

  it("is empty when the notice has no installment block", () => {
    expect(installmentFacts("INSTALLMENT_DUE_SOON", null, "en")).toEqual([]);
    expect(installmentFacts("INSTALLMENT_DUE_SOON", undefined, "en")).toEqual([]);
  });

  it("formats the amounts in Bangla for a Bangla student", () => {
    const facts = installmentFacts("INSTALLMENT_PAYMENT_RECEIVED", { amount: 2100 }, "bn");
    expect(facts[0].params.amount).toBe("৳২,১০০");
  });
});
