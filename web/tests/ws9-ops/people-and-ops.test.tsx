import { Suspense } from "react";
import { screen, within } from "@testing-library/react";
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

describe("participation summary", () => {
  it("keeps the table when the summary fails", async () => {
    mockApi({
      "GET /api/v1/live-exam-participation": pageOf([
        {
          examId: EXAM,
          titleEn: "Evidence Act",
          titleBn: "সাক্ষ্য আইন",
          moduleId: MODULE,
          moduleTitleEn: "Law",
          moduleTitleBn: "আইন",
          examType: "WRITTEN",
          liveAt: "2026-10-24T06:00:00Z",
          endAt: "2026-10-24T07:00:00Z",
          sitting: 3,
          submitted: 2,
          failedToSubmit: 1,
          awaitingMarking: 2,
          draftsAwaitingPublish: 0,
          published: 0,
          retakes: 0,
        },
      ]),
    });
    renderWithProviders(<ParticipationPage />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/participation" });

    expect(await screen.findByText("The summary could not load. The table below still works.")).toBeInTheDocument();
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
