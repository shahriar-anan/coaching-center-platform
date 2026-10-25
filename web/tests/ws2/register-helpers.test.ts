import { describe, expect, it } from "vitest";
import { formatPhone, passwordStatus } from "@/app/(auth)/_components/validate";

describe("formatPhone", () => {
  it("groups an 01 number as 3-4-4 while the student types", () => {
    expect(formatPhone("01")).toBe("01");
    expect(formatPhone("0171")).toBe("017 1");
    expect(formatPhone("01712345678")).toBe("017 1234 5678");
  });

  it("drops digits past eleven and keeps the +880 form", () => {
    expect(formatPhone("017123456789999")).toBe("017 1234 5678");
    expect(formatPhone("+8801712345678")).toBe("+8801712345678");
  });

  it("removes characters that are not digits", () => {
    expect(formatPhone("017-12a34")).toBe("017 1234");
  });
});

describe("passwordStatus", () => {
  it("counts the characters still needed for the 8 character minimum", () => {
    expect(passwordStatus("abc")).toEqual({ length: 3, short: 5, ok: false });
    expect(passwordStatus("abcdefgh")).toEqual({ length: 8, short: 0, ok: true });
  });

  it("is not ok past 72 characters", () => {
    expect(passwordStatus("a".repeat(73)).ok).toBe(false);
  });
});
