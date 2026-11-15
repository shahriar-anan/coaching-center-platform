import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { RoutineSection, monthLabel, monthOf, shiftMonth } from "@/components/student/my-admission/routine-section";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { ROUTINE_INSTRUCTOR, admission, routineClasses } from "./fixture";

// The PDF viewer loads a file in the browser. Here it only reports its label and asks for the file once.
vi.mock("@/components/pdf/pdf-viewer", async () => {
  const React = await import("react");
  return {
    PdfViewer: ({ load, label }: { load: () => Promise<Blob>; label?: string }) => {
      React.useEffect(() => {
        void load().catch(() => undefined);
      }, [load]);
      return React.createElement("div", { "aria-label": label }, "routine viewer");
    },
  };
});

const ROUTINE = "/api/v1/me/admission/routine";
const PDF = "/api/v1/me/admission/batch-routine.pdf";

/** The routine answers the classes of the month the request names, as the API does. */
function renderRoutine(classesFor: (month: string) => unknown = (month) => routineClasses(month)) {
  const api = mockApi({
    [`GET ${ROUTINE}`]: ({ url }: { url: URL }) => classesFor(url.searchParams.get("month") ?? monthOf(Date.now())),
    [`GET ${PDF}`]: { routine: true },
  });
  renderWithProviders(
    <RoutineSection admission={admission("ONBOARDED", { hasRoutine: true, batchNameEn: "Batch 3 (Evening)" })} />,
    { user: fixtures.student, locale: "en", route: "/my-admission" },
  );
  return api;
}

/** The agenda item of one class: the list item that holds its subject. */
async function classItem(subject: string): Promise<HTMLElement> {
  const text = await screen.findByText(subject);
  const item = text.closest<HTMLElement>("li[data-class]");
  if (!item) throw new Error(`no agenda item for ${subject}`);
  return item;
}

const routineRequests = (api: { calls: { path: string; search: string }[] }) =>
  api.calls.filter((call) => call.path === ROUTINE).map((call) => call.search);

describe("the month switcher (plan 8.5)", () => {
  it("opens on the current month and asks the routine for it", async () => {
    const api = renderRoutine();
    const current = monthOf(Date.now());

    expect(await classItem("Constitutional Law")).toBeInTheDocument();
    expect(screen.getByText(monthLabel(current, "en"))).toBeInTheDocument();
    expect(routineRequests(api)).toContain(`?month=${current}`);
    // One month ahead is allowed, since the next routine is often confirmed before the month starts.
    expect(screen.getByRole("button", { name: "Next month" })).toBeEnabled();
  });

  it("goes one month ahead and no further", async () => {
    const api = renderRoutine();
    const ahead = shiftMonth(monthOf(Date.now()), 1);

    await classItem("Constitutional Law");
    await userEvent.click(screen.getByRole("button", { name: "Next month" }));

    await waitFor(() => expect(routineRequests(api)).toContain(`?month=${ahead}`));
    expect(screen.getByText(monthLabel(ahead, "en"))).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Next month" })).toBeDisabled();
  });

  it("changes the request to the previous month when Previous month is pressed", async () => {
    const api = renderRoutine();
    const current = monthOf(Date.now());
    const previous = shiftMonth(current, -1);

    await classItem("Constitutional Law");
    await userEvent.click(screen.getByRole("button", { name: "Previous month" }));

    await waitFor(() => expect(routineRequests(api)).toContain(`?month=${previous}`));
    expect(screen.getByText(monthLabel(previous, "en"))).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Next month" })).toBeEnabled();
  });

  it("stops at the third month back", async () => {
    renderRoutine();
    const oldest = shiftMonth(monthOf(Date.now()), -3);

    await classItem("Constitutional Law");
    for (let step = 0; step < 3; step += 1) {
      await userEvent.click(screen.getByRole("button", { name: "Previous month" }));
    }
    expect(screen.getByText(monthLabel(oldest, "en"))).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Previous month" })).toBeDisabled();
  });
});

describe("the agenda (plan 8.5)", () => {
  it("shows each day under its weekday and date, and each class with its time, subject and topic", async () => {
    renderRoutine();
    const monthName = monthLabel(monthOf(Date.now()), "en").split(" ")[0];

    const lecture = await classItem("Constitutional Law");
    expect(screen.getByRole("heading", { level: 3, name: new RegExp(`^Thursday, 5 ${monthName}`) })).toBeInTheDocument();
    expect(within(lecture).getByText("6:00 PM – 7:10 PM")).toBeInTheDocument();
    expect(within(lecture).getByText("Fundamental rights")).toBeInTheDocument();

    // A class with no topic shows no topic line: the subject is its only paragraph.
    const noTopic = await classItem("Evidence Act");
    expect(within(noTopic).getByText("9:00 AM – 10:10 AM")).toBeInTheDocument();
    expect(noTopic.querySelectorAll("p")).toHaveLength(1);
  });

  it("marks an exam with the Exam pill and leaves a lecture without it", async () => {
    renderRoutine();

    const exam = await classItem("MCQ Exam");
    expect(within(exam).getByText("Exam")).toBeInTheDocument();

    const lecture = await classItem("Constitutional Law");
    expect(within(lecture).queryByText("Exam")).not.toBeInTheDocument();
  });

  it("says so when the month has no classes", async () => {
    renderRoutine(() => []);

    expect(await screen.findByText("No classes are scheduled for this month yet.")).toBeInTheDocument();
    expect(document.querySelector("li[data-class]")).toBeNull();
  });
});

describe("what the student never sees", () => {
  it("shows no instructor name and no amount, even when the response carries an instructor", async () => {
    renderRoutine();

    await classItem("Constitutional Law");
    expect(document.body).not.toHaveTextContent(ROUTINE_INSTRUCTOR);
    expect(document.body.textContent ?? "").not.toMatch(/৳/);
  });
});

describe("the PDF button", () => {
  it("opens the student copy of the month, with the month in the request", async () => {
    const api = renderRoutine();
    const current = monthOf(Date.now());

    await classItem("Constitutional Law");
    expect(screen.queryByText("routine viewer")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Open the PDF" }));

    expect(await screen.findByText("routine viewer")).toBeInTheDocument();
    await waitFor(() =>
      expect(api.calls.filter((call) => call.path === PDF).map((call) => call.search)).toContain(`?month=${current}`),
    );
  });
});

describe("hiding the routine (15 Nov)", () => {
  it("folds the routine to the next class and the next exam, remembers it, and shows it all again", async () => {
    const user = userEvent.setup();
    window.localStorage.removeItem("myAdmission.routineHidden");
    renderRoutine();
    expect(await classItem("Evidence Act")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Hide routine" }));
    // Only the next class and the next exam are left: the other classes, the month switcher and the PDF button are gone.
    expect(await screen.findByText("MCQ Exam")).toBeInTheDocument();
    expect(screen.queryByText("Evidence Act")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Next month" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Open the PDF" })).not.toBeInTheDocument();
    expect(window.localStorage.getItem("myAdmission.routineHidden")).toBe("1");

    await user.click(screen.getByRole("button", { name: "Show full routine" }));
    expect(await screen.findByRole("button", { name: "Next month" })).toBeInTheDocument();
    expect(window.localStorage.getItem("myAdmission.routineHidden")).toBe("0");
  });
});
