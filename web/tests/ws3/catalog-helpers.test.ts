import { expect, it } from "vitest";
import type { RoutineRow } from "@/lib/api/exam/catalog";
import { buildIcs, groupRoutine, statusPill } from "@/components/student/catalog/catalog-helpers";

function row(id: string, liveAt: string, endAt: string): RoutineRow {
  return {
    id,
    moduleId: "m1",
    examType: "MCQ",
    titleEn: `Exam ${id}`,
    titleBn: `পরীক্ষা ${id}`,
    moduleTitleEn: "Module",
    moduleTitleBn: "মডিউল",
    categories: [],
    liveAt,
    endAt,
    durationMinutes: 40,
    myStatus: "UPCOMING",
    retakeAvailable: null,
  };
}

it("maps the written failed-to-submit status to its own wording", () => {
  expect(statusPill("FAILED_TO_SUBMIT", { examType: "WRITTEN" }).key).toBe(
    "catalog.status.FAILED_TO_SUBMIT_WRITTEN",
  );
  expect(statusPill("FAILED_TO_SUBMIT", { examType: "MCQ" }).key).toBe("catalog.status.FAILED_TO_SUBMIT");
});

it("offers the retake wording only when a retake is available", () => {
  expect(statusPill("MISSED", { examType: "MCQ", retakeAvailable: true }).key).toBe(
    "catalog.status.MISSED_RETAKE",
  );
  expect(statusPill("MISSED", { examType: "MCQ", retakeAvailable: false }).key).toBe("catalog.status.MISSED");
});

it("pulses only the open and in-progress pills", () => {
  expect(statusPill("OPEN", { examType: "MCQ" }).pulse).toBe(true);
  expect(statusPill("IN_PROGRESS", { examType: "MCQ" }).pulse).toBe(true);
  expect(statusPill("UPCOMING", { examType: "MCQ" }).pulse).toBe(false);
});

it("groups the routine by Dhaka day, live rows first, and drops finished exams", () => {
  // 2026-10-25 12:00 in Asia/Dhaka.
  const now = Date.parse("2026-10-25T06:00:00Z");
  const live = row("live", "2026-10-25T05:30:00Z", "2026-10-25T06:30:00Z");
  const tomorrow = row("tomorrow", "2026-10-26T06:00:00Z", "2026-10-26T06:40:00Z");
  const done = row("done", "2026-10-24T05:00:00Z", "2026-10-24T05:40:00Z");

  const grouped = groupRoutine([tomorrow, done, live], now);

  expect(grouped.live.map((r) => r.id)).toEqual(["live"]);
  expect(grouped.days).toHaveLength(1);
  expect(grouped.days[0].relative).toBe("tomorrow");
  expect(grouped.days[0].rows.map((r) => r.id)).toEqual(["tomorrow"]);
});

it("builds an .ics event with UTC times and escaped text", () => {
  const ics = buildIcs({
    id: "exam-1",
    title: "CrPC; part, one",
    start: "2026-10-26T06:00:00Z",
    end: "2026-10-26T06:40:00Z",
    url: "https://example.test/exam/exam-1",
  });

  expect(ics).toContain("BEGIN:VEVENT");
  expect(ics).toContain("DTSTART:20261026T060000Z");
  expect(ics).toContain("DTEND:20261026T064000Z");
  expect(ics).toContain("SUMMARY:CrPC\\; part\\, one");
  expect(ics).toContain("URL:https://example.test/exam/exam-1");
  expect(ics.endsWith("END:VCALENDAR\r\n")).toBe(true);
});
