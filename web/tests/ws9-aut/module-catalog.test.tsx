import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ModuleList } from "@/components/staff/catalog/module-list";
import { ModuleExams } from "@/components/staff/catalog/module-exams";
import { ModuleSetup } from "@/components/staff/catalog/module-setup";
import { InstructorTab } from "@/components/staff/catalog/instructor-tab";
import type { StaffExam, StaffModule } from "@/lib/api/exam/staff-catalog";
import { fixtures, mockApi, renderWithProviders, routerState } from "../helpers";

const moduleId = "aaaaaaaa-0000-0000-0000-000000000001";
const categoryId = "cccccccc-0000-0000-0000-000000000001";

const moduleFixture: StaffModule = {
  id: moduleId,
  categoryId,
  titleEn: "19th BCS",
  titleBn: "১৯তম বিজেএস",
  descriptionEn: null,
  descriptionBn: null,
  accessMode: "MANUAL",
  status: "PUBLISHED",
  finallySaved: true,
  position: 1,
  price: 2500,
  instructor: null,
  mcqCount: 2,
  writtenCount: 1,
};

const categories = [
  {
    id: categoryId,
    parentId: null,
    titleEn: "BCS",
    titleBn: "বিজেএস",
    position: 1,
    archived: false,
    children: [],
    modules: [],
  },
];

function exam(partial: Partial<StaffExam> & Pick<StaffExam, "id" | "titleBn" | "examType">): StaffExam {
  return {
    moduleId,
    paperLanguage: "BN",
    titleEn: partial.titleBn,
    descriptionEn: null,
    descriptionBn: null,
    rulesEn: null,
    rulesBn: null,
    liveAt: null,
    endAt: null,
    durationMinutes: 40,
    passPercent: 40,
    maximum: 50,
    status: "DRAFT",
    finallySaved: false,
    price: null,
    questionPdf: null,
    questions: [],
    ...partial,
  };
}

describe("module list (G5)", () => {
  it("sends search and status to the server and shows the exam counts", async () => {
    const api = mockApi({
      "GET /api/v1/live-exam-categories": categories,
      "GET /api/v1/live-exam-modules": { content: [moduleFixture], page: 0, size: 100, totalElements: 1, totalPages: 1 },
    });
    renderWithProviders(<ModuleList />, { user: fixtures.masterAdmin });

    // The table renders a desktop row and a stacked card for each module, so the title appears twice.
    expect((await screen.findAllByText("১৯তম বিজেএস"))[0]).toBeInTheDocument();
    expect(screen.getAllByText("২")[0]).toBeInTheDocument();
    expect(screen.getAllByText("১")[0]).toBeInTheDocument();

    // The test router does not rewrite the URL, so check what the screen writes: the debounced search goes to the URL.
    await userEvent.type(screen.getByRole("searchbox"), "BCS");
    await waitFor(() => expect(routerState.replace.mock.calls.some((call) => String(call[0]).includes("q=BCS"))).toBe(true));
    expect(api.calls.some((call) => call.path === "/api/v1/live-exam-modules")).toBe(true);
  });

  it("shows a reset link while a filter is set, and clears every filter", async () => {
    mockApi({
      "GET /api/v1/live-exam-categories": categories,
      "GET /api/v1/live-exam-modules": { content: [moduleFixture], page: 0, size: 100, totalElements: 1, totalPages: 1 },
    });
    renderWithProviders(<ModuleList />, { user: fixtures.masterAdmin, route: "/staff/modules?status=DRAFT" });

    await userEvent.click(await screen.findByRole("button", { name: "ফিল্টার সরান" }));
    await waitFor(() => expect(routerState.replace.mock.calls.length + routerState.push.mock.calls.length).toBeGreaterThan(0));
    const written = [...routerState.replace.mock.calls, ...routerState.push.mock.calls].map((call) => String(call[0])).pop();
    expect(written).not.toContain("status=DRAFT");
  });
});

describe("module exams table (U6, G13)", () => {
  const dated = exam({ id: "e-dated", titleBn: "সিআরপিসি", examType: "MCQ", liveAt: "2026-11-12T06:00:00Z", price: 400, status: "PUBLISHED" });
  const undated = exam({ id: "e-undated", titleBn: "সিপিসি", examType: "WRITTEN", questionPdf: true, maximum: 100 });
  const empty = exam({ id: "e-empty", titleBn: "খসড়া", examType: "MCQ" });

  it("lists dated exams first, undated last, with the readiness of each", async () => {
    mockApi({
      // The server orders package exams by start, dated first and undated last (liveAt, nulls last).
      [`GET /api/v1/live-exam-modules/${moduleId}/exams`]: { content: [dated, undated, empty], page: 0, size: 20, totalElements: 3, totalPages: 1 },
    });
    renderWithProviders(<ModuleExams moduleId={moduleId} />, { user: fixtures.masterAdmin });

    const rows = await screen.findAllByRole("row");
    const names = rows.slice(1).map((row) => within(row).getAllByRole("link")[0].textContent);
    // The client keeps the server's order: it does not sort the rows again (the Exams tab is the server's list).
    expect(names).toEqual(["সিআরপিসি", "সিপিসি", "খসড়া"]);
    expect(screen.getAllByText("প্রশ্ন নেই")[0]).toBeInTheDocument();
    expect(screen.getAllByText("প্রশ্নপত্র আছে")[0]).toBeInTheDocument();
    expect(screen.getAllByText("৳৪০০")[0]).toBeInTheDocument();
  });

  it("copies an exam from its row menu and opens the copy", async () => {
    const api = mockApi({
      [`GET /api/v1/live-exam-modules/${moduleId}/exams`]: { content: [dated], page: 0, size: 20, totalElements: 1, totalPages: 1 },
      [`POST /api/v1/live-exams/${dated.id}/copy`]: exam({ id: "e-copy", titleBn: "সিআরপিসি (কপি)", examType: "MCQ" }),
    });
    renderWithProviders(<ModuleExams moduleId={moduleId} />, { user: fixtures.masterAdmin });

    await userEvent.click((await screen.findAllByRole("button", { name: "সিআরপিসি-এর কাজ" }))[0]);
    await userEvent.click(await screen.findByRole("menuitem", { name: "কপি করুন" }));
    await waitFor(() => expect(api.calls.some((call) => call.method === "POST" && call.path.endsWith("/copy"))).toBe(true));
  });
});

describe("module setup steps (U4)", () => {
  it("System Admin sees Master-Admin-only steps as information, never as a link", () => {
    mockApi({ [`GET /api/v1/live-exam-modules/${moduleId}/exams`]: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } });
    renderWithProviders(<ModuleSetup module={{ ...moduleFixture, status: "DRAFT", price: null }} />, { user: fixtures.systemAdmin });

    expect(screen.getAllByText("এটি শুধু মাস্টার অ্যাডমিন ঠিক করেন।", { exact: false })[0]).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "শিক্ষক" })).not.toBeInTheDocument();
    expect(screen.getByText("মডিউলটি এখনো প্রকাশ হয়নি, তাই শিক্ষার্থীরা এখনো দেখতে বা কিনতে পারবেন না।")).toBeInTheDocument();
  });

  it("Master Admin sees the teacher's name and that students can see and buy the module", () => {
    mockApi({ [`GET /api/v1/live-exam-modules/${moduleId}/exams`]: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } });
    renderWithProviders(
      <ModuleSetup module={{ ...moduleFixture, instructor: { id: "i1", name: "ফারিহা রহমান", phone: null } }} />,
      { user: fixtures.masterAdmin },
    );

    expect(screen.getByText("ফারিহা রহমান")).toBeInTheDocument();
    expect(screen.getByText("শিক্ষার্থীরা এখন মডিউলটি দেখতে ও কিনতে পারবেন।")).toBeInTheDocument();
  });
});

describe("instructor on the module (G4)", () => {
  it("shows the instructor from the module read, with remove for Master Admin", async () => {
    mockApi({
      [`GET /api/v1/live-exam-modules/${moduleId}`]: { ...moduleFixture, instructor: { id: "i1", name: "ফারিহা রহমান", phone: "01711000020" } },
    });
    renderWithProviders(<InstructorTab moduleId={moduleId} />, { user: fixtures.masterAdmin });

    expect(await screen.findByText("ফারিহা রহমান")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "সরান" })).toBeInTheDocument();
  });
});
