import { describe, expect, it } from "vitest";
import { editImpactKeys, editNeedsPrompt } from "@/components/staff/catalog/edit-impact";

const draft = { status: "DRAFT", publishedAt: null, started: false } as const;
const published = { status: "PUBLISHED", publishedAt: "2026-11-01T00:00:00Z", started: false } as const;
const short = (keys: string[]) => keys.map((key) => key.replace("staffCatalog.editImpact.", ""));

describe("when an edit asks first", () => {
  it("does not ask on a draft nobody has sat", () => {
    expect(editNeedsPrompt(draft)).toBe(false);
  });

  it("asks on a published exam, and on a withdrawn one that was published", () => {
    expect(editNeedsPrompt(published)).toBe(true);
    expect(editNeedsPrompt({ ...published, status: "ARCHIVED" })).toBe(true);
  });

  it("does not ask on a withdrawn exam that was never published", () => {
    expect(editNeedsPrompt({ status: "ARCHIVED", publishedAt: null, started: false })).toBe(false);
  });

  it("asks on a restored draft that has attempts", () => {
    expect(editNeedsPrompt({ ...draft, started: true })).toBe(true);
  });
});

describe("the consequences listed", () => {
  it("names who publishes the staged change", () => {
    expect(short(editImpactKeys(published, "questions", { masterAdmin: true, totalChanges: true }))).toContain("stagedMaster");
    expect(short(editImpactKeys(published, "questions", { masterAdmin: false, totalChanges: true }))).toContain("stagedSystem");
  });

  it("says what started attempts keep, by the kind of edit", () => {
    const started = { ...published, started: true };
    const options = { masterAdmin: true, totalChanges: true };
    expect(short(editImpactKeys(started, "maximum", options))).toContain("startedMaximum");
    expect(short(editImpactKeys(started, "questions", options))).toContain("startedQuestions");
    expect(short(editImpactKeys(started, "paper", options))).toContain("startedPaper");
  });

  it("mentions the leaderboard only when attempts exist and the total moves", () => {
    expect(short(editImpactKeys(published, "questions", { masterAdmin: true, totalChanges: true }))).not.toContain("leaderboard");
    const started = { ...published, started: true };
    expect(short(editImpactKeys(started, "paper", { masterAdmin: true, totalChanges: false }))).not.toContain("leaderboard");
    expect(short(editImpactKeys(started, "questions", { masterAdmin: true, totalChanges: true }))).toContain("leaderboard");
  });

  it("does not promise silence for a Word upload, which can change the exam time", () => {
    expect(short(editImpactKeys(published, "replace", { masterAdmin: true, totalChanges: true }))).not.toContain("noNotice");
  });
});
