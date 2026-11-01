import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { DirectEnrolment } from "@/components/staff/enrol/direct-enrolment";
import { apiError, fixtures, mockApi, renderWithProviders } from "../helpers";
import { categoryTree, examRow, packageRow, pageOf } from "./picker-data";
import "./copy-setup";

const rafi = { id: "s1", fullName: "Rafi Ahmed", phone: "01711000001", email: null, studentCode: "STU-001" };
const EMPTY_PAGE = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

/** The routes a Direct enrolment screen reads: categories, the picker lists, one exam and one package, the students, and the list. */
function setup(routes: Record<string, unknown> = {}) {
  return mockApi({
    "GET /api/v1/live-exam-categories": categoryTree,
    "GET /api/v1/live-exam-modules": (req: { url: URL }) => pageOf(req.url, [packageRow(1)]),
    "GET /api/v1/live-exam-modules/p1": packageRow(1, { titleEn: "BCS Preliminary", titleBn: "বিসিএস প্রিলি" }),
    "GET /api/v1/live-exam-modules/p1/enrolments": EMPTY_PAGE,
    "GET /api/v1/live-exams": (req: { url: URL }) => pageOf(req.url, [examRow(1), examRow(5)]),
    "GET /api/v1/live-exams/e5": examRow(5),
    "GET /api/v1/live-exams/e5/enrolments": EMPTY_PAGE,
    "GET /api/v1/students": { content: [rafi], page: 0, size: 10, totalElements: 1, totalPages: 1 },
    "POST /api/v1/live-exam-grants": {},
    ...routes,
  });
}

/** Finds Rafi through the student search and adds him to the list. */
async function addRafi() {
  await userEvent.type(screen.getByRole("searchbox", { name: "Search" }), "Ra");
  await userEvent.click(await screen.findByRole("button", { name: "Add to list" }));
}

function grantPosts(calls: { method: string; path: string; body: unknown }[]) {
  return calls.filter((call) => call.method === "POST" && call.path === "/api/v1/live-exam-grants").map((call) => call.body);
}

describe("Direct enrolment: step 1 with the target picker", () => {
  it("starts with the exam from the URL chosen, and shows its title", async () => {
    const api = setup();
    renderWithProviders(<DirectEnrolment initialStudents={[]} initialExamId="e5" />, { user: fixtures.masterAdmin, locale: "en" });

    expect(await screen.findByText("Mock exam 5")).toBeInTheDocument();
    expect(screen.queryByRole("radiogroup")).not.toBeInTheDocument();
    expect(api.calls.some((call) => call.method === "GET" && call.path === "/api/v1/live-exams/e5")).toBe(true);
  });

  it("starts with the package from the URL chosen, and shows its title", async () => {
    setup();
    renderWithProviders(<DirectEnrolment initialStudents={[]} initialPackageId="p1" />, { user: fixtures.masterAdmin, locale: "en" });

    expect(await screen.findByText("BCS Preliminary")).toBeInTheDocument();
    expect(screen.queryByRole("radiogroup")).not.toBeInTheDocument();
  });

  it("shows the chosen line before the title loads, without showing the id", async () => {
    setup();
    renderWithProviders(<DirectEnrolment initialStudents={[]} initialExamId="e5" />, { user: fixtures.masterAdmin, locale: "en" });

    expect(screen.getByText("Chosen")).toBeInTheDocument();
    expect(screen.queryByText("e5")).not.toBeInTheDocument();
    await screen.findByText("Mock exam 5");
  });

  it("lists the packages by default, and the exams after the switch", async () => {
    setup();
    renderWithProviders(<DirectEnrolment initialStudents={[]} />, { user: fixtures.masterAdmin, locale: "en" });

    expect(await screen.findByRole("radio", { name: /Package 1/ })).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Exam" }));

    expect(await screen.findByRole("radio", { name: /^Mock exam 1(?!\d)/ })).toBeInTheDocument();
    expect(screen.queryByRole("radio", { name: /Package 1/ })).not.toBeInTheDocument();
  });

  it("asks for a package when none is chosen, and for an exam once the Exam tab is picked", async () => {
    const api = setup();
    renderWithProviders(<DirectEnrolment initialStudents={[]} />, { user: fixtures.masterAdmin, locale: "en" });
    await screen.findByRole("radio", { name: /Package 1/ });

    await addRafi();
    await userEvent.type(screen.getByRole("textbox", { name: /Reason/ }), "Walk-in");
    await userEvent.click(screen.getByRole("button", { name: "Enrol 1 student(s)" }));
    expect(await screen.findByText("Choose a package.")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Exam" }));
    await userEvent.click(screen.getByRole("button", { name: "Enrol 1 student(s)" }));
    expect(await screen.findByText("Choose an exam.")).toBeInTheDocument();
    expect(grantPosts(api.calls)).toHaveLength(0);
  });
});

describe("Direct enrolment: the enrol flow", () => {
  it("posts one grant per student into the chosen exam, with the same body as before", async () => {
    const api = setup();
    renderWithProviders(<DirectEnrolment initialStudents={[]} initialExamId="e5" />, { user: fixtures.masterAdmin, locale: "en" });
    await screen.findByText("Mock exam 5");

    await addRafi();
    await userEvent.type(screen.getByRole("textbox", { name: /Reason/ }), "Missed the batch");
    await userEvent.click(screen.getByRole("button", { name: "Enrol 1 student(s)" }));

    await waitFor(() => expect(grantPosts(api.calls)).toHaveLength(1));
    expect(grantPosts(api.calls)[0]).toEqual({ scope: "EXAM", examId: "e5", studentId: "s1", reason: "Missed the batch" });
  });

  it("posts a MODULE grant when a package is chosen from the list", async () => {
    const api = setup();
    renderWithProviders(<DirectEnrolment initialStudents={[]} />, { user: fixtures.masterAdmin, locale: "en" });

    await userEvent.click(await screen.findByRole("radio", { name: /Package 1/ }));
    expect(await screen.findByText("Package 1")).toBeInTheDocument();
    await addRafi();
    await userEvent.type(screen.getByRole("textbox", { name: /Reason/ }), "Paid by bank");
    await userEvent.click(screen.getByRole("button", { name: "Enrol 1 student(s)" }));

    await waitFor(() => expect(grantPosts(api.calls)).toHaveLength(1));
    expect(grantPosts(api.calls)[0]).toEqual({ scope: "MODULE", moduleId: "p1", studentId: "s1", reason: "Paid by bank" });
  });

  it("keeps a student whose grant failed on the list, with the reason beside them", async () => {
    setup({ "POST /api/v1/live-exam-grants": apiError(409, "GRANT_ALREADY_ACTIVE") });
    renderWithProviders(<DirectEnrolment initialStudents={[]} initialExamId="e5" />, { user: fixtures.masterAdmin, locale: "en" });
    await screen.findByText("Mock exam 5");

    await addRafi();
    await userEvent.type(screen.getByRole("textbox", { name: /Reason/ }), "Second try");
    await userEvent.click(screen.getByRole("button", { name: "Enrol 1 student(s)" }));

    expect(await screen.findByText("1 could not be enrolled. Their reason is shown beside them; fix it and enrol again.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Remove Rafi Ahmed from the list" })).toBeInTheDocument();
  });
});
