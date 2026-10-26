import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import AnnouncementsPage from "@/app/(staff)/staff/announcements/page";
import { fixtures, renderWithProviders } from "../helpers";
import { apiError, mockApi } from "../helpers/mock-api";
import "./copy-setup";

const modules = {
  content: [{ id: "aaaaaaaa-0000-4000-8000-000000000001", titleEn: "19th BJS", titleBn: "১৯তম বিজেএস", status: "PUBLISHED" }],
  page: 0,
  size: 100,
  totalElements: 1,
  totalPages: 1,
};

/** The contract of `GET /api/v1/announcements`, newest first. */
const history = {
  content: [
    {
      id: "bbbbbbbb-0000-4000-8000-000000000002",
      titleEn: "Exam moved",
      titleBn: "",
      audience: "MODULE",
      moduleId: "aaaaaaaa-0000-4000-8000-000000000001",
      moduleTitleEn: "19th BJS",
      moduleTitleBn: "১৯তম বিজেএস",
      createdAt: "2026-10-26T06:00:00Z",
      recipients: 12,
    },
    {
      id: "bbbbbbbb-0000-4000-8000-000000000001",
      titleEn: "Welcome",
      titleBn: "স্বাগতম",
      audience: "ALL",
      moduleId: null,
      moduleTitleEn: null,
      moduleTitleBn: null,
      createdAt: "2026-10-20T06:00:00Z",
      recipients: 340,
    },
  ],
  page: 0,
  size: 20,
  totalElements: 2,
  totalPages: 1,
};

const emptyHistory = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

describe("announcement history", () => {
  it("lists sent announcements in the reader's language, with the fallback and the audience", async () => {
    mockApi({ "GET /api/v1/announcements": history, "GET /api/v1/live-exam-modules": modules });
    renderWithProviders(<AnnouncementsPage />, { user: fixtures.masterAdmin, locale: "bn" });

    // The Bangla title is used when there is one; a blank Bangla title falls back to English.
    expect((await screen.findAllByText("Exam moved"))[0]).toBeInTheDocument();
    expect((await screen.findAllByText("স্বাগতম"))[0]).toBeInTheDocument();
    // The module's name is the audience for a module announcement; "সব শিক্ষার্থী" for everyone.
    expect(screen.getAllByText("১৯তম বিজেএস")[0]).toBeInTheDocument();
    expect(screen.getAllByText("সব শিক্ষার্থী")[0]).toBeInTheDocument();
    expect(screen.getAllByText("১২")[0]).toBeInTheDocument();
    expect(screen.getAllByText("৩৪০")[0]).toBeInTheDocument();
  });

  it("uses the English title in the English locale", async () => {
    mockApi({ "GET /api/v1/announcements": history, "GET /api/v1/live-exam-modules": modules });
    renderWithProviders(<AnnouncementsPage />, { user: fixtures.masterAdmin, locale: "en" });

    expect((await screen.findAllByText("Welcome"))[0]).toBeInTheDocument();
    expect(screen.getAllByText("19th BJS")[0]).toBeInTheDocument();
    expect(screen.getAllByText("All students")[0]).toBeInTheDocument();
  });

  it("shows a loading state while the history is on its way", async () => {
    mockApi({
      "GET /api/v1/announcements": () => new Promise(() => undefined),
      "GET /api/v1/live-exam-modules": modules,
    });
    renderWithProviders(<AnnouncementsPage />, { user: fixtures.masterAdmin, locale: "bn" });

    expect(await screen.findByText("লোড হচ্ছে…")).toBeInTheDocument();
  });

  it("shows the empty state when nothing has been sent", async () => {
    mockApi({ "GET /api/v1/announcements": emptyHistory, "GET /api/v1/live-exam-modules": modules });
    renderWithProviders(<AnnouncementsPage />, { user: fixtures.masterAdmin, locale: "bn" });

    expect(await screen.findByText("এখনো কোনো ঘোষণা পাঠানো হয়নি।")).toBeInTheDocument();
  });

  it("shows the error state, and retries when asked", async () => {
    let attempts = 0;
    mockApi({
      "GET /api/v1/announcements": () => {
        attempts += 1;
        return attempts === 1 ? apiError(500, "INTERNAL_ERROR", "Boom.") : history;
      },
      "GET /api/v1/live-exam-modules": modules,
    });
    renderWithProviders(<AnnouncementsPage />, { user: fixtures.masterAdmin, locale: "bn" });
    const user = userEvent.setup();

    expect(await screen.findByText("ঘোষণার তালিকা আনা যায়নি।")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "আবার চেষ্টা করুন" }));

    expect((await screen.findAllByText("Exam moved"))[0]).toBeInTheDocument();
    expect(attempts).toBe(2);
  });

  it("reloads the history after an announcement is sent", async () => {
    let listed = 0;
    const { calls } = mockApi({
      "GET /api/v1/announcements": () => {
        listed += 1;
        return listed === 1 ? emptyHistory : history;
      },
      "GET /api/v1/live-exam-modules": modules,
      "POST /api/v1/announcements": {
        id: "bbbbbbbb-0000-4000-8000-000000000003",
        audience: "ALL",
        moduleId: null,
        recipients: 340,
        createdAt: "2026-10-26T06:00:00Z",
      },
    });
    renderWithProviders(<AnnouncementsPage />, { user: fixtures.masterAdmin, locale: "bn" });
    const user = userEvent.setup();

    expect(await screen.findByText("এখনো কোনো ঘোষণা পাঠানো হয়নি।")).toBeInTheDocument();
    await user.type(screen.getByLabelText("শিরোনাম (ইংরেজি)"), "Exam moved");
    await user.click(screen.getByRole("button", { name: "ঘোষণা পাঠান" }));
    await user.click(screen.getAllByRole("button", { name: "ঘোষণা পাঠান" }).at(-1)!);

    await waitFor(() => expect(calls.some((call) => call.method === "POST")).toBe(true));
    expect((await screen.findAllByText("Exam moved"))[0]).toBeInTheDocument();
    expect(listed).toBe(2);
  });
});
