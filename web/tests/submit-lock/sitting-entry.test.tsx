import { waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SittingEntry } from "@/components/student/sitting/sitting-entry";
import { fixtures, mockApi, renderWithProviders, routerState } from "../helpers";

// The paper is replaced by a probe, so a test can see whether it was mounted and call its callbacks.
const probe = vi.hoisted(() => ({
  paper: [] as { onSubmitted?: () => void; onClosed?: () => void }[],
  written: 0,
}));

vi.mock("@/components/student/sitting/mcq-paper", () => ({
  McqPaper: (props: { onSubmitted?: () => void; onClosed?: () => void }) => {
    probe.paper.push(props);
    return null;
  },
}));

vi.mock("@/components/student/sitting/written-sitting", () => ({
  WrittenSitting: () => {
    probe.written += 1;
    return null;
  },
}));

const HOUR = 60 * 60_000;

function studentExam(extra: Record<string, unknown>) {
  return {
    id: "e1",
    moduleId: "m1",
    examType: "MCQ",
    titleEn: "CPC MCQ",
    titleBn: "CPC MCQ",
    paperLanguage: "EN",
    questionPdf: null,
    questions: [
      {
        id: "q1",
        stem: "Question one",
        marks: 1,
        explanation: null,
        options: [{ id: "a", text: "one", correct: null, selected: null }],
      },
    ],
    myStatus: "IN_PROGRESS",
    answerStored: false,
    sitting: {
      kind: "LIVE",
      startedAt: new Date(Date.now() - HOUR).toISOString(),
      deadlineAt: new Date(Date.now() + HOUR).toISOString(),
      submittedAt: null,
    },
    ...extra,
  };
}

function mount(exam: Record<string, unknown>) {
  mockApi({ "GET /api/v1/live-exams/e1": exam });
  return renderWithProviders(<SittingEntry examId="e1" start={null} />, {
    user: fixtures.student,
    locale: "en",
    route: "/exam/e1/sit",
  });
}

beforeEach(() => {
  probe.paper = [];
  probe.written = 0;
});

describe("the sitting entry for a submitted MCQ", () => {
  it("sends a submitted MCQ to its result, and never mounts the paper", async () => {
    mount(
      studentExam({
        // The API omits the questions of a submitted MCQ while its review is closed.
        questions: undefined,
        reviewOpensAt: new Date(Date.now() + HOUR).toISOString(),
        sitting: { kind: "LIVE", startedAt: new Date(Date.now() - HOUR).toISOString(), deadlineAt: new Date(Date.now() + HOUR).toISOString(), submittedAt: new Date().toISOString() },
      }),
    );
    await waitFor(() => expect(routerState.replace).toHaveBeenCalledWith("/exam/e1/result"));
    expect(probe.paper).toHaveLength(0);
  });

  it("also sends a submitted MCQ to its result once the review is open, and never mounts the paper", async () => {
    mount(
      studentExam({
        sitting: { kind: "LIVE", startedAt: new Date(Date.now() - 2 * HOUR).toISOString(), deadlineAt: new Date(Date.now() - HOUR).toISOString(), submittedAt: new Date(Date.now() - 2 * HOUR).toISOString() },
      }),
    );
    await waitFor(() => expect(routerState.replace).toHaveBeenCalledWith("/exam/e1/result"));
    expect(probe.paper).toHaveLength(0);
  });

  it("mounts the paper for an MCQ that is still open and not submitted", async () => {
    mount(studentExam({}));
    await waitFor(() => expect(probe.paper.length).toBeGreaterThan(0));
    expect(routerState.replace).not.toHaveBeenCalledWith("/exam/e1/result");
  });

  it("the paper's submit goes to the result with the submitted notice", async () => {
    mount(studentExam({}));
    await waitFor(() => expect(probe.paper.length).toBeGreaterThan(0));
    probe.paper[probe.paper.length - 1].onSubmitted?.();
    expect(routerState.replace).toHaveBeenCalledWith("/exam/e1/result?notice=submitted");
  });

  it("the paper's time-up goes to the result with the time-up notice", async () => {
    mount(studentExam({}));
    await waitFor(() => expect(probe.paper.length).toBeGreaterThan(0));
    probe.paper[probe.paper.length - 1].onClosed?.();
    expect(routerState.replace).toHaveBeenCalledWith("/exam/e1/result?notice=timeUp");
  });
});

describe("the sitting entry for a written paper", () => {
  it("a script already stored goes to the submitted page, and the writing screen is not mounted", async () => {
    mount(
      studentExam({
        examType: "WRITTEN",
        questions: null,
        questionPdf: true,
        myStatus: "SUBMITTED",
        answerStored: true,
      }),
    );
    await waitFor(() => expect(routerState.replace).toHaveBeenCalledWith("/exam/e1/submitted"));
    expect(probe.written).toBe(0);
  });
});
