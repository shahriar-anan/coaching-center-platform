import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { WrittenSitting } from "@/components/student/sitting/written-sitting";
import type { StudentExam } from "@/lib/api/exam/sitting";
import { renderWithProviders } from "../helpers";

const uploadMock = vi.fn();

vi.mock("@/lib/api/exam/sitting", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api/exam/sitting")>();
  return {
    ...actual,
    useUploadAnswer: () => ({ mutateAsync: uploadMock, isPending: false, isError: false }),
  };
});

const exam = {
  id: "ex-w",
  examType: "WRITTEN",
  paperLanguage: "BN",
  titleEn: "BJS",
  titleBn: "বিজেএস",
  questionPdf: null,
} as unknown as StudentExam;

function pdf(name: string) {
  return new File(["%PDF-1.4 test"], name, { type: "application/pdf" });
}

function renderSitting() {
  return renderWithProviders(
    <WrittenSitting
      exam={exam}
      deadlineAt={new Date(Date.now() + 60 * 60 * 1000).toISOString()}
      watermark="নাঈম হাসান"
      onClosed={() => undefined}
    />,
  );
}

function fileInput() {
  return document.querySelector('input[type="file"]') as HTMLInputElement;
}

describe("written sitting", () => {
  beforeEach(() => uploadMock.mockReset());

  it("a later file replaces the stored one, and the stored note waits until the new upload", async () => {
    uploadMock.mockResolvedValue(undefined);
    renderSitting();

    await userEvent.upload(fileInput(), pdf("first.pdf"));
    await userEvent.click(screen.getByRole("button", { name: "খাতা জমা দিন" }));
    expect(await screen.findByText("খাতা জমা দেওয়া হয়েছে")).toBeInTheDocument();

    await userEvent.upload(fileInput(), pdf("second.pdf"));
    expect(screen.queryByText("খাতা জমা দেওয়া হয়েছে")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "খাতা জমা দিন" })).toBeEnabled();

    await userEvent.click(screen.getByRole("button", { name: "খাতা জমা দিন" }));
    expect(uploadMock).toHaveBeenCalledTimes(2);
    expect(uploadMock.mock.calls[1][0]).toMatchObject({ name: "second.pdf" });
  });
});
