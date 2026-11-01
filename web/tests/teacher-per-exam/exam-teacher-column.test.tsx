import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ModuleExams } from "@/components/staff/catalog/module-exams";
import type { StaffExam, StaffModule } from "@/lib/api/exam/staff-catalog";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

/*
 * The package's Exams tab shows each exam's teacher. A written exam shows its teacher's name, or "No teacher yet";
 * an MCQ exam shows "Marked by the system". The link goes to the exam's own page, where the teacher is set.
 */

const moduleId = "aaaaaaaa-0000-4000-8000-000000000001";

const moduleFixture: StaffModule = {
  id: moduleId,
  categoryId: "cccccccc-0000-4000-8000-000000000001",
  titleEn: "19th BCS",
  titleBn: "১৯তম বিজেএস",
  descriptionEn: null,
  descriptionBn: null,
  accessMode: "MANUAL",
  status: "PUBLISHED",
  finallySaved: true,
  position: 1,
  price: 2500,
};

function exam(partial: Partial<StaffExam> & Pick<StaffExam, "id" | "titleEn" | "examType">): StaffExam {
  return {
    moduleId,
    paperLanguage: "EN",
    titleBn: partial.titleEn,
    descriptionEn: null,
    descriptionBn: null,
    rulesEn: null,
    rulesBn: null,
    liveAt: null,
    endAt: null,
    durationMinutes: 40,
    passPercent: 40,
    maximum: 50,
    status: "PUBLISHED",
    finallySaved: true,
    price: null,
    questionPdf: null,
    questions: [],
    instructor: null,
    ...partial,
  };
}

const writtenWithTeacher = exam({
  id: "e-written-taught",
  titleEn: "CPC Paper",
  examType: "WRITTEN",
  questionPdf: true,
  instructor: { id: "i-1", name: "Farihan Rahman" },
});
const writtenWithout = exam({ id: "e-written-open", titleEn: "Evidence Paper", examType: "WRITTEN", questionPdf: true });
const mcq = exam({ id: "e-mcq", titleEn: "CrPC MCQ", examType: "MCQ" });

const page = (rows: StaffExam[]) => ({ content: rows, page: 0, size: 100, totalElements: rows.length, totalPages: 1 });

/** The desktop table row of one exam, found by its title link. */
async function rowOf(title: string) {
  const rows = await screen.findAllByRole("row");
  const row = rows.find((r) => within(r).queryByRole("link", { name: title }));
  expect(row).toBeDefined();
  return row as HTMLElement;
}

describe("package Exams tab: the teacher of each exam", () => {
  it("shows the teacher's name for a written exam that has one, with a link to change it on the exam page", async () => {
    mockApi({
      [`GET /api/v1/live-exam-modules/${moduleId}`]: moduleFixture,
      [`GET /api/v1/live-exam-modules/${moduleId}/exams`]: page([writtenWithTeacher]),
    });
    renderWithProviders(<ModuleExams moduleId={moduleId} />, { user: fixtures.masterAdmin, locale: "en" });

    const row = await rowOf("CPC Paper");
    expect(within(row).getByText("Farihan Rahman")).toBeInTheDocument();
    expect(within(row).queryByText("No teacher yet")).not.toBeInTheDocument();
    const link = within(row).getByRole("link", { name: "Change teacher" });
    expect(link).toHaveAttribute("href", `/staff/exams/${writtenWithTeacher.id}`);
  });

  it("shows 'No teacher yet' and an Assign teacher link for a written exam without one", async () => {
    mockApi({
      [`GET /api/v1/live-exam-modules/${moduleId}`]: moduleFixture,
      [`GET /api/v1/live-exam-modules/${moduleId}/exams`]: page([writtenWithout]),
    });
    renderWithProviders(<ModuleExams moduleId={moduleId} />, { user: fixtures.masterAdmin, locale: "en" });

    const row = await rowOf("Evidence Paper");
    expect(within(row).getByText("No teacher yet")).toBeInTheDocument();
    const link = within(row).getByRole("link", { name: "Assign teacher" });
    expect(link).toHaveAttribute("href", `/staff/exams/${writtenWithout.id}`);
  });

  it("shows 'Marked by the system' for an MCQ exam, with no teacher link", async () => {
    mockApi({
      [`GET /api/v1/live-exam-modules/${moduleId}`]: moduleFixture,
      [`GET /api/v1/live-exam-modules/${moduleId}/exams`]: page([mcq]),
    });
    renderWithProviders(<ModuleExams moduleId={moduleId} />, { user: fixtures.masterAdmin, locale: "en" });

    const row = await rowOf("CrPC MCQ");
    expect(within(row).getByText("Marked by the system")).toBeInTheDocument();
    expect(within(row).queryByText("No teacher yet")).not.toBeInTheDocument();
    expect(within(row).queryByRole("link", { name: "Assign teacher" })).not.toBeInTheDocument();
    expect(within(row).queryByRole("link", { name: "Change teacher" })).not.toBeInTheDocument();
  });

  it("a System Admin sees the teacher's name but no link to change it", async () => {
    mockApi({
      [`GET /api/v1/live-exam-modules/${moduleId}`]: moduleFixture,
      [`GET /api/v1/live-exam-modules/${moduleId}/exams`]: page([writtenWithTeacher, writtenWithout]),
    });
    renderWithProviders(<ModuleExams moduleId={moduleId} />, { user: fixtures.systemAdmin, locale: "en" });

    const row = await rowOf("CPC Paper");
    expect(within(row).getByText("Farihan Rahman")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Change teacher" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Assign teacher" })).not.toBeInTheDocument();
  });
});
