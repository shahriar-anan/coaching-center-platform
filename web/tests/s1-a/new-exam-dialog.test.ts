import { describe, expect, it } from "vitest";
import { buildNewExamRequest, type NewExamForm, type NewExamPlace } from "@/components/staff/catalog/new-exam-dialog";

const filled: NewExamForm = { titleEn: " Quiz ", titleBn: "কুইজ", paperLanguage: "", durationMinutes: "40" };
const inPackage: NewExamPlace = { mode: "package", id: "pkg-1" };
const inCategory: NewExamPlace = { mode: "category", id: "cat-1" };

describe("buildNewExamRequest", () => {
  it("leaves paper language out of the body when none is chosen (D5, C2)", () => {
    const built = buildNewExamRequest(filled, "MCQ", inPackage);
    expect(built.ok).toBe(true);
    if (built.ok) {
      expect("paperLanguage" in built.request.body).toBe(false);
      expect(built.request.body).toEqual({ examType: "MCQ", titleEn: "Quiz", titleBn: "কুইজ", durationMinutes: 40 });
    }
  });

  it("sends the chosen paper language, including Both", () => {
    for (const language of ["BN", "EN", "BOTH"] as const) {
      const built = buildNewExamRequest({ ...filled, paperLanguage: language }, "WRITTEN", inPackage);
      expect(built.ok && built.request.body.paperLanguage).toBe(language);
    }
  });

  it("validates only the titles, the duration and the place, not the description or rules", () => {
    const built = buildNewExamRequest({ ...filled, titleBn: " " }, "MCQ", inPackage);
    expect(built).toEqual({ ok: false, missing: ["titleBn"] });
    const nothing = buildNewExamRequest({ titleEn: "", titleBn: "", paperLanguage: "", durationMinutes: "" }, "MCQ", {
      mode: "package",
      id: "",
    });
    expect(nothing).toEqual({ ok: false, missing: ["titleEn", "titleBn", "durationMinutes", "place"] });
  });

  it("rejects a duration under two minutes or not a whole number", () => {
    for (const duration of ["1", "2.5", "abc", ""]) {
      const built = buildNewExamRequest({ ...filled, durationMinutes: duration }, "MCQ", inPackage);
      expect(built).toEqual({ ok: false, missing: ["durationMinutes"] });
    }
    expect(buildNewExamRequest({ ...filled, durationMinutes: "2" }, "MCQ", inPackage).ok).toBe(true);
  });

  it("posts into a package's own exam list, with no category in the body", () => {
    const built = buildNewExamRequest(filled, "MCQ", inPackage);
    expect(built.ok && built.request.path).toBe("/api/v1/live-exam-modules/pkg-1/exams");
    expect(built.ok && "categoryId" in built.request.body).toBe(false);
  });

  it("posts a category-only exam to the standalone create with its categoryId", () => {
    const built = buildNewExamRequest(filled, "MCQ", inCategory);
    expect(built.ok && built.request.path).toBe("/api/v1/live-exams");
    expect(built.ok && built.request.body.categoryId).toBe("cat-1");
  });
});
