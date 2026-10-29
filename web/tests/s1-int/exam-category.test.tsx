import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { changeRows } from "@/components/staff/catalog/change-list-dialog";
import { ExamMetaForm } from "@/components/staff/catalog/exam-meta-form";
import {
  SECTION_KEYS,
  examBodyFor,
  examFormErrors,
  examFormState,
  sectionDirty,
  type ExamFormContext,
} from "@/components/staff/catalog/exam-form-state";
import type { StaffExam } from "@/lib/api/exam/staff-catalog";
import { fixtures, renderWithProviders } from "../helpers";
import { mockApi } from "../helpers/mock-api";

const MCQ: ExamFormContext = { written: false, canSetPrice: true };

/** A draft MCQ exam in a package, in the home category. */
const exam = {
  id: "exam-1",
  moduleId: "pkg-1",
  examType: "MCQ",
  paperLanguage: null,
  titleEn: "Weekly quiz",
  titleBn: "সাপ্তাহিক কুইজ",
  categoryId: "c-home",
  previewEnabled: false,
  descriptionEn: "Description",
  descriptionBn: "বিবরণ",
  rulesEn: "Rules",
  rulesBn: "নিয়ম",
  liveAt: null,
  endAt: null,
  durationMinutes: 30,
  maximum: 0,
  mode: "LIVE",
  status: "DRAFT",
  price: null,
  free: false,
  discountPercent: null,
  discountEndsAt: null,
  accessDays: null,
  passPercent: 50,
  negativeMarks: 0,
  answersChangeable: true,
  finallySaved: false,
  popular: false,
  pendingChanges: false,
  questions: [],
  questionPdf: null,
  packages: [{ id: "pkg-1", titleEn: "BJS Full", titleBn: "বিজেএস" }],
  instructor: null,
} as unknown as StaffExam;

const tree = [
  {
    id: "c-home",
    parentId: null,
    titleEn: "Home",
    titleBn: "ঘর",
    position: 0,
    archived: false,
    children: [{ id: "c-sub", parentId: "c-home", titleEn: "Sub", titleBn: "উপ", position: 0, archived: false, children: [] }],
  },
  { id: "c-old", parentId: null, titleEn: "Closed", titleBn: "বন্ধ", position: 1, archived: true, children: [] },
  { id: "c-new", parentId: null, titleEn: "New", titleBn: "নতুন", position: 2, archived: false, children: [] },
];

describe("the category of an exam, in the form state", () => {
  it("reads the category of the exam, and sends it in the details body", () => {
    const form = examFormState(exam);

    expect(form.categoryId).toBe("c-home");
    expect(examBodyFor(form, "details", MCQ)).toMatchObject({ categoryId: "c-home" });
    expect(examFormState({ ...exam, categoryId: null } as unknown as StaffExam).categoryId).toBe("");
    expect(examBodyFor({ ...form, categoryId: "" }, "details", MCQ)).toMatchObject({ categoryId: null });
  });

  it("asks for a category, and makes the details section dirty when it changes", () => {
    const form = examFormState(exam);

    expect(SECTION_KEYS.details).toContain("categoryId");
    expect(examFormErrors({ ...form, categoryId: "" }, "details", MCQ).categoryId).toBe("staffCatalog.examMeta.categoryRequired");
    expect(examFormErrors(form, "details", MCQ)).toEqual({});
    expect(sectionDirty({ ...form, categoryId: "c-new" }, form, "details")).toBe(true);
    expect(sectionDirty({ ...form, categoryId: "c-new" }, form, "text")).toBe(false);
  });

  it("names the categories in the change list instead of showing their ids", () => {
    const names = new Map([
      ["c-home", { titleEn: "Home", titleBn: "ঘর" }],
      ["c-new", { titleEn: "New", titleBn: "নতুন" }],
    ]);
    const rows = changeRows(
      [{ field: "categoryId", before: "c-home", after: "c-new" }],
      { empty: "-", yes: "Yes", no: "No" },
      (id) => names.get(id)?.titleEn,
    );

    expect(rows).toEqual([{ field: "categoryId", before: "Home", after: "New" }]);
    expect(changeRows([{ field: "categoryId", before: "c-x", after: null }], { empty: "-", yes: "Yes", no: "No" }, () => undefined)).toEqual([
      { field: "categoryId", before: "c-x", after: "-" },
    ]);
  });
});

describe("the category select on the exam page", () => {
  it("offers the live categories for any exam, subcategories indented, and sends the new one on save", async () => {
    const api = mockApi({
      "GET /api/v1/live-exam-categories": tree,
      "PATCH /api/v1/live-exams/exam-1": { ...exam, categoryId: "c-new" },
    });
    renderWithProviders(<ExamMetaForm exam={exam} section="details" canEdit onDirtyChange={() => undefined} />, {
      user: fixtures.masterAdmin,
      locale: "en",
    });

    const select = await screen.findByRole("combobox", { name: /category/i });
    await waitFor(() => expect(screen.getByRole("option", { name: "— Sub" })).toBeInTheDocument());
    expect(screen.getByRole("option", { name: "Home" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "New" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "Closed" })).toBeNull();
    expect(select).toHaveValue("c-home");

    await userEvent.selectOptions(select, "c-new");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => {
      const patch = api.calls.find((call) => call.method === "PATCH");
      expect(patch?.path).toBe("/api/v1/live-exams/exam-1");
      expect(patch?.body).toMatchObject({ categoryId: "c-new" });
    });
  });
});
