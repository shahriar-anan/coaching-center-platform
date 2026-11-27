import { describe, expect, it } from "vitest";
import { amountInWords, banglaWords, englishWords } from "@/components/staff/accounting/amount-words";

/** Amounts in words for vouchers (accounting-plan 4.6, B10): Indian grouping, paisa after the taka, "only" and "মাত্র". */
describe("amount in words, English", () => {
  it("writes the taka with lakh and crore, as Indian accounts do", () => {
    expect(amountInWords("12500.00", "en")).toBe("Twelve thousand five hundred taka only");
    expect(amountInWords("100000.00", "en")).toBe("One lakh taka only");
    expect(amountInWords("10000000.00", "en")).toBe("One crore taka only");
    expect(amountInWords("1250.00", "en")).toBe("One thousand two hundred fifty taka only");
  });

  it("writes the paisa after the taka", () => {
    expect(amountInWords("2050.50", "en")).toBe("Two thousand fifty taka and fifty paisa only");
    expect(amountInWords("7.05", "en")).toBe("Seven taka and five paisa only");
  });

  it("writes zero, and the tens with a hyphen", () => {
    expect(englishWords(0)).toBe("Zero");
    expect(englishWords(21)).toBe("Twenty-one");
    expect(englishWords(905)).toBe("Nine hundred five");
  });
});

describe("amount in words, Bangla", () => {
  it("writes the taka in the usual Bangla words", () => {
    expect(amountInWords("12500.00", "bn")).toBe("বারো হাজার পাঁচশত টাকা মাত্র");
    expect(amountInWords("100000.00", "bn")).toBe("এক লক্ষ টাকা মাত্র");
    expect(amountInWords("10000000.00", "bn")).toBe("এক কোটি টাকা মাত্র");
  });

  it("writes the paisa, and zero", () => {
    expect(amountInWords("2050.50", "bn")).toBe("দুই হাজার পঞ্চাশ টাকা পঞ্চাশ পয়সা মাত্র");
    expect(banglaWords(0)).toBe("শূন্য");
  });
});
