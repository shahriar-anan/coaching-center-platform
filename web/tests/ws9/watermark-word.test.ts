import { describe, expect, it } from "vitest";
import enCatalog from "@/i18n/messages/en/staffCatalog";
import bnCatalog from "@/i18n/messages/bn/staffCatalog";
import enMarking from "@/i18n/messages/en/staffMarking";
import bnMarking from "@/i18n/messages/bn/staffMarking";
import enPeople from "@/i18n/messages/en/staffPeople";
import bnPeople from "@/i18n/messages/bn/staffPeople";

/** The old diagonal brand word. Papers now carry the school mark once per page, not this text. */
const OLD_WORD = "SYMAMLAW";

describe("papers carry no diagonal brand word", () => {
  it.each([
    ["catalog (preview and written paper)", enCatalog, bnCatalog],
    ["marking (question paper, answer script and marked file)", enMarking, bnMarking],
    ["people (submission page)", enPeople, bnPeople],
  ])("%s carries no brand word in either language", (_name, en, bn) => {
    expect(JSON.stringify(en)).not.toContain(OLD_WORD);
    expect(JSON.stringify(bn)).not.toContain(OLD_WORD);
  });
});
