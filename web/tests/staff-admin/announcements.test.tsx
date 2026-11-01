import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import AnnouncementsPage from "@/app/(staff)/staff/announcements/page";
import type { Me } from "@/lib/api/exam/types";
import { fixtures, renderWithProviders } from "../helpers";
import { mockApi } from "../helpers/mock-api";
import { categoryTree, examRow, packageRow, pageOf } from "./picker-data";
import "./copy-setup";

/*
 * The announcements page: the four audiences, what each one sends, the validation, and the role gate. English copy, so
 * the labels read as they are written here. The confirm dialog and the history have their own files.
 */

const STUDENT = {
  id: "11111111-1111-4111-8111-111111111111",
  fullName: "Rahim Uddin",
  phone: "01711000002",
  email: null,
  studentCode: "STU-0001",
};

const emptyHistory = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

/** Every route the announcements page calls, answered with usable data. Tests replace what they need. */
function routes(extra: Record<string, unknown> = {}) {
  return {
    "GET /api/v1/live-exam-modules": (req: { url: URL }) => pageOf(req.url, [packageRow(1), packageRow(2)]),
    "GET /api/v1/live-exams": (req: { url: URL }) => pageOf(req.url, [examRow(1), examRow(2)]),
    "GET /api/v1/live-exam-categories": categoryTree,
    "GET /api/v1/students": (req: { url: URL }) => pageOf(req.url, [STUDENT]),
    "GET /api/v1/announcements": emptyHistory,
    "GET /api/v1/announcements/preview": { recipients: 5 },
    "POST /api/v1/announcements": {
      id: "bbbbbbbb-0000-4000-8000-000000000009",
      audience: "ALL",
      recipients: 5,
      createdAt: "2026-11-01T06:00:00Z",
    },
    ...extra,
  };
}

type Call = { method: string; path: string; search: string; body: unknown };

/** The announcements the mock received, in order. */
function sent(calls: Call[]) {
  return calls.filter((call) => call.method === "POST" && call.path === "/api/v1/announcements");
}

function renderPage(user: Me = fixtures.masterAdmin) {
  return renderWithProviders(<AnnouncementsPage />, { user, locale: "en" });
}

/** Confirms the send in the dialog that is open. */
async function confirmSend(person: ReturnType<typeof userEvent.setup>) {
  const dialog = await screen.findByRole("dialog");
  await person.click(within(dialog).getByRole("button", { name: "Send announcement" }));
}

/** The body every send carries besides its target. */
const base = { titleEn: "Exam moved", titleBn: "", bodyEn: "", bodyBn: "" };

describe("announcements page: what each audience sends", () => {
  it("asks for at least one of the four fields before it sends", async () => {
    const { calls } = mockApi(routes());
    renderPage();
    const person = userEvent.setup();

    await person.click(screen.getByRole("button", { name: "Send announcement" }));

    expect(await screen.findByText("Enter at least one title or message.")).toBeInTheDocument();
    expect(sent(calls)).toHaveLength(0);
  });

  it("sends to all students with no target", async () => {
    const { calls } = mockApi(routes());
    renderPage();
    const person = userEvent.setup();

    await person.type(screen.getByLabelText("Title (English)"), "Exam moved");
    await person.click(screen.getByRole("button", { name: "Send announcement" }));
    await confirmSend(person);

    await waitFor(() => expect(sent(calls)[0]?.body).toEqual({ ...base, audience: "ALL" }));
  });

  it("sends to a package with its moduleId, and no examId or studentIds", async () => {
    const { calls } = mockApi(routes());
    renderPage();
    const person = userEvent.setup();

    await person.type(screen.getByLabelText("Title (English)"), "Exam moved");
    await person.click(screen.getByRole("radio", { name: "A package" }));
    await person.click(await screen.findByRole("radio", { name: /^Package 1/ }));
    await person.click(screen.getByRole("button", { name: "Send announcement" }));
    await confirmSend(person);

    await waitFor(() => expect(sent(calls)[0]?.body).toEqual({ ...base, audience: "MODULE", moduleId: "p1" }));
  });

  it("sends to an exam with its examId, and no moduleId", async () => {
    const { calls } = mockApi(routes());
    renderPage();
    const person = userEvent.setup();

    await person.type(screen.getByLabelText("Title (English)"), "Exam moved");
    await person.click(screen.getByRole("radio", { name: "An exam" }));
    await person.click(await screen.findByRole("radio", { name: /^Mock exam 2/ }));
    await person.click(screen.getByRole("button", { name: "Send announcement" }));
    await confirmSend(person);

    await waitFor(() => expect(sent(calls)[0]?.body).toEqual({ ...base, audience: "EXAM", examId: "e2" }));
  });

  it("sends to the chosen students with their ids", async () => {
    const { calls } = mockApi(routes());
    renderPage();
    const person = userEvent.setup();

    await person.type(screen.getByLabelText("Title (English)"), "Exam moved");
    await person.click(screen.getByRole("radio", { name: "Selected students" }));
    await person.type(screen.getByPlaceholderText("Name, email, student code or phone"), "Rah");
    await person.click(await screen.findByRole("button", { name: "Add to list" }));
    await person.click(screen.getByRole("button", { name: "Send announcement" }));
    await confirmSend(person);

    await waitFor(() =>
      expect(sent(calls)[0]?.body).toEqual({ ...base, audience: "SELECTED", studentIds: [STUDENT.id] }),
    );
  });
});

describe("announcements page: the target is required", () => {
  it("needs a package when the audience is a package", async () => {
    const { calls } = mockApi(routes());
    renderPage();
    const person = userEvent.setup();

    await person.type(screen.getByLabelText("Title (English)"), "Exam moved");
    await person.click(screen.getByRole("radio", { name: "A package" }));
    await person.click(screen.getByRole("button", { name: "Send announcement" }));

    expect(await screen.findByText("Choose a package.")).toBeInTheDocument();
    expect(sent(calls)).toHaveLength(0);
  });

  it("needs an exam when the audience is an exam", async () => {
    const { calls } = mockApi(routes());
    renderPage();
    const person = userEvent.setup();

    await person.type(screen.getByLabelText("Title (English)"), "Exam moved");
    await person.click(screen.getByRole("radio", { name: "An exam" }));
    await person.click(screen.getByRole("button", { name: "Send announcement" }));

    expect(await screen.findByText("Choose an exam.")).toBeInTheDocument();
    expect(sent(calls)).toHaveLength(0);
  });

  it("needs at least one student when the audience is selected students", async () => {
    const { calls } = mockApi(routes());
    renderPage();
    const person = userEvent.setup();

    await person.type(screen.getByLabelText("Title (English)"), "Exam moved");
    await person.click(screen.getByRole("radio", { name: "Selected students" }));
    await person.click(screen.getByRole("button", { name: "Send announcement" }));

    expect(await screen.findByText("Add at least one student.")).toBeInTheDocument();
    expect(sent(calls)).toHaveLength(0);
  });
});

describe("announcements page: switching audience clears the other choice", () => {
  it("drops a chosen package when the audience becomes an exam, and again when it comes back", async () => {
    const { calls } = mockApi(routes());
    renderPage();
    const person = userEvent.setup();

    await person.type(screen.getByLabelText("Title (English)"), "Exam moved");
    await person.click(screen.getByRole("radio", { name: "A package" }));
    await person.click(await screen.findByRole("radio", { name: /^Package 1/ }));
    expect(screen.getByText("Chosen")).toBeInTheDocument();

    await person.click(screen.getByRole("radio", { name: "An exam" }));
    expect(screen.queryByText("Chosen")).not.toBeInTheDocument();

    await person.click(screen.getByRole("radio", { name: "A package" }));
    await person.click(screen.getByRole("button", { name: "Send announcement" }));

    // Nothing from the first choice is left: the package is asked for again.
    expect(await screen.findByText("Choose a package.")).toBeInTheDocument();
    expect(sent(calls)).toHaveLength(0);
  });

  it("empties the list of students when the audience changes away and back", async () => {
    mockApi(routes());
    renderPage();
    const person = userEvent.setup();

    await person.click(screen.getByRole("radio", { name: "Selected students" }));
    await person.type(screen.getByPlaceholderText("Name, email, student code or phone"), "Rah");
    await person.click(await screen.findByRole("button", { name: "Add to list" }));
    expect(screen.getByText("1 of 500 in the list")).toBeInTheDocument();

    await person.click(screen.getByRole("radio", { name: "All students" }));
    await person.click(screen.getByRole("radio", { name: "Selected students" }));

    expect(screen.getByText("0 of 500 in the list")).toBeInTheDocument();
    expect(screen.queryByText("Rahim Uddin")).not.toBeInTheDocument();
  });
});

describe("announcements page: who may open it", () => {
  it.each([
    ["Master Admin", fixtures.masterAdmin],
    ["System Admin", fixtures.systemAdmin],
  ])("opens for %s, with the form and the history", async (_role, user) => {
    mockApi(routes());
    renderPage(user);

    expect(screen.getByLabelText("Title (English)")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Send announcement" })).toBeInTheDocument();
    expect(await screen.findByText("No announcements have been sent yet.")).toBeInTheDocument();
  });

  it("refuses an instructor: no form, and no request is made", async () => {
    const { calls } = mockApi(routes());
    renderPage(fixtures.instructor);
    await new Promise((resolve) => setTimeout(resolve, 30));

    expect(screen.queryByLabelText("Title (English)")).not.toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });
});
