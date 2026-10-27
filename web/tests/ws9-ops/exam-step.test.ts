import { describe, expect, it } from "vitest";
import {
  focusTotals,
  isFocus,
  matchesFocus,
  paperCounts,
  phaseOf,
  stepOf,
} from "@/components/staff/participation/exam-step";
import type { ParticipationRow } from "@/lib/api/exam/staff-people";

const NOW = Date.parse("2026-10-27T12:00:00Z");

function row(extra: Partial<ParticipationRow> = {}): ParticipationRow {
  return {
    examId: "e1",
    moduleId: "m1",
    moduleTitleEn: "Law",
    moduleTitleBn: "আইন",
    examType: "WRITTEN",
    titleEn: "Paper",
    titleBn: "পত্র",
    liveAt: "2026-10-27T06:00:00Z",
    endAt: "2026-10-27T07:00:00Z",
    sitting: 0,
    submitted: 4,
    failedToSubmit: 0,
    awaitingMarking: 0,
    draftsAwaitingPublish: 0,
    published: 0,
    retakes: 0,
    ...extra,
  };
}

describe("the next step of an exam", () => {
  it("is automatic for an MCQ, whatever its numbers", () => {
    expect(stepOf(row({ examType: "MCQ", awaitingMarking: null }), NOW)).toBe("auto");
  });

  it("goes from not started, to open, to mark, to release, to done", () => {
    expect(stepOf(row({ liveAt: "2026-10-28T06:00:00Z", endAt: "2026-10-28T07:00:00Z" }), NOW)).toBe("upcoming");
    expect(stepOf(row({ endAt: "2026-10-27T13:00:00Z" }), NOW)).toBe("open");
    expect(stepOf(row({ sitting: 2 }), NOW)).toBe("open");
    expect(stepOf(row({ awaitingMarking: 3, draftsAwaitingPublish: 1 }), NOW)).toBe("mark");
    expect(stepOf(row({ draftsAwaitingPublish: 2 }), NOW)).toBe("release");
    expect(stepOf(row({ published: 4 }), NOW)).toBe("done");
    expect(stepOf(row({ submitted: 0 }), NOW)).toBe("empty");
  });

  it("reads counts the API leaves out as zero", () => {
    const counts = paperCounts(row({ awaitingMarking: undefined, draftsAwaitingPublish: null, published: undefined }));
    expect(counts).toMatchObject({ toMark: 0, toRelease: 0, released: 0, marked: 0 });
  });

  it("counts marked papers as those waiting for release plus those released", () => {
    expect(paperCounts(row({ draftsAwaitingPublish: 2, published: 3 })).marked).toBe(5);
  });

  it("tells upcoming, live and closed apart, and an exam with no dates", () => {
    expect(phaseOf(row({ liveAt: "2026-10-28T06:00:00Z", endAt: "2026-10-28T07:00:00Z" }), NOW)).toBe("upcoming");
    expect(phaseOf(row({ endAt: "2026-10-27T13:00:00Z" }), NOW)).toBe("live");
    expect(phaseOf(row(), NOW)).toBe("closed");
    expect(phaseOf(row({ liveAt: null, endAt: null }), NOW)).toBe("unset");
  });
});

describe("the tabs", () => {
  const rows = [
    row({ examId: "a", draftsAwaitingPublish: 2, correctionsWaiting: 1 }),
    row({ examId: "b", awaitingMarking: 3 }),
    row({ examId: "c", examType: "MCQ", awaitingMarking: null, draftsAwaitingPublish: null }),
  ];

  it("keeps only the exams that fit the tab, and never an MCQ", () => {
    expect(rows.filter((r) => matchesFocus(r, "release")).map((r) => r.examId)).toEqual(["a"]);
    expect(rows.filter((r) => matchesFocus(r, "mark")).map((r) => r.examId)).toEqual(["b"]);
    expect(rows.filter((r) => matchesFocus(r, "changes")).map((r) => r.examId)).toEqual(["a"]);
    expect(rows.filter((r) => matchesFocus(r, "")).length).toBe(3);
  });

  it("totals papers, and the exams the papers to release sit in", () => {
    expect(focusTotals(rows)).toEqual({ release: 2, releaseExams: 1, mark: 3, changes: 1 });
  });

  it("accepts only its own values from the URL", () => {
    expect(isFocus("release")).toBe(true);
    expect(isFocus("nonsense")).toBe(false);
  });
});
