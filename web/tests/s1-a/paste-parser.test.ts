import { describe, expect, it } from "vitest";
import { PASTE_FORMAT_EXAMPLE, parsePastedQuestions } from "@/components/staff/catalog/paste-questions";

describe("paste parser", () => {
  it("reads a single-answer block: one star, a single-answer question", () => {
    const [block] = parsePastedQuestions("প্রশ্ন: রাজধানী?\nক) ঢাকা *\nখ) চট্টগ্রাম\nনম্বর: 2");
    expect("question" in block).toBe(true);
    if ("question" in block) {
      expect(block.question.multipleAnswer).toBe(false);
      expect(block.question.marks).toBe(2);
      expect(block.question.options.filter((o) => o.correct).map((o) => o.text)).toEqual(["ঢাকা"]);
    }
  });

  it("reads several stars as a multiple-answer question, with every star option correct", () => {
    const [block] = parsePastedQuestions("Question: none\nক) ঢাকা *\nখ) রাজশাহী *\nগ) সিলেট");
    expect("error" in block && block.error).toBe("noStem");
    const [multi] = parsePastedQuestions("প্রশ্ন: বিভাগীয় শহর কোনগুলো?\nক) ঢাকা *\nখ) রাজশাহী *\nগ) সিলেট\nনম্বর: ২");
    expect("question" in multi).toBe(true);
    if ("question" in multi) {
      expect(multi.question.multipleAnswer).toBe(true);
      expect(multi.question.marks).toBe(2);
      expect(multi.question.options.filter((o) => o.correct).map((o) => o.text)).toEqual(["ঢাকা", "রাজশাহী"]);
    }
  });

  it("reports a block with no correct option", () => {
    const [block] = parsePastedQuestions("প্রশ্ন: কোনটি?\nক) ঢাকা\nখ) সিলেট");
    expect(block).toEqual({ index: 1, error: "noCorrect" });
  });

  it("reports a block with no stem, and one with fewer than two options", () => {
    expect(parsePastedQuestions("ক) ঢাকা *\nখ) সিলেট")[0]).toEqual({ index: 1, error: "noStem" });
    expect(parsePastedQuestions("প্রশ্ন: একটি?\nক) ঢাকা *")[0]).toEqual({ index: 1, error: "fewOptions" });
  });

  it("reports marks that are zero, and reads Bangla digits as marks", () => {
    expect(parsePastedQuestions("প্রশ্ন: একটি?\nক) ঢাকা *\nখ) সিলেট\nনম্বর: 0")[0]).toEqual({ index: 1, error: "badMarks" });
    const [block] = parsePastedQuestions("প্রশ্ন: একটি?\nক) ঢাকা *\nখ) সিলেট\nনম্বর: ৩");
    expect("question" in block && block.question.marks).toBe(3);
  });

  it("keeps the other blocks when one is bad, and reads blocks separated by a blank line", () => {
    const blocks = parsePastedQuestions("প্রশ্ন: এক?\nক) ক *\nখ) খ\n\nপ্রশ্ন: দুই?\nক) ক\nখ) খ");
    expect(blocks).toHaveLength(2);
    expect("question" in blocks[0]).toBe(true);
    expect(blocks[1]).toEqual({ index: 2, error: "noCorrect" });
  });

  it("parses the format example on the screen into one single-answer and one multiple-answer question", () => {
    const blocks = parsePastedQuestions(PASTE_FORMAT_EXAMPLE);
    expect(blocks).toHaveLength(2);
    const [single, multi] = blocks;
    expect("question" in single && single.question.multipleAnswer).toBe(false);
    expect("question" in multi && multi.question.multipleAnswer).toBe(true);
  });
});
