import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ResultsHub } from "@/components/student/result/results-hub";
import { SittingEntry } from "@/components/student/sitting/sitting-entry";
import { apiUpload } from "@/lib/api/client";
import { catalogKeys } from "@/lib/api/exam/catalog";
import { sittingKeys, useSubmitExam, useUploadAnswer, type MyExamResult } from "@/lib/api/exam/sitting";
import { fixtures, mockApi, renderWithProviders, routerState } from "../helpers";

// The upload goes through XHR in the app; here it is a stub that stores the script.
vi.mock("@/lib/api/client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api/client")>();
  return { ...actual, apiUpload: vi.fn() };
});

const MINUTE = 60_000;

function sat(overrides: Partial<MyExamResult> = {}): MyExamResult {
  return {
    examId: "ex-1",
    titleEn: "CRPC",
    titleBn: "CRPC",
    examType: "MCQ",
    liveAt: new Date(Date.now() - 60 * MINUTE).toISOString(),
    passPercent: 40,
    status: "SUBMITTED",
    detail: "SUBMITTED",
    kind: "LIVE",
    attempts: 1,
    submittedAt: new Date().toISOString(),
    resultVisible: true,
    score: 1,
    maximum: 3,
    passed: false,
    ...overrides,
  };
}

function page(content: MyExamResult[]) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

/** Submits the MCQ with the id it is given. */
function SubmitProbe({ examId }: { examId: string }) {
  const submit = useSubmitExam(examId);
  return (
    <button type="button" onClick={() => void submit.mutateAsync()}>
      send submit
    </button>
  );
}

/** Uploads a script for the written exam with the id it is given. */
function UploadProbe({ examId }: { examId: string }) {
  const upload = useUploadAnswer(examId);
  return (
    <button type="button" onClick={() => void upload.mutateAsync(new File(["%PDF-1.4"], "script.pdf", { type: "application/pdf" }))}>
      send upload
    </button>
  );
}

const openMcq = (extra: Record<string, unknown> = {}) => ({
  id: "ex-1",
  moduleId: "m-1",
  examType: "MCQ",
  paperLanguage: "EN",
  titleEn: "CRPC",
  titleBn: "CRPC",
  questionPdf: null,
  questions: [
    {
      id: "q1",
      stem: "Question one",
      marks: 1,
      explanation: null,
      options: ["a", "b"].map((letter) => ({ id: `q1-${letter}`, text: `option ${letter}`, correct: null, selected: null })),
    },
  ],
  myStatus: "IN_PROGRESS",
  answerStored: false,
  sitting: {
    kind: "LIVE",
    startedAt: new Date(Date.now() - 60 * MINUTE).toISOString(),
    deadlineAt: new Date(Date.now() + 60 * MINUTE).toISOString(),
    submittedAt: null,
  },
  ...extra,
});

beforeEach(() => {
  routerState.replace.mockReset();
});

describe("the reads that show a finished sitting are refreshed at once", () => {
  it("a submit puts the MCQ under Results without a reload, and refreshes the sitting reads", async () => {
    const user = userEvent.setup();
    let submitted = false;
    mockApi({
      "GET /api/v1/me/exam-results": () => page(submitted ? [sat({ titleEn: "Submitted CRPC" })] : []),
      "GET /api/v1/me/reviews": [],
      "POST /api/v1/live-exams/ex-1/submit": () => {
        submitted = true;
        return { participantId: "p", score: 1, maximum: 3, passed: false, status: "SUBMITTED" };
      },
    });
    const { queryClient } = renderWithProviders(
      <>
        <ResultsHub />
        <SubmitProbe examId="ex-1" />
      </>,
      { user: fixtures.student, locale: "en" },
    );
    expect(await screen.findByText("No results yet")).toBeInTheDocument();
    const spy = vi.spyOn(queryClient, "invalidateQueries");

    await user.click(screen.getByRole("button", { name: "send submit" }));

    expect(await screen.findByText("Submitted CRPC")).toBeInTheDocument();
    expect(spy).toHaveBeenCalledWith({ queryKey: sittingKeys.exam("ex-1") });
    expect(spy).toHaveBeenCalledWith({ queryKey: sittingKeys.result("ex-1") });
    expect(spy).toHaveBeenCalledWith({ queryKey: ["sitting", "my-results"] });
    expect(spy).toHaveBeenCalledWith({ queryKey: catalogKeys.myExam("ex-1") });
    expect(spy).toHaveBeenCalledWith({ queryKey: ["catalog", "my-exams"] });
  });

  it("a written upload puts the script under Being marked at once, and refreshes the sitting reads", async () => {
    const user = userEvent.setup();
    let stored = false;
    vi.mocked(apiUpload).mockImplementation(async () => {
      stored = true;
    });
    mockApi({
      "GET /api/v1/me/exam-results": () =>
        page(
          stored
            ? [sat({ examId: "ex-w", titleEn: "Stored BJS", examType: "WRITTEN", status: "SUBMITTED", detail: "SUBMITTED", resultVisible: false, score: undefined, passed: undefined })]
            : [],
        ),
      "GET /api/v1/me/reviews": [],
    });
    const { queryClient } = renderWithProviders(
      <>
        <ResultsHub />
        <UploadProbe examId="ex-w" />
      </>,
      { user: fixtures.student, locale: "en" },
    );
    expect(await screen.findByText("No results yet")).toBeInTheDocument();
    const spy = vi.spyOn(queryClient, "invalidateQueries");

    await user.click(screen.getByRole("button", { name: "send upload" }));

    await user.click(screen.getByRole("tab", { name: /Being marked/ }));
    expect(await screen.findByText("Stored BJS")).toBeInTheDocument();
    await user.click(screen.getByRole("tab", { name: /In progress/ }));
    expect(screen.queryByText("Stored BJS")).not.toBeInTheDocument();
    expect(spy).toHaveBeenCalledWith({ queryKey: sittingKeys.exam("ex-w") });
    expect(spy).toHaveBeenCalledWith({ queryKey: ["sitting", "my-results"] });
    expect(spy).toHaveBeenCalledWith({ queryKey: catalogKeys.myExam("ex-w") });
  });

  it("the time running out refreshes the same reads and goes to the result with the time-up notice", async () => {
    mockApi({
      "GET /api/v1/live-exams/ex-1": openMcq({
        sitting: {
          kind: "LIVE",
          startedAt: new Date(Date.now() - 60 * MINUTE).toISOString(),
          deadlineAt: new Date(Date.now() + 300).toISOString(),
          submittedAt: null,
        },
      }),
      "PUT /api/v1/live-exams/ex-1/answers": () => undefined,
    });
    const { queryClient } = renderWithProviders(<SittingEntry examId="ex-1" start={null} />, {
      user: fixtures.student,
      locale: "en",
      route: "/exam/ex-1/sit",
    });
    const spy = vi.spyOn(queryClient, "invalidateQueries");

    await waitFor(() => expect(routerState.replace).toHaveBeenCalledWith("/exam/ex-1/result?notice=timeUp"), { timeout: 4000 });
    expect(spy).toHaveBeenCalledWith({ queryKey: sittingKeys.result("ex-1") });
    expect(spy).toHaveBeenCalledWith({ queryKey: ["sitting", "my-results"] });
    expect(spy).toHaveBeenCalledWith({ queryKey: catalogKeys.myExam("ex-1") });
  });

  it("the paper's submit refreshes the reads and goes to the result with the submitted notice", async () => {
    const user = userEvent.setup();
    mockApi({
      "GET /api/v1/live-exams/ex-1": openMcq(),
      "PUT /api/v1/live-exams/ex-1/answers": () => undefined,
      "POST /api/v1/live-exams/ex-1/submit": { participantId: "p", score: 0, maximum: 1, passed: false, status: "SUBMITTED" },
    });
    const { queryClient } = renderWithProviders(<SittingEntry examId="ex-1" start={null} />, {
      user: fixtures.student,
      locale: "bn",
      route: "/exam/ex-1/sit",
    });
    const radios = await screen.findAllByRole("radio");
    const spy = vi.spyOn(queryClient, "invalidateQueries");

    await user.click(radios[0]);
    await user.click(screen.getByRole("button", { name: "জমা দিন" }));
    await user.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "জমা দিন" }));

    await waitFor(() => expect(routerState.replace).toHaveBeenCalledWith("/exam/ex-1/result?notice=submitted"));
    // The last navigation is the one the student lands on, so it must carry the notice.
    await waitFor(() => expect(routerState.replace).toHaveBeenLastCalledWith("/exam/ex-1/result?notice=submitted"));
    expect(spy).toHaveBeenCalledWith({ queryKey: sittingKeys.exam("ex-1") });
    expect(spy).toHaveBeenCalledWith({ queryKey: ["sitting", "my-results"] });
  });
});
