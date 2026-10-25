import { describe, expect, it } from "vitest";
import bn from "@/i18n/messages/bn/notices";
import en from "@/i18n/messages/en/notices";
import {
  NOTICE_KINDS,
  isKnownNoticeKind,
  noticeKindLabelKey,
} from "@/app/(site)/notices/_components/notice-kind";

describe("notice kind labels (S-002)", () => {
  it.each(NOTICE_KINDS)("%s has a label in Bangla and English", (kind) => {
    expect(bn.kind[kind]).toBeTruthy();
    expect(en.kind[kind]).toBeTruthy();
  });

  it("the two retired keys are gone from both languages", () => {
    for (const catalog of [bn.kind, en.kind]) {
      expect(catalog).not.toHaveProperty("REMINDER");
      expect(catalog).not.toHaveProperty("MCQ_RESULT");
    }
  });

  it("an unknown kind falls back to the generic label", () => {
    expect(isKnownNoticeKind("SOMETHING_NEW")).toBe(false);
    expect(noticeKindLabelKey("SOMETHING_NEW")).toBe("notices.kind.other");
    expect(bn.kind.other).toBeTruthy();
    expect(en.kind.other).toBeTruthy();
  });

  it("a known kind maps to its own label key", () => {
    expect(noticeKindLabelKey("ANNOUNCEMENT")).toBe("notices.kind.ANNOUNCEMENT");
  });
});
