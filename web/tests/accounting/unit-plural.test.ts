import { describe, expect, it } from "vitest";
import { pluralEnglish, unitWithCount } from "@/components/staff/accounting/accounting-model";

/*
 * A counted unit reads with its count: "5 classes", not "5 classs". The plural of a singular unit is taken from its ending.
 */
describe("counted units with a count", () => {
  it("makes the plural from the ending of the singular", () => {
    expect(pluralEnglish("class")).toBe("classes");
    expect(pluralEnglish("poster")).toBe("posters");
    expect(pluralEnglish("answer sheet")).toBe("answer sheets");
    expect(pluralEnglish("hour")).toBe("hours");
    expect(pluralEnglish("question")).toBe("questions");
    expect(pluralEnglish("body")).toBe("bodies");
    expect(pluralEnglish("box")).toBe("boxes");
  });

  it("keeps the singular for a count of one and for Bangla", () => {
    expect(unitWithCount("class", "1", "en")).toBe("class");
    expect(unitWithCount("class", "5", "en")).toBe("classes");
    expect(unitWithCount("ক্লাস", "5", "bn")).toBe("ক্লাস");
  });
});
