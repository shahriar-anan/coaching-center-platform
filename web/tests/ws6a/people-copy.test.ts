import { describe, expect, it } from "vitest";
import {
  accessReasonKey,
  isDraft,
  matchesName,
  matchesSubmissionFilter,
  nextToMark,
  neighbour,
} from "@/components/staff/people/people-copy";
import type { SubmissionRow } from "@/lib/api/exam/staff-people";

function row(id: string, overrides: Partial<SubmissionRow> = {}): SubmissionRow {
  return {
    id,
    examId: "e1",
    studentId: `s-${id}`,
    studentName: `Student ${id}`,
    status: "SUBMITTED",
    score: null,
    maximum: 50,
    passed: null,
    kind: "LIVE",
    answerPdf: true,
    evaluatedPdf: null,
    evaluatorName: null,
    published: false,
    ...overrides,
  };
}

describe("access reasons", () => {
  it("names each reason with its own key", () => {
    expect(accessReasonKey("ROSTER")).toBe("staffPeople.student.reason.ROSTER");
    expect(accessReasonKey("MODULE_PURCHASE")).toBe("staffPeople.student.reason.MODULE_PURCHASE");
    expect(accessReasonKey("EXAM_PURCHASE")).toBe("staffPeople.student.reason.EXAM_PURCHASE");
  });
});

describe("submission filters", () => {
  const draft = row("d", { evaluatedPdf: true });
  const published = row("p", { evaluatedPdf: true, published: true });
  const failed = row("f", { status: "FAILED_TO_SUBMIT", answerPdf: false });

  it("treats a marked script that is not published as a draft", () => {
    expect(isDraft(draft)).toBe(true);
    expect(isDraft(published)).toBe(false);
    expect(isDraft(row("n"))).toBe(false);
  });

  it("applies each filter to the loaded rows", () => {
    expect(matchesSubmissionFilter(draft, "all")).toBe(true);
    expect(matchesSubmissionFilter(draft, "answer")).toBe(true);
    expect(matchesSubmissionFilter(failed, "answer")).toBe(false);
    expect(matchesSubmissionFilter(failed, "failed")).toBe(true);
    expect(matchesSubmissionFilter(draft, "draft")).toBe(true);
    expect(matchesSubmissionFilter(published, "draft")).toBe(false);
    expect(matchesSubmissionFilter(published, "published")).toBe(true);
  });

  it("matches a name without regard to case", () => {
    expect(matchesName(row("1"), "student 1")).toBe(true);
    expect(matchesName(row("1"), "rahim")).toBe(false);
    expect(matchesName(row("1"), "  ")).toBe(true);
  });
});

describe("marking queue", () => {
  const rows = [row("a"), row("b", { evaluatedPdf: true }), row("c"), row("d", { answerPdf: false })];

  it("moves to the neighbour in list order and stops at the ends", () => {
    expect(neighbour(rows, "a", 1)?.id).toBe("b");
    expect(neighbour(rows, "a", -1)).toBeNull();
    expect(neighbour(rows, "d", 1)).toBeNull();
  });

  it("picks the next paper with an answer and no draft, wrapping round", () => {
    expect(nextToMark(rows, "a")?.id).toBe("c");
    expect(nextToMark(rows, "c")?.id).toBe("a");
  });

  it("returns null when nothing is left to mark", () => {
    expect(nextToMark([row("only")], "only")).toBeNull();
  });
});
