import { describe, expect, it } from "vitest";
import { outcomeOf } from "@/components/student/result/result-page";

const option = (selected: boolean | null, correct: boolean | null) => ({ selected, correct });

describe("review outcome", () => {
  it("is right when the picked option is the correct one", () => {
    expect(outcomeOf({ options: [option(null, false), option(true, true)] })).toBe("right");
  });

  it("is wrong when a picked option is not correct", () => {
    expect(outcomeOf({ options: [option(true, false), option(null, true)] })).toBe("wrong");
  });

  it("is skipped when nothing was picked", () => {
    expect(outcomeOf({ options: [option(null, false), option(null, true)] })).toBe("skipped");
  });
});
