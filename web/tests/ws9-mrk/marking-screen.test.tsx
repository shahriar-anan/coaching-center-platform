import { describe, expect, it, vi } from "vitest";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import StaffSubmissionPage from "@/app/(staff)/staff/submissions/[participantId]/page";
import { PaperViews } from "@/components/staff/marking/paper-views";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { feed, MODULE_ID, notAllowed, paper, serveFeed } from "./fixtures";

// The PDF viewer loads pdf.js; these tests only read the text around it.
vi.mock("@/components/pdf/pdf-viewer", () => ({ PdfViewer: () => null }));

/** A promise React reads at once, as Next does for route params, so `use()` does not suspend in a test. */
function resolved<T>(value: T): Promise<T> {
  const promise = Promise.resolve(value) as Promise<T> & { status?: string; value?: T };
  promise.status = "fulfilled";
  promise.value = value;
  return promise;
}

const views = { answerId: "a1", studentName: "রহিম উদ্দিন", examId: "e1", examTitle: "রচনা পত্র", marked: false, markedAt: null };

describe("question paper while marking (G8)", () => {
  it("shows the answer tab first, and asks the server for the question paper only when its tab is opened", async () => {
    const api = mockApi({ "GET /api/v1/live-exams/e1/question-pdf": notAllowed() });
    renderWithProviders(<PaperViews {...views} />, { user: fixtures.instructor });
    expect(screen.getByRole("tab", { name: "খাতা" })).toHaveAttribute("aria-selected", "true");
    expect(api.calls.some((call) => call.path.endsWith("/question-pdf"))).toBe(false);

    await userEvent.click(screen.getByRole("tab", { name: "প্রশ্নপত্র" }));
    expect(await screen.findByText("এই প্রশ্নপত্র পাওয়া যাচ্ছে না। হয়তো এই পরীক্ষার জন্য কোনো ফাইল আপলোড করা হয়নি, অথবা আপনার অ্যাকাউন্টে এটি দেখার অনুমতি নেই।")).toBeInTheDocument();
  });

  it("gives an unassigned instructor the permission line, with no error stack or code", async () => {
    mockApi({ "GET /api/v1/live-exams/e1/question-pdf": notAllowed() });
    renderWithProviders(<PaperViews {...views} />, { user: fixtures.instructor });
    await userEvent.click(screen.getByRole("tab", { name: "প্রশ্নপত্র" }));
    const body = await screen.findByText("এই প্রশ্নপত্র পাওয়া যাচ্ছে না। হয়তো এই পরীক্ষার জন্য কোনো ফাইল আপলোড করা হয়নি, অথবা আপনার অ্যাকাউন্টে এটি দেখার অনুমতি নেই।");
    expect(within(body.parentElement!).queryByRole("alert")).toBeNull();
    expect(document.body.textContent).not.toContain("RESOURCE_NOT_FOUND");
  });
});

describe("marking screen titles (G2)", () => {
  it("shows the student and the exam from the paper, with no queue when no exam or package is given", async () => {
    const api = mockApi({
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
    expect(api.calls.some((call) => call.path === "/api/v1/live-exam-submissions")).toBe(false);
  });

  it("takes its queue from ?exam=, so the progress counts that exam's papers", async () => {
    const api = mockApi({
      "GET /api/v1/live-exam-participants/p1": paper(),
      "GET /api/v1/live-exam-submissions": serveFeed(),
    });
    renderWithProviders(<StaffSubmissionPage params={resolved({ participantId: "p1" })} />, {
      user: fixtures.instructor,
      route: "/staff/submissions/p1?exam=e1",
    });
    expect(await screen.findByText("৩টির মধ্যে ২টি মূল্যায়িত")).toBeInTheDocument();
    expect(api.calls.some((call) => call.path === "/api/v1/live-exam-submissions" && call.search.includes("examId=e1"))).toBe(true);
  });

  it("ignores a legacy ?m=undefined: no queue request, no progress line, nothing sent as undefined", async () => {
    const api = mockApi({
      "GET /api/v1/live-exam-participants/p1": paper(),
      "GET /api/v1/live-exam-submissions": serveFeed(),
    });
    renderWithProviders(<StaffSubmissionPage params={resolved({ participantId: "p1" })} />, {
      user: fixtures.instructor,
      route: "/staff/submissions/p1?m=undefined",
    });
    expect((await screen.findAllByText("রহিম উদ্দিন")).length).toBeGreaterThan(0);
    expect(api.calls.some((call) => call.path === "/api/v1/live-exam-submissions")).toBe(false);
    expect(api.calls.every((call) => !/undefined|null/.test(call.search))).toBe(true);
  });

  it("honours a legacy ?m= that is a real package id, filtering the queue by it", async () => {
    const api = mockApi({
      "GET /api/v1/live-exam-participants/p1": paper({ moduleId: MODULE_ID, moduleTitleEn: "Bank Job", moduleTitleBn: "ব্যাংক চাকরি" }),
      "GET /api/v1/live-exam-submissions": serveFeed(),
    });
    renderWithProviders(<StaffSubmissionPage params={resolved({ participantId: "p1" })} />, {
      user: fixtures.instructor,
      route: `/staff/submissions/p1?m=${MODULE_ID}`,
    });
    expect(await screen.findByRole("link", { name: "ব্যাংক চাকরি" })).toBeInTheDocument();
    expect(api.calls.some((call) => call.path === "/api/v1/live-exam-submissions" && call.search.includes(`moduleId=${MODULE_ID}`))).toBe(true);
  });

  it("links back to the category the list was on, when every paper in the list is marked", async () => {
    const marked = feed.filter((row) => row.status !== "AWAITING");
    mockApi({
      "GET /api/v1/live-exam-participants/p3": paper({ id: "p3", studentName: "সালমা খাতুন", published: true, evaluatedPdf: true }),
      "GET /api/v1/live-exam-submissions": serveFeed(marked),
    });
    renderWithProviders(<StaffSubmissionPage params={resolved({ participantId: "p3" })} />, {
      user: fixtures.instructor,
      route: "/staff/submissions/p3?exam=e1&cat=c-bcs",
    });
    const back = await screen.findByRole("link", { name: "এই তালিকার সব খাতা দেখা হয়েছে। তালিকায় ফিরুন" });
    expect(back).toHaveAttribute("href", "/staff/submissions?cat=c-bcs");
  });

  it("shows a paper another account may not see as unavailable, not as a failed list", async () => {
    mockApi({ "GET /api/v1/live-exam-participants/p1": () => notAllowed() });
    renderWithProviders(<StaffSubmissionPage params={resolved({ participantId: "p1" })} />, {
      user: fixtures.instructor,
      route: "/staff/submissions/p1",
    });
    expect(await screen.findByText("এই খাতাটি এই অ্যাকাউন্টে দেখা যাবে না।")).toBeInTheDocument();
    expect(screen.queryByText("এই তালিকা আনা যায়নি। ফিল্টার মুছে আবার চেষ্টা করুন।")).toBeNull();
  });
});
