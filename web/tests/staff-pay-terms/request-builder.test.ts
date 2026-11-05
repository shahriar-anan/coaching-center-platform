import { describe, expect, it } from "vitest";
import { buildNewExamRequest, type NewExamForm, type NewExamPay, type NewExamPlace } from "@/components/staff/catalog/new-exam-dialog";

const filled: NewExamForm = { titleEn: "Muslim Law", titleBn: "মুসলিম আইন", paperLanguage: "", durationMinutes: "120" };
const inPackage: NewExamPlace = { mode: "package", id: "pkg-1" };
const inCategory: NewExamPlace = { mode: "category", id: "cat-1" };
const withMarker: NewExamPay = { sheetRate: "60", instructorId: "ins-1" };
const noMarker: NewExamPay = { sheetRate: "50", instructorId: "" };

describe("buildNewExamRequest for a written exam's pay (P18)", () => {
  it("adds the pay per sheet and the marker to a written exam", () => {
    const built = buildNewExamRequest(filled, "WRITTEN", inPackage, withMarker);
    expect(built.ok).toBe(true);
    if (built.ok) {
      expect(built.request.path).toBe("/api/v1/live-exam-modules/pkg-1/exams");
      expect(built.request.body).toEqual({
        examType: "WRITTEN",
        titleEn: "Muslim Law",
        titleBn: "মুসলিম আইন",
        durationMinutes: 120,
        sheetRate: 60,
        instructorId: "ins-1",
      });
    }
  });

  it("leaves the marker out when none is chosen, and still sends the rate", () => {
    const built = buildNewExamRequest(filled, "WRITTEN", inPackage, noMarker);
    expect(built.ok).toBe(true);
    if (built.ok) {
      expect(built.request.body.sheetRate).toBe(50);
      expect("instructorId" in built.request.body).toBe(false);
    }
  });

  it("refuses a missing, zero, negative or non-numeric rate with a field error", () => {
    for (const sheetRate of ["", "0", "-5", "abc", "   "]) {
      const built = buildNewExamRequest(filled, "WRITTEN", inPackage, { sheetRate, instructorId: "" });
      expect(built).toEqual({ ok: false, missing: ["sheetRate"] });
    }
  });

  it("sends the pay fields for a category-only written exam with its categoryId", () => {
    const built = buildNewExamRequest(filled, "WRITTEN", inCategory, withMarker);
    expect(built.ok && built.request.path).toBe("/api/v1/live-exams");
    expect(built.ok && built.request.body).toMatchObject({ categoryId: "cat-1", sheetRate: 60, instructorId: "ins-1" });
  });

  it("never sends the pay fields for an MCQ exam, even when they are given", () => {
    const built = buildNewExamRequest(filled, "MCQ", inPackage, withMarker);
    expect(built.ok).toBe(true);
    if (built.ok) {
      expect("sheetRate" in built.request.body).toBe(false);
      expect("instructorId" in built.request.body).toBe(false);
    }
  });

  it("sends no pay fields when the dialog does not pass them (a System Admin creates the exam)", () => {
    const built = buildNewExamRequest(filled, "WRITTEN", inPackage);
    expect(built.ok).toBe(true);
    if (built.ok) {
      expect("sheetRate" in built.request.body).toBe(false);
      expect("instructorId" in built.request.body).toBe(false);
    }
  });

  it("keeps the existing checks: the titles, duration and place are still required for a written exam", () => {
    const built = buildNewExamRequest({ ...filled, titleEn: "" }, "WRITTEN", { mode: "package", id: "" }, noMarker);
    expect(built).toEqual({ ok: false, missing: ["titleEn", "place"] });
  });
});
