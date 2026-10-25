import { describe, expect, it, vi } from "vitest";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import StaffSubmissionPage from "@/app/(staff)/staff/submissions/[participantId]/page";
import { QuestionPaperPanel } from "@/components/staff/marking/question-paper-panel";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { notAllowed, paper, serveFeed } from "./fixtures";

// The PDF viewer loads pdf.js; these tests only read the text around it.
vi.mock("@/components/pdf/pdf-viewer", () => ({ PdfViewer: () => null }));

/** A promise React reads at once, as Next does for route params, so `use()` does not suspend in a test. */
function resolved<T>(value: T): Promise<T> {
  const promise = Promise.resolve(value) as Promise<T> & { status?: string; value?: T };
  promise.status = "fulfilled";
  promise.value = value;
  return promise;
}

describe("question paper while marking (G8)", () => {
  it("stays closed, and asks the server for nothing, until the panel is opened", async () => {
    const api = mockApi({ "GET /api/v1/live-exams/e1/question-pdf": notAllowed() });
    renderWithProviders(<QuestionPaperPanel examId="e1" examTitle="রচনা পত্র" />, { user: fixtures.instructor });
    expect(api.calls.some((call) => call.path.endsWith("/question-pdf"))).toBe(false);

    await userEvent.click(screen.getByRole("button", { name: /প্রশ্নপত্র/ }));
    expect(await screen.findByText("এই প্রশ্নপত্র দেখার অনুমতি নেই।")).toBeInTheDocument();
  });

  it("gives an unassigned instructor the permission line, with no error stack or code", async () => {
    mockApi({ "GET /api/v1/live-exams/e1/question-pdf": notAllowed() });
    renderWithProviders(<QuestionPaperPanel examId="e1" examTitle="রচনা পত্র" />, { user: fixtures.instructor });
    await userEvent.click(screen.getByRole("button", { name: /প্রশ্নপত্র/ }));
    const body = await screen.findByText("এই প্রশ্নপত্র দেখার অনুমতি নেই।");
    expect(within(body.parentElement!).queryByRole("alert")).toBeNull();
    expect(document.body.textContent).not.toContain("RESOURCE_NOT_FOUND");
  });
});

describe("marking screen titles (G2)", () => {
  it("shows the student, the exam and the module from the paper, without ?m=", async () => {
    mockApi({
      "GET /api/v1/live-exam-participants/p1": paper(),
      "GET /api/v1/live-exam-submissions": serveFeed(),
    });
    renderWithProviders(<StaffSubmissionPage params={resolved({ participantId: "p1" })} />, {
      user: fixtures.instructor,
      route: "/staff/submissions/p1",
    });
    expect((await screen.findAllByText("রহিম উদ্দিন")).length).toBeGreaterThan(0);
    expect(screen.getAllByText("রচনা পত্র").length).toBeGreaterThan(0);
    expect(screen.queryByRole("link", { name: "বিসিএস প্রিলিমিনারি" })).toBeNull();
    expect(screen.getByRole("button", { name: "খসড়া নম্বর সেভ করুন" })).toBeInTheDocument();
  });

  it("keeps the module crumb and the queue when ?m= is given", async () => {
    mockApi({
      "GET /api/v1/live-exam-participants/p1": paper(),
      "GET /api/v1/live-exam-submissions": serveFeed(),
    });
    renderWithProviders(<StaffSubmissionPage params={resolved({ participantId: "p1" })} />, {
      user: fixtures.instructor,
      route: "/staff/submissions/p1?m=m1",
    });
    expect(await screen.findByRole("link", { name: "বিসিএস প্রিলিমিনারি" })).toBeInTheDocument();
    expect(screen.getByText("৩টির মধ্যে ২টি মূল্যায়িত")).toBeInTheDocument();
  });
});
