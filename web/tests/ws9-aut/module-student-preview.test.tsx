import { screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ModuleStudentPreview } from "@/components/staff/catalog/module-student-preview";
import type { StaffExam, StaffModule } from "@/lib/api/exam/staff-catalog";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const moduleId = "aaaaaaaa-0000-0000-0000-000000000009";

const moduleFixture: StaffModule = {
  id: moduleId,
  categoryId: "cccccccc-0000-0000-0000-000000000001",
  titleEn: "Draft module",
  titleBn: "খসড়া মডিউল",
  descriptionEn: "Preview me",
  descriptionBn: "দেখুন",
  accessMode: "MANUAL",
  status: "DRAFT",
  finallySaved: false,
  position: 1,
  price: null,
  instructor: null,
};

function exam(overrides: Partial<StaffExam>): StaffExam {
  return {
    id: "eeeeeeee-0000-0000-0000-000000000011",
    moduleId,
    examType: "MCQ",
    paperLanguage: "BN",
    titleEn: "Published MCQ",
    titleBn: "প্রকাশিত এমসিকিউ",
    descriptionEn: "d",
    descriptionBn: "বি",
    rulesEn: "r",
    rulesBn: "নি",
    liveAt: null,
    endAt: null,
    durationMinutes: 30,
    passPercent: 40,
    maximum: 40,
    status: "PUBLISHED",
    finallySaved: true,
    price: null,
    questionPdf: null,
    questions: [],
    ...overrides,
  };
}

describe("module student preview (the student's view, before publish)", () => {
  it("shows the published exams and hides the drafts, with a count of the hidden drafts", async () => {
    mockApi({
      [`GET /api/v1/live-exam-modules/${moduleId}/exams`]: {
        content: [
          exam({ id: "e1", titleEn: "Published MCQ", titleBn: "প্রকাশিত এমসিকিউ" }),
          exam({ id: "e2", titleEn: "Draft written", titleBn: "খসড়া রিটেন", examType: "WRITTEN", status: "DRAFT" }),
        ],
        page: 0,
        size: 100,
        totalElements: 2,
        totalPages: 1,
      },
    });
    renderWithProviders(<ModuleStudentPreview module={moduleFixture} />, { user: fixtures.masterAdmin });

    await waitFor(() => expect(screen.getByText("প্রকাশিত এমসিকিউ")).toBeInTheDocument());
    expect(screen.queryByText("খসড়া রিটেন")).not.toBeInTheDocument();
    expect(screen.getByText("এই প্যাকেজে এখনো কোনো Written পরীক্ষা নেই।")).toBeInTheDocument();
    expect(screen.getByText(/প্রকাশ না হওয়া পর্যন্ত লুকানো: .*খসড়া পরীক্ষা/)).toBeInTheDocument();
  });

  it("says the module has no price yet, the way the student page does", async () => {
    mockApi({
      [`GET /api/v1/live-exam-modules/${moduleId}/exams`]: { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 },
    });
    renderWithProviders(<ModuleStudentPreview module={moduleFixture} />, { user: fixtures.masterAdmin });

    expect(await screen.findByText("দাম এখনো নির্ধারণ হয়নি।")).toBeInTheDocument();
  });
});
