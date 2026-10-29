import { describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import { InstructorHome } from "@/components/staff/home/instructor-home";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { modules, participation, serveFeed } from "./fixtures";

vi.mock("@/components/pdf/pdf-viewer", () => ({ PdfViewer: () => null }));

const LISTS = {
  "GET /api/v1/live-exam-modules": { content: modules, page: 0, size: 100, totalElements: 2, totalPages: 1 },
  "GET /api/v1/live-exam-participation": { content: participation, page: 0, size: 100, totalElements: 2, totalPages: 1 },
};

describe("instructor home as a marking dashboard (U1)", () => {
  it("shows the work waiting, a start button to the oldest paper, and the recent papers", async () => {
    mockApi({ ...LISTS, "GET /api/v1/live-exam-submissions": serveFeed() });
    renderWithProviders(<InstructorHome />, { user: fixtures.instructor });

    const start = await screen.findByRole("link", { name: "মূল্যায়ন শুরু করুন" });
    expect(start).toHaveAttribute("href", "/staff/submissions/p1?m=m1");
    expect(screen.getByText("সাম্প্রতিক খাতা")).toBeInTheDocument();
    expect((await screen.findAllByText("সালমা খাতুন")).length).toBeGreaterThan(0);
  });

  it("groups the assigned modules by category path", async () => {
    mockApi({ ...LISTS, "GET /api/v1/live-exam-submissions": serveFeed() });
    renderWithProviders(<InstructorHome />, { user: fixtures.instructor });
    expect(await screen.findByRole("heading", { name: "সরকারি চাকরি › বিসিএস" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "সরকারি চাকরি" })).toBeInTheDocument();
  });

  it("disables the start button with a line when nothing waits", async () => {
    mockApi({
      ...LISTS,
      "GET /api/v1/live-exam-submissions": (req: { url: URL }) =>
        req.url.searchParams.get("status") === "AWAITING" ? { content: [], page: 0, size: 1, totalElements: 0, totalPages: 0 } : serveFeed()(req),
    });
    renderWithProviders(<InstructorHome />, { user: fixtures.instructor });
    expect(await screen.findByText("এখন কোনো খাতা বাকি নেই")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "মূল্যায়ন শুরু করুন" })).toBeDisabled();
  });

  it("states today's count from the marks saved today", async () => {
    mockApi({ ...LISTS, "GET /api/v1/live-exam-submissions": serveFeed() });
    renderWithProviders(<InstructorHome />, { user: fixtures.instructor });
    // The draft and the published paper were both marked by this teacher today.
    expect(await screen.findByText("আজ ২টি খাতা মূল্যায়ন করেছেন")).toBeInTheDocument();
  });

  it("hides today's count when the feed cannot be read in full", async () => {
    mockApi({
      ...LISTS,
      "GET /api/v1/live-exam-submissions": (req: { url: URL }) => {
        const page = serveFeed()(req);
        return { ...page, totalElements: 101 };
      },
    });
    renderWithProviders(<InstructorHome />, { user: fixtures.instructor });
    expect(await screen.findByText("সাম্প্রতিক খাতা")).toBeInTheDocument();
    expect(screen.queryByText(/মূল্যায়ন করেছেন/)).toBeNull();
  });

  it("shows the sentence to contact the Master Admin when no module is assigned", async () => {
    mockApi({
      "GET /api/v1/live-exam-modules": { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 },
      "GET /api/v1/live-exam-participation": { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 },
      "GET /api/v1/live-exam-submissions": serveFeed([]),
    });
    renderWithProviders(<InstructorHome />, { user: fixtures.instructor });
    expect(await screen.findByText("আপনাকে এখনো কোনো প্যাকেজ দেওয়া হয়নি")).toBeInTheDocument();
    expect(screen.getByText("আরও প্যাকেজে মূল্যায়ন করতে মাস্টার অ্যাডমিনের সাথে যোগাযোগ করুন।")).toBeInTheDocument();
  });
});
