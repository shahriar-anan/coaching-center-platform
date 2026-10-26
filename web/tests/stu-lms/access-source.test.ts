import { describe, expect, it } from "vitest";
import bn from "@/i18n/messages/bn/result";
import en from "@/i18n/messages/en/result";
import { accessSourceKey } from "@/components/student/result/access-source";

describe("access source labels (my modules)", () => {
  it.each([
    ["ROSTER", "সরাসরি ভর্তি", "Added directly"],
    ["MODULE", "মডিউল কেনা", "Module purchase"],
    ["EXAM", "পরীক্ষা কেনা", "Exam purchase"],
    ["MANUAL", "প্রশাসক দিয়েছেন", "Added by the centre"],
  ])("%s has its label in both languages", (via, bnText, enText) => {
    const key = accessSourceKey(via);
    expect(key).not.toBeNull();
    const path = (key as string).split(".").slice(1) as ["access", string];
    expect(bn.access[path[1] as keyof typeof bn.access]).toBe(bnText);
    expect(en.access[path[1] as keyof typeof en.access]).toBe(enText);
  });

  it("falls back to no label when the source is missing or unknown", () => {
    expect(accessSourceKey(null)).toBeNull();
    expect(accessSourceKey(undefined)).toBeNull();
    expect(accessSourceKey("")).toBeNull();
    expect(accessSourceKey("SOMETHING_NEW")).toBeNull();
    expect(accessSourceKey("constructor")).toBeNull();
  });
});
