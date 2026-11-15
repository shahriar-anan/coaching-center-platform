import { describe, expect, it } from "vitest";
import bn from "@/i18n/messages/bn/notices";
import en from "@/i18n/messages/en/notices";
import { NOTICE_KINDS, noticeHref } from "@/app/(site)/notices/_components/notice-kind";

const RESULT_KINDS = ["OFFLINE_RESULT_PUBLISHED", "OFFLINE_RESULT_UPDATED"] as const;

describe("offline result notices (plan 7.12)", () => {
  it("are known notice kinds", () => {
    for (const kind of RESULT_KINDS) {
      expect(NOTICE_KINDS).toContain(kind);
    }
  });

  it("have their English and Bangla labels", () => {
    expect(en.kind.OFFLINE_RESULT_PUBLISHED).toBe("Exam result published");
    expect(en.kind.OFFLINE_RESULT_UPDATED).toBe("Exam result updated");
    expect(bn.kind.OFFLINE_RESULT_PUBLISHED).toBe("পরীক্ষার ফল প্রকাশিত হয়েছে");
    expect(bn.kind.OFFLINE_RESULT_UPDATED).toBe("পরীক্ষার ফল হালনাগাদ হয়েছে");
  });

  it.each(RESULT_KINDS)("%s opens the results section of the offline activities page", (kind) => {
    expect(noticeHref({ kind, exam: null })).toBe("/offline-activities#results");
  });

  it("still opens the results section when the API also names an exam", () => {
    expect(noticeHref({ kind: "OFFLINE_RESULT_UPDATED", exam: { id: "e1" } })).toBe("/offline-activities#results");
  });
});
