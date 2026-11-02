import { screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { ExamEditor } from "@/components/staff/catalog/exam-editor";
import type { StaffExam, StaffModule } from "@/lib/api/exam/staff-catalog";
import { fixtures, mockApi, renderWithProviders, routerState } from "../helpers";

const moduleId = "aaaaaaaa-0000-0000-0000-000000000001";
const examId = "eeeeeeee-0000-0000-0000-000000000021";

const moduleFixture: StaffModule = {
  id: moduleId,
  categoryId: "cccccccc-0000-0000-0000-000000000001",
  titleEn: "19th BCS",
  titleBn: "১৯তম বিজেএস",
  descriptionEn: null,
  descriptionBn: null,
  accessMode: "MANUAL",
  status: "DRAFT",
  finallySaved: true,
  position: 1,
  price: null,
  instructor: null,
};

const draftExam: StaffExam = {
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
  status: "DRAFT",
  finallySaved: false,
  price: null,
  questionPdf: null,
  questions: [],
};

afterEach(() => {
  routerState.search = "";
});

// A written exam has a teacher; an MCQ exam is marked by the system and takes none (the API rule).
const writtenDraft: StaffExam = { ...draftExam, examType: "WRITTEN", questionPdf: null, questions: null };

describe("next step after an exam is created (decided 2026-10-27: another exam, or the teacher of this exam)", () => {
  it("offers creating another exam and assigning the teacher of this exam right after the create", async () => {
    routerState.search = "created=1";
    mockApi({
      [`GET /api/v1/live-exams/${examId}`]: writtenDraft,
      [`GET /api/v1/live-exam-modules/${moduleId}`]: moduleFixture,
    });
    renderWithProviders(<ExamEditor examId={examId} />, { user: fixtures.masterAdmin });

    expect(await screen.findByRole("heading", { name: "পরীক্ষা সংরক্ষিত হয়েছে। এরপর কী?" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /আরেকটি পরীক্ষা তৈরি করুন/ })).toHaveAttribute(
      "href",
      `/staff/modules/${moduleId}?tab=exams&new=1`,
    );
    // The teacher is set per exam, so the choice jumps to the teacher field on this page, not to a package tab.
    expect(screen.getByRole("link", { name: /শিক্ষক নিয়োগ দিন/ })).toHaveAttribute("href", "#exam-instructor");
    // A clean draft has no unsaved edits, so the header offers its next step: the final save (plan 7.2 header table).
    expect(screen.getByRole("button", { name: "চূড়ান্ত করুন" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "সব পরিবর্তন সংরক্ষণ করুন" })).not.toBeInTheDocument();
  });

  it("tells a System Admin that only the Master Admin assigns the teacher of a written exam", async () => {
    mockApi({
      [`GET /api/v1/live-exams/${examId}`]: writtenDraft,
      [`GET /api/v1/live-exam-modules/${moduleId}`]: moduleFixture,
    });
    renderWithProviders(<ExamEditor examId={examId} />, { user: fixtures.systemAdmin });

    await waitFor(() => expect(screen.getAllByText("সিআরপিসি").length).toBeGreaterThan(0));
    expect(screen.getByText("শুধু মূল ব্যবস্থাপক (Master Admin) শিক্ষক নিয়োগ দেন।")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /শিক্ষক নিয়োগ দিন/ })).not.toBeInTheDocument();
  });

  it("offers no teacher choice after an MCQ exam is created, since the system marks it", async () => {
    routerState.search = "created=1";
    mockApi({
      [`GET /api/v1/live-exams/${examId}`]: draftExam,
      [`GET /api/v1/live-exam-modules/${moduleId}`]: moduleFixture,
    });
    renderWithProviders(<ExamEditor examId={examId} />, { user: fixtures.masterAdmin });

    expect(await screen.findByRole("heading", { name: "পরীক্ষা সংরক্ষিত হয়েছে। এরপর কী?" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /আরেকটি পরীক্ষা তৈরি করুন/ })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /শিক্ষক নিয়োগ দিন/ })).not.toBeInTheDocument();
    expect(screen.queryByText("শুধু মূল ব্যবস্থাপক (Master Admin) শিক্ষক নিয়োগ দেন।")).not.toBeInTheDocument();
  });
});
