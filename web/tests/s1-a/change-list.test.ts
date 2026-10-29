import { describe, expect, it } from "vitest";
import { changeRows, changeValueText, impactCounts } from "@/components/staff/catalog/change-list-dialog";
import type { PendingChangeList } from "@/lib/api/exam/staff-catalog";

const words = { empty: "none", yes: "yes", no: "no" };

describe("change list rendering", () => {
  it("shows a rich text field as plain text, without its tags", () => {
    expect(changeValueText("rulesEn", "<p>Be <strong>on time</strong></p><p>No phones</p>", words)).toBe(
      "Be on time No phones",
    );
  });

  it("shows a missing value as the empty word and a boolean as yes or no", () => {
    expect(changeValueText("liveAt", null, words)).toBe("none");
    expect(changeValueText("rulesBn", "", words)).toBe("none");
    expect(changeValueText("free", true, words)).toBe("yes");
    expect(changeValueText("previewEnabled", false, words)).toBe("no");
  });

  it("keeps a plain value as it is, for a title or a number", () => {
    expect(changeValueText("titleEn", "Quiz & more", words)).toBe("Quiz & more");
    expect(changeValueText("durationMinutes", 40, words)).toBe("40");
  });

  it("lists every changed field, before and after, in the server's order", () => {
    const rows = changeRows(
      [
        { field: "titleEn", before: "Old", after: "New" },
        { field: "descriptionEn", before: "<p>A</p>", after: "<p>B</p>" },
      ],
      words,
    );
    expect(rows).toEqual([
      { field: "titleEn", before: "Old", after: "New" },
      { field: "descriptionEn", before: "A", after: "B" },
    ]);
  });

  it("counts the impact: papers to re-score, students to tell, questions and package changes", () => {
    const changes: PendingChangeList = {
      revisionId: "r1",
      fields: [],
      questions: [{ questionId: "q1", position: 1, change: "UPDATED", before: "a", after: "b" }],
      paper: { beforeVersion: 1, afterVersion: 2, afterFileName: "paper.pdf" },
      packageExams: [],
      impact: { papersToRescore: 3, notifiesStudents: true, studentsToNotify: 12 },
    };
    expect(impactCounts(changes)).toEqual({ papers: 3, students: 12, notify: true, questions: 1, packageExams: 0 });
  });

  it("reports no students to tell when notifications are off", () => {
    const changes = {
      revisionId: "r2",
      fields: [],
      questions: [],
      paper: null,
      packageExams: [],
      impact: { papersToRescore: 0, notifiesStudents: false, studentsToNotify: 0 },
    } as PendingChangeList;
    expect(impactCounts(changes).notify).toBe(false);
  });
});
