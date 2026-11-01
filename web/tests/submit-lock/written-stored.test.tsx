import { screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { WrittenSitting } from "@/components/student/sitting/written-sitting";
import type { StudentExam } from "@/lib/api/exam/sitting";
import { renderWithProviders } from "../helpers";

vi.mock("@/components/pdf/pdf-viewer", () => ({
  PdfViewer: ({ label }: { label?: string }) => <p data-testid="pdf-viewer">{label}</p>,
}));

function writtenExam(extra: Record<string, unknown>): StudentExam {
  return {
    id: "ex-w",
    examType: "WRITTEN",
    paperLanguage: "EN",
    titleEn: "BJS",
    titleBn: "BJS",
    questionPdf: true,
    ...extra,
  } as unknown as StudentExam;
}

function renderSitting(exam: StudentExam) {
  return renderWithProviders(
    <WrittenSitting
      exam={exam}
      deadlineAt={new Date(Date.now() + 60 * 60_000).toISOString()}
      studentLine="Student"
      onClosed={() => undefined}
      onSubmitted={() => undefined}
    />,
    { locale: "en" },
  );
}

describe("the question paper of a written exam, by stored script", () => {
  it("is offered while no script is stored", () => {
    renderSitting(writtenExam({ answerStored: false }));
    expect(screen.getByTestId("pdf-viewer")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Question paper" })).toBeInTheDocument();
  });

  it("is no longer offered once the script is stored, and no 'not added' line replaces it", () => {
    renderSitting(writtenExam({ answerStored: true }));
    expect(screen.queryByTestId("pdf-viewer")).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Question paper" })).not.toBeInTheDocument();
    expect(screen.queryByText("The question paper has not been added yet.")).not.toBeInTheDocument();
    expect(screen.getByText("Your earlier answer script is saved.")).toBeInTheDocument();
  });
});
