import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ExamInstructorSection } from "@/components/staff/catalog/exam-details-fields";
import { formatMoney } from "@/lib/format";
import type { StaffExam } from "@/lib/api/exam/staff-catalog";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const exam = (extra: Partial<StaffExam> = {}): StaffExam =>
  ({
    id: "exam-1",
    moduleId: "mod-1",
    examType: "WRITTEN",
    paperLanguage: null,
    titleEn: "Muslim Law",
    titleBn: "মুসলিম আইন",
    descriptionEn: null,
    descriptionBn: null,
    rulesEn: null,
    rulesBn: null,
    liveAt: null,
    endAt: null,
    durationMinutes: 120,
    passPercent: null,
    maximum: 100,
    status: "DRAFT",
    finallySaved: false,
    price: null,
    questionPdf: true,
    questions: null,
    instructor: null,
    ...extra,
  }) as StaffExam;

/** The API's own shape: examRate, effectiveRate and log, with null fields left out (the page translates it). */
const terms = (extra: Record<string, unknown> = {}) => ({
  effectiveRate: 50,
  log: [],
  ...extra,
});

const emptyDirectory = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

describe("Instructor and pay section of a written exam", () => {
  it("shows the no-instructor warning but no rate or history to a System Admin (plan decision O1)", async () => {
    mockApi({
      "GET /api/v1/live-exams/exam-1/pay-terms": terms({
        examRate: 60,
        effectiveRate: 60,
        log: [
          {
            kind: "RATE_SET",
            instructorName: null,
            previousInstructorName: null,
            sheetRate: 60,
            previousSheetRate: null,
            changedByName: "Master Admin",
            createdAt: "2026-11-05T10:00:00+06:00",
          },
        ],
      }),
    });
    renderWithProviders(<ExamInstructorSection exam={exam()} />, { user: fixtures.systemAdmin, locale: "en" });

    expect(await screen.findByText("No instructor assigned")).toBeInTheDocument();
    expect(screen.getByText("The Master Admin assigns the marker.")).toBeInTheDocument();
    // Only the Master Admin sees the rate and the history, and the pay-terms request is never made for a System Admin.
    expect(screen.queryByText(`${formatMoney(60, "en")} per sheet`)).not.toBeInTheDocument();
    expect(screen.queryByText("Rate set")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Save rate" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Assign" })).not.toBeInTheDocument();
  });

  it("shows the warning as a hint, with the rate form and the empty history, to the Master Admin", async () => {
    mockApi({
      "GET /api/v1/live-exams/exam-1/pay-terms": terms(),
      "GET /api/v1/instructors": emptyDirectory,
    });
    renderWithProviders(<ExamInstructorSection exam={exam()} />, { user: fixtures.masterAdmin, locale: "en" });

    expect(await screen.findByText("No instructor assigned")).toBeInTheDocument();
    expect(screen.getByText("Written answers need a marker. You can still publish the exam.")).toBeInTheDocument();
    expect(await screen.findByRole("button", { name: "Save rate" })).toBeInTheDocument();
    expect(screen.getByText("No changes recorded yet.")).toBeInTheDocument();
    // The Master Admin does not see the System Admin's wording.
    expect(screen.queryByText("The Master Admin assigns the marker.")).not.toBeInTheDocument();
  });

  it("drops the warning once a marker is assigned", async () => {
    mockApi({ "GET /api/v1/live-exams/exam-1/pay-terms": terms({ instructor: { id: "i-1", name: "Rahim" } }) });
    renderWithProviders(<ExamInstructorSection exam={exam({ instructor: { id: "i-1", name: "Rahim" } })} />, {
      user: fixtures.systemAdmin,
      locale: "en",
    });

    expect(await screen.findByText("Rahim")).toBeInTheDocument();
    expect(screen.queryByText("No instructor assigned")).not.toBeInTheDocument();
  });

  it("shows neither the warning nor the rate for an MCQ exam", async () => {
    mockApi({ "GET /api/v1/instructors": emptyDirectory });
    renderWithProviders(<ExamInstructorSection exam={exam({ examType: "MCQ", questionPdf: null })} />, {
      user: fixtures.masterAdmin,
      locale: "en",
    });

    expect(await screen.findByText("An MCQ exam has no instructor.")).toBeInTheDocument();
    expect(screen.queryByText("No instructor assigned")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Save rate" })).not.toBeInTheDocument();
  });
});
