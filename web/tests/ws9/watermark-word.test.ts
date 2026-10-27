import { describe, expect, it } from "vitest";
import enCatalog from "@/i18n/messages/en/staffCatalog";
import bnCatalog from "@/i18n/messages/bn/staffCatalog";
import enMarking from "@/i18n/messages/en/staffMarking";
import bnMarking from "@/i18n/messages/bn/staffMarking";
import enPeople from "@/i18n/messages/en/staffPeople";
import bnPeople from "@/i18n/messages/bn/staffPeople";

/** The brand word drawn across every question paper and script that staff open. A student's own sitting uses their name and code. */
const WORD = "SYMAMLAW";

describe("watermark word on papers staff open", () => {
  it.each([
    ["exam preview", enCatalog.preview.watermark, bnCatalog.preview.watermark],
    ["written paper, staff view", enCatalog.written.staffWatermark, bnCatalog.written.staffWatermark],
    ["marking: question paper", enMarking.questionPaper.watermark, bnMarking.questionPaper.watermark],
    ["marking: answer script and marked file", enMarking.form.watermark, bnMarking.form.watermark],
    ["submission page", enPeople.submission.watermark, bnPeople.submission.watermark],
  ])("%s reads %s in both languages", (_name, en, bn) => {
    expect(en).toBe(WORD);
    expect(bn).toBe(WORD);
  });
});
