import { describe, expect, it } from "vitest";
import { primaryExamAction } from "@/lib/api/exam/staff-catalog";

type Exam = Parameters<typeof primaryExamAction>[0];

const draft: Exam = { status: "DRAFT", finallySaved: false, pendingChanges: false };
const saved: Exam = { status: "DRAFT", finallySaved: true, pendingChanges: false };
const published: Exam = { status: "PUBLISHED", finallySaved: true, pendingChanges: false };
const staged: Exam = { status: "PUBLISHED", finallySaved: true, pendingChanges: true };

describe("primaryExamAction (plan 7.2 header table)", () => {
  it("offers Save all changes for a draft with unsaved edits, whatever the role", () => {
    expect(primaryExamAction(draft, { dirty: true, masterAdmin: true })).toBe("save");
    expect(primaryExamAction(draft, { dirty: true, masterAdmin: false })).toBe("save");
  });

  it("offers Publish to the Master Admin on a saved draft, and a final save first when it is missing", () => {
    expect(primaryExamAction(saved, { dirty: false, masterAdmin: true })).toBe("publish");
    expect(primaryExamAction(draft, { dirty: false, masterAdmin: true })).toBe("finalSave");
  });

  it("offers nothing on a saved draft to a System Admin, who cannot publish", () => {
    expect(primaryExamAction(saved, { dirty: false, masterAdmin: false })).toBe("none");
  });

  it("offers nothing on a published exam with no staged changes", () => {
    expect(primaryExamAction(published, { dirty: false, masterAdmin: true })).toBe("none");
    expect(primaryExamAction(published, { dirty: false, masterAdmin: false })).toBe("none");
  });

  it("offers Publish changes to the Master Admin only when changes are staged", () => {
    expect(primaryExamAction(staged, { dirty: false, masterAdmin: true })).toBe("publishChanges");
    expect(primaryExamAction(staged, { dirty: false, masterAdmin: false })).toBe("none");
  });

  it("saves an unsaved edit on a published exam, which stages it", () => {
    expect(primaryExamAction(published, { dirty: true, masterAdmin: true })).toBe("save");
  });

  it("offers nothing on an archived exam", () => {
    const archived: Exam = { status: "ARCHIVED", finallySaved: true, pendingChanges: true };
    expect(primaryExamAction(archived, { dirty: true, masterAdmin: true })).toBe("none");
    expect(primaryExamAction(archived, { dirty: false, masterAdmin: true })).toBe("none");
  });
});
