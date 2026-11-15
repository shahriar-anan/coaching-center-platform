import { describe, expect, it } from "vitest";
import { noticeActionKey } from "@/app/(site)/notices/_components/notice-kind";

describe("notice link words", () => {
  it("says View result for online and offline results", () => {
    for (const kind of ["MCQ_SCORE", "WRITTEN_RESULT", "OFFLINE_RESULT_PUBLISHED", "OFFLINE_RESULT_UPDATED"]) {
      expect(noticeActionKey({ kind, exam: null }), kind).toBe("notices.viewResult");
    }
  });

  it("never says Open exam for an admission or fee notice", () => {
    expect(noticeActionKey({ kind: "ADMISSION_APPROVED", exam: null })).toBe("notices.openOffline");
    expect(noticeActionKey({ kind: "BATCH_ROUTINE_UPDATED", exam: null })).toBe("notices.openOffline");
    expect(noticeActionKey({ kind: "ADMISSION_JOIN_CODE", exam: null })).toBe("notices.enterJoinCode");
    expect(noticeActionKey({ kind: "OFFLINE_FEE_DUE_SOON", exam: null })).toBe("notices.viewPayment");
  });

  it("keeps Open exam for exam notices, and My exams for a purchase with no exam", () => {
    expect(noticeActionKey({ kind: "EXAM_OPEN", exam: { id: "e1" } })).toBe("notices.openExamAction");
    expect(noticeActionKey({ kind: "PURCHASE_GRANTED", exam: null })).toBe("notices.myExams");
  });
});
