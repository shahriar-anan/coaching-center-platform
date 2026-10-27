import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { WrittenSitting } from "@/components/student/sitting/written-sitting";
import { ApiError } from "@/lib/api/errors";
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

const onSubmitted = vi.fn();

function renderSitting() {
  return renderWithProviders(
    <WrittenSitting
      exam={exam}
      deadlineAt={new Date(Date.now() + 60 * 60 * 1000).toISOString()}
      watermark="নাঈম হাসান"
      onClosed={() => undefined}
      onSubmitted={onSubmitted}
    />,
  );
}

function fileInput() {
  return document.querySelector('input[type="file"]') as HTMLInputElement;
}

describe("written sitting", () => {
  beforeEach(() => {
    uploadMock.mockReset();
    onSubmitted.mockReset();
  });

  it("shows the stored note after a confirmed upload, and a second upload is refused by the server", async () => {
    uploadMock.mockResolvedValueOnce(undefined);
    renderSitting();

    await userEvent.upload(fileInput(), pdf("first.pdf"));
    await userEvent.click(screen.getByRole("button", { name: "খাতা জমা দিন" }));
    await userEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "হ্যাঁ, জমা দিন" }));
    expect(await screen.findByText("খাতা জমা দেওয়া হয়েছে")).toBeInTheDocument();
    expect(onSubmitted).toHaveBeenCalledTimes(1);
  });

  it("goes to the submitted page when the server says the script was already submitted", async () => {
    uploadMock.mockRejectedValue(new ApiError({ code: "ANSWER_ALREADY_SUBMITTED", message: "already", timestamp: "", requestId: "" }, 409));
    renderSitting();

    await userEvent.upload(fileInput(), pdf("again.pdf"));
    await userEvent.click(screen.getByRole("button", { name: "খাতা জমা দিন" }));
    await userEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "হ্যাঁ, জমা দিন" }));
    await waitFor(() => expect(onSubmitted).toHaveBeenCalledTimes(1));
  });

  it("asks before submitting, and uploads nothing until the student confirms", async () => {
    uploadMock.mockResolvedValue(undefined);
    renderSitting();

    await userEvent.upload(fileInput(), pdf("script.pdf"));
    await userEvent.click(screen.getByRole("button", { name: "খাতা জমা দিন" }));

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/script\.pdf/)).toBeInTheDocument();
    expect(uploadMock).not.toHaveBeenCalled();

    await userEvent.click(within(dialog).getByRole("button", { name: "বাতিল" }));
    expect(uploadMock).not.toHaveBeenCalled();
    expect(onSubmitted).not.toHaveBeenCalled();
  });

  it("goes to the submitted page after the student confirms and the upload succeeds", async () => {
    uploadMock.mockResolvedValue(undefined);
    renderSitting();

    await userEvent.upload(fileInput(), pdf("script.pdf"));
    await userEvent.click(screen.getByRole("button", { name: "খাতা জমা দিন" }));
    await userEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "হ্যাঁ, জমা দিন" }));

    await waitFor(() => expect(onSubmitted).toHaveBeenCalledTimes(1));
    expect(uploadMock).toHaveBeenCalledTimes(1);
  });

  it("stays on the page and shows the error when the upload fails", async () => {
    uploadMock.mockRejectedValue(new Error("network"));
    renderSitting();

    await userEvent.upload(fileInput(), pdf("script.pdf"));
    await userEvent.click(screen.getByRole("button", { name: "খাতা জমা দিন" }));
    await userEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "হ্যাঁ, জমা দিন" }));

    expect(await screen.findByText("খাতা আপলোড হয়নি। আবার চেষ্টা করুন।")).toBeInTheDocument();
    expect(onSubmitted).not.toHaveBeenCalled();
  });
});
