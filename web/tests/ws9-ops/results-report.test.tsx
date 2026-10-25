import { Suspense } from "react";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import ResultsPage from "@/app/(staff)/staff/exams/[examId]/results/page";
import { fixtures, renderWithProviders, type RenderWithProvidersOptions } from "../helpers";
import { mockApi } from "../helpers/mock-api";

const EXAM = "eeeeeeee-0000-4000-8000-000000000001";
const STUDENT_RAHIM = "11111111-1111-4111-8111-000000000001";
const STUDENT_NAZMA = "11111111-1111-4111-8111-000000000002";

/** A promise React reads at once, as Next does for route params, so `use()` does not suspend in a test. */
function resolved<T>(value: T): Promise<T> {
  const promise = Promise.resolve(value) as Promise<T> & { status?: string; value?: T };
  promise.status = "fulfilled";
  promise.value = value;
  return promise;
}

const writtenExam = {
  id: EXAM,
  moduleId: "bbbbbbbb-0000-4000-8000-000000000001",
  examType: "WRITTEN",
  titleEn: "Evidence Act",
  titleBn: "সাক্ষ্য আইন",
  liveAt: "2026-10-24T06:00:00Z",
  endAt: "2026-10-24T07:00:00Z",
  status: "PUBLISHED",
};

const summary = {
  participants: 3,
  submitted: 2,
  failedToSubmit: 1,
  averageScore: 7.5,
  highestScore: 9,
  lowestScore: 6,
  passCount: 2,
  passRate: 66.7,
  practiceParticipants: 0,
};

const rows = [
  {
    participantId: "p1",
    studentId: STUDENT_RAHIM,
    studentName: "Rahim",
    studentCode: "STU-1001",
    status: "SUBMITTED",
    kind: "LIVE",
    score: 9,
    maximum: 10,
    passed: true,
    startedAt: "2026-10-24T06:00:00Z",
    submittedAt: "2026-10-24T06:35:00Z",
    publishedAt: null,
  },
  {
    participantId: "p2",
    studentId: STUDENT_NAZMA,
    studentName: "Nazma",
    studentCode: null,
    status: "FAILED_TO_SUBMIT",
    kind: "LIVE",
    score: null,
    maximum: 10,
    passed: null,
    startedAt: "2026-10-24T06:00:00Z",
    submittedAt: null,
    publishedAt: null,
  },
];

function report(content: typeof rows) {
  return {
    summary,
    rows: { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 },
  };
}

function routes(content: typeof rows = rows, drafts = 2) {
  return {
    [`GET /api/v1/live-exams/${EXAM}`]: writtenExam,
    [`GET /api/v1/live-exams/${EXAM}/results`]: report(content),
    "GET /api/v1/live-exam-submissions": { content: [], page: 0, size: 1, totalElements: drafts, totalPages: 1 },
    [`POST /api/v1/live-exams/${EXAM}/publish-results`]: { published: drafts },
  };
}

type User = NonNullable<RenderWithProvidersOptions["user"]>;

function renderResults(user: User, route = `/staff/exams/${EXAM}/results`) {
  return renderWithProviders(
    <Suspense fallback={null}>
      <ResultsPage params={resolved({ examId: EXAM })} />
    </Suspense>,
    { user, locale: "en", route },
  );
}

describe("exam results report", () => {
  it("shows the summary, the rows and a failed-to-submit row for Master Admin", async () => {
    const api = mockApi(routes());
    renderResults(fixtures.masterAdmin);

    expect((await screen.findAllByText("Rahim")).length).toBeGreaterThan(0);
    expect(screen.getByText("Participants")).toBeInTheDocument();
    expect(screen.getByText("66.7%")).toBeInTheDocument();
    expect(screen.getAllByText("Not submitted").length).toBeGreaterThanOrEqual(1);
    expect((await screen.findAllByRole("link", { name: "Rahim" }))[0]).toHaveAttribute(
      "href",
      `/staff/students/${STUDENT_RAHIM}`,
    );
    expect(api.calls.some((call) => call.path === `/api/v1/live-exams/${EXAM}/results`)).toBe(true);
  });

  it("shows the bulk publish button with the draft count, only to Master Admin", async () => {
    mockApi(routes());
    renderResults(fixtures.masterAdmin);

    expect(await screen.findByRole("button", { name: "Publish all drafts (2)" })).toBeEnabled();
  });

  it("shows the same copy in Bangla, with Bengali digits in the publish count", async () => {
    mockApi(routes());
    renderWithProviders(
      <Suspense fallback={null}>
        <ResultsPage params={resolved({ examId: EXAM })} />
      </Suspense>,
      { user: fixtures.masterAdmin, locale: "bn", route: `/staff/exams/${EXAM}/results` },
    );

    expect(await screen.findByRole("button", { name: "সব খসড়া প্রকাশ করুন (২টি)" })).toBeEnabled();
    expect(screen.getByText("অংশগ্রহণকারী")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "পরীক্ষার ফলাফল" })).toBeInTheDocument();
  });

  it("hides the bulk publish button and the student links from an instructor", async () => {
    const api = mockApi(routes());
    renderResults(fixtures.instructor);

    expect((await screen.findAllByText("Rahim")).length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: /Publish all drafts/ })).toBeNull();
    expect(screen.queryByRole("link", { name: "Rahim" })).toBeNull();
    expect(screen.getByRole("link", { name: "Merit list" })).toBeInTheDocument();
    expect(api.calls.some((call) => call.path === "/api/v1/live-exam-submissions")).toBe(false);
  });

  it("keeps the filters in the URL and sends them to the API", async () => {
    const api = mockApi(routes());
    renderResults(fixtures.masterAdmin, `/staff/exams/${EXAM}/results?kind=LIVE&status=FAILED_TO_SUBMIT&sort=score,asc`);

    await screen.findAllByText("Rahim");
    const call = api.calls.find((item) => item.path === `/api/v1/live-exams/${EXAM}/results`);
    expect(call?.search).toContain("kind=LIVE");
    expect(call?.search).toContain("status=FAILED_TO_SUBMIT");
    expect(call?.search).toContain("sort=score%2Casc");
  });

  it("says there are no results yet when nobody has sat the exam", async () => {
    mockApi(routes([], 0));
    renderResults(fixtures.masterAdmin);

    expect(await screen.findByText("No results yet")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "No drafts to publish" })).toBeDisabled();
  });

  it("publishes every draft after a confirm that names the exam and the count", async () => {
    const user = userEvent.setup();
    const api = mockApi(routes());
    renderResults(fixtures.masterAdmin);

    await user.click(await screen.findByRole("button", { name: "Publish all drafts (2)" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Publish results for Evidence Act?")).toBeInTheDocument();
    expect(within(dialog).getByText(/2 written results will be published/)).toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: "Publish results" }));
    await waitFor(() =>
      expect(api.calls.some((call) => call.method === "POST" && call.path === `/api/v1/live-exams/${EXAM}/publish-results`)).toBe(true),
    );
    expect(await screen.findByText("2 results published.")).toBeInTheDocument();
  });
});
