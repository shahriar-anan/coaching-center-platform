import { describe, expect, it, vi } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SubmissionBrowser } from "@/components/staff/marking/submission-browser";
import { fixtures, mockApi, renderWithProviders, routerState } from "../helpers";
import { modules, paper, participation, serveFeed } from "./fixtures";

// The PDF viewer loads pdf.js; the browser does not show one.
vi.mock("@/components/pdf/pdf-viewer", () => ({ PdfViewer: () => null }));

const LISTS = {
  "GET /api/v1/live-exam-modules": { content: modules, page: 0, size: 100, totalElements: 2, totalPages: 1 },
  "GET /api/v1/live-exam-participation": { content: participation, page: 0, size: 100, totalElements: 2, totalPages: 1 },
};

describe("submissions browser: instructor view", () => {
  it("starts on the papers still waiting for a mark", async () => {
    mockApi({ ...LISTS, "GET /api/v1/live-exam-submissions": serveFeed() });
    renderWithProviders(<SubmissionBrowser />, { user: fixtures.instructor, route: "/staff/submissions" });
    expect((await screen.findAllByText("রহিম উদ্দিন")).length).toBeGreaterThan(0);
    expect(screen.queryByText("করিম মিয়া")).toBeNull();
    expect(screen.queryByText("সালমা খাতুন")).toBeNull();
  });

  it("shows the category, module and exam tree, and no bulk publish or tick boxes", async () => {
    mockApi({ ...LISTS, "GET /api/v1/live-exam-submissions": serveFeed() });
    renderWithProviders(<SubmissionBrowser />, { user: fixtures.instructor, route: "/staff/submissions" });
    expect(await screen.findByRole("button", { name: /সরকারি চাকরি/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /বিসিএস প্রিলিমিনারি/ })).toBeInTheDocument();
    expect(screen.queryByRole("checkbox")).toBeNull();
    expect(screen.queryByRole("button", { name: /সব খসড়া প্রকাশ করুন/ })).toBeNull();
    expect(screen.queryByRole("button", { name: /নির্বাচিতগুলো প্রকাশ করুন/ })).toBeNull();
  });

  it("opens a paper from its row, keeping the module in the link", async () => {
    mockApi({ ...LISTS, "GET /api/v1/live-exam-submissions": serveFeed() });
    renderWithProviders(<SubmissionBrowser />, { user: fixtures.instructor, route: "/staff/submissions" });
    await userEvent.click((await screen.findAllByText("রহিম উদ্দিন"))[0]!);
    expect(routerState.push).toHaveBeenCalledWith("/staff/submissions/p1?m=m1");
  });
});

describe("submissions browser: Master Admin view", () => {
  it("shows every status by default and the teacher column", async () => {
    mockApi({ ...LISTS, "GET /api/v1/live-exam-submissions": serveFeed() });
    renderWithProviders(<SubmissionBrowser />, { user: fixtures.masterAdmin, route: "/staff/submissions" });
    expect((await screen.findAllByText("সালমা খাতুন")).length).toBeGreaterThan(0);
    expect(screen.getAllByText("করিম মিয়া").length).toBeGreaterThan(0);
    expect(screen.getAllByText("নাসরিন সুলতানা").length).toBeGreaterThan(0);
  });

  it("lists every paper of a category by reading each module (the category view)", async () => {
    mockApi({ ...LISTS, "GET /api/v1/live-exam-submissions": serveFeed() });
    renderWithProviders(<SubmissionBrowser />, { user: fixtures.masterAdmin, route: "/staff/submissions?cat=c-root" });
    expect((await screen.findAllByText("রহিম উদ্দিন")).length).toBeGreaterThan(0);
    expect(screen.getAllByText("সালমা খাতুন").length).toBeGreaterThan(0);
  });

  it("offers bulk publish for the chosen exam, names the exam and count, and publishes once", async () => {
    const api = mockApi({
      ...LISTS,
      "GET /api/v1/live-exam-submissions": serveFeed(),
      "POST /api/v1/live-exams/e1/publish-results": { published: 1 },
    });
    renderWithProviders(<SubmissionBrowser />, {
      user: fixtures.masterAdmin,
      route: "/staff/submissions?exam=e1&module=m1",
    });
    await userEvent.click(await screen.findByRole("button", { name: "সব খসড়া প্রকাশ করুন (১)" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/রচনা পত্র/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "প্রকাশ করুন" }));

    await waitFor(() =>
      expect(api.calls.some((call) => call.method === "POST" && call.path === "/api/v1/live-exams/e1/publish-results")).toBe(true),
    );
    expect(await screen.findByText("১টি ফলাফল প্রকাশ হয়েছে।")).toBeInTheDocument();
  });

  it("publishes the ticked drafts one request each, in list order", async () => {
    const api = mockApi({
      ...LISTS,
      "GET /api/v1/live-exam-submissions": serveFeed(),
      "POST /api/v1/live-exam-participants/p2/publish": paper({ id: "p2", published: true }),
    });
    renderWithProviders(<SubmissionBrowser />, {
      user: fixtures.masterAdmin,
      route: "/staff/submissions?exam=e1&module=m1",
    });
    await userEvent.click((await screen.findAllByRole("checkbox", { name: "করিম মিয়া-এর খাতা বেছে নিন" }))[0]!);
    await userEvent.click(screen.getByRole("button", { name: "নির্বাচিতগুলো প্রকাশ করুন (১)" }));
    const dialog = await screen.findByRole("dialog");
    await userEvent.click(within(dialog).getByRole("button", { name: "প্রকাশ করুন" }));

    await waitFor(() =>
      expect(api.calls.filter((call) => call.path === "/api/v1/live-exam-participants/p2/publish")).toHaveLength(1),
    );
    expect(await screen.findByText("১টি প্রকাশ হয়েছে। ০টি হয়নি।")).toBeInTheDocument();
  });
});
