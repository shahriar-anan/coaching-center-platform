import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { WrittenSitting } from "@/components/student/sitting/written-sitting";
import { ApiError } from "@/lib/api/errors";
import type { StudentExam } from "@/lib/api/exam/sitting";
import { renderWithProviders } from "../helpers";

const MINUTE = 60_000;
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

const exam = { id: "ex-w", questionPdf: null } as unknown as StudentExam;

describe("the deadline passes during a written sitting (ST-6)", () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    draftMock.mockReset();
    submitMock.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("a submit after the deadline is refused, and the page reports the draft so the server's submission counts", async () => {
    const onClosed = vi.fn();
    const deadlineAt = new Date(Date.now() + MINUTE).toISOString();
    draftMock.mockResolvedValue({ fileName: "late.pdf", byteSize: 8, uploadedAt: new Date().toISOString() });
    submitMock.mockRejectedValue(
      new ApiError({ code: "DEADLINE_PASSED", message: "deadline passed", requestId: "r1", timestamp: new Date().toISOString() }),
    );

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderWithProviders(
      <WrittenSitting exam={exam} deadlineAt={deadlineAt} studentLine="নাঈম" onClosed={onClosed} onSubmitted={() => undefined} />,
      { locale: "bn" },
    );

    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    await user.upload(input, new File(["%PDF-1.4"], "late.pdf", { type: "application/pdf" }));
    await screen.findByText(/আপলোড হয়েছে/);

    // Time passes the deadline before the student presses Submit.
    vi.advanceTimersByTime(2 * MINUTE);
    await user.click(screen.getByRole("button", { name: "খাতা জমা দিন" }));
    await user.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "হ্যাঁ, জমা দিন" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("সময় শেষ, তাই আর খাতা জমা দেওয়া যাবে না।");
    expect(document.querySelector('input[type="file"]')).toBeNull();
    // The draft was on the server at the deadline, so the page says a script exists (the server submits it).
    expect(onClosed).toHaveBeenCalledWith(true);
  });
});
