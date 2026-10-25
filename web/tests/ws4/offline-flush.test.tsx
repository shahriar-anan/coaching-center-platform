import { act, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { McqPaper } from "@/components/student/sitting/mcq-paper";
import type { StudentExam } from "@/lib/api/exam/sitting";
import { mockApi, renderWithProviders } from "../helpers";

const exam = {
  id: "ex-1",
  moduleId: "m-1",
  examType: "MCQ",
  paperLanguage: "BN",
  titleEn: "CRPC",
  titleBn: "সিআরপিসি",
  descriptionEn: null,
  descriptionBn: null,
  rulesEn: null,
  rulesBn: null,
  liveAt: null,
  endAt: null,
  durationMinutes: 40,
  passPercent: 40,
  maximum: 1,
  status: "PUBLISHED",
  finallySaved: true,
  price: null,
  questionPdf: null,
  questions: [
    {
      id: "q1",
      stem: "প্রশ্ন ১",
      marks: 1,
      explanation: null,
      options: ["a", "b"].map((letter) => ({ id: `q1-${letter}`, text: `option ${letter}`, correct: null, selected: null })),
    },
  ],
  myStatus: "IN_PROGRESS",
  retakeAvailable: null,
  sitting: null,
} as unknown as StudentExam;

const deadline = new Date(Date.now() + 60 * 60_000).toISOString();

describe("offline answers flush when the connection returns", () => {
  let online = false;

  beforeEach(() => {
    window.localStorage.clear();
    online = false;
    vi.spyOn(navigator, "onLine", "get").mockImplementation(() => online);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("keeps the answer on this device while offline, then sends it once online", async () => {
    const { calls } = mockApi({ "PUT /api/v1/live-exams/ex-1/answers": () => undefined });
    renderWithProviders(
      <McqPaper exam={exam} studentId="stu-1" startedAt="2026-11-12T06:00:00Z" deadlineAt={deadline} kind="LIVE" onClosed={() => {}} />,
    );
    await userEvent.click(screen.getAllByRole("radio")[1]);
    expect(await screen.findByText("ইন্টারনেট সংযোগ নেই · উত্তর এই ডিভাইসে রাখা হচ্ছে")).toBeInTheDocument();
    expect(calls.filter((c) => c.method === "PUT")).toHaveLength(0);

    online = true;
    act(() => {
      window.dispatchEvent(new Event("online"));
    });
    await waitFor(() => expect(calls.filter((c) => c.method === "PUT").length).toBeGreaterThan(0));
  });
});
