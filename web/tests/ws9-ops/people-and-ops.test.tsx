import { Suspense } from "react";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import StudentPage from "@/app/(staff)/staff/students/[id]/page";
import StudentsPage from "@/app/(staff)/staff/students/page";
import PaymentsPage from "@/app/(staff)/staff/payments/page";
import ParticipationPage from "@/app/(staff)/staff/participation/page";
import { StudentExamHistory } from "@/components/staff/results/student-exam-history";
import { fixtures, renderWithProviders } from "../helpers";
import { mockApi } from "../helpers/mock-api";

const EXAM = "eeeeeeee-0000-4000-8000-000000000001";
const MODULE = "bbbbbbbb-0000-4000-8000-000000000001";
const STUDENT = "11111111-1111-4111-8111-000000000001";

/** A promise React reads at once, as Next does for route params, so `use()` does not suspend in a test. */
function resolved<T>(value: T): Promise<T> {
  const promise = Promise.resolve(value) as Promise<T> & { status?: string; value?: T };
  promise.status = "fulfilled";
  promise.value = value;
  return promise;
}

const rahim = {
  id: STUDENT,
  fullName: "Rahim",
  phone: "01711111111",
  email: "rahim@example.com",
  role: "STUDENT",
  status: "ACTIVE",
  studentCode: "STU-1001",
};

function pageOf<T>(content: T[], totalElements = content.length) {
  return { content, page: 0, size: 20, totalElements, totalPages: 1 };
}

const historyRows = [
  {
    participantId: "h1",
    examId: EXAM,
    examTitleEn: "Evidence Act",
    examTitleBn: "সাক্ষ্য আইন",
    moduleId: MODULE,
    moduleTitleEn: "Law",
    moduleTitleBn: "আইন",
    kind: "LIVE",
    status: "SUBMITTED",
    score: 9,
    maximum: 10,
    passed: true,
    submittedAt: "2026-10-24T06:35:00Z",
    published: true,
  },
  {
    participantId: "h2",
    examId: EXAM,
    examTitleEn: "Evidence Act",
    examTitleBn: "সাক্ষ্য আইন",
    moduleId: MODULE,
    moduleTitleEn: "Law",
    moduleTitleBn: "আইন",
    kind: "LIVE",
    status: "FAILED_TO_SUBMIT",
    score: null,
    maximum: 10,
    passed: null,
    submittedAt: null,
    published: true,
  },
];

describe("student exam history", () => {
  it("lists each sitting, and each exam title opens that exam's results report", async () => {
    mockApi({
      [`GET /api/v1/students/${STUDENT}/live-exam-results`]: pageOf(historyRows),
    });
    renderWithProviders(<StudentExamHistory studentId={STUDENT} />, { user: fixtures.masterAdmin, locale: "en" });

    const link = (await screen.findAllByRole("link", { name: "Evidence Act" }))[0];
    expect(link).toHaveAttribute("href", `/staff/exams/${EXAM}/results`);
    expect(screen.getAllByText("Not submitted").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("9 / 10").length).toBeGreaterThanOrEqual(1);
  });

  it("says the student has not sat an exam yet", async () => {
    mockApi({
      [`GET /api/v1/students/${STUDENT}/live-exam-results`]: pageOf([]),
    });
    renderWithProviders(<StudentExamHistory studentId={STUDENT} />, { user: fixtures.masterAdmin, locale: "en" });

    expect(await screen.findByText("This student has not sat any exam yet.")).toBeInTheDocument();
  });
});

describe("student page", () => {
  it("shows the exam history under the access list", async () => {
    mockApi({
      [`GET /api/v1/students/${STUDENT}`]: rahim,
      [`GET /api/v1/students/${STUDENT}/live-exam-results`]: pageOf(historyRows),
    });
    renderWithProviders(
      <Suspense fallback={null}>
        <StudentPage params={resolved({ id: STUDENT })} />
      </Suspense>,
      { user: fixtures.masterAdmin, locale: "en", route: `/staff/students/${STUDENT}` },
    );

    const heading = await screen.findByRole("heading", { name: "Exam history" });
    expect(heading).toBeInTheDocument();
    expect((await screen.findAllByRole("link", { name: "Evidence Act" })).length).toBeGreaterThan(0);
  });
});

describe("payments summary strip", () => {
  it("counts today's paid orders and their total, and the pending orders", async () => {
    const now = new Date().toISOString();
    const order = (id: string, amount: number, createdAt: string) => ({
      orderId: id,
      tranId: `tran-${id}`,
      status: "SUCCEEDED",
      amount,
      currency: "BDT",
      scope: "EXAM",
      moduleId: MODULE,
      moduleTitleEn: "Law",
      moduleTitleBn: "আইন",
      examId: EXAM,
      examTitleEn: "Evidence Act",
      examTitleBn: "সাক্ষ্য আইন",
      paymentChannel: "BKASH-BKash",
      valId: null,
      accessGranted: true,
      studentId: STUDENT,
      studentName: "Rahim",
      studentPhone: "01711111111",
      studentEmail: "rahim@example.com",
      studentCode: "STU-1001",
      createdAt,
      updatedAt: createdAt,
    });
    const paid = [order("a", 500, now), order("b", 300, now), order("c", 999, "2020-01-01T00:00:00Z")];

    const api = mockApi({
      "GET /api/v1/live-exam-orders": ({ url }: { url: URL }) => {
        const status = url.searchParams.get("status");
        if (status === "INITIATED") {
          return pageOf([], 3);
        }
        return pageOf(paid, paid.length);
      },
    });
    renderWithProviders(<PaymentsPage />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/payments" });

    const strip = (await screen.findByText("Successful today")).closest("dl") as HTMLElement;
    expect(await within(strip).findByText("2")).toBeInTheDocument();
    expect(await within(strip).findByText(/৳800/)).toBeInTheDocument();
    expect(await within(strip).findByText("3")).toBeInTheDocument();
    // The walk stops at the first order from before today, so the 2020 row is read but not counted.
    expect(api.calls.filter((call) => call.search.includes("status=SUCCEEDED")).length).toBe(1);
  });
});

describe("participation page", () => {
  const written = (extra: Record<string, unknown> = {}) => ({
    examId: EXAM,
    titleEn: "Evidence Act",
    titleBn: "সাক্ষ্য আইন",
    moduleId: MODULE,
    moduleTitleEn: "Law",
    moduleTitleBn: "আইন",
    examType: "WRITTEN",
    liveAt: "2026-10-24T06:00:00Z",
    endAt: "2026-10-24T07:00:00Z",
    sitting: 0,
    submitted: 4,
    failedToSubmit: 0,
    awaitingMarking: 0,
    draftsAwaitingPublish: 2,
    published: 2,
    retakes: 0,
    ...extra,
  });
  const idle = written({ examId: "e-idle", titleEn: "Old paper", titleBn: "পুরনো", draftsAwaitingPublish: 0, published: 4 });

  it("gives the Master Admin a Release results button that opens the release panel", async () => {
    mockApi({ "GET /api/v1/live-exam-participation": pageOf([written(), idle]) });
    renderWithProviders(<ParticipationPage />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/participation" });

    expect((await screen.findAllByText("2 to release")).length).toBeGreaterThan(0);
    const buttons = screen.getAllByRole("link", { name: "Release results" });
    expect(buttons[0]).toHaveAttribute("href", `/staff/exams/${EXAM}/results#release`);
    // Only the exam with something to release has the button.
    expect(screen.getAllByRole("link", { name: "View results" }).length).toBeGreaterThan(0);
    expect(screen.getAllByText("All released").length).toBeGreaterThan(0);
    expect(screen.getByRole("tab", { name: /To release/ })).toHaveTextContent("2");
  });

  it("shows the System Admin the same step with no release button", async () => {
    mockApi({ "GET /api/v1/live-exam-participation": pageOf([written()]) });
    renderWithProviders(<ParticipationPage />, { user: fixtures.systemAdmin, locale: "en", route: "/staff/participation" });

    expect((await screen.findAllByText("2 waiting for the Master Admin to release")).length).toBeGreaterThan(0);
    expect(screen.queryByRole("link", { name: "Release results" })).toBeNull();
  });

  it("the To release tab keeps only the exams that have papers to release", async () => {
    mockApi({ "GET /api/v1/live-exam-participation": pageOf([written(), idle]) });
    renderWithProviders(<ParticipationPage />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: "/staff/participation?focus=release",
    });

    expect((await screen.findAllByText("Evidence Act")).length).toBeGreaterThan(0);
    expect(screen.queryByText("Old paper")).toBeNull();
  });

  describe("the Drafts tab", () => {
    const draftModule = (extra: Record<string, unknown>) => ({
      id: "m-draft",
      categoryId: "c1",
      titleEn: "New module",
      titleBn: "নতুন মডিউল",
      status: "DRAFT",
      finallySaved: true,
      position: 0,
      mcqCount: 1,
      writtenCount: 2,
      ...extra,
    });
    const exam = (extra: Record<string, unknown>) => ({
      id: "x-draft",
      moduleId: "m-pub",
      examType: "WRITTEN",
      titleEn: "Draft paper",
      titleBn: "খসড়া পত্র",
      liveAt: "2026-12-01T06:00:00Z",
      endAt: "2026-12-01T07:00:00Z",
      status: "DRAFT",
      finallySaved: false,
      ...extra,
    });
    const mocks = () =>
      mockApi({
        "GET /api/v1/live-exam-participation": pageOf([]),
        "GET /api/v1/live-exam-modules": pageOf([
          draftModule({}),
          draftModule({ id: "m-pub", titleEn: "Live module", titleBn: "চালু মডিউল", status: "PUBLISHED" }),
          draftModule({ id: "m-old", titleEn: "Old module", status: "ARCHIVED" }),
        ]),
        "GET /api/v1/live-exam-modules/m-pub/exams": pageOf([exam({}), exam({ id: "x-live", titleEn: "Live paper", status: "PUBLISHED" })]),
      });

    it("lists a draft module and a draft exam of a published module, and counts both on the tab", async () => {
      mocks();
      renderWithProviders(<ParticipationPage />, {
        user: fixtures.masterAdmin,
        locale: "en",
        route: "/staff/participation?focus=drafts",
      });

      expect((await screen.findAllByText("New module")).length).toBeGreaterThan(0);
      expect(screen.getAllByText("Draft paper").length).toBeGreaterThan(0);
      expect(screen.queryByText("Live paper")).toBeNull();
      expect(screen.queryByText("Old module")).toBeNull();
      expect(screen.getAllByRole("link", { name: "Open module" })[0]).toHaveAttribute("href", "/staff/modules/m-draft");
      expect(screen.getAllByRole("link", { name: "Open exam" })[0]).toHaveAttribute("href", "/staff/exams/x-draft");
      expect(screen.getAllByText("Ready to publish").length).toBeGreaterThan(0);
      expect(screen.getAllByText("Needs finishing").length).toBeGreaterThan(0);
      await waitFor(() => expect(screen.getByRole("tab", { name: /Drafts/ })).toHaveTextContent("2"));
    });

    it("says so when everything is published", async () => {
      mockApi({
        "GET /api/v1/live-exam-participation": pageOf([]),
        "GET /api/v1/live-exam-modules": pageOf([draftModule({ status: "PUBLISHED" })]),
        "GET /api/v1/live-exam-modules/m-draft/exams": pageOf([exam({ moduleId: "m-draft", status: "PUBLISHED" })]),
      });
      renderWithProviders(<ParticipationPage />, {
        user: fixtures.masterAdmin,
        locale: "en",
        route: "/staff/participation?focus=drafts",
      });
      expect(await screen.findByText("No drafts")).toBeInTheDocument();
    });
  });

  it("names the corrections tab and explains it, with the same words in the row", async () => {
    mockApi({
      "GET /api/v1/live-exam-participation": pageOf([written({ draftsAwaitingPublish: 2, correctionsWaiting: 1 })]),
    });
    renderWithProviders(<ParticipationPage />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: "/staff/participation?focus=changes",
    });

    expect(await screen.findByRole("tab", { name: /Corrected marks/ })).toBeInTheDocument();
    expect(screen.getByText(/changed these marks after the result was released/)).toBeInTheDocument();
    expect((await screen.findAllByText("2 to release, 1 of them corrected marks")).length).toBeGreaterThan(0);
  });

  it("keeps the table when the tab counts cannot load", async () => {
    mockApi({ "GET /api/v1/live-exam-participation": pageOf([written()]) });
    renderWithProviders(<ParticipationPage />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/participation" });
    expect((await screen.findAllByText("Evidence Act")).length).toBeGreaterThan(0);
  });
});

describe("students list row menu", () => {
  it("offers the next actions for a student, with delete only for Master Admin", async () => {
    const user = userEvent.setup();
    mockApi({
      "GET /api/v1/students": pageOf([rahim]),
    });
    renderWithProviders(<StudentsPage />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/students" });

    // The table renders a desktop and a stacked mobile view, so each row action appears twice.
    await user.click((await screen.findAllByRole("button", { name: "Actions for Rahim" }))[0]);
    const menu = await screen.findByRole("menu");
    expect(within(menu).getByRole("menuitem", { name: "Open the student" })).toHaveAttribute(
      "href",
      `/staff/students/${STUDENT}`,
    );
    expect(within(menu).getByRole("menuitem", { name: "Deactivate" })).toBeInTheDocument();
    expect(within(menu).getByRole("menuitem", { name: "Delete" })).toBeInTheDocument();
  });
});
