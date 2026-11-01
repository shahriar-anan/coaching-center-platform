import { describe, expect, it, vi } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SubmissionBrowser } from "@/components/staff/marking/submission-browser";
import { apiError, fixtures, mockApi, renderWithProviders, routerState } from "../helpers";
import { participation, paper, serveFeed } from "./fixtures";

// The PDF viewer loads pdf.js; the browser does not show one.
vi.mock("@/components/pdf/pdf-viewer", () => ({ PdfViewer: () => null }));

const LISTS = {
  "GET /api/v1/live-exam-participation": { content: participation, page: 0, size: 100, totalElements: 3, totalPages: 1 },
};

/** The `search` of every feed request, in order. */
function feedSearches(calls: { path: string; search: string }[]): string[] {
  return calls.filter((call) => call.path === "/api/v1/live-exam-submissions").map((call) => call.search);
}

describe("submissions browser: instructor view", () => {
  it("starts on the papers still waiting for a mark", async () => {
    mockApi({ ...LISTS, "GET /api/v1/live-exam-submissions": serveFeed() });
    renderWithProviders(<SubmissionBrowser />, { user: fixtures.instructor, route: "/staff/submissions" });
    expect((await screen.findAllByText("রহিম উদ্দিন")).length).toBeGreaterThan(0);
    // An exam in no package still lists: its awaiting paper shows, with a dash for the package.
    expect((await screen.findAllByText("তানভীর আহমেদ")).length).toBeGreaterThan(0);
    expect(screen.queryByText("করিম মিয়া")).toBeNull();
    expect(screen.queryByText("সালমা খাতুন")).toBeNull();
  });

  it("counts the status tabs for the exam on screen, not for every exam", async () => {
    mockApi({ ...LISTS, "GET /api/v1/live-exam-submissions": serveFeed() });
    const { unmount } = renderWithProviders(<SubmissionBrowser />, {
      user: fixtures.instructor,
      route: "/staff/submissions?exam=e1",
    });
    const awaiting = await screen.findByRole("tab", { name: /মূল্যায়ন বাকি/ });
    await waitFor(() => expect(within(awaiting).getByText("৩")).toBeInTheDocument());
    expect(within(screen.getByRole("tab", { name: /প্রকাশের অপেক্ষায়/ })).getByText("১")).toBeInTheDocument();
    unmount();

    // Written test has no papers waiting, so its tab shows no count, even though the Essay paper has three.
    renderWithProviders(<SubmissionBrowser />, { user: fixtures.instructor, route: "/staff/submissions?exam=e2" });
    await screen.findByRole("button", { name: /লিখিত পরীক্ষা/, pressed: true });
    const empty = screen.getByRole("tab", { name: /মূল্যায়ন বাকি/ });
    expect(within(empty).queryByText("৩")).toBeNull();
  });

  it("shows the category tree with the uncategorised group, and no bulk publish or tick boxes", async () => {
    mockApi({ ...LISTS, "GET /api/v1/live-exam-submissions": serveFeed() });
    renderWithProviders(<SubmissionBrowser />, { user: fixtures.instructor, route: "/staff/submissions" });
    // A category starts closed; its toggle names the category, so the label button is the one not pressed.
    expect(await screen.findByRole("button", { name: /সরকারি চাকরি/, pressed: false })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "সরকারি চাকরি দেখান" }));
    await userEvent.click(screen.getByRole("button", { name: "বিসিএস দেখান" }));
    expect(screen.getByRole("button", { name: /রচনা পত্র/, pressed: false })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /শ্রেণিবিহীন/, pressed: false })).toBeInTheDocument();
    expect(screen.queryByRole("checkbox")).toBeNull();
    expect(screen.queryByRole("button", { name: /সব খসড়া প্রকাশ করুন/ })).toBeNull();
    expect(screen.queryByRole("button", { name: /নির্বাচিতগুলো প্রকাশ করুন/ })).toBeNull();
  });

  it("opens a paper from its student-name link, which names the exam and keeps the view", async () => {
    mockApi({ ...LISTS, "GET /api/v1/live-exam-submissions": serveFeed() });
    renderWithProviders(<SubmissionBrowser />, { user: fixtures.instructor, route: "/staff/submissions" });
    const link = (await screen.findAllByRole("link", { name: "রহিম উদ্দিন" }))[0]!;
    expect(link).toHaveAttribute("href", "/staff/submissions/p1?exam=e1");
  });

  it("chooses a category by writing it to the URL", async () => {
    mockApi({ ...LISTS, "GET /api/v1/live-exam-submissions": serveFeed() });
    renderWithProviders(<SubmissionBrowser />, { user: fixtures.masterAdmin, route: "/staff/submissions" });
    await userEvent.click(await screen.findByRole("button", { name: /সরকারি চাকরি/, pressed: false }));
    expect(routerState.replace).toHaveBeenCalledWith(expect.stringContaining("cat=c-root"), expect.anything());
  });

  it("opens a paper row by pushing the paper's own address", async () => {
    mockApi({ ...LISTS, "GET /api/v1/live-exam-submissions": serveFeed() });
    renderWithProviders(<SubmissionBrowser />, { user: fixtures.instructor, route: "/staff/submissions?exam=e1" });
    const row = (await screen.findAllByText("রহিম উদ্দিন"))[0]!;
    await userEvent.click(row.closest("tr")!);
    expect(routerState.push).toHaveBeenCalledWith("/staff/submissions/p1?exam=e1");
  });

  it("gives a refused list the request line, and a server fault the server line", async () => {
    mockApi({
      ...LISTS,
      "GET /api/v1/live-exam-submissions": () => apiError(400, "VALIDATION_FAILED", "Parameter examId is invalid."),
    });
    const { unmount } = renderWithProviders(<SubmissionBrowser />, { user: fixtures.instructor, route: "/staff/submissions" });
    expect(await screen.findByText("এই তালিকা আনা যায়নি। ফিল্টার মুছে আবার চেষ্টা করুন।")).toBeInTheDocument();
    unmount();

    mockApi({
      ...LISTS,
      "GET /api/v1/live-exam-submissions": () => apiError(500, "INTERNAL_ERROR", "An unexpected error occurred."),
    });
    renderWithProviders(<SubmissionBrowser />, { user: fixtures.instructor, route: "/staff/submissions" });
    expect(await screen.findByText("সার্ভারে একটি সমস্যা হয়েছে। একটু পরে আবার চেষ্টা করুন।")).toBeInTheDocument();
  });
});

describe("submissions browser: Master Admin view", () => {
  it("shows every status by default, and the teacher column from each exam's instructor", async () => {
    mockApi({ ...LISTS, "GET /api/v1/live-exam-submissions": serveFeed() });
    renderWithProviders(<SubmissionBrowser />, { user: fixtures.masterAdmin, route: "/staff/submissions" });
    expect((await screen.findAllByText("সালমা খাতুন")).length).toBeGreaterThan(0);
    expect(screen.getAllByText("করিম মিয়া").length).toBeGreaterThan(0);
    expect(screen.getAllByText("নাসরিন সুলতানা").length).toBeGreaterThan(0);
    // The exam with no instructor shows a dash, not a blank cell.
    expect(screen.getAllByText("—").length).toBeGreaterThan(0);
  });

  it("lists every paper of a category, its sub-categories included, with the category id in the request", async () => {
    const api = mockApi({ ...LISTS, "GET /api/v1/live-exam-submissions": serveFeed() });
    renderWithProviders(<SubmissionBrowser />, { user: fixtures.masterAdmin, route: "/staff/submissions?cat=c-root" });
    expect((await screen.findAllByText("রহিম উদ্দিন")).length).toBeGreaterThan(0);
    expect(screen.getAllByText("সালমা খাতুন").length).toBeGreaterThan(0);
    // The uncategorised exam is not in the Government jobs category.
    expect(screen.queryByText("তানভীর আহমেদ")).toBeNull();
    expect(feedSearches(api.calls).some((search) => search.includes("categoryId=c-root"))).toBe(true);
  });

  it("asks the feed for one exam when an exam is chosen", async () => {
    const api = mockApi({ ...LISTS, "GET /api/v1/live-exam-submissions": serveFeed() });
    renderWithProviders(<SubmissionBrowser />, { user: fixtures.masterAdmin, route: "/staff/submissions?exam=e1" });
    await waitFor(() => expect(feedSearches(api.calls).some((search) => search.includes("examId=e1"))).toBe(true));
  });

  it("links the chosen exam to its release panel, where the release gate and the counts are", async () => {
    mockApi({
      ...LISTS,
      "GET /api/v1/live-exam-submissions": serveFeed(),
    });
    renderWithProviders(<SubmissionBrowser />, {
      user: fixtures.masterAdmin,
      route: "/staff/submissions?exam=e1",
    });
    const link = await screen.findByRole("link", { name: "ফল প্রকাশ" });
    expect(link).toHaveAttribute("href", "/staff/exams/e1/results");
  });

  it("publishes the ticked drafts one request each, in list order", async () => {
    const api = mockApi({
      ...LISTS,
      "GET /api/v1/live-exam-submissions": serveFeed(),
      "POST /api/v1/live-exam-participants/p2/publish": paper({ id: "p2", published: true }),
    });
    renderWithProviders(<SubmissionBrowser />, {
      user: fixtures.masterAdmin,
      route: "/staff/submissions?exam=e1",
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

describe("no undefined or null in a request or a link", () => {
  it("ignores a legacy package value that is not an id, and sends no undefined or null", async () => {
    const api = mockApi({ ...LISTS, "GET /api/v1/live-exam-submissions": serveFeed() });
    renderWithProviders(<SubmissionBrowser />, {
      user: fixtures.masterAdmin,
      route: "/staff/submissions?module=undefined&exam=e1",
    });
    expect((await screen.findAllByText("সালমা খাতুন")).length).toBeGreaterThan(0);

    const searches = feedSearches(api.calls);
    expect(searches.length).toBeGreaterThan(0);
    for (const search of searches) {
      expect(search).not.toMatch(/undefined|null/);
    }
    const hrefs = screen.getAllByRole("link").map((link) => link.getAttribute("href") ?? "");
    for (const href of hrefs) {
      expect(href).not.toMatch(/undefined|null/);
    }
    expect(screen.queryByText(/প্যাকেজ:/)).toBeNull();
  });
});
