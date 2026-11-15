import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { WrittenSitting } from "@/components/student/sitting/written-sitting";
import { ApiError } from "@/lib/api/errors";
import type { StudentExam } from "@/lib/api/exam/sitting";
import { renderWithProviders } from "../helpers";

const draftMock = vi.fn();
const submitMock = vi.fn();

vi.mock("@/lib/api/exam/sitting", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api/exam/sitting")>();
  return {
    ...actual,
    useSaveAnswerDraft: () => ({ mutateAsync: draftMock, isPending: false }),
    useSubmitExam: () => ({ mutateAsync: submitMock, isPending: false }),
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

function draftOf(name: string) {
  return { fileName: name, byteSize: 13, uploadedAt: "2026-11-15T04:00:00Z" };
}

const onSubmitted = vi.fn();

function renderSitting(over: Partial<StudentExam> = {}) {
  return renderWithProviders(
    <WrittenSitting
      exam={{ ...exam, ...over } as StudentExam}
      deadlineAt={new Date(Date.now() + 60 * 60 * 1000).toISOString()}
      studentLine="নাঈম হাসান"
      onClosed={() => undefined}
      onSubmitted={onSubmitted}
    />,
  );
}

function fileInput() {
  return document.querySelector('input[type="file"]') as HTMLInputElement;
}

async function confirmSubmit() {
  await userEvent.click(screen.getByRole("button", { name: "খাতা জমা দিন" }));
  await userEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "হ্যাঁ, জমা দিন" }));
}

describe("written sitting: the script is a draft on the server until it is submitted", () => {
  beforeEach(() => {
    draftMock.mockReset();
    submitMock.mockReset();
    onSubmitted.mockReset();
  });

  it("uploads a chosen PDF as the draft at once, and submits nothing", async () => {
    draftMock.mockResolvedValueOnce(draftOf("first.pdf"));
    renderSitting();

    await userEvent.upload(fileInput(), pdf("first.pdf"));

    await waitFor(() => expect(draftMock).toHaveBeenCalledWith(expect.objectContaining({ name: "first.pdf" })));
    expect(await screen.findByText(/আপলোড হয়েছে, তবে এখনো জমা হয়নি/)).toBeInTheDocument();
    expect(submitMock).not.toHaveBeenCalled();
    expect(onSubmitted).not.toHaveBeenCalled();
  });

  it("replaces the draft with another PDF before submitting", async () => {
    draftMock.mockResolvedValueOnce(draftOf("first.pdf")).mockResolvedValueOnce(draftOf("second.pdf"));
    renderSitting();

    await userEvent.upload(fileInput(), pdf("first.pdf"));
    await screen.findByText("first.pdf");
    await userEvent.upload(fileInput(), pdf("second.pdf"));

    expect(await screen.findByText("second.pdf")).toBeInTheDocument();
    expect(draftMock).toHaveBeenCalledTimes(2);
    expect(submitMock).not.toHaveBeenCalled();
  });

  it("asks before submitting, and submits nothing when the student cancels", async () => {
    draftMock.mockResolvedValueOnce(draftOf("script.pdf"));
    renderSitting();

    await userEvent.upload(fileInput(), pdf("script.pdf"));
    await screen.findByText(/আপলোড হয়েছে/);
    await userEvent.click(screen.getByRole("button", { name: "খাতা জমা দিন" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/script\.pdf/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "বাতিল" }));

    expect(submitMock).not.toHaveBeenCalled();
    expect(onSubmitted).not.toHaveBeenCalled();
  });

  it("submits the draft after the student confirms, and goes on", async () => {
    draftMock.mockResolvedValueOnce(draftOf("script.pdf"));
    submitMock.mockResolvedValueOnce({ status: "SUBMITTED" });
    renderSitting();

    await userEvent.upload(fileInput(), pdf("script.pdf"));
    await screen.findByText(/আপলোড হয়েছে/);
    await confirmSubmit();

    await waitFor(() => expect(onSubmitted).toHaveBeenCalledTimes(1));
    expect(submitMock).toHaveBeenCalledTimes(1);
  });

  it("can submit a draft the server already holds (after a reload)", async () => {
    submitMock.mockResolvedValueOnce({ status: "SUBMITTED" });
    renderSitting({ answerDraft: draftOf("earlier.pdf") });

    expect(screen.getByText("earlier.pdf")).toBeInTheDocument();
    await confirmSubmit();

    await waitFor(() => expect(onSubmitted).toHaveBeenCalledTimes(1));
    expect(draftMock).not.toHaveBeenCalled();
  });

  it("keeps a failed upload on screen with Try again, and holds Submit until it is uploaded", async () => {
    draftMock.mockRejectedValueOnce(new Error("network")).mockResolvedValueOnce(draftOf("script.pdf"));
    renderSitting();

    await userEvent.upload(fileInput(), pdf("script.pdf"));
    expect(await screen.findByText("খাতা আপলোড হয়নি। আবার চেষ্টা করুন।")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "খাতা জমা দিন" })).toBeDisabled();

    await userEvent.click(screen.getByRole("button", { name: "আবার চেষ্টা করুন" }));
    expect(await screen.findByText(/আপলোড হয়েছে/)).toBeInTheDocument();
    expect(draftMock).toHaveBeenCalledTimes(2);
    expect(screen.getByRole("button", { name: "খাতা জমা দিন" })).toBeEnabled();
  });

  // Written-upload incident (15 Nov 2026): a phone's picked file can become unreadable during the exam, and every retry of it failed.
  it("uploads a copy read when the file is chosen, so a retry never depends on the original", async () => {
    draftMock.mockRejectedValueOnce(new Error("server")).mockResolvedValueOnce(draftOf("script.pdf"));
    renderSitting();
    const original = pdf("script.pdf");

    await userEvent.upload(fileInput(), original);
    await userEvent.click(await screen.findByRole("button", { name: "আবার চেষ্টা করুন" }));
    await screen.findByText(/আপলোড হয়েছে/);

    const sent = draftMock.mock.calls.map(([file]) => file as File);
    expect(sent).toHaveLength(2);
    for (const file of sent) {
      expect(file).not.toBe(original);
      expect(file.name).toBe("script.pdf");
      expect(await file.text()).toBe("%PDF-1.4 test");
    }
  });

  it("retries a dropped connection by itself before asking the student", async () => {
    draftMock.mockRejectedValueOnce(new TypeError("Network request failed")).mockResolvedValueOnce(draftOf("script.pdf"));
    renderSitting();

    await userEvent.upload(fileInput(), pdf("script.pdf"));

    expect(await screen.findByText(/আবার পাঠানো হচ্ছে/)).toBeInTheDocument();
    expect(await screen.findByText(/আপলোড হয়েছে/, undefined, { timeout: 4000 })).toBeInTheDocument();
    expect(draftMock).toHaveBeenCalledTimes(2);
    expect(screen.queryByRole("button", { name: "আবার চেষ্টা করুন" })).not.toBeInTheDocument();
  });

  it("asks for another file when the server cannot open the PDF, instead of offering Try again", async () => {
    draftMock.mockRejectedValueOnce(new ApiError({ code: "VALIDATION_FAILED", message: "bad", timestamp: "", requestId: "" }, 400));
    renderSitting();

    await userEvent.upload(fileInput(), pdf("locked.pdf"));

    expect(await screen.findByText(/এই পিডিএফটি খোলা যায়নি/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "আবার চেষ্টা করুন" })).not.toBeInTheDocument();
    expect(draftMock).toHaveBeenCalledTimes(1);
  });

  it("keeps Submit on for the earlier draft when a replacement does not upload, and says which file it sends", async () => {
    draftMock.mockRejectedValueOnce(new Error("server"));
    submitMock.mockResolvedValueOnce({ status: "SUBMITTED" });
    renderSitting({ answerDraft: draftOf("earlier.pdf") });

    await userEvent.upload(fileInput(), pdf("newer.pdf"));

    expect(await screen.findByText(/আগে আপলোড করা earlier\.pdf জমা হবে/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "খাতা জমা দিন" })).toBeEnabled();
    await confirmSubmit();
    await waitFor(() => expect(onSubmitted).toHaveBeenCalledTimes(1));
  });

  it("goes to the submitted page when the server says the script was already submitted", async () => {
    draftMock.mockRejectedValue(new ApiError({ code: "ANSWER_ALREADY_SUBMITTED", message: "already", timestamp: "", requestId: "" }, 409));
    renderSitting();

    await userEvent.upload(fileInput(), pdf("again.pdf"));
    await waitFor(() => expect(onSubmitted).toHaveBeenCalledTimes(1));
  });
});
