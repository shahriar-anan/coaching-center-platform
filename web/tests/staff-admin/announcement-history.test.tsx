import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import AnnouncementsPage from "@/app/(staff)/staff/announcements/page";
import { fixtures, renderWithProviders } from "../helpers";
import { apiError, mockApi } from "../helpers/mock-api";
import type { AnnouncementHistoryRow } from "@/lib/api/exam/staff-admin";
import { categoryTree } from "./picker-data";
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
      withdrawn: false,
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
      withdrawn: false,
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
    // A module announcement names its package; "সব শিক্ষার্থী" for everyone.
    expect(screen.getAllByText("প্যাকেজ: ১৯তম বিজেএস")[0]).toBeInTheDocument();
    expect(screen.getAllByText("সব শিক্ষার্থী")[0]).toBeInTheDocument();
    expect(screen.getAllByText("১২")[0]).toBeInTheDocument();
    expect(screen.getAllByText("৩৪০")[0]).toBeInTheDocument();
  });

  it("uses the English title in the English locale", async () => {
    mockApi({ "GET /api/v1/announcements": history, "GET /api/v1/live-exam-modules": modules });
    renderWithProviders(<AnnouncementsPage />, { user: fixtures.masterAdmin, locale: "en" });

    expect((await screen.findAllByText("Welcome"))[0]).toBeInTheDocument();
    expect(screen.getAllByText("Package: 19th BJS")[0]).toBeInTheDocument();
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

/* ---------- audience, status, filter and withdraw (English) ---------- */

const PACKAGE_ROW = "bbbbbbbb-0000-4000-8000-000000000011";
const EXAM_ROW = "bbbbbbbb-0000-4000-8000-000000000012";
const SELECTED_ROW = "bbbbbbbb-0000-4000-8000-000000000013";
const WITHDRAW_PATH = `/api/v1/announcements/${PACKAGE_ROW}/withdraw`;

/** Rows as the API returns them. The withdraw test changes the first row, as the server would. */
function sentRows(): AnnouncementHistoryRow[] {
  return [
    {
      id: PACKAGE_ROW,
      titleEn: "Exam moved",
      titleBn: "",
      audience: "MODULE",
      moduleId: "aaaaaaaa-0000-4000-8000-000000000001",
      moduleTitleEn: "19th BJS",
      moduleTitleBn: "১৯তম বিজেএস",
      createdAt: "2026-10-26T06:00:00Z",
      recipients: 12,
      withdrawn: false,
    },
    {
      id: EXAM_ROW,
      titleEn: "Mock exam postponed",
      titleBn: "",
      audience: "EXAM",
      examId: "eeeeeeee-0000-4000-8000-000000000002",
      examTitleEn: "Mock exam 2",
      examTitleBn: "মক এক্সাম ২",
      createdAt: "2026-10-25T06:00:00Z",
      recipients: 3,
      withdrawn: false,
    },
    {
      id: SELECTED_ROW,
      titleEn: "Bring your card",
      titleBn: "",
      audience: "SELECTED",
      createdAt: "2026-10-24T06:00:00Z",
      recipients: 2,
      withdrawn: true,
      withdrawnAt: "2026-10-27T08:30:00Z",
    },
  ];
}

function renderHistory() {
  renderWithProviders(<AnnouncementsPage />, { user: fixtures.masterAdmin, locale: "en" });
}

/** The history section, by its heading. The send form is above it on the same page. */
async function historySection() {
  const section = await screen.findByRole("region", { name: "Sent announcements" });
  // The rows are on their way at first; the tests read them only once the first one is on the page.
  await within(section).findAllByText("Exam moved");
  return section;
}

/** The table row that holds a title. The phone view repeats the rows as cards, so only table rows are searched. */
function rowWith(section: HTMLElement, title: string) {
  const row = within(section)
    .getAllByRole("row")
    .find((candidate) => candidate.textContent?.includes(title));
  if (!row) {
    throw new Error(`no row for ${title}`);
  }
  return row;
}

describe("announcement history: audience, target and status", () => {
  it("shows each audience with its target, the recipients, and the status", async () => {
    mockApi({
      "GET /api/v1/announcements": { content: sentRows(), page: 0, size: 20, totalElements: 3, totalPages: 1 },
      "GET /api/v1/live-exam-modules": modules,
      "GET /api/v1/live-exam-categories": categoryTree,
    });
    renderHistory();
    const section = await historySection();

    // Each row appears once in the table and once as a card on a phone, so the audience text is matched with getAll.
    expect((await within(section).findAllByText("Package: 19th BJS"))[0]).toBeInTheDocument();
    expect(within(section).getAllByText("Exam: Mock exam 2")[0]).toBeInTheDocument();
    expect(within(section).getAllByText("Selected students")[0]).toBeInTheDocument();

    expect(within(rowWith(section, "Exam moved")).getByText("Sent")).toBeInTheDocument();
    const withdrawn = rowWith(section, "Bring your card");
    expect(within(withdrawn).getByText("Withdrawn")).toBeInTheDocument();
    expect(within(withdrawn).getByText(/Withdrawn on /)).toBeInTheDocument();
    expect(within(withdrawn).queryByRole("button", { name: "Withdraw" })).not.toBeInTheDocument();
    expect(within(rowWith(section, "Exam moved")).getByRole("button", { name: "Withdraw" })).toBeInTheDocument();
  });

  it("the audience filter asks the server for that audience, from the first page", async () => {
    const { calls } = mockApi({
      "GET /api/v1/announcements": { content: sentRows(), page: 0, size: 20, totalElements: 3, totalPages: 1 },
      "GET /api/v1/live-exam-modules": modules,
      "GET /api/v1/live-exam-categories": categoryTree,
    });
    renderHistory();
    await historySection();
    const person = userEvent.setup();

    await person.selectOptions(screen.getByLabelText("Filter by audience"), "MODULE");

    await waitFor(() =>
      expect(
        calls.some((call) => call.method === "GET" && call.path === "/api/v1/announcements" && call.search.includes("audience=MODULE")),
      ).toBe(true),
    );
    const last = calls.filter((call) => call.path === "/api/v1/announcements").at(-1);
    expect(last?.search).toContain("page=0");
  });
});

describe("announcement history: withdraw", () => {
  it("asks first, and cancelling sends nothing", async () => {
    const { calls } = mockApi({
      "GET /api/v1/announcements": { content: sentRows(), page: 0, size: 20, totalElements: 3, totalPages: 1 },
      "GET /api/v1/live-exam-modules": modules,
      "GET /api/v1/live-exam-categories": categoryTree,
      [`PUT ${WITHDRAW_PATH}`]: () => sentRows()[0],
    });
    renderHistory();
    const section = await historySection();
    const person = userEvent.setup();

    await person.click(within(rowWith(section, "Exam moved")).getByRole("button", { name: "Withdraw" }));

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Withdraw this announcement?")).toBeInTheDocument();
    expect(
      within(dialog).getByText("Students will no longer see it on the page or in notifications. This cannot be undone."),
    ).toBeInTheDocument();
    await person.click(within(dialog).getByRole("button", { name: "Cancel" }));

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(calls.some((call) => call.method === "PUT")).toBe(false);
  });

  it("calls the endpoint once confirmed, and the row then shows Withdrawn", async () => {
    const rows = sentRows();
    const { calls } = mockApi({
      "GET /api/v1/announcements": () => ({ content: rows, page: 0, size: 20, totalElements: 3, totalPages: 1 }),
      "GET /api/v1/live-exam-modules": modules,
      "GET /api/v1/live-exam-categories": categoryTree,
      [`PUT ${WITHDRAW_PATH}`]: () => {
        rows[0] = { ...rows[0], withdrawn: true, withdrawnAt: "2026-10-27T08:30:00Z" };
        return rows[0];
      },
    });
    renderHistory();
    const section = await historySection();
    const person = userEvent.setup();

    await person.click(within(rowWith(section, "Exam moved")).getByRole("button", { name: "Withdraw" }));
    const dialog = await screen.findByRole("dialog");
    await person.click(within(dialog).getByRole("button", { name: "Withdraw" }));

    await waitFor(() => expect(calls.some((call) => call.method === "PUT" && call.path === WITHDRAW_PATH)).toBe(true));
    await waitFor(() => expect(within(rowWith(section, "Exam moved")).getByText("Withdrawn")).toBeInTheDocument());
    expect(within(rowWith(section, "Exam moved")).queryByRole("button", { name: "Withdraw" })).not.toBeInTheDocument();
    expect(within(rowWith(section, "Exam moved")).getByText(/Withdrawn on /)).toBeInTheDocument();
    expect(await screen.findByText("Announcement withdrawn. Students no longer see it.")).toBeInTheDocument();
  });

  it("keeps the row and says why when the withdraw fails", async () => {
    mockApi({
      "GET /api/v1/announcements": { content: sentRows(), page: 0, size: 20, totalElements: 3, totalPages: 1 },
      "GET /api/v1/live-exam-modules": modules,
      "GET /api/v1/live-exam-categories": categoryTree,
      [`PUT ${WITHDRAW_PATH}`]: () => apiError(403, "ACCESS_DENIED", "Denied."),
    });
    renderHistory();
    const section = await historySection();
    const person = userEvent.setup();

    await person.click(within(rowWith(section, "Exam moved")).getByRole("button", { name: "Withdraw" }));
    const dialog = await screen.findByRole("dialog");
    await person.click(within(dialog).getByRole("button", { name: "Withdraw" }));

    expect(await within(dialog).findByText("You do not have permission for this.")).toBeInTheDocument();
    // Radix hides the page behind an open dialog, so the dialog is closed before the row is read.
    await person.click(within(dialog).getByRole("button", { name: "Cancel" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(within(rowWith(section, "Exam moved")).getByRole("button", { name: "Withdraw" })).toBeInTheDocument();
  });
});
