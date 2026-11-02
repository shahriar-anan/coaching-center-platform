import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { CategoryTree } from "@/components/staff/catalog/category-tree";
import { ExamEditor } from "@/components/staff/catalog/exam-editor";
import { ModuleDetails } from "@/components/staff/catalog/module-details";
import type { StaffExam, StaffModule } from "@/lib/api/exam/staff-catalog";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const RESTORE = "ফিরিয়ে আনুন";
const ARCHIVED_EXAM_NOTICE =
  "এই পরীক্ষা প্রত্যাহার করা হয়েছে। শিক্ষার্থীরা এটি দেখতে পাবে না। প্রকাশিত ফল দেখা যাবে। এটি এখনো বদলানো যাবে। আগে প্রকাশিত হয়ে থাকলে বদল মাস্টার অ্যাডমিন প্রকাশ না করা পর্যন্ত অপেক্ষায় থাকবে।";

const categoryId = "cccccccc-0000-0000-0000-000000000001";
const moduleId = "aaaaaaaa-0000-0000-0000-000000000001";
const examId = "eeeeeeee-0000-0000-0000-000000000005";

const archivedCategory = {
  id: categoryId,
  parentId: null,
  titleEn: "BCS",
  titleBn: "বিজেএস",
  position: 1,
  archived: true,
  children: [],
  modules: [],
};

const archivedModule: StaffModule = {
  id: moduleId,
  categoryId,
  titleEn: "19th BCS",
  titleBn: "১৯তম বিজেএস",
  descriptionEn: null,
  descriptionBn: null,
  accessMode: "MANUAL",
  status: "ARCHIVED",
  finallySaved: true,
  position: 1,
  price: 2500,
};

const archivedExam: StaffExam = {
  id: examId,
  moduleId,
  examType: "MCQ",
  paperLanguage: "BN",
  titleEn: "CrPC",
  titleBn: "সিআরপিসি",
  descriptionEn: "Demo description",
  descriptionBn: "ডেমো বিবরণ",
  rulesEn: "Read every question first.",
  rulesBn: "প্রতিটি প্রশ্ন আগে পড়ুন।",
  liveAt: null,
  endAt: null,
  durationMinutes: 40,
  passPercent: 40,
  maximum: 50,
  status: "ARCHIVED",
  finallySaved: true,
  price: null,
  questionPdf: null,
  questions: [
    { id: "q1", stem: "এক", marks: 20, explanation: null, position: 1, options: [{ id: "o1", text: "ক", correct: true, position: 1 }] },
  ],
};

describe("restore an archived exam, package or category (Master Admin only)", () => {
  it("Master Admin sees Restore on an archived exam, and the confirm posts to the restore route", async () => {
    const api = mockApi({
      [`GET /api/v1/live-exams/${examId}`]: archivedExam,
      [`GET /api/v1/live-exam-modules/${moduleId}`]: archivedModule,
      [`POST /api/v1/live-exams/${examId}/restore`]: { ...archivedExam, status: "DRAFT", finallySaved: false },
    });
    renderWithProviders(<ExamEditor examId={examId} />, { user: fixtures.masterAdmin });

    await userEvent.click(await screen.findByRole("button", { name: RESTORE }));
    const dialog = await screen.findByRole("dialog");
    await userEvent.click(within(dialog).getByRole("button", { name: RESTORE }));

    await waitFor(() =>
      expect(api.calls.some((call) => call.method === "POST" && call.path === `/api/v1/live-exams/${examId}/restore`)).toBe(true),
    );
  });

  it("an instructor sees no Restore on an archived exam", async () => {
    mockApi({
      [`GET /api/v1/live-exams/${examId}`]: archivedExam,
      [`GET /api/v1/live-exam-modules/${moduleId}`]: archivedModule,
    });
    renderWithProviders(<ExamEditor examId={examId} />, { user: fixtures.instructor });

    expect(await screen.findByText(ARCHIVED_EXAM_NOTICE)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: RESTORE })).not.toBeInTheDocument();
  });

  it("Master Admin sees Restore on an archived package, and an instructor does not", async () => {
    mockApi({ "GET /api/v1/live-exam-categories": [archivedCategory] });
    const { unmount } = renderWithProviders(<ModuleDetails module={archivedModule} />, { user: fixtures.masterAdmin });
    expect(await screen.findByRole("button", { name: RESTORE })).toBeInTheDocument();
    unmount();

    mockApi({ "GET /api/v1/live-exam-categories": [archivedCategory] });
    renderWithProviders(<ModuleDetails module={archivedModule} />, { user: fixtures.instructor });
    await screen.findAllByDisplayValue("১৯তম বিজেএস");
    expect(screen.queryByRole("button", { name: RESTORE })).not.toBeInTheDocument();
  });

  it("Master Admin sees Restore on an archived category, and an instructor does not", async () => {
    mockApi({ "GET /api/v1/live-exam-categories": [archivedCategory] });
    const { unmount } = renderWithProviders(<CategoryTree />, { user: fixtures.masterAdmin });
    expect(await screen.findByRole("button", { name: RESTORE })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "আর্কাইভ করুন" })).not.toBeInTheDocument();
    unmount();

    mockApi({ "GET /api/v1/live-exam-categories": [archivedCategory] });
    renderWithProviders(<CategoryTree />, { user: fixtures.instructor });
    expect(await screen.findByText("বিজেএস")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: RESTORE })).not.toBeInTheDocument();
  });
});
