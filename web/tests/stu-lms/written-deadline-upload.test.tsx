import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { WrittenSitting } from "@/components/student/sitting/written-sitting";
import { ApiError } from "@/lib/api/errors";
import type { StudentExam } from "@/lib/api/exam/sitting";
import { renderWithProviders } from "../helpers";

const MINUTE = 60_000;
const uploadMock = vi.fn();

vi.mock("@/lib/api/exam/sitting", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api/exam/sitting")>();
  return { ...actual, useUploadAnswer: () => ({ mutateAsync: uploadMock, isPending: false }) };
});

const exam = { id: "ex-w", questionPdf: null } as unknown as StudentExam;

describe("the deadline passes during a written upload (ST-6)", () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    uploadMock.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("the upload after the deadline is refused, and the paper shows the deadline state", async () => {
    const onClosed = vi.fn();
    const deadlineAt = new Date(Date.now() + MINUTE).toISOString();

    // Attach the assertion before anything runs, so the refusal is caught by it.
    const refusal = new ApiError({
      code: "DEADLINE_PASSED",
      message: "deadline passed",
      requestId: "r1",
      timestamp: new Date().toISOString(),
    });
    const refused = Promise.reject(refusal);
    const refusedAssertion = expect(refused).rejects.toMatchObject({ code: "DEADLINE_PASSED" });
    uploadMock.mockReturnValue(refused);

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderWithProviders(
      <WrittenSitting exam={exam} deadlineAt={deadlineAt} watermark="নাঈম" onClosed={onClosed} />,
      { locale: "bn" },
    );

    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    await user.upload(input, new File(["%PDF-1.4"], "late.pdf", { type: "application/pdf" }));

    // Time passes the deadline while the student is still holding the file.
    vi.advanceTimersByTime(2 * MINUTE);
    await user.click(screen.getByRole("button", { name: "খাতা জমা দিন" }));

    await refusedAssertion;
    expect(uploadMock).toHaveBeenCalledWith(expect.objectContaining({ name: "late.pdf" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("সময় শেষ, তাই আর খাতা জমা দেওয়া যাবে না।");
    expect(document.querySelector('input[type="file"]')).toBeNull();
    expect(onClosed).toHaveBeenCalled();
  });
});
