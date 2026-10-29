import { describe, expect, it } from "vitest";
import {
  examBodyFor,
  examFormErrors,
  examFormState,
  examChecklist,
  examPublishable,
  sectionDirty,
  type ExamFormContext,
} from "@/components/staff/catalog/exam-form-state";
import type { StaffExam } from "@/lib/api/exam/staff-catalog";

const mcq: ExamFormContext = { written: false, canSetPrice: true };
const written: ExamFormContext = { written: true, canSetPrice: true };
const sysAdmin: ExamFormContext = { written: false, canSetPrice: false };

/** A draft MCQ exam with a one-hour window (06:00 to 07:00 UTC) and a 40 minute duration. */
function exam(overrides: Partial<StaffExam> = {}): StaffExam {
  return {
    id: "e1",
    moduleId: null,
    examType: "MCQ",
    paperLanguage: "BOTH",
    titleEn: "Quiz",
    titleBn: "কুইজ",
    descriptionEn: "English description",
    descriptionBn: "বাংলা বিবরণ",
    rulesEn: "English rules",
    rulesBn: "বাংলা নিয়ম",
    liveAt: "2026-10-20T06:00:00Z",
    endAt: "2026-10-20T07:00:00Z",
    durationMinutes: 40,
    passPercent: 50,
    maximum: 0,
    status: "DRAFT",
    finallySaved: false,
    price: null,
    questionPdf: null,
    questions: [{ id: "q1", stem: "Stem", marks: 1, explanation: null, position: 0, options: [] }],
    mode: "LIVE",
    answersChangeable: true,
    negativeMarks: null,
    free: false,
    pendingChanges: false,
    ...overrides,
  } as StaffExam;
}

describe("exam form state", () => {
  it("reads a null paper language as an empty choice, and Both as Both", () => {
    expect(examFormState(exam({ paperLanguage: null })).paperLanguage).toBe("");
    expect(examFormState(exam({ paperLanguage: "BOTH" })).paperLanguage).toBe("BOTH");
  });

  it("turns negative marking on only when the saved mark is above zero", () => {
    expect(examFormState(exam({ negativeMarks: null })).negativeOn).toBe(false);
    expect(examFormState(exam({ negativeMarks: 0.25 })).negativeOn).toBe(true);
    expect(examFormState(exam({ negativeMarks: null })).negativeMarks).toBe("0.25");
  });
});

describe("window against duration (EXAM_WINDOW_INVALID, checked in the form)", () => {
  it("accepts a window at least as long as the duration", () => {
    expect(examFormErrors(examFormState(exam()), "schedule", mcq)).toEqual({});
  });

  it("refuses a window shorter than the duration, on the end field", () => {
    const form = examFormState(exam({ endAt: "2026-10-20T06:30:00Z" }));
    expect(examFormErrors(form, "schedule", mcq)).toEqual({ window: "staffCatalog.examMeta.windowShort" });
  });

  it("refuses an end before the start", () => {
    const form = examFormState(exam({ endAt: "2026-10-20T05:00:00Z" }));
    expect(examFormErrors(form, "schedule", mcq)).toEqual({ window: "staffCatalog.examMeta.windowOrder" });
  });

  it("does not check a practice exam or an undated one", () => {
    const practice = examFormState(exam({ mode: "PRACTICE", endAt: "2026-10-20T06:05:00Z" }));
    expect(examFormErrors(practice, "schedule", mcq)).toEqual({});
    const undated = examFormState(exam({ liveAt: null, endAt: null }));
    expect(examFormErrors(undated, "schedule", mcq)).toEqual({});
  });

  it("sends no window for a practice exam", () => {
    const body = examBodyFor(examFormState(exam({ mode: "PRACTICE" })), "schedule", mcq);
    expect(body.mode).toBe("PRACTICE");
    expect(body.liveAt).toBeNull();
    expect(body.endAt).toBeNull();
  });
});

describe("price and access", () => {
  it("refuses a discount outside 1 to 99", () => {
    expect(examFormErrors({ ...examFormState(exam()), discountPercent: "0" }, "price", mcq)).toEqual({
      discountPercent: "staffCatalog.examMeta.discountInvalid",
    });
    expect(examFormErrors({ ...examFormState(exam()), discountPercent: "100" }, "price", mcq).discountPercent).toBe(
      "staffCatalog.examMeta.discountInvalid",
    );
    expect(examFormErrors({ ...examFormState(exam()), discountPercent: "99" }, "price", mcq)).toEqual({});
  });

  it("refuses a free exam that still has a price", () => {
    const form = { ...examFormState(exam()), free: true, price: "250" };
    expect(examFormErrors(form, "price", mcq).price).toBe("staffCatalog.examMeta.freeNoPrice");
    const clean = { ...examFormState(exam()), free: true, price: "" };
    expect(examFormErrors(clean, "price", mcq)).toEqual({});
    expect(examBodyFor(clean, "price", mcq)).toEqual({
      free: true,
      price: null,
      discountPercent: null,
      discountEndsAt: null,
      accessDays: null,
    });
  });

  it("refuses an access period under one day", () => {
    expect(examFormErrors({ ...examFormState(exam()), accessDays: "0" }, "price", mcq).accessDays).toBe(
      "staffCatalog.examMeta.accessDaysInvalid",
    );
  });

  it("does not validate or send the price for a System Admin", () => {
    const form = { ...examFormState(exam()), price: "abc" };
    expect(examFormErrors(form, "price", sysAdmin)).toEqual({});
  });
});

describe("marks and the request body", () => {
  it("needs a negative mark above zero only when negative marking is on", () => {
    const off = { ...examFormState(exam()), negativeOn: false, negativeMarks: "x" };
    expect(examFormErrors(off, "schedule", mcq)).toEqual({});
    const on = { ...examFormState(exam()), negativeOn: true, negativeMarks: "0" };
    expect(examFormErrors(on, "schedule", mcq).negativeMarks).toBe("staffCatalog.examMeta.negativeInvalid");
  });

  it("sends zero negative marks when the switch is off", () => {
    const body = examBodyFor({ ...examFormState(exam()), negativeOn: false }, "schedule", mcq);
    expect(body.negativeMarks).toBe(0);
    expect(body.answersChangeable).toBe(true);
    expect("maximum" in body).toBe(false);
  });

  it("needs a maximum for a written exam, and sends no marks switches", () => {
    const form = { ...examFormState(exam({ examType: "WRITTEN", maximum: 0 })), maximum: "0" };
    expect(examFormErrors(form, "schedule", written).maximum).toBe("staffCatalog.examMeta.maximumInvalid");
    const body = examBodyFor({ ...form, maximum: "50" }, "schedule", written);
    expect(body.maximum).toBe(50);
    expect("negativeMarks" in body).toBe(false);
  });

  it("sends an unset paper language as null, so the server clears it", () => {
    const body = examBodyFor({ ...examFormState(exam()), paperLanguage: "" }, "details", mcq);
    expect(body.paperLanguage).toBeNull();
    expect(examBodyFor({ ...examFormState(exam()), paperLanguage: "BOTH" }, "details", mcq).paperLanguage).toBe("BOTH");
  });

  it("needs both titles on the details section", () => {
    expect(examFormErrors({ ...examFormState(exam()), titleEn: " " }, "details", mcq).titleEn).toBe(
      "staffCatalog.examMeta.titleEnRequired",
    );
  });

  it("counts a section as dirty only for its own fields", () => {
    const saved = examFormState(exam());
    const edited = { ...saved, rulesEn: "<p>New</p>" };
    expect(sectionDirty(edited, saved, "text")).toBe(true);
    expect(sectionDirty(edited, saved, "schedule")).toBe(false);
  });
});

describe("publish checklist", () => {
  const t = (key: string) => key;

  it("lets a draft with every item done be published", () => {
    const items = examChecklist(exam(), t);
    expect(items.every((item) => item.done)).toBe(true);
    expect(examPublishable(exam(), items)).toBe(true);
  });

  it("blocks publish while the window does not fit the duration, and names the window", () => {
    const short = exam({ endAt: "2026-10-20T06:30:00Z" });
    const items = examChecklist(short, t);
    expect(items.find((item) => item.key === "window")?.done).toBe(false);
    expect(examPublishable(short, items)).toBe(false);
  });

  it("blocks publish while the description or the rules are blank", () => {
    const blank = exam({ rulesBn: "   " });
    const items = examChecklist(blank, t);
    expect(items.find((item) => item.key === "examText")?.done).toBe(false);
    expect(examPublishable(blank, items)).toBe(false);
  });

  it("never publishes a published exam directly: its changes go through the change list", () => {
    const published = exam({ status: "PUBLISHED" });
    expect(examPublishable(published, examChecklist(published, t))).toBe(false);
  });
});
