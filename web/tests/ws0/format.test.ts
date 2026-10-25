import { describe, expect, it } from "vitest";
import {
  bnDayPeriod,
  formatClock,
  formatDate,
  formatDuration,
  formatMoney,
  formatNumber,
  formatRelative,
  formatTime,
  formatTimeRange,
  fromDhakaParts,
  pickText,
  pickTitle,
  relativeDay,
} from "@/lib/format";

// Dhaka is UTC+6. These instants are chosen as Dhaka wall-clock times.
const at = (day: number, hour: number, minute = 0) =>
  fromDhakaParts({ year: 2026, month: 10, day, hour, minute }).toISOString();

describe("formatNumber", () => {
  it("groups thousands and uses Bengali digits in Bangla", () => {
    expect(formatNumber(12000, "bn")).toBe("১২,০০০");
    expect(formatNumber(12000, "en")).toBe("12,000");
  });

  it("keeps the minus sign and the decimals", () => {
    expect(formatNumber(-2500.5, "en", { decimals: 2 })).toBe("-2,500.50");
  });
});

describe("formatMoney", () => {
  it("shows whole taka without decimals in Bangla", () => {
    expect(formatMoney(2500, "bn")).toBe("৳২,৫০০");
  });

  it("shows paisa only when there are some", () => {
    expect(formatMoney(2500.5, "bn")).toBe("৳২,৫০০.৫০");
    expect(formatMoney(2500, "en")).toBe("৳2,500.00");
  });

  it("returns null for a missing price, so the caller writes a sentence", () => {
    expect(formatMoney(null, "bn")).toBeNull();
    expect(formatMoney(undefined, "en")).toBeNull();
  });
});

describe("formatDate", () => {
  it("writes the Bangla month and Bengali digits", () => {
    expect(formatDate(at(12, 10), "bn", { year: "always" })).toBe("১২ নভেম্বর ২০২৬");
  });

  it("drops the year for the current year on request", () => {
    expect(formatDate(at(12, 10), "bn", { year: "auto", now: at(1, 10) })).toBe("১২ নভেম্বর");
  });

  it("adds the weekday when asked", () => {
    // 12 November 2026 is a Thursday.
    expect(formatDate(at(12, 10), "bn", { weekday: true, year: "always" })).toBe(
      "বৃহস্পতিবার, ১২ নভেম্বর ২০২৬",
    );
    expect(formatDate(at(12, 10), "en", { weekday: true, year: "always" })).toBe(
      "Thursday, 12 November 2026",
    );
  });

  it("uses the Dhaka calendar day near midnight", () => {
    // 23:30 in Dhaka on the 11th is 17:30 UTC on the 11th; 00:30 Dhaka on the 12th is still the 11th in UTC.
    expect(formatDate(at(12, 0, 30), "en", { year: "always" })).toBe("12 November 2026");
  });
});

describe("day periods and times", () => {
  it("maps each edge hour to the right Bangla period", () => {
    expect(bnDayPeriod(3)).toBe("রাত");
    expect(bnDayPeriod(4)).toBe("ভোর");
    expect(bnDayPeriod(6)).toBe("সকাল");
    expect(bnDayPeriod(11)).toBe("সকাল");
    expect(bnDayPeriod(12)).toBe("দুপুর");
    expect(bnDayPeriod(15)).toBe("বিকেল");
    expect(bnDayPeriod(18)).toBe("সন্ধ্যা");
    expect(bnDayPeriod(20)).toBe("রাত");
    expect(bnDayPeriod(23)).toBe("রাত");
  });

  it("shows noon and midnight as 12, never 0", () => {
    expect(formatTime(at(12, 12), "bn")).toBe("দুপুর ১২:০০");
    expect(formatTime(at(12, 0), "bn")).toBe("রাত ১২:০০");
    expect(formatTime(at(12, 0), "en")).toBe("12:00 AM");
  });

  it("writes a range with the period once when it is the same", () => {
    expect(formatTimeRange(at(12, 12), at(12, 12, 40), "bn")).toBe("দুপুর ১২:০০ – ১২:৪০");
  });

  it("writes the period again when a range crosses it", () => {
    expect(formatTimeRange(at(12, 11, 50), at(12, 12, 20), "bn")).toBe("সকাল ১১:৫০ – দুপুর ১২:২০");
  });

  it("uses Bengali digits in the clock", () => {
    expect(formatClock(32 * 60 * 1000 + 10 * 1000, "bn")).toBe("০০:৩২:১০");
  });
});

describe("durations and countdowns", () => {
  it("writes minutes and hours in words", () => {
    expect(formatDuration(40, "bn")).toBe("৪০ মিনিট");
    expect(formatDuration(90, "bn")).toBe("১ ঘণ্টা ৩০ মিনিট");
    expect(formatDuration(60, "en")).toBe("1 hour");
  });

  it("writes the time left in words", () => {
    const ms = (2 * 60 + 14) * 60 * 1000;
    expect(formatRelative(ms, "bn")).toBe("২ ঘণ্টা ১৪ মিনিট");
    expect(formatRelative(45 * 1000, "bn")).toBe("৪৫ সেকেন্ড");
  });
});

describe("relativeDay", () => {
  const now = at(12, 9);
  it("names today, tomorrow and yesterday", () => {
    expect(relativeDay(at(12, 20), "bn", now)).toBe("আজ");
    expect(relativeDay(at(13, 8), "bn", now)).toBe("আগামীকাল");
    expect(relativeDay(at(11, 8), "en", now)).toBe("Yesterday");
    expect(relativeDay(at(20, 8), "en", now)).toBeNull();
  });
});

describe("pickTitle and pickText", () => {
  const exam = { titleEn: "CRPC Mock", titleBn: "সিআরপিসি মডেল টেস্ট", descriptionEn: "Notes", descriptionBn: null };

  it("picks the title in the UI language", () => {
    expect(pickTitle(exam, "bn")).toBe("সিআরপিসি মডেল টেস্ট");
    expect(pickTitle(exam, "en")).toBe("CRPC Mock");
  });

  it("falls back to the other language when one is empty", () => {
    expect(pickText(exam, "description", "bn")).toBe("Notes");
    expect(pickText(exam, "description", "en")).toBe("Notes");
  });
});
