import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import AnnouncementsPage from "@/app/(staff)/staff/announcements/page";
import { apiError, fixtures, renderWithProviders } from "../helpers";
import { mockApi } from "../helpers/mock-api";
import { categoryTree, examRow, packageRow, pageOf } from "./picker-data";
import "./copy-setup";

/*
 * The confirm dialog of the announcements form: the live recipient count from the preview endpoint, the note for
 * students who join later, the failed and loading count, and the exact count the toast shows after the send.
 */

const STUDENT = {
  id: "11111111-1111-4111-8111-111111111111",
  fullName: "Rahim Uddin",
  phone: "01711000002",
  email: null,
  studentCode: "STU-0001",
};

const emptyHistory = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

type Call = { method: string; path: string; search: string; body: unknown };

/** The routes the page calls. `preview` answers the count, and `sent` the send's recipients. */
function routes(preview: unknown, sentRecipients = 5) {
  return {
    "GET /api/v1/live-exam-modules": (req: { url: URL }) => pageOf(req.url, [packageRow(1)]),
    "GET /api/v1/live-exams": (req: { url: URL }) => pageOf(req.url, [examRow(1), examRow(2)]),
    "GET /api/v1/live-exam-categories": categoryTree,
    "GET /api/v1/students": (req: { url: URL }) => pageOf(req.url, [STUDENT]),
    "GET /api/v1/announcements": emptyHistory,
    "GET /api/v1/announcements/preview": preview,
    "POST /api/v1/announcements": {
      id: "bbbbbbbb-0000-4000-8000-000000000009",
      audience: "ALL",
      recipients: sentRecipients,
      createdAt: "2026-11-01T06:00:00Z",
    },
  };
}

function renderPage() {
  return renderWithProviders(<AnnouncementsPage />, { user: fixtures.masterAdmin, locale: "en" });
}

function previewCalls(calls: Call[]) {
  return calls.filter((call) => call.method === "GET" && call.path === "/api/v1/announcements/preview");
}

/** Writes a title and opens the dialog for the audience the test picks. */
async function openDialog(person: ReturnType<typeof userEvent.setup>, audience: string) {
  await person.type(screen.getByLabelText("Title (English)"), "Exam moved");
  await person.click(screen.getByRole("radio", { name: audience }));
  return person;
}

async function pickPackageOne(person: ReturnType<typeof userEvent.setup>) {
  await person.click(await screen.findByRole("radio", { name: /^Package 1/ }));
}

async function sendFromDialog(person: ReturnType<typeof userEvent.setup>) {
  const dialog = await screen.findByRole("dialog");
  await person.click(within(dialog).getByRole("button", { name: "Send announcement" }));
}

describe("announcements dialog: the count comes from the preview", () => {
  it("a package names the package, the live count, and the note for students who join later", async () => {
    const { calls } = mockApi(routes({ recipients: 38 }));
    renderPage();
    const person = await openDialog(userEvent.setup(), "A package");
    await pickPackageOne(person);
    await person.click(screen.getByRole("button", { name: "Send announcement" }));

    const dialog = await screen.findByRole("dialog");
    expect(
      await within(dialog).findByText(
        "It goes to the students with access to Package 1. This will notify about 38 students. Students who join later will see it on the page. It cannot be changed after sending.",
      ),
    ).toBeInTheDocument();
    expect(within(dialog).queryByText(/Nobody will be notified now/)).not.toBeInTheDocument();

    const [preview] = previewCalls(calls);
    expect(preview?.search).toContain("audience=MODULE");
    expect(preview?.search).toContain("moduleId=p1");
  });

  it("an exam with nobody to notify says so, and still gives the note for later joiners", async () => {
    const { calls } = mockApi(routes({ recipients: 0 }));
    renderPage();
    const person = await openDialog(userEvent.setup(), "An exam");
    await person.click(await screen.findByRole("radio", { name: /^Mock exam 1/ }));
    await person.click(screen.getByRole("button", { name: "Send announcement" }));

    const dialog = await screen.findByRole("dialog");
    expect(
      await within(dialog).findByText(
        "It goes to the students with access to Mock exam 1. This will notify about 0 students. Students who join later will see it on the page. Nobody will be notified now. It cannot be changed after sending.",
      ),
    ).toBeInTheDocument();
    expect(previewCalls(calls)[0]?.search).toContain("examId=e1");
  });

  it("all students gives the count, with no note about later joiners", async () => {
    const { calls } = mockApi(routes({ recipients: 340 }));
    renderPage();
    const person = await openDialog(userEvent.setup(), "All students");
    await person.click(screen.getByRole("button", { name: "Send announcement" }));

    const dialog = await screen.findByRole("dialog");
    expect(
      await within(dialog).findByText("It goes to every student. This will notify about 340 students. It cannot be changed after sending."),
    ).toBeInTheDocument();
    expect(previewCalls(calls)[0]?.search).toContain("audience=ALL");
  });

  it("one recipient reads as one student", async () => {
    mockApi(routes({ recipients: 1 }));
    renderPage();
    const person = await openDialog(userEvent.setup(), "All students");
    await person.click(screen.getByRole("button", { name: "Send announcement" }));

    const dialog = await screen.findByRole("dialog");
    expect(await within(dialog).findByText(/This will notify about 1 student\./)).toBeInTheDocument();
  });

  it("says the count is on its way while the preview loads, and still lets the admin send", async () => {
    mockApi(routes(() => new Promise(() => undefined)));
    renderPage();
    const person = await openDialog(userEvent.setup(), "All students");
    await person.click(screen.getByRole("button", { name: "Send announcement" }));

    const dialog = await screen.findByRole("dialog");
    expect(await within(dialog).findByText(/Counting the students who will be notified/)).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Send announcement" })).toBeEnabled();
  });

  it("says the count is unknown when the preview fails, and the send still goes", async () => {
    const { calls } = mockApi(routes(apiError(500, "INTERNAL_ERROR", "Boom.")));
    renderPage();
    const person = await openDialog(userEvent.setup(), "All students");
    await person.click(screen.getByRole("button", { name: "Send announcement" }));

    const dialog = await screen.findByRole("dialog");
    expect(
      await within(dialog).findByText(/The number of students is not known right now\. You can still send it\./),
    ).toBeInTheDocument();
    await sendFromDialog(person);

    await waitFor(() => expect(calls.some((call) => call.method === "POST")).toBe(true));
  });

  it("a selected list names the chosen students and asks for no preview", async () => {
    const { calls } = mockApi(routes({ recipients: 99 }));
    renderPage();
    const person = await openDialog(userEvent.setup(), "Selected students");
    await person.type(screen.getByPlaceholderText("Name, email, student code or phone"), "Rah");
    await person.click(await screen.findByRole("button", { name: "Add to list" }));
    await person.click(screen.getByRole("button", { name: "Send announcement" }));

    const dialog = await screen.findByRole("dialog");
    expect(
      await within(dialog).findByText("It goes to the one student you chose. It cannot be changed after sending."),
    ).toBeInTheDocument();
    expect(previewCalls(calls)).toHaveLength(0);
  });
});

describe("announcements dialog: the toast and the reset", () => {
  it("shows the exact count the send returned, not the preview, and clears the form", async () => {
    mockApi(routes({ recipients: 5 }, 7));
    renderPage();
    const person = await openDialog(userEvent.setup(), "All students");
    await person.click(screen.getByRole("button", { name: "Send announcement" }));
    await sendFromDialog(person);

    expect(await screen.findByText("Announcement sent. 7 students received it.")).toBeInTheDocument();
    expect(screen.getByLabelText("Title (English)")).toHaveValue("");
  });

  it("uses the one-student wording in the toast", async () => {
    mockApi(routes({ recipients: 1 }, 1));
    renderPage();
    const person = await openDialog(userEvent.setup(), "All students");
    await person.click(screen.getByRole("button", { name: "Send announcement" }));
    await sendFromDialog(person);

    expect(await screen.findByText("Announcement sent. 1 student received it.")).toBeInTheDocument();
  });
});
